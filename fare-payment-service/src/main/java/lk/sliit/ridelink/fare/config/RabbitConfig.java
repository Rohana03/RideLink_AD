package lk.sliit.ridelink.fare.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Asynchronous side of the ride-completion handoff: declares the exchange, queue and
 * binding the Ride Management Service publishes to. Loaded only when
 * ridelink.messaging.enabled=true, so the service also runs without a RabbitMQ broker.
 */
@Configuration
@ConditionalOnProperty(prefix = "ridelink.messaging", name = "enabled", havingValue = "true")
public class RabbitConfig {

    @Bean
    public TopicExchange rideEventsExchange(@Value("${ridelink.rabbit.ride-events-exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    public Queue rideCompletedQueue(@Value("${ridelink.rabbit.ride-completed-queue}") String name) {
        return QueueBuilder.durable(name).build();
    }

    @Bean
    public Binding rideCompletedBinding(
            Queue rideCompletedQueue,
            TopicExchange rideEventsExchange,
            @Value("${ridelink.rabbit.ride-completed-routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(rideCompletedQueue).to(rideEventsExchange).with(routingKey);
    }

    /**
     * JSON messages. The publisher's own class name (sent in the __TypeId__ header) does not
     * exist here, so the payload is always mapped to the listener's parameter type instead.
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
