package com.example.library_management.configuration;

import com.example.library_management.entity.Role;
import com.example.library_management.entity.User;
import com.example.library_management.repository.PermissionRepository;
import com.example.library_management.repository.RoleRepository;
import com.example.library_management.repository.UserRepository;
import com.example.library_management.service.DataSeedingService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Set;

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
