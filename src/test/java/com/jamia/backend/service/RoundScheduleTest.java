package com.jamia.backend.service;

import com.jamia.backend.entity.ContributionFrequency;
import com.jamia.backend.entity.RoomRound;
import com.jamia.backend.entity.TurnOrderMethod;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The turn moves by the clock: checks which turn is running at a given moment.
 */
class RoundScheduleTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 31, 10, 0);

    // 3 turns of 5 minutes: 10:00-10:05, 10:05-10:10, 10:10-10:15
    private final RoomRound fiveMinuteRound =
            new RoomRound(null, 1, TurnOrderMethod.RANDOM, START, START.plusMinutes(15));

    @Test
    void countsTheTurns() {
        assertThat(RoundSchedule.turnCount(fiveMinuteRound, ContributionFrequency.FIVE_MINUTES)).isEqualTo(3);

        RoomRound monthly = new RoomRound(null, 1, TurnOrderMethod.RANDOM, START, START.plusMonths(4));
        assertThat(RoundSchedule.turnCount(monthly, ContributionFrequency.MONTHLY)).isEqualTo(4);
    }

    @Test
    void theTurnMovesEveryFiveMinutes() {
        ContributionFrequency f = ContributionFrequency.FIVE_MINUTES;
        assertThat(RoundSchedule.currentTurn(fiveMinuteRound, f, START.minusSeconds(1))).isZero();   // not begun
        assertThat(RoundSchedule.currentTurn(fiveMinuteRound, f, START)).isEqualTo(1);
        assertThat(RoundSchedule.currentTurn(fiveMinuteRound, f, START.plusMinutes(4).plusSeconds(59))).isEqualTo(1);
        assertThat(RoundSchedule.currentTurn(fiveMinuteRound, f, START.plusMinutes(5))).isEqualTo(2);
        assertThat(RoundSchedule.currentTurn(fiveMinuteRound, f, START.plusMinutes(14))).isEqualTo(3);
        assertThat(RoundSchedule.currentTurn(fiveMinuteRound, f, START.plusMinutes(15))).isEqualTo(4); // all passed
    }

    @Test
    void knowsWhenEachTurnEnds() {
        assertThat(RoundSchedule.turnEndsAt(fiveMinuteRound, ContributionFrequency.FIVE_MINUTES, 2))
                .isEqualTo(START.plusMinutes(10));
    }
}
