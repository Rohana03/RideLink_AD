package lk.sliit.ridelink.fare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Users are authenticated by the Account Service's JWT, so Spring's default in-memory user
// (and the generated password it logs at start-up) is not needed.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class FarePaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FarePaymentServiceApplication.class, args);
    }
}
