package com.jamia.backend.service;

import com.jamia.backend.dto.RoomResponse;
import com.jamia.backend.dto.RoomSummaryResponse;
import com.jamia.backend.entity.Contribution;
import com.jamia.backend.entity.ContributionFrequency;
import com.jamia.backend.entity.RoomMember;
import com.jamia.backend.entity.RoomStatus;
import com.jamia.backend.entity.SavingsRoom;
import com.jamia.backend.entity.TurnOrderMethod;
import com.jamia.backend.entity.User;
import com.jamia.backend.exception.BadRequestException;
import com.jamia.backend.exception.BusinessRuleException;
import com.jamia.backend.exception.ForbiddenActionException;
import com.jamia.backend.exception.ResourceNotFoundException;
import com.jamia.backend.exception.UserNotFoundException;
import com.jamia.backend.repository.ContributionRepository;
import com.jamia.backend.repository.RoomMemberRepository;
import com.jamia.backend.repository.SavingsRoomRepository;
import com.jamia.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Business rules for savings rooms: create, view, join, and start (turn order + contribution schedule).
 * Methods return response objects because the database session closes when the service finishes
 * (open-in-view = false), so all data is loaded here.
 */
@Service
public class SavingsRoomService {

    // Join codes avoid look-alike characters (0/O, 1/I/L) so they are easy to read out loud.
    private static final String JOIN_CODE_CHARACTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int JOIN_CODE_LENGTH = 8;

    private final SavingsRoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final ContributionRepository contributionRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public SavingsRoomService(SavingsRoomRepository roomRepository,
                              RoomMemberRepository memberRepository,
                              ContributionRepository contributionRepository,
                              UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.contributionRepository = contributionRepository;
        this.userRepository = userRepository;
    }

    // Creates a room. The creator becomes its first member.
    @Transactional
    public RoomResponse createRoom(Long creatorId, String name, String description, BigDecimal contributionAmount,
                                   String currency, ContributionFrequency frequency, int maxMembers) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new UserNotFoundException(creatorId));

        // The creator's plan decides how big the room may be. The limit comes from the database.
        int planLimit = creator.getSubscriptionPlan().getMaxMembersPerRoom();
        if (maxMembers > planLimit) {
            throw new BusinessRuleException("Your " + creator.getSubscriptionPlan().getCode()
                    + " plan allows rooms of up to " + planLimit + " members");
        }

        SavingsRoom room = roomRepository.save(new SavingsRoom(name.trim(), description, contributionAmount,
                currency, frequency, maxMembers, creator, newJoinCode()));
        RoomMember creatorMembership = memberRepository.save(new RoomMember(room, creator));

        return RoomResponse.from(room, List.of(creatorMembership));
    }

    // The rooms the user belongs to.
    @Transactional(readOnly = true)
    public List<RoomSummaryResponse> getMyRooms(Long userId) {
        return memberRepository.findByUserIdOrderByJoinedAtDesc(userId).stream()
                .map(membership -> RoomSummaryResponse.from(membership.getRoom(), userId))
                .toList();
    }

    // One room with its members. Only members can see it.
    @Transactional(readOnly = true)
    public RoomResponse getRoom(Long roomId, Long userId) {
        SavingsRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        requireMember(roomId, userId);
        return RoomResponse.from(room, memberRepository.findByRoomIdOrderByTurnPositionAscIdAsc(roomId));
    }

    // Joins a room using the code the creator shared.
    @Transactional
    public RoomResponse joinRoom(Long userId, String joinCode) {
        SavingsRoom room = roomRepository.findByJoinCodeForUpdate(joinCode)
                .orElseThrow(() -> new ResourceNotFoundException("No room found with this join code"));

        if (room.getStatus() != RoomStatus.OPEN) {
            throw new BusinessRuleException("This room has already started; nobody can join now");
        }
        if (memberRepository.existsByRoomIdAndUserId(room.getId(), userId)) {
            throw new BusinessRuleException("You are already a member of this room");
        }
        if (memberRepository.countByRoomId(room.getId()) >= room.getMaxMembers()) {
            throw new BusinessRuleException("This room is full");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        memberRepository.save(new RoomMember(room, user));

        return RoomResponse.from(room, memberRepository.findByRoomIdOrderByTurnPositionAscIdAsc(room.getId()));
    }

    // Starts the room: fixes the turn order and creates every contribution for every cycle.
    // After this, nobody can join.
    @Transactional
    public RoomResponse startRoom(Long roomId, Long userId, TurnOrderMethod method,
                                  LocalDate startDate, List<Long> manualOrder) {
        SavingsRoom room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found"));
        requireMember(roomId, userId);

        if (!room.isCreatedBy(userId)) {
            throw new ForbiddenActionException("Only the room creator can start the room");
        }
        if (room.getStatus() != RoomStatus.OPEN) {
            throw new BusinessRuleException("This room has already started");
        }

        List<RoomMember> members = memberRepository.findByRoomIdOrderByTurnPositionAscIdAsc(roomId);
        if (members.size() < 2) {
            throw new BusinessRuleException("A room needs at least 2 members to start");
        }

        List<RoomMember> turnOrder = decideTurnOrder(members, method, manualOrder);
        for (int i = 0; i < turnOrder.size(); i++) {
            turnOrder.get(i).setTurnPosition(i + 1);
        }

        room.start(method, startDate);
        contributionRepository.saveAll(buildContributionSchedule(room, turnOrder));

        return RoomResponse.from(room, turnOrder);
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

    // Cycle 1 goes to turn 1, cycle 2 to turn 2, ... Every other member pays the recipient each cycle.
    private List<Contribution> buildContributionSchedule(SavingsRoom room, List<RoomMember> turnOrder) {
        List<Contribution> schedule = new ArrayList<>();
        for (int cycle = 1; cycle <= turnOrder.size(); cycle++) {
            RoomMember recipient = turnOrder.get(cycle - 1);
            LocalDate dueDate = room.getFrequency().dueDateOfCycle(room.getStartDate(), cycle);

            for (RoomMember payer : turnOrder) {
                if (payer != recipient) {
                    schedule.add(new Contribution(room, cycle, dueDate, payer, recipient,
                            room.getContributionAmount()));
                }
            }
        }
        return schedule;
    }

    // Non-members get "not found", so they can't even tell whether the room exists.
    private void requireMember(Long roomId, Long userId) {
        if (!memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ResourceNotFoundException("Room not found");
        }
    }

    private String newJoinCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder(JOIN_CODE_LENGTH);
            for (int i = 0; i < JOIN_CODE_LENGTH; i++) {
                builder.append(JOIN_CODE_CHARACTERS.charAt(secureRandom.nextInt(JOIN_CODE_CHARACTERS.length())));
            }
            code = builder.toString();
        } while (roomRepository.existsByJoinCode(code));
        return code;
    }
}
