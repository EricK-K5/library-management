package com.example.library_management.configuration;

import com.example.library_management.dto.request.IntrospectRequest;
import com.example.library_management.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;

@Component
@RequiredArgsConstructor
//trien khai phuong thuc decode theo jwt
public class CustomJwtDecoder implements JwtDecoder {
    @Value("${jwt.access-key}")
    private String accessKey;

    private final AuthenticationService authenticationService;

//    class giai ma va xac minh jwt
    private volatile NimbusJwtDecoder nimbus;

    @Override
    public Jwt decode(String token) throws JwtException {
        // introspect kiểm tra: chữ ký access-key, type=ACCESS, hạn dùng, blacklist
        if (!authenticationService.introspect(IntrospectRequest.builder().token(token).build()).isValid())
            throw new JwtException("Token invalid");

        if (nimbus == null) {
            nimbus = NimbusJwtDecoder
                    .withSecretKey(new SecretKeySpec(accessKey.getBytes(), "HS256"))
                    .macAlgorithm(MacAlgorithm.HS256).build();
        }
        return nimbus.decode(token);
    }
}