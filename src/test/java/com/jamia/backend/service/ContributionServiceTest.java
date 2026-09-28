package com.jamia.backend.service;

import com.jamia.backend.entity.Contribution;
import com.jamia.backend.entity.ContributionFrequency;
import com.jamia.backend.entity.ContributionStatus;
import com.jamia.backend.entity.RoomMember;
import com.jamia.backend.entity.RoomStatus;
import com.jamia.backend.entity.SavingsRoom;
import com.jamia.backend.entity.TurnOrderMethod;
import com.jamia.backend.entity.User;
import com.jamia.backend.exception.BusinessRuleException;
import com.jamia.backend.exception.ForbiddenActionException;
import com.jamia.backend.exception.ResourceNotFoundException;
import com.jamia.backend.repository.ContributionRepository;
import com.jamia.backend.repository.RoomMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests the payment-tracking rules. Repositories are mocks, so no database is needed.
 */
@ExtendWith(MockitoExtension.class)
class ContributionServiceTest {

    private static final Long ROOM_ID = 10L;
    private static final Long CONTRIBUTION_ID = 100L;
    private static final Long PAYER_ID = 1L;
    private static final Long RECIPIENT_ID = 2L;
    private static final Long OTHER_MEMBER_ID = 3L;

    @Mock
    private ContributionRepository contributionRepository;
    @Mock
    private RoomMemberRepository memberRepository;

    private ContributionService contributionService;
    private SavingsRoom room;
    private Contribution contribution;

    @BeforeEach
    void setUp() {
        contributionService = new ContributionService(contributionRepository, memberRepository);

        User payer = user(PAYER_ID);
        User recipient = user(RECIPIENT_ID);
        room = new SavingsRoom("Family", null, new BigDecimal("100.00"), "AED",
                ContributionFrequency.MONTHLY, 3, payer, "CODE2345");
        room.start(TurnOrderMethod.RANDOM, LocalDate.now());
        contribution = new Contribution(room, 1, LocalDate.now(), new RoomMember(room, payer),
                new RoomMember(room, recipient), new BigDecimal("100.00"));
    }

    @Test
    void getContributions_hidesThemFromNonMembers() {
        when(memberRepository.existsByRoomIdAndUserId(ROOM_ID, 99L)).thenReturn(false);

        assertThatThrownBy(() -> contributionService.getContributions(ROOM_ID, 99L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markPaid_onlyThePayerCanMarkPaid() {
        givenContributionVisibleTo(OTHER_MEMBER_ID);

        assertThatThrownBy(() -> contributionService.markPaid(ROOM_ID, CONTRIBUTION_ID, OTHER_MEMBER_ID))
                .isInstanceOf(ForbiddenActionException.class);
        assertThat(contribution.getStatus()).isEqualTo(ContributionStatus.PENDING);
    }

    @Test
    void markPaid_setsPaidAndTheTime() {
        givenContributionVisibleTo(PAYER_ID);

        contributionService.markPaid(ROOM_ID, CONTRIBUTION_ID, PAYER_ID);

        assertThat(contribution.getStatus()).isEqualTo(ContributionStatus.PAID);
        assertThat(contribution.getPaidAt()).isNotNull();
    }

    @Test
    void markPaid_cannotBeDoneTwice() {
        givenContributionVisibleTo(PAYER_ID);
        contribution.markPaid();

        assertThatThrownBy(() -> contributionService.markPaid(ROOM_ID, CONTRIBUTION_ID, PAYER_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void confirm_onlyTheRecipientCanConfirm() {
        givenContributionVisibleTo(PAYER_ID);

        assertThatThrownBy(() -> contributionService.confirmReceived(ROOM_ID, CONTRIBUTION_ID, PAYER_ID))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void confirm_keepsTheRoomActiveWhileOtherContributionsAreOpen() {
        givenContributionVisibleTo(RECIPIENT_ID);
        when(contributionRepository.existsByRoomIdAndStatusNot(ROOM_ID, ContributionStatus.CONFIRMED))
                .thenReturn(true);

        contributionService.confirmReceived(ROOM_ID, CONTRIBUTION_ID, RECIPIENT_ID);

        assertThat(contribution.getStatus()).isEqualTo(ContributionStatus.CONFIRMED);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.ACTIVE);
    }

    @Test
    void confirm_completesTheRoomWhenTheLastContributionIsConfirmed() {
        givenContributionVisibleTo(RECIPIENT_ID);
        when(contributionRepository.existsByRoomIdAndStatusNot(ROOM_ID, ContributionStatus.CONFIRMED))
                .thenReturn(false);

        contributionService.confirmReceived(ROOM_ID, CONTRIBUTION_ID, RECIPIENT_ID);

        assertThat(room.getStatus()).isEqualTo(RoomStatus.COMPLETED);
    }

    @Test
    void confirm_cannotBeDoneTwice() {
        givenContributionVisibleTo(RECIPIENT_ID);
        contribution.confirm();

        assertThatThrownBy(() -> contributionService.confirmReceived(ROOM_ID, CONTRIBUTION_ID, RECIPIENT_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ----- helpers -----

    private void givenContributionVisibleTo(Long userId) {
        when(memberRepository.existsByRoomIdAndUserId(ROOM_ID, userId)).thenReturn(true);
        when(contributionRepository.findByIdAndRoomId(CONTRIBUTION_ID, ROOM_ID)).thenReturn(Optional.of(contribution));
    }

    private static User user(Long id) {
        User user = new User("User" + id, "Test", "user" + id + "@mail.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
