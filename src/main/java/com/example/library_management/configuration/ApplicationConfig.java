package com.example.library_management.configuration;

import com.example.library_management.service.DataSeedingService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class ApplicationConfig {
    DataSeedingService dataSeedingService;
    @Bean
    ApplicationRunner applicationRunner() {
        return args -> dataSeedingService.seed();
    }
}
