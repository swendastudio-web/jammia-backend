package com.jamia.backend.service;

import com.jamia.backend.entity.ContributionFrequency;
import com.jamia.backend.entity.RoomRound;

import java.time.LocalDateTime;

/**
 * Small time calculations for a round. The turn moves by the clock:
 * turn 1 runs from startedAt for one period, then turn 2, and so on until endsAt.
 */
public final class RoundSchedule {

    private RoundSchedule() {
    }

    // How many turns the round has (= members at the start of the round).
    public static int turnCount(RoomRound round, ContributionFrequency frequency) {
        int turns = 1;
        while (frequency.startOfCycle(round.getStartedAt(), turns + 1).isBefore(round.getEndsAt())) {
            turns++;
        }
        return turns;
    }

    // The turn running now: 0 = not begun yet, 1..count = running, count + 1 = all turns have passed.
    public static int currentTurn(RoomRound round, ContributionFrequency frequency, LocalDateTime now) {
        if (now.isBefore(round.getStartedAt())) {
            return 0;
        }
        int turns = turnCount(round, frequency);
        for (int turn = 1; turn <= turns; turn++) {
            if (now.isBefore(frequency.startOfCycle(round.getStartedAt(), turn + 1))) {
                return turn;
            }
        }
        return turns + 1;
    }

    // When a given turn ends (= when the next one begins).
    public static LocalDateTime turnEndsAt(RoomRound round, ContributionFrequency frequency, int turn) {
        return frequency.startOfCycle(round.getStartedAt(), turn + 1);
    }
}
