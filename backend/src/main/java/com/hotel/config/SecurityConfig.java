package com.hotel.config;

import com.hotel.security.JwtAccessDeniedHandler;
import com.hotel.security.JwtAuthFilter;
import com.hotel.security.JwtAuthenticationEntryPoint;
import com.hotel.security.PaymentTrafficAbuseFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Allows @PreAuthorize("hasAuthority('ROOM_VIEW')")
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final String[] corsAllowedOrigins;
    private final int callbackMaxBodyBytes;
    private final int callbackRequestsPerMinute;
    private final int pollingRequestsPerMinute;
    private final int maximumTrackedPaymentClients;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            @Value("${app.cors.allowed-origins:http://localhost:4200}") String[] corsAllowedOrigins,
            @Value("${app.security.payment-abuse.callback-max-body-bytes:1048576}") int callbackMaxBodyBytes,
            @Value("${app.security.payment-abuse.callback-requests-per-minute:60}") int callbackRequestsPerMinute,
            @Value("${app.security.payment-abuse.polling-requests-per-minute:30}") int pollingRequestsPerMinute,
            @Value("${app.security.payment-abuse.maximum-tracked-clients:10000}") int maximumTrackedPaymentClients) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.callbackMaxBodyBytes = callbackMaxBodyBytes;
        this.callbackRequestsPerMinute = callbackRequestsPerMinute;
        this.pollingRequestsPerMinute = pollingRequestsPerMinute;
        this.maximumTrackedPaymentClients = maximumTrackedPaymentClients;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            JwtAccessDeniedHandler jwtAccessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/auth/social-identities").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/social-identities/*/link").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/auth/social-identities/*").authenticated()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/v1/hotels/public/**").permitAll()
                .requestMatchers("/api/room-types/public/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/reservations/public/book").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/partner/register", "/api/v1/partner/register").permitAll()
                .requestMatchers(
                        "/api/payments/vnpay-callback",
                        "/api/payments/vnpay-ipn",
                        "/api/payments/momo-ipn",
                        "/api/payments/zalopay-callback").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                        "/api/payment-providers/property/*/callback",
                        "/api/payment-providers/platform/*/callback",
                        "/api/payment-providers/property/*/refund-callback",
                        "/api/payment-providers/platform/*/refund-callback").permitAll()
                .requestMatchers("/api/rooms/search").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                .requestMatchers("/actuator/**").hasAnyAuthority("SUPER_ADMIN", "ROLE_SUPER_ADMIN")
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/ws-chat/**").permitAll()
<<<<<<< HEAD
                .requestMatchers("/api/notifications/**").authenticated()
=======
>>>>>>> codex/ui-functional-audit-polish
                .requestMatchers("/api/subscriptions/plans").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(paymentTrafficAbuseFilter(), AuthorizationFilter.class);

        return http.build();
    }

    private PaymentTrafficAbuseFilter paymentTrafficAbuseFilter() {
        return new PaymentTrafficAbuseFilter(
                callbackMaxBodyBytes,
                callbackRequestsPerMinute,
                pollingRequestsPerMinute,
                maximumTrackedPaymentClients);
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOrigins(java.util.Arrays.asList(corsAllowedOrigins));
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(java.util.Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.Arrays.asList(
<<<<<<< HEAD
                "Authorization", "Content-Type", "X-Refresh-Request", "X-Logout-Request",
                "X-Correlation-ID", "Idempotency-Key"));
        configuration.setExposedHeaders(java.util.List.of("X-Correlation-ID", "Retry-After"));
=======
                "Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-ID", "X-Refresh-Request", "X-Logout-Request"));
        configuration.setExposedHeaders(java.util.List.of("X-Correlation-ID"));
>>>>>>> codex/ui-functional-audit-polish
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
