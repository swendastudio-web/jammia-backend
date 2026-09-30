package com.jamia.backend.service;

import com.jamia.backend.dto.ContributionResponse;
import com.jamia.backend.entity.Contribution;
import com.jamia.backend.entity.ContributionFrequency;
import com.jamia.backend.entity.ContributionStatus;
import com.jamia.backend.entity.MemberStatus;
import com.jamia.backend.entity.RoomMember;
import com.jamia.backend.entity.RoomRound;
import com.jamia.backend.entity.SavingsRoom;
import com.jamia.backend.entity.TurnOrderMethod;
import com.jamia.backend.entity.User;
import com.jamia.backend.exception.BusinessRuleException;
import com.jamia.backend.exception.ForbiddenActionException;
import com.jamia.backend.exception.ResourceNotFoundException;
import com.jamia.backend.repository.ContributionRepository;
import com.jamia.backend.repository.RoomMemberRepository;
import com.jamia.backend.repository.RoomRoundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests the payment-tracking rules, including "late" when the turn has moved on.
 * Repositories are mocks; the clock is fixed.
 */
@ExtendWith(MockitoExtension.class)
class ContributionServiceTest {

    private static final Long ROOM_ID = 10L;
    private static final Long CONTRIBUTION_ID = 100L;
    private static final Long PAYER_ID = 1L;
    private static final Long RECIPIENT_ID = 2L;
    private static final Long OTHER_MEMBER_ID = 3L;
    private static final LocalDateTime ROUND_START = LocalDateTime.of(2026, 1, 1, 10, 0);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Mock
    private ContributionRepository contributionRepository;
    @Mock
    private RoomMemberRepository memberRepository;
    @Mock
    private RoomRoundRepository roundRepository;

    private RoomRound round;
    private Contribution contribution;

    @BeforeEach
    void setUp() {
        User payer = user(PAYER_ID);
        User recipient = user(RECIPIENT_ID);
        SavingsRoom room = new SavingsRoom("Test", null, new BigDecimal("10.00"), "OMR",
                ContributionFrequency.FIVE_MINUTES, 3, payer);
        room.startRound();
        // 2 turns of 5 minutes: 10:00-10:05, 10:05-10:10
        round = new RoomRound(room, 1, TurnOrderMethod.RANDOM, ROUND_START, ROUND_START.plusMinutes(10));
        contribution = new Contribution(round, 1, ROUND_START, new RoomMember(room, payer),
                new RoomMember(room, recipient), new BigDecimal("10.00"));
    }

    @Test
    void getContributions_hidesThemFromNonMembers() {
        when(memberRepository.existsByRoomIdAndUserIdAndStatus(ROOM_ID, 99L, MemberStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> service(ROUND_START).getContributions(ROOM_ID, 99L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getContributions_isEmptyBeforeTheFirstRound() {
        givenMember(PAYER_ID);
        when(roundRepository.findFirstByRoomIdOrderByRoundNumberDesc(ROOM_ID)).thenReturn(Optional.empty());

        assertThat(service(ROUND_START).getContributions(ROOM_ID, PAYER_ID, null)).isEmpty();
    }

    @Test
    void anUnpaidPaymentBecomesLateWhenItsTurnHasPassed() {
        givenMember(PAYER_ID);
        when(roundRepository.findFirstByRoomIdOrderByRoundNumberDesc(ROOM_ID)).thenReturn(Optional.of(round));
        ReflectionTestUtils.setField(round, "id", 7L);
        when(contributionRepository.findByRoundIdOrderByCycleNumberAscIdAsc(7L)).thenReturn(List.of(contribution));

        ContributionResponse during = service(ROUND_START.plusMinutes(4)).getContributions(ROOM_ID, PAYER_ID, null).get(0);
        ContributionResponse after = service(ROUND_START.plusMinutes(5)).getContributions(ROOM_ID, PAYER_ID, null).get(0);

        assertThat(during.late()).isFalse();
        assertThat(after.late()).isTrue();
        assertThat(after.turnEndsAt()).isEqualTo(ROUND_START.plusMinutes(5));
        assertThat(after.roundNumber()).isEqualTo(1);
    }

    @Test
    void aPaidPaymentIsNeverLate() {
        givenPaymentVisibleTo(PAYER_ID);
        ContributionResponse paid = service(ROUND_START.plusMinutes(8)).markPaid(ROOM_ID, CONTRIBUTION_ID, PAYER_ID);

        assertThat(paid.status()).isEqualTo(ContributionStatus.PAID);
        assertThat(paid.late()).isFalse();
    }

    @Test
    void markPaid_onlyThePayer() {
        givenPaymentVisibleTo(OTHER_MEMBER_ID);

        assertThatThrownBy(() -> service(ROUND_START).markPaid(ROOM_ID, CONTRIBUTION_ID, OTHER_MEMBER_ID))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void markPaid_cannotBeDoneTwice() {
        givenPaymentVisibleTo(PAYER_ID);
        contribution.markPaid();

        assertThatThrownBy(() -> service(ROUND_START).markPaid(ROOM_ID, CONTRIBUTION_ID, PAYER_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void confirm_onlyTheRecipient_andNotTwice() {
        givenPaymentVisibleTo(PAYER_ID);
        assertThatThrownBy(() -> service(ROUND_START).confirmReceived(ROOM_ID, CONTRIBUTION_ID, PAYER_ID))
                .isInstanceOf(ForbiddenActionException.class);

        givenPaymentVisibleTo(RECIPIENT_ID);
        assertThat(service(ROUND_START).confirmReceived(ROOM_ID, CONTRIBUTION_ID, RECIPIENT_ID).status())
                .isEqualTo(ContributionStatus.CONFIRMED);
        assertThatThrownBy(() -> service(ROUND_START).confirmReceived(ROOM_ID, CONTRIBUTION_ID, RECIPIENT_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ----- helpers -----

    private ContributionService service(LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
        return new ContributionService(contributionRepository, memberRepository, roundRepository, clock);
    }

    private void givenMember(Long userId) {
        when(memberRepository.existsByRoomIdAndUserIdAndStatus(ROOM_ID, userId, MemberStatus.ACTIVE)).thenReturn(true);
    }

    private void givenPaymentVisibleTo(Long userId) {
        givenMember(userId);
        when(contributionRepository.findByIdAndRoomId(CONTRIBUTION_ID, ROOM_ID)).thenReturn(Optional.of(contribution));
    }

    private static User user(Long id) {
        User user = new User("User" + id, "Test", "user" + id + "@mail.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
