package lk.sliit.ridelink.ride.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RideLink — Ride Management Service API")
                        .version("1.0.0")
                        .description("Ride requests, driver assignment and the ride lifecycle "
                                + "(REQUESTED -> ASSIGNED -> ACCEPTED -> IN_PROGRESS -> COMPLETED, or CANCELLED). "
                                + "Calls the Driver & Vehicle Service for eligible drivers and the Fare & Payment Service "
                                + "for estimates and final fares. Log in on the Account Service, then Authorize with the token.")
                        .contact(new Contact().name("RideLink Team - Member 3").email("ridelink@sliit.lk"))
                        .license(new License().name("Academic Use Only")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT issued by the Account Service (POST /api/auth/login)")));
    }
}
