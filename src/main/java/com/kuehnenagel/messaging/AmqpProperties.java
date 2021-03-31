package com.kuehnenagel.messaging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.core.QueueBuilder.Overflow;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import static org.springframework.amqp.core.QueueBuilder.Overflow.dropHead;
import static org.springframework.amqp.core.QueueBuilder.Overflow.rejectPublish;
import static org.springframework.util.ObjectUtils.isEmpty;

import static com.kuehnenagel.messaging.AmqpProperties.RabbitMQConfiguration.RabbitChannel.ExchangeType.topic;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Component("amqpProperties")
@ConfigurationProperties(prefix = "")
public class AmqpProperties {

    private Map<String, RabbitMQConfiguration> amqp;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RabbitMQConfiguration {

        private Integer prefetchCount;

        private RabbitProperties rabbitmq;
        private Map<String, RabbitChannel> consumers;
        private Map<String, RabbitChannel> producers;

        @Getter
        @Setter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class RabbitChannel {

            private DeclarableExchange exchange;
            private DeclarableExchange deadletterExchange;
            private DeclarableQueue queue;
            private DeclarableQueue deadletterQueue;

            // Random key/value pairs that don't affect exchange, queue or binding creation but can be helpful in your
            // usage of the configuration parameters, e.g. if you're reading the properties to implement some custom code.
            private Map<String, String> metadata;

            @Getter
            @Setter
            @SuperBuilder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class Declarable {

                private String name;
                private boolean shouldDeclare;
            }

            @Getter
            @Setter
            @SuperBuilder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class DeclarableExchange extends Declarable {

                private ExchangeType type;
                private String routingKey;

                public ExchangeType getType() {
                    return (type == null) ? topic : type;
                }

                public String getRoutingKey() {
                    return isEmpty(routingKey) ? "" : routingKey;
                }
            }

            @Getter
            @Setter
            @SuperBuilder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class DeclarableQueue extends Declarable {

                private Long maxLength;
                private Long maxLengthBytes;
                private String overflow;

                private boolean declareBinding;
                private List<BindingArguments> bindings;

                public List<BindingArguments> getBindings() {
                    if (bindings == null) {
                        bindings = new ArrayList<>();
                        // Allows for one (1) argument-less binding, in case no bindings were specified
                        bindings.add(new BindingArguments());
                    }

                    return bindings;
                }

                public Overflow getOverflow() {
                    if (overflow == null) {
                        return null;
                    }

                    switch (overflow) {
                    case "reject-publish":
                        return rejectPublish;
                    case "drop-head":
                        return dropHead;
                    default:
                        return null;
                    }
                }

                @Getter
                @Setter
                @Builder
                @NoArgsConstructor
                @AllArgsConstructor
                public static class BindingArguments {

                    private Map<String, String> arguments;

                    public Map<String, String> getArguments() {
                        if (arguments == null) {
                            arguments = new HashMap<>();
                        }

                        return arguments;
                    }
                }
            }

            public enum ExchangeType {
                topic, // default
                headers;
            }
        }
    }
}
