package com.example.library_management.configuration;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    private static final String[] PUBLIC_ENDPOINTS = {
            "/users", "/auth/token", "/auth/introspect", "/auth/logout", "/auth/refresh"
    };

    private CustomJwtDecoder customJwtDecoder;
    public SecurityConfig(CustomJwtDecoder customJwtDecoder){
        this.customJwtDecoder = customJwtDecoder;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {

        //        QUI DINH API DUOC PHEP TRUY CAP
        httpSecurity.authorizeHttpRequests(
                request ->
                        request
//                    CHO PHEP OPTIONS đi qua khi backend có CORS + frontend khác origin.
                                .requestMatchers(HttpMethod.OPTIONS, "/**")
                                .permitAll()
                                .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS)
                                .permitAll()
                                .anyRequest()
                                .authenticated());

        //        CAU HINH RESOURCE SERVER
        httpSecurity.oauth2ResourceServer(
                oauth ->
                        oauth
                                .jwt(
                                        jwtConfigurer ->
                                                jwtConfigurer
                                                        .decoder(customJwtDecoder)));
//                                                        .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                                //                        NEU LOI SE DIEU HUONG DI QUA ...
//                                .authenticationEntryPoint(new JwtAuthenticationEntryPoint()));

        //        TAT CSRF CUA SPRING SECURITY
        httpSecurity.csrf(AbstractHttpConfigurer::disable);
        return httpSecurity.build();
    }

//    PASSWORD ENCODER
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

}
