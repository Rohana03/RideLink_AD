package lk.sliit.ridelink.ride.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * One RestClient per downstream service. Each sends the shared X-Internal-Api-Key and uses
 * short timeouts, so a service that is down fails fast with a 503 instead of hanging the caller.
 */
@Configuration
public class ServiceClientConfig {

    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final String internalApiKey;
    private final SimpleClientHttpRequestFactory requestFactory;

    public ServiceClientConfig(
            @Value("${ridelink.internal.api-key}") String internalApiKey,
            @Value("${ridelink.services.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${ridelink.services.read-timeout-ms:5000}") long readTimeoutMs) {
        this.internalApiKey = internalApiKey;
        this.requestFactory = new SimpleClientHttpRequestFactory();
        this.requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        this.requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
    }

    @Bean
    public RestClient driverServiceRestClient(RestClient.Builder builder,
                                              @Value("${ridelink.services.driver-vehicle-base-url}") String baseUrl) {
        return build(builder, baseUrl);
    }

    @Bean
    public RestClient fareServiceRestClient(RestClient.Builder builder,
                                            @Value("${ridelink.services.fare-payment-base-url}") String baseUrl) {
        return build(builder, baseUrl);
    }

    private RestClient build(RestClient.Builder builder, String baseUrl) {
        // clone(): the auto-configured builder is shared, so each client gets its own copy
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(INTERNAL_API_KEY_HEADER, internalApiKey)
                .build();
    }
}
