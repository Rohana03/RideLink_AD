package lk.sliit.ridelink.fare.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RideLink — Fare & Payment Service API")
                        .version("1.0.0")
                        .description("Microservice for fare estimation and final fare calculation using a documented rule " +
                                "(max(minimumFare, (baseFare + perKmRate x km + perMinRate x minutes) x vehicleMultiplier)), " +
                                "simulated payment recording, payment status, and receipt generation and retrieval. " +
                                "Completed rides arrive from the Ride Management Service as a RideCompletedEvent " +
                                "(RabbitMQ, or the internal REST endpoint).")
                        .contact(new Contact()
                                .name("RideLink Team - Member 4")
                                .email("ridelink@sliit.lk"))
                        .license(new License().name("Academic Use Only")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT issued by the Account Service (POST /api/auth/login)"))
                        .addSecuritySchemes("internalApiKey", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Internal-Api-Key")
                                .description("Shared internal secret key for interservice calls (e.g. from Ride Management Service)")));
    }
}
