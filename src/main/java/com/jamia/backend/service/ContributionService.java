package com.jamia.backend.service;

import com.jamia.backend.dto.ContributionResponse;
import com.jamia.backend.entity.Contribution;
import com.jamia.backend.entity.ContributionStatus;
import com.jamia.backend.exception.BusinessRuleException;
import com.jamia.backend.exception.ForbiddenActionException;
import com.jamia.backend.exception.ResourceNotFoundException;
import com.jamia.backend.repository.ContributionRepository;
import com.jamia.backend.repository.RoomMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business rules for payment tracking. JAMIA does not move money:
 * the payer reports "I paid" and the recipient reports "I received it".
 */
@Service
public class ContributionService {

    private final ContributionRepository contributionRepository;
    private final RoomMemberRepository memberRepository;

    public ContributionService(ContributionRepository contributionRepository,
                               RoomMemberRepository memberRepository) {
        this.contributionRepository = contributionRepository;
        this.memberRepository = memberRepository;
    }

    // The room's contributions (all cycles, or one cycle). Only members can see them.
    @Transactional(readOnly = true)
    public List<ContributionResponse> getContributions(Long roomId, Long userId, Integer cycleNumber) {
        requireMember(roomId, userId);

        List<Contribution> contributions = (cycleNumber == null)
                ? contributionRepository.findByRoomIdOrderByCycleNumberAscIdAsc(roomId)
                : contributionRepository.findByRoomIdAndCycleNumberOrderByIdAsc(roomId, cycleNumber);

        return contributions.stream().map(ContributionResponse::from).toList();
    }

    // The payer says "I paid". Only the payer can do this.
    @Transactional
    public ContributionResponse markPaid(Long roomId, Long contributionId, Long userId) {
        Contribution contribution = findInRoom(roomId, contributionId, userId);

        if (!contribution.getPayer().getUser().getId().equals(userId)) {
            throw new ForbiddenActionException("Only the payer can mark this contribution as paid");
        }
        if (contribution.getStatus() != ContributionStatus.PENDING) {
            throw new BusinessRuleException("This contribution is already " + contribution.getStatus());
        }

        contribution.markPaid();
        return ContributionResponse.from(contribution);
    }

    // The recipient says "I received it". Only the recipient can do this.
    // They may confirm even if the payer forgot to press "I paid".
    // When the last contribution of the room is confirmed, the room is completed.
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

        // Hibernate saves the change above before running this query, so it is counted correctly.
        if (!contributionRepository.existsByRoomIdAndStatusNot(roomId, ContributionStatus.CONFIRMED)) {
            contribution.getRoom().complete();
        }

        return ContributionResponse.from(contribution);
    }

    private Contribution findInRoom(Long roomId, Long contributionId, Long userId) {
        requireMember(roomId, userId);
        return contributionRepository.findByIdAndRoomId(contributionId, roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Contribution not found"));
    }

    // Non-members get "not found", so they can't even tell whether the room exists.
    private void requireMember(Long roomId, Long userId) {
        if (!memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ResourceNotFoundException("Room not found");
        }
    }
}
