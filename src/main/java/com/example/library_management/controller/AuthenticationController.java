package com.example.library_management.controller;

import com.example.library_management.dto.request.AuthenticationRequest;
import com.example.library_management.dto.request.LogOutRequest;
import com.example.library_management.dto.request.RefreshRequest;
import com.example.library_management.dto.response.ApiResponse;
import com.example.library_management.dto.response.AuthenticationResponse;
import com.example.library_management.service.AuthenticationService;
import com.nimbusds.jose.JOSEException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.ParseException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE,makeFinal = true)
public class AuthenticationController {
    AuthenticationService authenticationService;

//    LOGIN
    @PostMapping("/token")
    ApiResponse<AuthenticationResponse> authenticate(@RequestBody AuthenticationRequest request) {
        var result = authenticationService.authenticate(request);
        return ApiResponse.<AuthenticationResponse>builder()
                .result(result)
                .build();
    }

//    LOG OUT
    @PostMapping("/logout")
    ApiResponse<Void> logOut(@RequestBody LogOutRequest request)
//   Parse khong doc/parse duoc du lieu
//    JOSSE Ki hoac verify co van de thi nem
            throws ParseException, JOSEException {
        authenticationService.logOut(request);
        return ApiResponse.<Void>builder()
                .build();
    }

//    REFRESH TOKEN
    @PostMapping("/refresh")
    ApiResponse<AuthenticationResponse> refresh(@RequestBody RefreshRequest request)
            throws ParseException, JOSEException{
        var result = authenticationService.refreshToken(request);
        return ApiResponse.<AuthenticationResponse>builder()
                .result(result)
                .build();
    }
}
