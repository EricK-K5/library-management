package com.example.library_management.controller;

import com.example.library_management.dto.request.AuthenticationRequest;
import com.example.library_management.dto.response.ApiResponse;
import com.example.library_management.dto.response.AuthenticationResponse;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.service.AuthenticationService;
import com.example.library_management.service.AuthenticationService.TokenPair;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

//  access token tra trong body
// refresh tra trong cookie, httpOnly, secure, sameSite=Strict, path=/api/auth
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationController {
    static final String COOKIE = "refresh_token";

    AuthenticationService authenticationService;

    @NonFinal @Value("${jwt.refresh-duration}")            long refreshDuration;
    @NonFinal @Value("${jwt.cookie.secure:true}")          boolean cookieSecure;
    @NonFinal @Value("${jwt.cookie.same-site:Strict}")     String cookieSameSite;
    @NonFinal @Value("${jwt.cookie.path:/api/auth}")       String cookiePath; // = context-path + /auth

    // LOGIN
    @PostMapping("/token")
    ApiResponse<AuthenticationResponse> login(@RequestBody AuthenticationRequest request,
                                              HttpServletResponse res) {
        TokenPair pair = authenticationService.authenticate(request);
        setRefreshCookie(res, pair.refreshToken(), Duration.ofSeconds(refreshDuration));
        return ok(pair.accessToken());
    }

    // REFRESH: không cần body, không cần Authorization, chỉ cần cookie
    @PostMapping("/refresh")
    ApiResponse<AuthenticationResponse> refresh(
            @CookieValue(name = COOKIE, required = false) String refreshToken,
            HttpServletResponse res) {
        if (refreshToken == null) throw new AppException(ErrorCode.UNAUTHENTICATED);
        TokenPair pair = authenticationService.refresh(refreshToken);
        setRefreshCookie(res, pair.refreshToken(), Duration.ofSeconds(refreshDuration)); // rotation
        return ok(pair.accessToken());
    }

    // LOGOUT: access lấy từ header, refresh lấy từ cookie
    @PostMapping("/logout")
    ApiResponse<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            @CookieValue(name = COOKIE, required = false) String refreshToken,
            HttpServletResponse res) {

        String access = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7) : null;

        // Thiếu access token => không thể huỷ nó, báo lỗi thay vì trả 200 giả
        if (access == null || access.isBlank())
            throw new AppException(ErrorCode.UNAUTHENTICATED);

        authenticationService.logOut(access, refreshToken);
        setRefreshCookie(res, "", Duration.ZERO); // xoá cookie
        return ApiResponse.<Void>builder().build();
    }

    private ApiResponse<AuthenticationResponse> ok(String accessToken) {
        return ApiResponse.<AuthenticationResponse>builder()
                .result(AuthenticationResponse.builder().token(accessToken).authenticated(true).build())
                .build();
    }

    private void setRefreshCookie(HttpServletResponse res, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path(cookiePath)
                .maxAge(maxAge)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}