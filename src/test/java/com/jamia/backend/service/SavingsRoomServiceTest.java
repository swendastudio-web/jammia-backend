package com.jamia.backend.service;

import com.jamia.backend.dto.RoomResponse;
import com.jamia.backend.entity.Contribution;
import com.jamia.backend.entity.ContributionFrequency;
import com.jamia.backend.entity.JoinRequest;
import com.jamia.backend.entity.JoinRequestStatus;
import com.jamia.backend.entity.RoomMember;
import com.jamia.backend.entity.RoomStatus;
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
import com.jamia.backend.repository.SavingsRoomRepository;
import com.jamia.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the savings-room rules. Repositories are mocks, so no database is needed.
 */
@ExtendWith(MockitoExtension.class)
class SavingsRoomServiceTest {

    @Mock
    private SavingsRoomRepository roomRepository;
    @Mock
    private RoomMemberRepository memberRepository;
    @Mock
    private ContributionRepository contributionRepository;
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
        roomService = new SavingsRoomService(roomRepository, memberRepository, contributionRepository, userRepository,
                joinRequestRepository);
        SubscriptionPlan freePlan = new SubscriptionPlan(SubscriptionPlanCode.FREE, 5);
        amal = user(1L, "Amal", freePlan);
        badr = user(2L, "Badr", freePlan);
        carim = user(3L, "Carim", freePlan);
    }

    @Test
    void createRoom_rejectsMoreMembersThanThePlanAllows() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(amal));

        assertThatThrownBy(() -> roomService.createRoom(1L, "Big", null, new BigDecimal("100"), "AED",
                ContributionFrequency.MONTHLY, 6))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Your FREE plan allows rooms of up to 5 members");
        verify(roomRepository, never()).save(any());
    }

    @Test
    void createRoom_savesRoomAsOpenWithCreatorAsFirstMember() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(amal));
        when(roomRepository.save(any(SavingsRoom.class))).thenAnswer(call -> call.getArgument(0));
        when(memberRepository.save(any(RoomMember.class))).thenAnswer(call -> call.getArgument(0));

        RoomResponse response = roomService.createRoom(1L, " Family ", null, new BigDecimal("100"), "AED",
                ContributionFrequency.MONTHLY, 5);

        assertThat(response.name()).isEqualTo("Family");
        assertThat(response.status()).isEqualTo(RoomStatus.OPEN);
        assertThat(response.members()).extracting("firstName").containsExactly("Amal");
    }

    @Test
    void getRoom_hidesTheRoomFromNonMembers() {
        SavingsRoom room = room(10L, 3);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(memberRepository.existsByRoomIdAndUserId(10L, 3L)).thenReturn(false);

        assertThatThrownBy(() -> roomService.getRoom(10L, 3L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Room not found");
    }

    @Test
    void startRoom_onlyTheCreatorCanStart() {
        SavingsRoom room = room(10L, 3);
        when(roomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(memberRepository.existsByRoomIdAndUserId(10L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> roomService.startRoom(10L, 2L, TurnOrderMethod.RANDOM, LocalDate.now(), null))
                .isInstanceOf(ForbiddenActionException.class);
    }

    @Test
    void startRoom_needsAtLeastTwoMembers() {
        SavingsRoom room = room(10L, 3);
        givenRoomForStart(room, List.of(member(room, amal)));

        assertThatThrownBy(() -> roomService.startRoom(10L, 1L, TurnOrderMethod.RANDOM, LocalDate.now(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A room needs at least 2 members to start");
    }

    @Test
    void startRoom_manualOrderSetsTurnsAndBuildsTheFullSchedule() {
        SavingsRoom room = room(10L, 3);
        givenRoomForStart(room, List.of(member(room, amal), member(room, badr), member(room, carim)));
        LocalDate start = LocalDate.of(2026, 1, 31);

        RoomResponse response = roomService.startRoom(10L, 1L, TurnOrderMethod.MANUAL, start, List.of(2L, 1L, 3L));

        assertThat(response.status()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(response.members()).extracting("firstName", "turnPosition")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Badr", 1),
                        org.assertj.core.groups.Tuple.tuple("Amal", 2),
                        org.assertj.core.groups.Tuple.tuple("Carim", 3));

        List<Contribution> schedule = savedSchedule();
        // 3 cycles x 2 payers; nobody pays themselves
        assertThat(schedule).hasSize(6);
        assertThat(schedule).allMatch(c -> c.getPayer() != c.getRecipient());
        // Cycle 1 goes to Badr; monthly due dates keep the end of the month
        assertThat(schedule).filteredOn(c -> c.getCycleNumber() == 1)
                .allMatch(c -> c.getRecipient().getUser() == badr && c.getDueDate().equals(start));
        assertThat(schedule).filteredOn(c -> c.getCycleNumber() == 2)
                .allMatch(c -> c.getDueDate().equals(LocalDate.of(2026, 2, 28)));
        assertThat(schedule).allMatch(c -> c.getAmount().equals(new BigDecimal("100.00")));
    }

    @Test
    void startRoom_rejectsJoinRequestsThatAreStillWaiting() {
        SavingsRoom room = room(10L, 3);
        givenRoomForStart(room, List.of(member(room, amal), member(room, badr)));
        JoinRequest waiting = new JoinRequest(room, carim, member(room, badr));
        when(joinRequestRepository.findByRoomIdAndStatusOrderByCreatedAtAsc(10L, JoinRequestStatus.PENDING))
                .thenReturn(List.of(waiting));

        roomService.startRoom(10L, 1L, TurnOrderMethod.RANDOM, LocalDate.now(), null);

        assertThat(waiting.getStatus()).isEqualTo(JoinRequestStatus.REJECTED);
    }

    @Test
    void startRoom_manualOrderMustListEveryMemberExactlyOnce() {
        SavingsRoom room = room(10L, 3);
        givenRoomForStart(room, List.of(member(room, amal), member(room, badr), member(room, carim)));

        assertThatThrownBy(() -> roomService.startRoom(10L, 1L, TurnOrderMethod.MANUAL, LocalDate.now(),
                List.of(1L, 1L, 2L)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> roomService.startRoom(10L, 1L, TurnOrderMethod.MANUAL, LocalDate.now(),
                List.of(1L, 2L)))
                .isInstanceOf(BadRequestException.class);
        verify(contributionRepository, never()).saveAll(anyList());
    }

    @Test
    void startRoom_randomOrderGivesEveryMemberOneUniqueTurn() {
        SavingsRoom room = room(10L, 3);
        givenRoomForStart(room, List.of(member(room, amal), member(room, badr), member(room, carim)));

        RoomResponse response = roomService.startRoom(10L, 1L, TurnOrderMethod.RANDOM, LocalDate.now(), null);

        assertThat(response.members()).extracting("turnPosition").containsExactlyInAnyOrder(1, 2, 3);
        assertThat(savedSchedule()).hasSize(6);
    }

    // ----- helpers -----

    private void givenRoomForStart(SavingsRoom room, List<RoomMember> members) {
        when(roomRepository.findByIdForUpdate(room.getId())).thenReturn(Optional.of(room));
        when(memberRepository.existsByRoomIdAndUserId(room.getId(), 1L)).thenReturn(true);
        when(memberRepository.findByRoomIdOrderByTurnPositionAscIdAsc(room.getId()))
                .thenReturn(new ArrayList<>(members));
    }

    @SuppressWarnings("unchecked")
    private List<Contribution> savedSchedule() {
        ArgumentCaptor<List<Contribution>> captor = ArgumentCaptor.forClass(List.class);
        verify(contributionRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    // A room created by Amal (user 1)
    private SavingsRoom room(Long id, int maxMembers) {
        SavingsRoom room = new SavingsRoom("Family", null, new BigDecimal("100.00"), "AED",
                ContributionFrequency.MONTHLY, maxMembers, amal);
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
