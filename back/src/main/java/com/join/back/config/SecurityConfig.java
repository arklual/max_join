package com.join.back.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.join.back.security.JwtAuthFilter;
import com.join.back.security.JwtService;
import com.join.back.security.MaxAuthFilter;
import com.join.back.security.TelegramAuthFilter;
import com.join.back.security.WebAppInitDataValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final WebAppInitDataValidator webAppInitDataValidator;
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;

    @Value("${max.bot-token}")
    private String botToken;

    @Value("${telegram.bot-token:}")
    private String telegramBotToken;

    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/admin/**")
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("ADMIN"))
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/api/config").permitAll()
                        .requestMatchers("/api/cities").permitAll()
                        .requestMatchers("/api/universities").permitAll()
                        .requestMatchers("/api/max/webhook").permitAll()
                        .requestMatchers("/api/telegram/webhook").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/max-login", "/api/auth/max-login/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(maxAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(telegramAuthFilter(), MaxAuthFilter.class)
                .addFilterBefore(jwtAuthFilter(), TelegramAuthFilter.class);

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${admin.username:admin}") String adminUsername,
            @Value("${admin.password:change_me}") String adminPassword
    ) {
        return new InMemoryUserDetailsManager(
                User.withUsername(adminUsername)
                        .password("{noop}" + adminPassword)
                        .roles("ADMIN")
                        .build()
        );
    }

    @Bean
    public MaxAuthFilter maxAuthFilter() {
        return new MaxAuthFilter(botToken, webAppInitDataValidator, objectMapper);
    }

    @Bean
    public TelegramAuthFilter telegramAuthFilter() {
        return new TelegramAuthFilter(telegramBotToken, webAppInitDataValidator, objectMapper);
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtService);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // DelegatingPasswordEncoder recognises {id} prefixes such as {noop} used
        // by the in-memory admin user; falling back to BCrypt for stored hashes
        // without a prefix keeps existing user passwords (legacy raw bcrypt)
        // verifiable.
        DelegatingPasswordEncoder delegating =
                (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        delegating.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return delegating;
    }
}
