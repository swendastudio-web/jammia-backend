package com.jamia.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every 30 seconds: finish rounds whose last turn has ended, so their rooms become OPEN again.
 * (The current turn itself needs no job: it is calculated from the clock whenever someone looks.)
 */
@Component
public class RoundClockJob {

    private static final Logger log = LoggerFactory.getLogger(RoundClockJob.class);

    private final SavingsRoomService roomService;

    public RoundClockJob(SavingsRoomService roomService) {
        this.roomService = roomService;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    public void finishEndedRounds() {
        int finished = roomService.completeFinishedRounds();
        if (finished > 0) {
            log.info("Finished {} round(s); their rooms are open again", finished);
        }
    }
}
