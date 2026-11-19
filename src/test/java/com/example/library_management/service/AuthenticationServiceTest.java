package com.example.library_management.service;

import com.example.library_management.dto.request.AuthenticationRequest;
import com.example.library_management.entity.InvalidatedToken;
import com.example.library_management.entity.Permission;
import com.example.library_management.entity.Role;
import com.example.library_management.entity.User;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.repository.InvalidatedRepository;
import com.example.library_management.repository.UserRepository;
import com.example.library_management.service.AuthenticationService.TokenPair;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Token duoc tao va kiem tra bang thu vien Nimbus THAT
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock UserRepository userRepository;
    @Mock InvalidatedRepository invalidatedRepository;
    @Mock PasswordEncoder passwordEncoder;

    AuthenticationService authenticationService;

    User user;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(userRepository, invalidatedRepository, passwordEncoder);

        // Cac field @Value khong duoc Spring tiem khi tao bang new -> tu gan. HS256 can key >= 32 byte
        ReflectionTestUtils.setField(authenticationService, "ACCESS_KEY", "access-key-access-key-access-key-0001");
        ReflectionTestUtils.setField(authenticationService, "REFRESH_KEY", "refresh-key-refresh-key-refresh-key-02");
        ReflectionTestUtils.setField(authenticationService, "ACCESS_DURATION", 900L);
        ReflectionTestUtils.setField(authenticationService, "REFRESH_DURATION", 604800L);

        Permission permission = Permission.builder().code("book:create").build();
        Role role = Role.builder().name("MEMBER").permissions(Set.of(permission)).build();
        user = User.builder().username("khoa").password("ENCODED").roles(Set.of(role)).build();
    }

    private TokenPair login() {
        when(userRepository.findByUsername("khoa")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("12345678", "ENCODED")).thenReturn(true);
        return authenticationService.authenticate(
                AuthenticationRequest.builder().username("khoa").password("12345678").build());
    }

    private static JWTClaimsSet claimsOf(String token) throws Exception {
        return SignedJWT.parse(token).getJWTClaimsSet();
    }

    // LOGIN

    @Test
    void authenticate_success() throws Exception {
        TokenPair pair = login();

        JWTClaimsSet access = claimsOf(pair.accessToken());
        assertEquals("khoa", access.getSubject());
        assertEquals("ACCESS", access.getStringClaim("type"));
        assertTrue(access.getExpirationTime().after(new Date()));
        // scope = "ROLE_<ten role>" + cac permission cua role -> dung de @PreAuthorize hasAuthority(...)
        List<String> scopes = List.of(access.getStringClaim("scope").split(" "));
        assertTrue(scopes.contains("ROLE_MEMBER"));
        assertTrue(scopes.contains("book:create"));

        JWTClaimsSet refresh = claimsOf(pair.refreshToken());
        assertEquals("khoa", refresh.getSubject());
        assertEquals("REFRESH", refresh.getStringClaim("type"));
        assertNull(refresh.getClaim("scope")); // refresh token khong mang quyen
    }

    @Test
    void authenticate_userNotFound() {
        when(userRepository.findByUsername("hoa")).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> authenticationService.authenticate(
                AuthenticationRequest.builder().username("hoa").password("12345678").build()));

        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void authenticate_wrongPassword() {
        when(userRepository.findByUsername("khoa")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "ENCODED")).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> authenticationService.authenticate(
                AuthenticationRequest.builder().username("khoa").password("wrong").build()));

        // Cung ErrorCode voi "user khong ton tai" -> client khong do duoc username nao co that
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
    }

    // REFRESH

    @Test
    void refresh_validRefreshToken() throws Exception {
        TokenPair oldPair = login();
        String oldJti = claimsOf(oldPair.refreshToken()).getJWTID();

        TokenPair newPair = authenticationService.refresh(oldPair.refreshToken());

        // Token cu bi dua vao blacklist (rotation) -> khong dung lai duoc
        ArgumentCaptor<InvalidatedToken> captor = ArgumentCaptor.forClass(InvalidatedToken.class);
        verify(invalidatedRepository).save(captor.capture());
        assertEquals(oldJti, captor.getValue().getId());

        assertNotNull(newPair.accessToken());
        assertNotEquals(oldPair.refreshToken(), newPair.refreshToken());
    }

    @Test
    void refresh_withAccessToken() {
        TokenPair pair = login();

        // Access token ky bang ACCESS_KEY nen khong qua duoc buoc verify bang REFRESH_KEY
        AppException ex = assertThrows(AppException.class,
                () -> authenticationService.refresh(pair.accessToken()));

        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        verify(invalidatedRepository, never()).save(any());
    }

    @Test
    void refresh_revokedToken() {
        TokenPair pair = login();
        when(invalidatedRepository.existsById(anyString())).thenReturn(true); // token da logout / da refresh

        AppException ex = assertThrows(AppException.class,
                () -> authenticationService.refresh(pair.refreshToken()));

        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
        verify(invalidatedRepository, never()).save(any());
    }

    @Test
    void refresh_expiredToken() {
        // Tao refresh token da het han 10 giay truoc
        ReflectionTestUtils.setField(authenticationService, "REFRESH_DURATION", -10L);
        TokenPair pair = login();

//        do bi tu choi nen khong nhay vao kiem tra token
        AppException ex = assertThrows(AppException.class,
                () -> authenticationService.refresh(pair.refreshToken()));

        assertEquals(ErrorCode.UNAUTHENTICATED, ex.getErrorCode());
    }
}