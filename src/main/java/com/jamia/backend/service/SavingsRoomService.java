package com.jamia.backend.service;

import com.jamia.backend.dto.RoomResponse;
import com.jamia.backend.dto.RoomSummaryResponse;
import com.jamia.backend.dto.RoundResponse;
import com.jamia.backend.entity.Contribution;
import com.jamia.backend.entity.ContributionFrequency;
import com.jamia.backend.entity.ContributionStatus;
import com.jamia.backend.entity.JoinRequest;
import com.jamia.backend.entity.JoinRequestStatus;
import com.jamia.backend.entity.MemberStatus;
import com.jamia.backend.entity.RoomMember;
import com.jamia.backend.entity.RoomRound;
import com.jamia.backend.entity.RoomStatus;
import com.jamia.backend.entity.RoundStatus;
import com.jamia.backend.entity.SavingsRoom;
import com.jamia.backend.entity.TurnOrderMethod;
import com.jamia.backend.entity.User;
import com.jamia.backend.exception.BadRequestException;
import com.jamia.backend.exception.BusinessRuleException;
import com.jamia.backend.exception.ForbiddenActionException;
import com.jamia.backend.exception.ResourceNotFoundException;
import com.jamia.backend.exception.UserNotFoundException;
import com.jamia.backend.repository.ContributionRepository;
import com.jamia.backend.repository.JoinRequestRepository;
import com.jamia.backend.repository.RoomMemberRepository;
import com.jamia.backend.repository.RoomRoundRepository;
import com.jamia.backend.repository.SavingsRoomRepository;
import com.jamia.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Business rules for savings rooms, which run in ROUNDS:
 * OPEN (join, change size, remove, leave) -> admin starts a round -> ACTIVE (the turn moves by the clock)
 * -> the last turn ends -> OPEN again, everyone stays -> next round ...
 * Joining happens through invite links + the creator's approval (see InvitationService).
 * Methods return response objects because the database session closes when the service finishes
 * (open-in-view = false), so all data is loaded here.
 */
@Service
public class SavingsRoomService {

    private final SavingsRoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final ContributionRepository contributionRepository;
    private final RoomRoundRepository roundRepository;
    private final UserRepository userRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final boolean fiveMinuteCyclesEnabled;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public SavingsRoomService(SavingsRoomRepository roomRepository,
                              RoomMemberRepository memberRepository,
                              ContributionRepository contributionRepository,
                              RoomRoundRepository roundRepository,
                              UserRepository userRepository,
                              JoinRequestRepository joinRequestRepository,
                              @Value("${jamia.dev.five-minute-cycles}") boolean fiveMinuteCyclesEnabled,
                              Clock clock) {
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.contributionRepository = contributionRepository;
        this.roundRepository = roundRepository;
        this.userRepository = userRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.fiveMinuteCyclesEnabled = fiveMinuteCyclesEnabled;
        this.clock = clock;
    }

    // Creates a room. The creator becomes its first member (and the room admin).
    @Transactional
    public RoomResponse createRoom(Long creatorId, String name, String description, BigDecimal contributionAmount,
                                   String currency, ContributionFrequency frequency, int maxMembers) {
        if (frequency.isDevelopmentOnly() && !fiveMinuteCyclesEnabled) {
            throw new BadRequestException("The 5-minute period is only for testing and is not available here");
        }
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new UserNotFoundException(creatorId));
        checkPlanLimit(creator, maxMembers);

        SavingsRoom room = roomRepository.save(new SavingsRoom(name.trim(), description, contributionAmount,
                currency, frequency, maxMembers, creator));
        RoomMember creatorMembership = memberRepository.save(new RoomMember(room, creator));

        return RoomResponse.from(room, List.of(creatorMembership), null, 0);
    }

    // The rooms the user is an active member of.
    @Transactional(readOnly = true)
    public List<RoomSummaryResponse> getMyRooms(Long userId) {
        return memberRepository.findByUserIdAndStatusOrderByJoinedAtDesc(userId, MemberStatus.ACTIVE).stream()
                .map(membership -> RoomSummaryResponse.from(membership.getRoom(), userId))
                .toList();
    }

    // One room with its members and its current round. Only active members can see it.
    @Transactional(readOnly = true)
    public RoomResponse getRoom(Long roomId, Long userId) {
        SavingsRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        requireMember(roomId, userId);
        return toResponse(room);
    }

    // The admin starts a round: fixes the turn order and creates every payment of every turn.
    // During the round nobody can join or leave. Works again for round 2, 3 ... after each round ends.
    @Transactional
    public RoomResponse startRoom(Long roomId, Long userId, TurnOrderMethod method,
                                  LocalDate startDate, List<Long> manualOrder) {
        SavingsRoom room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        requireAdmin(room, userId);
        if (room.getStatus() != RoomStatus.OPEN) {
            throw new BusinessRuleException("A round is already running");
        }

        List<RoomMember> members = activeMembers(roomId);
        if (members.size() < 2) {
            throw new BusinessRuleException("A room needs at least 2 members to start");
        }
        List<RoomMember> turnOrder = decideTurnOrder(members, method, manualOrder);
        assignTurns(members, turnOrder);

        LocalDateTime startedAt = roundStart(room.getFrequency(), startDate);
        LocalDateTime endsAt = room.getFrequency().startOfCycle(startedAt, turnOrder.size() + 1);
        int roundNumber = (int) roundRepository.countByRoomId(roomId) + 1;
        RoomRound round = roundRepository.save(new RoomRound(room, roundNumber, method, startedAt, endsAt));

        room.startRound();
        contributionRepository.saveAll(buildContributionSchedule(room, round, turnOrder));

        // Nobody can join a running round, so requests still waiting are rejected.
        joinRequestRepository.findByRoomIdAndStatusOrderByCreatedAtAsc(roomId, JoinRequestStatus.PENDING)
                .forEach(JoinRequest::reject);

        return toResponse(room);
    }

    // Admin, between rounds: change how many members the room may have.
    @Transactional
    public RoomResponse updateMaxMembers(Long roomId, Long userId, int maxMembers) {
        SavingsRoom room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        requireAdmin(room, userId);
        requireBetweenRounds(room, "The number of members can only be changed after the round ends");
        checkPlanLimit(room.getCreator(), maxMembers);

        long current = memberRepository.countByRoomIdAndStatus(roomId, MemberStatus.ACTIVE);
        if (maxMembers < current) {
            throw new BusinessRuleException("The room already has " + current
                    + " members; remove members first or choose at least " + current);
        }
        room.setMaxMembers(maxMembers);
        return toResponse(room);
    }

    // Admin: remove a member (their payment history stays).
    // Between rounds: they simply leave the room.
    // During a round (Phase B):
    //  - they have NOT received yet -> their turn is removed, later turns move up, nobody pays them,
    //    and they stop owing for turns that have not started (what they already owe stays);
    //  - they HAVE received -> their turn stays and they keep owing everyone after them (reminded in the app).
    @Transactional
    public RoomResponse removeMember(Long roomId, Long userId, Long memberUserId) {
        SavingsRoom room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        requireAdmin(room, userId);
        if (room.isCreatedBy(memberUserId)) {
            throw new BusinessRuleException("The room admin cannot remove themselves");
        }
        RoomMember member = memberRepository.findByRoomIdAndUserId(roomId, memberUserId)
                .filter(RoomMember::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        if (room.getStatus() == RoomStatus.ACTIVE) {
            removeDuringRound(room, member);
        } else {
            member.end(MemberStatus.REMOVED);
        }
        return toResponse(room);
    }

    private void removeDuringRound(SavingsRoom room, RoomMember member) {
        RoomRound round = roundRepository.findFirstByRoomIdOrderByRoundNumberDesc(room.getId())
                .filter(r -> r.getStatus() == RoundStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Active room without a running round"));
        ContributionFrequency frequency = room.getFrequency();
        LocalDateTime now = LocalDateTime.now(clock);
        int turnCount = RoundSchedule.turnCount(round, frequency);
        int currentTurn = RoundSchedule.currentTurn(round, frequency, now);
        Integer memberTurn = member.getTurnPosition();
        boolean alreadyReceived = memberTurn == null || currentTurn >= memberTurn;

        member.end(MemberStatus.REMOVED); // clears their turn number
        memberRepository.flush();
        if (alreadyReceived) {
            return; // their payments to the people after them stay open: they still owe them
        }

        // 1. Their turn disappears, and they stop owing for turns that have not started yet.
        List<Contribution> payments = contributionRepository.findByRoundIdOrderByCycleNumberAscIdAsc(round.getId());
        List<Contribution> removed = payments.stream()
                .filter(c -> c.getCycleNumber() == memberTurn
                        || (c.getPayer().getId().equals(member.getId()) && c.getCycleNumber() > currentTurn))
                .toList();
        contributionRepository.deleteAll(removed);
        contributionRepository.flush();

        // 2. Everyone after them moves up one turn (one turn at a time: each turn number is unique).
        for (int turn = memberTurn + 1; turn <= turnCount; turn++) {
            int newTurn = turn - 1;
            LocalDateTime newDueAt = frequency.startOfCycle(round.getStartedAt(), newTurn);
            payments.stream()
                    .filter(c -> !removed.contains(c) && c.getCycleNumber() == newTurn + 1)
                    .forEach(c -> c.moveToTurn(newTurn, newDueAt));
            contributionRepository.flush();
        }
        for (RoomMember m : activeMembers(room.getId())) {
            if (m.getTurnPosition() != null && m.getTurnPosition() > memberTurn) {
                m.setTurnPosition(m.getTurnPosition() - 1);
                memberRepository.flush();
            }
        }

        // 3. The round is one period shorter.
        round.shortenTo(frequency.startOfCycle(round.getStartedAt(), turnCount));
    }

    // A member leaves the room, between rounds. The admin cannot leave (the room would have no admin).
    @Transactional
    public void leaveRoom(Long roomId, Long userId) {
        SavingsRoom room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        RoomMember member = memberRepository.findByRoomIdAndUserId(roomId, userId)
                .filter(RoomMember::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        if (room.isCreatedBy(userId)) {
            throw new BusinessRuleException("The room admin cannot leave the room");
        }
        requireBetweenRounds(room, "You can leave the room after the current round ends");

        member.end(MemberStatus.LEFT);
    }

    // The rounds history, newest first.
    @Transactional(readOnly = true)
    public List<RoundResponse> getRounds(Long roomId, Long userId) {
        SavingsRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        requireMember(roomId, userId);
        LocalDateTime now = LocalDateTime.now(clock);
        return roundRepository.findByRoomIdOrderByRoundNumberDesc(roomId).stream()
                .map(round -> toRoundResponse(room, round, now))
                .toList();
    }

    // Called by the clock (RoundClockJob): finish every running round whose last turn has ended.
    // The room becomes OPEN again and everyone stays for the next round.
    @Transactional
    public int completeFinishedRounds() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<RoomRound> finished = roundRepository.findByStatusAndEndsAtLessThanEqual(RoundStatus.ACTIVE, now);
        for (RoomRound round : finished) {
            round.complete(now);
            round.getRoom().finishRound();
            // Turns are decided again when the next round starts.
            activeMembers(round.getRoom().getId()).forEach(m -> m.setTurnPosition(null));
        }
        return finished.size();
    }

    // ----- helpers -----

    private RoomResponse toResponse(SavingsRoom room) {
        LocalDateTime now = LocalDateTime.now(clock);
        RoundResponse current = null;
        long completed = 0;
        for (RoomRound round : roundRepository.findByRoomIdOrderByRoundNumberDesc(room.getId())) {
            if (round.getStatus() == RoundStatus.ACTIVE) {
                current = toRoundResponse(room, round, now);
            } else {
                completed++;
            }
        }
        return RoomResponse.from(room, activeMembers(room.getId()), current, completed);
    }

    private RoundResponse toRoundResponse(SavingsRoom room, RoomRound round, LocalDateTime now) {
        ContributionFrequency frequency = room.getFrequency();
        int turnCount = RoundSchedule.turnCount(round, frequency);
        boolean running = round.getStatus() == RoundStatus.ACTIVE;
        int currentTurn = running ? RoundSchedule.currentTurn(round, frequency, now) : turnCount + 1;

        Long currentRecipient = null;
        LocalDateTime currentTurnEndsAt = null;
        if (currentTurn >= 1 && currentTurn <= turnCount) {
            currentTurnEndsAt = RoundSchedule.turnEndsAt(round, frequency, currentTurn);
            currentRecipient = contributionRepository.findByRoundIdOrderByCycleNumberAscIdAsc(round.getId()).stream()
                    .filter(c -> c.getCycleNumber() == currentTurn)
                    .map(c -> c.getRecipient().getUser().getId())
                    .findFirst()
                    .orElse(null);
        }

        return new RoundResponse(
                round.getRoundNumber(),
                round.getStatus(),
                round.getTurnOrderMethod(),
                round.getStartedAt(),
                round.getEndsAt(),
                round.getCompletedAt(),
                turnCount,
                currentTurn,
                currentRecipient,
                currentTurnEndsAt,
                contributionRepository.countByRoundId(round.getId()),
                contributionRepository.countByRoundIdAndStatus(round.getId(), ContributionStatus.CONFIRMED)
        );
    }

    // 5-minute test rooms start at once. Others start on the chosen day (today = now).
    private LocalDateTime roundStart(ContributionFrequency frequency, LocalDate startDate) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (frequency.isDevelopmentOnly()) {
            return now;
        }
        if (startDate == null) {
            throw new BadRequestException("Start date is required");
        }
        LocalDateTime startOfDay = startDate.atStartOfDay();
        return startOfDay.isAfter(now) ? startOfDay : now;
    }

    // The database allows each turn number only once per room, so old turns are cleared first.
    private void assignTurns(List<RoomMember> members, List<RoomMember> turnOrder) {
        members.forEach(m -> m.setTurnPosition(null));
        memberRepository.saveAllAndFlush(members);
        for (int i = 0; i < turnOrder.size(); i++) {
            turnOrder.get(i).setTurnPosition(i + 1);
        }
    }

    private List<RoomMember> decideTurnOrder(List<RoomMember> members, TurnOrderMethod method,
                                             List<Long> manualOrder) {
        if (method == TurnOrderMethod.RANDOM) {
            if (manualOrder != null && !manualOrder.isEmpty()) {
                throw new BadRequestException("memberOrder is only used with the MANUAL method");
            }
            List<RoomMember> shuffled = new ArrayList<>(members);
            // SecureRandom: an unpredictable shuffle, so nobody can influence who receives first.
            Collections.shuffle(shuffled, secureRandom);
            return shuffled;
        }

        // MANUAL: the list must contain every member's user id exactly once.
        Map<Long, RoomMember> membersByUserId = members.stream()
                .collect(Collectors.toMap(member -> member.getUser().getId(), Function.identity()));
        if (manualOrder == null
                || manualOrder.size() != members.size()
                || new HashSet<>(manualOrder).size() != manualOrder.size()
                || !membersByUserId.keySet().equals(Set.copyOf(manualOrder))) {
            throw new BadRequestException("memberOrder must list every member's user id exactly once");
        }
        return manualOrder.stream().map(membersByUserId::get).toList();
    }

    // Turn 1 goes to the first in the order, turn 2 to the second, ... Every other member pays the receiver.
    private List<Contribution> buildContributionSchedule(SavingsRoom room, RoomRound round,
                                                         List<RoomMember> turnOrder) {
        List<Contribution> schedule = new ArrayList<>();
        for (int turn = 1; turn <= turnOrder.size(); turn++) {
            RoomMember recipient = turnOrder.get(turn - 1);
            LocalDateTime dueAt = room.getFrequency().startOfCycle(round.getStartedAt(), turn);

            for (RoomMember payer : turnOrder) {
                if (payer != recipient) {
                    schedule.add(new Contribution(round, turn, dueAt, payer, recipient,
                            room.getContributionAmount()));
                }
            }
        }
        return schedule;
    }

    private void checkPlanLimit(User creator, int maxMembers) {
        int planLimit = creator.getSubscriptionPlan().getMaxMembersPerRoom();
        if (maxMembers > planLimit) {
            throw new BusinessRuleException("Your " + creator.getSubscriptionPlan().getCode()
                    + " plan allows rooms of up to " + planLimit + " members");
        }
    }

    private List<RoomMember> activeMembers(Long roomId) {
        return memberRepository.findByRoomIdAndStatusOrderByTurnPositionAscIdAsc(roomId, MemberStatus.ACTIVE);
    }

    // Non-members get "not found", so they can't even tell whether the room exists.
    private void requireMember(Long roomId, Long userId) {
        if (!memberRepository.existsByRoomIdAndUserIdAndStatus(roomId, userId, MemberStatus.ACTIVE)) {
            throw new ResourceNotFoundException("Room not found");
        }
    }

    private void requireAdmin(SavingsRoom room, Long userId) {
        requireMember(room.getId(), userId);
        if (!room.isCreatedBy(userId)) {
            throw new ForbiddenActionException("Only the room admin can do this");
        }
    }

    private void requireBetweenRounds(SavingsRoom room, String message) {
        if (room.getStatus() != RoomStatus.OPEN) {
            throw new BusinessRuleException(message);
        }
    }
}
