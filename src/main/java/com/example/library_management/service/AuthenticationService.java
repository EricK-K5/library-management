package com.example.library_management.service;

import com.example.library_management.dto.request.AuthenticationRequest;
import com.example.library_management.dto.request.IntrospectRequest;
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
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.StringJoiner;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationService {

    public enum TokenType { ACCESS, REFRESH }

    UserRepository userRepository;
    InvalidatedRepository invalidatedRepository;
    PasswordEncoder passwordEncoder;

    @NonFinal @Value("${jwt.access-key}")  String ACCESS_KEY;
    @NonFinal @Value("${jwt.refresh-key}") String REFRESH_KEY;
    @NonFinal @Value("${jwt.access-duration}")  long ACCESS_DURATION;
    @NonFinal @Value("${jwt.refresh-duration}") long REFRESH_DURATION;

    //LOGIN
    public TokenPair authenticate(AuthenticationRequest request) {
        var user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword()))
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        return issuePair(user);
    }

    //REFRESH
    public TokenPair refresh(String refreshToken) {
        SignedJWT jwt = verifyToken(refreshToken, TokenType.REFRESH);
        try {
            blacklist(jwt); // huỷ refresh token cũ
            var user = userRepository.findByUsername(jwt.getJWTClaimsSet().getSubject())
                    .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
            // TODO: nếu User có status (UserStatus) bị khoá thì chặn ở đây
            return issuePair(user);
        } catch (ParseException e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    //LOGOUT
    public void logOut(String accessToken, String refreshToken) {
        invalidateQuietly(accessToken, TokenType.ACCESS);
        invalidateQuietly(refreshToken, TokenType.REFRESH);
    }

    //INTROSPECT
    public IntrospectResponse introspect(IntrospectRequest request) {
        boolean valid = true;
        try {
            verifyToken(request.getToken(), TokenType.ACCESS);
        } catch (AppException e) {
            valid = false;
        }
        return IntrospectResponse.builder().valid(valid).build();
    }

    // ================= nội bộ =================
    private TokenPair issuePair(User user) {
        return new TokenPair(
                generateToken(user, TokenType.ACCESS),
                generateToken(user, TokenType.REFRESH));
    }

    private String generateToken(User user, TokenType type) {
        boolean isAccess = type == TokenType.ACCESS;
        long ttl = isAccess ? ACCESS_DURATION : REFRESH_DURATION;

        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer("Tri Khoa")
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plus(ttl, ChronoUnit.SECONDS).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .claim("type", type.name());
//        access mang quyen, refresh khong mang quyen(doc lai trong db)
        if (isAccess) claims.claim("scope", buildScope(user));

        JWSObject jws = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(claims.build().toJSONObject()));
        try {
            jws.sign(new MACSigner(keyOf(type).getBytes()));
            return jws.serialize();
        } catch (JOSEException e) {
            log.error("Cannot create token", e);
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    private SignedJWT verifyToken(String token, TokenType expected) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(keyOf(expected).getBytes()))) {
                log.warn("verifyToken[{}]: chu ky khong khop (sai key hoac token gia)", expected);
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            JWTClaimsSet c = jwt.getJWTClaimsSet();
            if (!expected.name().equals(c.getStringClaim("type"))) {
                log.warn("verifyToken[{}]: sai loai token, claim type={}", expected, c.getStringClaim("type"));
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }
            if (c.getExpirationTime() == null || !c.getExpirationTime().after(new Date())) {
                log.warn("verifyToken[{}]: het han luc {}", expected, c.getExpirationTime());
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }
            if (invalidatedRepository.existsById(c.getJWTID())) {
                log.warn("verifyToken[{}]: token da bi huy (jti={})", expected, c.getJWTID());
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }
            return jwt;
        } catch (ParseException | JOSEException | NullPointerException e) {
            log.error("verifyToken[{}]: loi khi parse/verify - {}", expected, e.getMessage(), e);
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }
    private void blacklist(SignedJWT jwt) throws ParseException {
        var c = jwt.getJWTClaimsSet();
        invalidatedRepository.save(InvalidatedToken.builder().id(c.getJWTID()).expiryTime(c.getExpirationTime()).build());
    }

    private void invalidateQuietly(String token, TokenType type) {
        if (token == null || token.isBlank()) return;
        try {
            blacklist(verifyToken(token, type));
        } catch (AppException | ParseException e) {
            log.info("{} token already invalid/expired", type);
        }
    }

    private String keyOf(TokenType t) { return t == TokenType.ACCESS ? ACCESS_KEY : REFRESH_KEY; }

    private String buildScope(User user) {
        StringJoiner sj = new StringJoiner(" ");
        if (!CollectionUtils.isEmpty(user.getRoles())) {
            user.getRoles().forEach(role -> {
                sj.add("ROLE_" + role.getName());
                if (!CollectionUtils.isEmpty(role.getPermissions()))
                    role.getPermissions().forEach(p -> sj.add(p.getCode()));
            });
        }
        return sj.toString();
    }

    public record TokenPair(String accessToken, String refreshToken) {}
}