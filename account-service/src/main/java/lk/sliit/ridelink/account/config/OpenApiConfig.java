package lk.sliit.ridelink.account.config;

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
                        .title("RideLink — Account Service API")
                        .version("1.0.0")
                        .description("Microservice for passenger and driver account registration, login and JWT issuance, " +
                                "role management, profile viewing and updating, and account status management. " +
                                "Log in, copy the accessToken, then click Authorize and paste it.")
                        .contact(new Contact()
                                .name("RideLink Team - Member 1")
                                .email("ridelink@sliit.lk"))
                        .license(new License().name("Academic Use Only")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT returned by POST /api/auth/login"))
                        .addSecuritySchemes("internalApiKey", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Internal-Api-Key")
                                .description("Shared internal secret key for interservice calls")));
    }
}
