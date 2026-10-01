package com.example.library_management.configuration;

import com.example.library_management.repository.InvalidatedRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

// Reset token
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {
    private final InvalidatedRepository invalidatedRepository;

    @Scheduled(cron = "0 0 1 * * *")
    public void purge() {
        invalidatedRepository.deleteByExpiryTimeBefore(new Date());
        log.info("Purged expired invalidated tokens");
    }
}