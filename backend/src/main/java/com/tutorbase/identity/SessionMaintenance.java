package com.tutorbase.identity;

import java.time.Clock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SessionMaintenance {
    private final IdentityStore store;
    private final Clock clock;

    SessionMaintenance(IdentityStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${tutor.identity.session-cleanup-interval:PT1H}",
            fixedDelayString = "${tutor.identity.session-cleanup-interval:PT1H}")
    @Transactional
    public void clean() {
        store.deleteExpiredOrRevokedSessions(clock.instant());
    }
}
