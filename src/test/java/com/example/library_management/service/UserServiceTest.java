package com.example.library_management.service;

import com.example.library_management.dto.request.UserCreationRequest;
import com.example.library_management.dto.response.UserResponse;
import com.example.library_management.entity.Role;
import com.example.library_management.entity.User;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.mapper.UserMapper;
import com.example.library_management.repository.BorrowRecordRepository;
import com.example.library_management.repository.FineRepository;
import com.example.library_management.repository.ReservationRepository;
import com.example.library_management.repository.RoleRepository;
import com.example.library_management.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RoleRepository roleRepository;
    @Mock FileStorageService fileStorageService;
    @Mock BorrowRecordRepository borrowRecordRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock FineRepository fineRepository;

    UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    UserService userService;

    @BeforeEach
    void setUp() {
        // thu tu tham so theo thu tu field trong UserService
        userService = new UserService(userRepository, userMapper, passwordEncoder, roleRepository,
                fileStorageService, borrowRecordRepository, reservationRepository, fineRepository);
    }

    private UserCreationRequest validRequest() {
        return UserCreationRequest.builder()
                .username("khoa")
                .password("12345678")
                .fullName("Tri Khoa")
                .email("khoa@example.com")
                .phoneNumber("0912345678")
                .build();
    }

    // CREATE USER

    @Test
    void createUser_success() {
        when(userRepository.existsByUsername("khoa")).thenReturn(false);
        when(userRepository.existsByEmail("khoa@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("0912345678")).thenReturn(false);
        when(passwordEncoder.encode("12345678")).thenReturn("ENCODED");
        when(roleRepository.findByName("MEMBER")).thenReturn(Optional.of(Role.builder().name("MEMBER").build()));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("u1");
            return u;
        });

        UserResponse response = userService.createUser(validRequest());

        // user thuc su duoc luu xuong: mat khau da ma hoa, khong luu mat khau goc, co role MEMBER
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("ENCODED", saved.getPassword());
        assertNotEquals("12345678", saved.getPassword());
        assertEquals(1, saved.getRoles().size());
        assertTrue(saved.getRoles().stream().anyMatch(r -> "MEMBER".equals(r.getName())));

        // response
        assertEquals("u1", response.getId());
        assertEquals("khoa", response.getUsername());
        assertEquals(Set.of("MEMBER"), response.getRoles());
    }

    @Test
    void createUser_usernameExisted() {
        when(userRepository.existsByUsername("khoa")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> userService.createUser(validRequest()));

        assertEquals(ErrorCode.USERS_EXISTED, ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_emailExisted() {
        when(userRepository.existsByUsername("khoa")).thenReturn(false);
        when(userRepository.existsByEmail("khoa@example.com")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> userService.createUser(validRequest()));

        assertEquals(ErrorCode.EMAIL_EXISTED, ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_phoneExisted() {
        when(userRepository.existsByUsername("khoa")).thenReturn(false);
        when(userRepository.existsByEmail("khoa@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("0912345678")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> userService.createUser(validRequest()));

        assertEquals(ErrorCode.PHONE_EXISTED, ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }
}
