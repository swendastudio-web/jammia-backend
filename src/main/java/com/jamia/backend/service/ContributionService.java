package com.jamia.backend.service;

import com.jamia.backend.dto.ContributionResponse;
import com.jamia.backend.dto.OwedPaymentResponse;
import com.jamia.backend.entity.Contribution;
import com.jamia.backend.entity.ContributionStatus;
import com.jamia.backend.entity.MemberStatus;
import com.jamia.backend.entity.RoomRound;
import com.jamia.backend.exception.BusinessRuleException;
import com.jamia.backend.exception.ForbiddenActionException;
import com.jamia.backend.exception.ResourceNotFoundException;
import com.jamia.backend.repository.ContributionRepository;
import com.jamia.backend.repository.RoomMemberRepository;
import com.jamia.backend.repository.RoomRoundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Business rules for payment tracking. JAMIA does not move money:
 * the payer reports "I paid" and the receiver reports "I received it".
 * The turn moves by the clock even if someone has not paid; their payment then shows as late.
 */
@Service
public class ContributionService {

    private final ContributionRepository contributionRepository;
    private final RoomMemberRepository memberRepository;
    private final RoomRoundRepository roundRepository;
    private final Clock clock;

    public ContributionService(ContributionRepository contributionRepository,
                               RoomMemberRepository memberRepository,
                               RoomRoundRepository roundRepository,
                               Clock clock) {
        this.contributionRepository = contributionRepository;
        this.memberRepository = memberRepository;
        this.roundRepository = roundRepository;
        this.clock = clock;
    }

    // The payments of one round (default: the newest round). Only members can see them.
    @Transactional(readOnly = true)
    public List<ContributionResponse> getContributions(Long roomId, Long userId, Integer roundNumber) {
        requireMember(roomId, userId);

        RoomRound round = (roundNumber == null)
                ? roundRepository.findFirstByRoomIdOrderByRoundNumberDesc(roomId).orElse(null)
                : roundRepository.findByRoomIdAndRoundNumber(roomId, roundNumber)
                        .orElseThrow(() -> new ResourceNotFoundException("Round not found"));
        if (round == null) {
            return List.of(); // no round has started yet
        }

        LocalDateTime now = LocalDateTime.now(clock);
        return contributionRepository.findByRoundIdOrderByCycleNumberAscIdAsc(round.getId()).stream()
                .map(c -> toResponse(c, now))
                .toList();
    }

    // The payer says "I paid". Only the payer can do this (also after the turn or round has passed,
    // and also a member who was removed but still owes this payment).
    @Transactional
    public ContributionResponse markPaid(Long roomId, Long contributionId, Long userId) {
        Contribution contribution = contributionRepository.findByIdAndRoomId(contributionId, roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Contribution not found"));
        if (!contribution.getPayer().getUser().getId().equals(userId)) {
            requireMember(roomId, userId); // strangers get "not found"
            throw new ForbiddenActionException("Only the payer can mark this contribution as paid");
        }
        if (contribution.getStatus() != ContributionStatus.PENDING) {
            throw new BusinessRuleException("This contribution is already " + contribution.getStatus());
        }

        contribution.markPaid();
        return toResponse(contribution, LocalDateTime.now(clock));
    }

    // The receiver says "I received it". Only the receiver can do this.
    // They may confirm even if the payer forgot to press "I paid".
    @Transactional
    public ContributionResponse confirmReceived(Long roomId, Long contributionId, Long userId) {
        Contribution contribution = findInRoom(roomId, contributionId, userId);

        if (!contribution.getRecipient().getUser().getId().equals(userId)) {
            throw new ForbiddenActionException("Only the recipient can confirm this contribution");
        }
        if (contribution.getStatus() == ContributionStatus.CONFIRMED) {
            throw new BusinessRuleException("This contribution is already CONFIRMED");
        }

        contribution.confirm();
        return toResponse(contribution, LocalDateTime.now(clock));
    }

    // Reminders: everything the user still has to pay (turn started, not marked paid), in every room.
    @Transactional(readOnly = true)
    public List<OwedPaymentResponse> getOwedPayments(Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        return contributionRepository
                .findByPayerUserIdAndStatusAndDueAtLessThanEqualOrderByDueAtAsc(userId, ContributionStatus.PENDING, now)
                .stream()
                .map(c -> OwedPaymentResponse.from(c,
                        RoundSchedule.turnEndsAt(c.getRound(), c.getRoom().getFrequency(), c.getCycleNumber()), now))
                .toList();
    }

    private ContributionResponse toResponse(Contribution c, LocalDateTime now) {
        RoomRound round = c.getRound();
        LocalDateTime turnEndsAt = RoundSchedule.turnEndsAt(round, c.getRoom().getFrequency(), c.getCycleNumber());
        return ContributionResponse.from(c, turnEndsAt, now);
    }

    private Contribution findInRoom(Long roomId, Long contributionId, Long userId) {
        requireMember(roomId, userId);
        return contributionRepository.findByIdAndRoomId(contributionId, roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Contribution not found"));
    }

    // Non-members get "not found", so they can't even tell whether the room exists.
    private void requireMember(Long roomId, Long userId) {
        if (!memberRepository.existsByRoomIdAndUserIdAndStatus(roomId, userId, MemberStatus.ACTIVE)) {
            throw new ResourceNotFoundException("Room not found");
        }
    }
}
