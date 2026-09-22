package lk.sliit.ridelink.driver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lk.sliit.ridelink.driver.exception.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final InternalApiKeyFilter internalApiKeyFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public / Swagger / OpenAPI endpoints
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**"
                        ).permitAll()
                        // Internal endpoints are validated by InternalApiKeyFilter
                        .requestMatchers("/api/drivers/internal/**").hasRole("INTERNAL_SERVICE")
                        // Only DRIVER accounts may create or manage a driver profile
                        .requestMatchers(HttpMethod.POST, "/api/drivers").hasRole("DRIVER")
                        .requestMatchers("/api/drivers/me", "/api/drivers/me/**").hasRole("DRIVER")
                        // Any signed-in user (e.g. a passenger viewing their assigned driver)
                        .requestMatchers(HttpMethod.GET, "/api/drivers/{id}").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, HttpStatus.UNAUTHORIZED,
                                        "Missing or invalid Bearer token", request.getRequestURI()))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(response, HttpStatus.FORBIDDEN,
                                        "You do not have permission to access this resource", request.getRequestURI()))
                )
                .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Security rejections happen before any controller runs, so they never reach
     * GlobalExceptionHandler. This keeps their body in the same ErrorResponse shape.
     */
    private void writeError(HttpServletResponse response, HttpStatus status, String message, String path)
            throws IOException {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .build();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
