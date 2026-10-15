package com.example.library_management.configuration;

import com.example.library_management.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@Slf4j
public class ReservationScheduler {

    ReservationService reservationService;

    // Chay moi ngay luc 00:05 - quet reservation ACCEPTED da qua expiryDate (het han cuoi ngay) chua den lay
    @Scheduled(cron = "0 5 0 * * *")
    public void expireOverdueReservations() {
        log.info("Running scheduled job: expire overdue reservations");

        try {
            reservationService.processExpiredReservations();
        } catch (Exception e) {
            log.error("Scheduled job: failed to process expired reservations", e);
        }

        try {
            int stale = reservationService.expireStalePendingReservations();
            log.info("Expired {} stale pending reservations", stale);
        } catch (Exception e) {
            log.error("Scheduled job: failed to expire stale pending reservations", e);
        }
    }

    // Chay bu khi ung dung khoi dong
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        log.info("Startup catch-up: expiring overdue reservations");
        expireOverdueReservations();
    }
}