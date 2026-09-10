package com.example.library_management.service;
import com.example.library_management.dto.request.AuthenticationRequest;
import com.example.library_management.dto.response.AuthenticationResponse;
import com.example.library_management.entity.User;
import com.example.library_management.repository.InvalidatedRepository;
import com.example.library_management.repository.UserRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.StringJoiner;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationService {
    UserRepository userRepository;

    @NonFinal
    @Value("${jwt.signerKey}")
    String SIGNER_KEY;

    @NonFinal
    @Value("${jwt.valid-duration}")
    long VALID_DURATION;

    @NonFinal
    @Value("${jwt.refreshable-duration}")
    long REFRESHABLE_DURATION;
    InvalidatedRepository invalidatedRepository;

    //    DANG NHAP KIEM TRA USER + PASSWORD => TAO TOKEN
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        var user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found with username: " + request.getUsername()));
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!authenticated) {
            throw new RuntimeException("Invalid credentials");
        }
        // Generate token here
        var token = generateToken(user);
        return AuthenticationResponse.builder()
                .token(token)
                .authenticated(true)
                .build();
    }

    //    TAO TOKEN
    public String generateToken(User user) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer("Tri Khoa")
                .issueTime(new Date())
                .expirationTime(
                        new Date(Instant.now().plus(VALID_DURATION, ChronoUnit.SECONDS).toEpochMilli())) // 24 hours
                .claim("scope", buildScope(user))
                .build();
        Payload payload = new Payload(claimsSet.toJSONObject());
        JWSObject jwsObject = new JWSObject(header, payload);
        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Cannot create token", e);
            throw new RuntimeException(e);
        }
    }

    //    BUILD SCOPE
    private String buildScope(User user) {
        StringJoiner stringJoiner = new StringJoiner(" ");
        if (!CollectionUtils.isEmpty(user.getRoles())) {
            user.getRoles()
                    .forEach(
                            role -> {
                                stringJoiner.add("ROLE_" + role.getName());
                                if (!CollectionUtils.isEmpty(role.getPermissions()))
                                    role.getPermissions()
                                            .forEach(permission -> stringJoiner.add(permission.getCode()));
                            });
        }
        return stringJoiner.toString();
    }

}
