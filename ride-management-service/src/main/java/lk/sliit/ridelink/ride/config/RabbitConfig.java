package lk.sliit.ridelink.ride.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Publisher side of the ride-completed event. The consumer (Fare &amp; Payment) declares and binds
 * its own queue; this service only needs the exchange and JSON messages. Loaded only when
 * ridelink.messaging.enabled=true, so the service runs without a broker by default.
 */
@Configuration
@ConditionalOnProperty(prefix = "ridelink.messaging", name = "enabled", havingValue = "true")
public class RabbitConfig {

    @Bean
    public TopicExchange rideEventsExchange(@Value("${ridelink.rabbit.ride-events-exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
