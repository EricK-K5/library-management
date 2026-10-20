package com.example.library_management.configuration;

import com.example.library_management.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private static final LocalTime SEND_TIME = LocalTime.of(8, 0);

    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 8 * * *")
    public void sendDailyNotices() {
        try {
            int reminders = notificationService.sendDueSoonReminders();
            int overdue = notificationService.sendOverdueNotices();
            log.info("Notification job: {} nhac han, {} bao qua han", reminders, overdue);
        } catch (Exception e) {
            log.error("Notification job failed", e);
        }
    }

    // chay bu
    @EventListener(ApplicationReadyEvent.class)
    public void catchUpOnStartup() {
        if (LocalTime.now().isAfter(SEND_TIME)) {
            log.info("Startup catch-up: sending due / overdue notices");
            sendDailyNotices();
        }
    }
}