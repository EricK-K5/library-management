package com.example.library_management.configuration;

import com.example.library_management.entity.Role;
import com.example.library_management.entity.User;
import com.example.library_management.repository.PermissionRepository;
import com.example.library_management.repository.RoleRepository;
import com.example.library_management.repository.UserRepository;
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
    PasswordEncoder passwordEncoder;
    UserRepository userRepository;
    RoleRepository roleRepository;
    PermissionRepository permissionRepository;

    @Bean
    ApplicationRunner applicationRunner() {
        return args -> {
            // TAO ROLE ADMIN
            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseGet(() -> roleRepository.save(
                            Role.builder()
                                    .name("ADMIN")
                                    .build()
                    ));

            // TAO ROLE MEMBER
            roleRepository.findByName("MEMBER")
                    .orElseGet(() -> roleRepository.save(
                            Role.builder()
                                    .name("MEMBER")
                                    .build()
                    ));

            // TAO ROLE LIBRARIAN
            roleRepository.findByName("LIBRARIAN")
                    .orElseGet(() -> roleRepository.save(
                            Role.builder()
                                    .name("LIBRARIAN")
                                    .build()
                    ));

            // TAO ADMIN
            if (!userRepository.existsByUsername("admin")) {

                User admin = User.builder()
                        .username("admin")
                        .password(passwordEncoder.encode("admin123"))
                        .email("admin@library.com")
                        .fullName("System Administrator")
                        .phoneNumber("0123456789")
                        .roles(new HashSet<>(Set.of(adminRole)))
                        .build();

                userRepository.save(admin);

                System.out.println("Default ADMIN account created.");
            }
        };
    }
}
