package com.example.library_management.service;
import com.example.library_management.dto.request.AuthenticationRequest;
import com.example.library_management.dto.request.IntrospectRequest;
import com.example.library_management.dto.request.LogOutRequest;
import com.example.library_management.dto.request.RefreshRequest;
import com.example.library_management.dto.response.AuthenticationResponse;
import com.example.library_management.dto.response.IntrospectResponse;
import com.example.library_management.entity.InvalidatedToken;
import com.example.library_management.entity.User;
import com.example.library_management.exception.AppException;
import com.example.library_management.exception.ErrorCode;
import com.example.library_management.repository.InvalidatedRepository;
import com.example.library_management.repository.UserRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

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
    long REFRESH_DURATION;
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
    private String generateToken(User user) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer("Tri Khoa")
                .issueTime(new Date())
                .expirationTime(
                        new Date(Instant.now().plus(VALID_DURATION, ChronoUnit.SECONDS).toEpochMilli()))
                .claim("scope", buildScope(user))
                .jwtID(UUID.randomUUID().toString())
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

    private SignedJWT verifyToken(String token, boolean isRefresh) {
        try {
            JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());

            SignedJWT signedJWT = SignedJWT.parse(token);

            Date expiryTime =
                    isRefresh
                            ? new Date(
                            signedJWT.getJWTClaimsSet()
                                    .getIssueTime()
                                    .toInstant()
                                    .plus(REFRESH_DURATION, ChronoUnit.SECONDS)
                                    .toEpochMilli())
                            : signedJWT.getJWTClaimsSet().getExpirationTime();

            boolean verified = signedJWT.verify(verifier);

            if (!verified || !expiryTime.after(new Date())) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            if (invalidatedRepository.existsById(
                    signedJWT.getJWTClaimsSet().getJWTID())) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            return signedJWT;

        } catch (ParseException | JOSEException e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }


    public void logOut(LogOutRequest request){
        try {
            var signToken = verifyToken(request.getToken(), true);
            String jit = signToken.getJWTClaimsSet().getJWTID();
            Date expiryTime = signToken.getJWTClaimsSet().getExpirationTime();

            InvalidatedToken invalidatedToken =
                    InvalidatedToken.builder().id(jit).expiryTime(expiryTime).build();
            invalidatedRepository.save(invalidatedToken);
        } catch (AppException e) {
            log.info("TOKEN ALREADY EXPIRED");
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    //    KIEM TRA JWT CON HOP LE KHONG
    public IntrospectResponse introspect(IntrospectRequest request)
            throws JOSEException, ParseException {
        var token = request.getToken();
        boolean inValid = true;
        try {
            verifyToken(token, false);
        } catch (AppException e) {
            inValid = false;
        }
        return IntrospectResponse.builder().valid(inValid).build();
    }

    //    REFRESH TOKEN
    public AuthenticationResponse refreshToken(RefreshRequest request)
            throws ParseException, JOSEException {
        var signJWT = verifyToken(request.getToken(), true);
        var jit = signJWT.getJWTClaimsSet().getJWTID();
        var expiryTime = signJWT.getJWTClaimsSet().getExpirationTime();

        //        CHO VAO BLACK LIST
        InvalidatedToken invalidatedToken =
                InvalidatedToken.builder().id(jit).expiryTime(expiryTime).build();
        invalidatedRepository.save(invalidatedToken);

        var username = signJWT.getJWTClaimsSet().getSubject();
        //        TAO TOKEN MOI
        var user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        var token = generateToken(user);

        return AuthenticationResponse.builder().token(token).authenticated(true).build();
    }
}
