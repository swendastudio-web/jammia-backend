package com.jamia.backend.service;

import com.jamia.backend.dto.RoomResponse;
import com.jamia.backend.entity.Contribution;
import com.jamia.backend.entity.ContributionFrequency;
import com.jamia.backend.entity.JoinRequest;
import com.jamia.backend.entity.JoinRequestStatus;
import com.jamia.backend.entity.MemberStatus;
import com.jamia.backend.entity.RoomMember;
import com.jamia.backend.entity.RoomRound;
import com.jamia.backend.entity.RoomStatus;
import com.jamia.backend.entity.RoundStatus;
import com.jamia.backend.entity.SavingsRoom;
import com.jamia.backend.entity.SubscriptionPlan;
import com.jamia.backend.entity.SubscriptionPlanCode;
import com.jamia.backend.entity.TurnOrderMethod;
import com.jamia.backend.entity.User;
import com.jamia.backend.exception.BadRequestException;
import com.jamia.backend.exception.BusinessRuleException;
import com.jamia.backend.exception.ForbiddenActionException;
import com.jamia.backend.exception.ResourceNotFoundException;
import com.jamia.backend.repository.ContributionRepository;
import com.jamia.backend.repository.JoinRequestRepository;
import com.jamia.backend.repository.RoomMemberRepository;
import com.jamia.backend.repository.RoomRoundRepository;
import com.jamia.backend.repository.SavingsRoomRepository;
import com.jamia.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the savings-room rules (rounds, turns, between-round changes).
 * Repositories are mocks; the clock is fixed so time can be controlled.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SavingsRoomServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 31, 10, 0);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Mock
    private SavingsRoomRepository roomRepository;
    @Mock
    private RoomMemberRepository memberRepository;
    @Mock
    private ContributionRepository contributionRepository;
    @Mock
    private RoomRoundRepository roundRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JoinRequestRepository joinRequestRepository;

    private SavingsRoomService roomService;
    private User amal;
    private User badr;
    private User carim;

    @BeforeEach
    void setUp() {
        roomService = service(false, NOW);
        SubscriptionPlan freePlan = new SubscriptionPlan(SubscriptionPlanCode.FREE, 5);
        amal = user(1L, "Amal", freePlan);
        badr = user(2L, "Badr", freePlan);
        carim = user(3L, "Carim", freePlan);
        when(roundRepository.save(any(RoomRound.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ----- create -----

    @Test
    void createRoom_rejectsMoreMembersThanThePlanAllows() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(amal));

        assertThatThrownBy(() -> roomService.createRoom(1L, "Big", null, new BigDecimal("100"), "AED",
                ContributionFrequency.MONTHLY, 6))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Your FREE plan allows rooms of up to 5 members");
    }

    @Test
    void createRoom_savesAnOpenRoomWithTheCreatorAsFirstMember() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(amal));
        when(roomRepository.save(any(SavingsRoom.class))).thenAnswer(call -> call.getArgument(0));
        when(memberRepository.save(any(RoomMember.class))).thenAnswer(call -> call.getArgument(0));

        RoomResponse response = roomService.createRoom(1L, " Family ", null, new BigDecimal("100"), "AED",
                ContributionFrequency.MONTHLY, 5);

        assertThat(response.name()).isEqualTo("Family");
        assertThat(response.status()).isEqualTo(RoomStatus.OPEN);
        assertThat(response.currentRound()).isNull();
        assertThat(response.members()).extracting("firstName").containsExactly("Amal");
    }

    @Test
    void createRoom_fiveMinutePeriodIsRefusedWhenTheDevSwitchIsOff() {
        assertThatThrownBy(() -> roomService.createRoom(1L, "Test", null, new BigDecimal("10"), "OMR",
                ContributionFrequency.FIVE_MINUTES, 3))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only for testing");
    }

    @Test
    void createRoom_fiveMinutePeriodIsAllowedWithTheDevSwitch() {
        roomService = service(true, NOW);
        when(userRepository.findById(1L)).thenReturn(Optional.of(amal));
        when(roomRepository.save(any(SavingsRoom.class))).thenAnswer(call -> call.getArgument(0));
        when(memberRepository.save(any(RoomMember.class))).thenAnswer(call -> call.getArgument(0));

        RoomResponse response = roomService.createRoom(1L, "Test", null, new BigDecimal("10"), "OMR",
                ContributionFrequency.FIVE_MINUTES, 3);

        assertThat(response.frequency()).isEqualTo(ContributionFrequency.FIVE_MINUTES);
    }

    @Test
    void getRoom_hidesTheRoomFromNonMembers() {
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room(10L, 3, ContributionFrequency.MONTHLY)));
        when(memberRepository.existsByRoomIdAndUserIdAndStatus(10L, 9L, MemberStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> roomService.getRoom(10L, 9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ----- start a round -----

    @Test
    void startRoom_onlyTheAdminCanStart() {
        SavingsRoom room = room(10L, 3, ContributionFrequency.MONTHLY);
        givenRoom(room, List.of(member(room, amal), member(room, badr)));
        givenActiveMember(10L, 2L);

        assertThatThrownBy(() -> roomService.startRoom(10L, 2L, TurnOrderMethod.RANDOM, LocalDate.of(2026, 2, 1), null))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void startRoom_needsAtLeastTwoMembers() {
        SavingsRoom room = room(10L, 3, ContributionFrequency.MONTHLY);
        givenRoom(room, List.of(member(room, amal)));

        assertThatThrownBy(() -> roomService.startRoom(10L, 1L, TurnOrderMethod.RANDOM, LocalDate.of(2026, 2, 1), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A room needs at least 2 members to start");
    }

    @Test
    void startRoom_cannotStartWhileARoundIsRunning() {
        SavingsRoom room = room(10L, 3, ContributionFrequency.MONTHLY);
        room.startRound();
        givenRoom(room, List.of(member(room, amal), member(room, badr)));

        assertThatThrownBy(() -> roomService.startRoom(10L, 1L, TurnOrderMethod.RANDOM, LocalDate.of(2026, 2, 1), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A round is already running");
    }

    @Test
    void startRoom_manualOrderCreatesRoundOneWithTheFullSchedule() {
        SavingsRoom room = room(10L, 3, ContributionFrequency.MONTHLY);
        givenRoom(room, List.of(member(room, amal), member(room, badr), member(room, carim)));

        roomService.startRoom(10L, 1L, TurnOrderMethod.MANUAL, LocalDate.of(2026, 1, 31), List.of(2L, 1L, 3L));

        RoomRound round = savedRound();
        assertThat(round.getRoundNumber()).isEqualTo(1);
        assertThat(round.getStatus()).isEqualTo(RoundStatus.ACTIVE);
        assertThat(round.getStartedAt()).isEqualTo(NOW); // start date is today -> starts now
        assertThat(room.getStatus()).isEqualTo(RoomStatus.ACTIVE);

        List<Contribution> schedule = savedSchedule();
        assertThat(schedule).hasSize(6); // 3 turns x 2 payers; nobody pays themselves
        assertThat(schedule).allMatch(c -> c.getPayer() != c.getRecipient());
        assertThat(schedule).filteredOn(c -> c.getCycleNumber() == 1)
                .allMatch(c -> c.getRecipient().getUser() == badr && c.getDueAt().equals(NOW));
        assertThat(schedule).filteredOn(c -> c.getCycleNumber() == 2)
                .allMatch(c -> c.getDueAt().equals(NOW.plusMonths(1)));   // Feb 28 (month end kept)
        assertThat(round.getEndsAt()).isEqualTo(NOW.plusMonths(3));
    }

    @Test
    void startRoom_aFutureStartDateBeginsAtMidnightThatDay() {
        SavingsRoom room = room(10L, 3, ContributionFrequency.WEEKLY);
        givenRoom(room, List.of(member(room, amal), member(room, badr)));

        roomService.startRoom(10L, 1L, TurnOrderMethod.RANDOM, LocalDate.of(2026, 2, 10), null);

        assertThat(savedRound().getStartedAt()).isEqualTo(LocalDateTime.of(2026, 2, 10, 0, 0));
    }

    @Test
    void startRoom_fiveMinuteRoomStartsNowAndEachTurnLastsFiveMinutes() {
        roomService = service(true, NOW);
        SavingsRoom room = room(10L, 3, ContributionFrequency.FIVE_MINUTES);
        givenRoom(room, List.of(member(room, amal), member(room, badr), member(room, carim)));

        roomService.startRoom(10L, 1L, TurnOrderMethod.RANDOM, null, null);

        RoomRound round = savedRound();
        assertThat(round.getStartedAt()).isEqualTo(NOW);
        assertThat(round.getEndsAt()).isEqualTo(NOW.plusMinutes(15));
        assertThat(savedSchedule()).extracting(Contribution::getCycleNumber, Contribution::getDueAt)
                .contains(tuple(1, NOW), tuple(2, NOW.plusMinutes(5)), tuple(3, NOW.plusMinutes(10)));
    }

    @Test
    void startRoom_theNextRoundGetsTheNextNumber() {
        SavingsRoom room = room(10L, 3, ContributionFrequency.MONTHLY);
        givenRoom(room, List.of(member(room, amal), member(room, badr)));
        when(roundRepository.countByRoomId(10L)).thenReturn(1L); // round 1 already happened

        roomService.startRoom(10L, 1L, TurnOrderMethod.RANDOM, LocalDate.of(2026, 1, 31), null);

        assertThat(savedRound().getRoundNumber()).isEqualTo(2);
    }

    @Test
    void startRoom_randomOrderGivesEveryMemberOneUniqueTurn() {
        SavingsRoom room = room(10L, 3, ContributionFrequency.MONTHLY);
        List<RoomMember> members = List.of(member(room, amal), member(room, badr), member(room, carim));
        givenRoom(room, members);

        roomService.startRoom(10L, 1L, TurnOrderMethod.RANDOM, LocalDate.of(2026, 1, 31), null);

        assertThat(members).extracting(RoomMember::getTurnPosition).containsExactlyInAnyOrder(1, 2, 3);
    }

    @Test
    void startRoom_manualOrderMustListEveryMemberExactlyOnce() {
        SavingsRoom room = room(10L, 3, ContributionFrequency.MONTHLY);
        givenRoom(room, List.of(member(room, amal), member(room, badr), member(room, carim)));

        assertThatThrownBy(() -> roomService.startRoom(10L, 1L, TurnOrderMethod.MANUAL, LocalDate.of(2026, 1, 31),
                List.of(1L, 1L, 2L))).isInstanceOf(BadRequestException.class);
        verify(contributionRepository, never()).saveAll(anyList());
    }

    @Test
    void startRoom_rejectsJoinRequestsThatAreStillWaiting() {
        SavingsRoom room = room(10L, 3, ContributionFrequency.MONTHLY);
        givenRoom(room, List.of(member(room, amal), member(room, badr)));
        JoinRequest waiting = new JoinRequest(room, carim, member(room, badr));
        when(joinRequestRepository.findByRoomIdAndStatusOrderByCreatedAtAsc(10L, JoinRequestStatus.PENDING))
                .thenReturn(List.of(waiting));

        roomService.startRoom(10L, 1L, TurnOrderMethod.RANDOM, LocalDate.of(2026, 1, 31), null);

        assertThat(waiting.getStatus()).isEqualTo(JoinRequestStatus.REJECTED);
    }

    // ----- the clock finishes rounds -----

    @Test
    void completeFinishedRounds_finishesTheRoundAndOpensTheRoomAgainWithEveryoneStaying() {
        SavingsRoom room = room(10L, 3, ContributionFrequency.FIVE_MINUTES);
        room.startRound();
        RoomMember a = member(room, amal);
        RoomMember b = member(room, badr);
        a.setTurnPosition(1);
        b.setTurnPosition(2);
        RoomRound round = new RoomRound(room, 1, TurnOrderMethod.RANDOM, NOW.minusMinutes(10), NOW);
        when(roundRepository.findByStatusAndEndsAtLessThanEqual(RoundStatus.ACTIVE, NOW)).thenReturn(List.of(round));
        when(memberRepository.findByRoomIdAndStatusOrderByTurnPositionAscIdAsc(10L, MemberStatus.ACTIVE))
                .thenReturn(List.of(a, b));

        assertThat(roomService.completeFinishedRounds()).isEqualTo(1);

        assertThat(round.getStatus()).isEqualTo(RoundStatus.COMPLETED);
        assertThat(round.getCompletedAt()).isEqualTo(NOW);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.OPEN);
        assertThat(List.of(a, b)).allMatch(m -> m.isActive() && m.getTurnPosition() == null);
    }

    // ----- between rounds -----

    @Test
    void updateMaxMembers_onlyBetweenRounds() {
        SavingsRoom room = room(10L, 3, ContributionFrequency.MONTHLY);
        room.startRound();
        givenRoom(room, List.of(member(room, amal), member(room, badr)));

        assertThatThrownBy(() -> roomService.updateMaxMembers(10L, 1L, 4))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The number of members can only be changed after the round ends");
    }

    @Test
    void updateMaxMembers_cannotGoBelowTheCurrentMembersOrAboveThePlan() {
        SavingsRoom room = room(10L, 5, ContributionFrequency.MONTHLY);
        givenRoom(room, List.of(member(room, amal), member(room, badr), member(room, carim)));
        when(memberRepository.countByRoomIdAndStatus(10L, MemberStatus.ACTIVE)).thenReturn(3L);

        assertThatThrownBy(() -> roomService.updateMaxMembers(10L, 1L, 2)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> roomService.updateMaxMembers(10L, 1L, 6))
                .hasMessage("Your FREE plan allows rooms of up to 5 members");

        roomService.updateMaxMembers(10L, 1L, 4);
        assertThat(room.getMaxMembers()).isEqualTo(4);
    }

    @Test
    void removeMember_adminRemovesBetweenRoundsAndHistoryStays() {
        SavingsRoom room = room(10L, 3, ContributionFrequency.MONTHLY);
        RoomMember b = member(room, badr);
        givenRoom(room, List.of(member(room, amal), b));
        when(memberRepository.findByRoomIdAndUserId(10L, 2L)).thenReturn(Optional.of(b));

        roomService.removeMember(10L, 1L, 2L);

        assertThat(b.getStatus()).isEqualTo(MemberStatus.REMOVED);
        assertThat(b.getLeftAt()).isNotNull();
    }

    @Test
    void removeMember_theAdminCannotRemoveThemselves() {
        SavingsRoom room = room(10L, 3, ContributionFrequency.MONTHLY);
        givenRoom(room, List.of(member(room, amal), member(room, badr)));

        assertThatThrownBy(() -> roomService.removeMember(10L, 1L, 1L))
                .hasMessage("The room admin cannot remove themselves");
    }

    @Test
    void removeMember_duringARound_notReceivedYet_turnRemovedLaterTurnsMoveUp() {
        roomService = service(true, NOW);
        // 3 turns of 5 minutes started 2 minutes ago: turn 1 (Amal) is running; Badr = turn 2, Carim = turn 3
        Fixture f = runningRound(NOW.minusMinutes(2));

        roomService.removeMember(10L, 1L, 2L);

        assertThat(f.b.getStatus()).isEqualTo(MemberStatus.REMOVED);
        // Deleted: Badr's turn (both payments to him) and his payment for turn 3 (not started)
        List<Contribution> deleted = deletedPayments();
        assertThat(deleted).containsExactlyInAnyOrder(f.aToB, f.cToB, f.bToC);
        // Kept: what Badr already owes for the running turn 1
        assertThat(deleted).doesNotContain(f.bToA);
        // Carim moves up from turn 3 to turn 2, five minutes earlier
        assertThat(f.c.getTurnPosition()).isEqualTo(2);
        assertThat(f.aToC.getCycleNumber()).isEqualTo(2);
        assertThat(f.aToC.getDueAt()).isEqualTo(f.start.plusMinutes(5));
        // The round is one turn shorter
        assertThat(f.round.getEndsAt()).isEqualTo(f.start.plusMinutes(10));
    }

    @Test
    void removeMember_duringARound_alreadyReceived_keepsWhatTheyOwe() {
        roomService = service(true, NOW);
        Fixture f = runningRound(NOW.minusMinutes(7)); // turn 2 (Badr) is running: Badr has received

        roomService.removeMember(10L, 1L, 2L);

        assertThat(f.b.getStatus()).isEqualTo(MemberStatus.REMOVED);
        verify(contributionRepository, never()).deleteAll(anyList());
        assertThat(f.bToC.getCycleNumber()).isEqualTo(3); // he still owes Carim in turn 3
        assertThat(f.round.getEndsAt()).isEqualTo(f.start.plusMinutes(15));
    }

    @Test
    void leaveRoom_memberLeavesBetweenRounds() {
        SavingsRoom room = room(10L, 3, ContributionFrequency.MONTHLY);
        RoomMember b = member(room, badr);
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(memberRepository.findByRoomIdAndUserId(10L, 2L)).thenReturn(Optional.of(b));

        roomService.leaveRoom(10L, 2L);

        assertThat(b.getStatus()).isEqualTo(MemberStatus.LEFT);
    }

    @Test
    void leaveRoom_adminCannotLeaveAndNobodyLeavesDuringARound() {
        SavingsRoom room = room(10L, 3, ContributionFrequency.MONTHLY);
        RoomMember a = member(room, amal);
        RoomMember b = member(room, badr);
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(memberRepository.findByRoomIdAndUserId(10L, 1L)).thenReturn(Optional.of(a));
        when(memberRepository.findByRoomIdAndUserId(10L, 2L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> roomService.leaveRoom(10L, 1L)).hasMessage("The room admin cannot leave the room");
        room.startRound();
        assertThatThrownBy(() -> roomService.leaveRoom(10L, 2L))
                .hasMessage("You can leave the room after the current round ends");
    }

    // ----- helpers -----

    /** A running 5-minute round: Amal turn 1, Badr turn 2, Carim turn 3, with all 6 payments. */
    private Fixture runningRound(LocalDateTime start) {
        SavingsRoom room = room(10L, 3, ContributionFrequency.FIVE_MINUTES);
        room.startRound();
        Fixture f = new Fixture();
        f.start = start;
        f.a = member(room, amal);
        f.b = member(room, badr);
        f.c = member(room, carim);
        long id = 100;
        for (RoomMember m : List.of(f.a, f.b, f.c)) {
            ReflectionTestUtils.setField(m, "id", id++);
        }
        f.a.setTurnPosition(1);
        f.b.setTurnPosition(2);
        f.c.setTurnPosition(3);
        f.round = new RoomRound(room, 1, TurnOrderMethod.MANUAL, start, start.plusMinutes(15));
        ContributionFrequency five = ContributionFrequency.FIVE_MINUTES;
        f.bToA = pay(f.round, 1, five.startOfCycle(start, 1), f.b, f.a);
        f.cToA = pay(f.round, 1, five.startOfCycle(start, 1), f.c, f.a);
        f.aToB = pay(f.round, 2, five.startOfCycle(start, 2), f.a, f.b);
        f.cToB = pay(f.round, 2, five.startOfCycle(start, 2), f.c, f.b);
        f.aToC = pay(f.round, 3, five.startOfCycle(start, 3), f.a, f.c);
        f.bToC = pay(f.round, 3, five.startOfCycle(start, 3), f.b, f.c);
        ReflectionTestUtils.setField(f.round, "id", 7L);

        givenRoom(room, List.of(f.a, f.b, f.c));
        when(memberRepository.findByRoomIdAndUserId(10L, 2L)).thenReturn(Optional.of(f.b));
        when(roundRepository.findFirstByRoomIdOrderByRoundNumberDesc(10L)).thenReturn(Optional.of(f.round));
        when(contributionRepository.findByRoundIdOrderByCycleNumberAscIdAsc(7L))
                .thenReturn(List.of(f.bToA, f.cToA, f.aToB, f.cToB, f.aToC, f.bToC));
        return f;
    }

    private static Contribution pay(RoomRound round, int turn, LocalDateTime dueAt, RoomMember payer, RoomMember to) {
        return new Contribution(round, turn, dueAt, payer, to, new BigDecimal("10.00"));
    }

    @SuppressWarnings("unchecked")
    private List<Contribution> deletedPayments() {
        ArgumentCaptor<List<Contribution>> captor = ArgumentCaptor.forClass(List.class);
        verify(contributionRepository).deleteAll(captor.capture());
        return captor.getValue();
    }

    private static class Fixture {
        LocalDateTime start;
        RoomMember a;
        RoomMember b;
        RoomMember c;
        RoomRound round;
        Contribution bToA;
        Contribution cToA;
        Contribution aToB;
        Contribution cToB;
        Contribution aToC;
        Contribution bToC;
    }

    private SavingsRoomService service(boolean fiveMinutes, LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
        return new SavingsRoomService(roomRepository, memberRepository, contributionRepository, roundRepository,
                userRepository, joinRequestRepository, fiveMinutes, clock);
    }

    private void givenRoom(SavingsRoom room, List<RoomMember> members) {
        when(roomRepository.findByIdForUpdate(room.getId())).thenReturn(Optional.of(room));
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        givenActiveMember(room.getId(), 1L);
        when(memberRepository.findByRoomIdAndStatusOrderByTurnPositionAscIdAsc(room.getId(), MemberStatus.ACTIVE))
                .thenReturn(new ArrayList<>(members));
    }

    private void givenActiveMember(Long roomId, Long userId) {
        when(memberRepository.existsByRoomIdAndUserIdAndStatus(roomId, userId, MemberStatus.ACTIVE)).thenReturn(true);
    }

    private RoomRound savedRound() {
        ArgumentCaptor<RoomRound> captor = ArgumentCaptor.forClass(RoomRound.class);
        verify(roundRepository).save(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<Contribution> savedSchedule() {
        ArgumentCaptor<List<Contribution>> captor = ArgumentCaptor.forClass(List.class);
        verify(contributionRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    // A room created by Amal (user 1)
    private SavingsRoom room(Long id, int maxMembers, ContributionFrequency frequency) {
        SavingsRoom room = new SavingsRoom("Family", null, new BigDecimal("100.00"), "AED", frequency, maxMembers, amal);
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }

    private static RoomMember member(SavingsRoom room, User user) {
        return new RoomMember(room, user);
    }

    private static User user(Long id, String firstName, SubscriptionPlan plan) {
        User user = new User(firstName, "Test", firstName.toLowerCase() + "@mail.com", "hash");
        user.setSubscriptionPlan(plan);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
