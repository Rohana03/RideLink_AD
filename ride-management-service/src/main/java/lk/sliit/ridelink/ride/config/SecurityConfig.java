package lk.sliit.ridelink.ride.config;

import java.io.IOException;
import java.time.Instant;

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

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;
import lk.sliit.ridelink.ride.exception.ApiError;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // Only passengers request rides; the passenger is taken from the token
                        .requestMatchers(HttpMethod.POST, "/api/rides").hasRole("PASSENGER")
                        // Listing every ride is administration
                        .requestMatchers(HttpMethod.GET, "/api/rides").hasRole("ADMIN")
                        // The passenger (or an admin) asks for a driver to be assigned
                        .requestMatchers(HttpMethod.PATCH, "/api/rides/*/assign").hasAnyRole("PASSENGER", "ADMIN")
                        // Only drivers move the ride forward (the assigned driver is checked in the service)
                        .requestMatchers(HttpMethod.PATCH, "/api/rides/*/accept", "/api/rides/*/start", "/api/rides/*/complete")
                        .hasAnyRole("DRIVER", "ADMIN")
                        // View, "my rides" and cancel: any signed-in user, ownership checked in the service
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
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Security rejections never reach GlobalExceptionHandler, so they are written in the same ApiError shape here. */
    private void writeError(HttpServletResponse response, HttpStatus status, String message, String path)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, path)));
    }
}
