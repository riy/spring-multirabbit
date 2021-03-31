package com.kuehnenagel.messaging;

import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.api.RabbitListenerErrorHandler;
import org.springframework.amqp.rabbit.test.RabbitListenerTest;
import org.springframework.amqp.rabbit.test.RabbitListenerTestHarness;
import org.springframework.amqp.rabbit.test.RabbitListenerTestHarness.InvocationData;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

import static com.kuehnenagel.messaging.MultiRabbitDeadletterRoutingIntegrationTest.DeadletterRoutingTestConfiguration;
import static com.kuehnenagel.messaging.MultiRabbitTestConfiguration.SomePojo;
import static java.lang.String.format;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("rabbitmq_docker")
@ContextConfiguration(
        initializers = ConfigDataApplicationContextInitializer.class,
        classes = { MultiRabbitTestConfiguration.class, DeadletterRoutingTestConfiguration.class })
@RabbitListenerTest(spy = false, capture = true)
class MultiRabbitDeadletterRoutingIntegrationTest {

    @Autowired
    private RabbitTemplate myBroker1RabbitTemplate;

    @Autowired
    private TopicExchange myBroker1Consumer1Exchange;

    @Autowired
    private RabbitListenerTestHarness harness;

    @Value("${amqp.my-broker-1.consumers.consumer1.exchange.routing-key}")
    private String routingKey;

    @Test
    void shouldFailGracefullyAndRouteToDeadletterQueue() throws InterruptedException {
        // when
        myBroker1RabbitTemplate.convertAndSend(
                myBroker1Consumer1Exchange.getName(),
                routingKey,
                SomePojo.builder().someAttribute("someValue").build());

        // then
        final InvocationData invocationData = harness
                .getNextInvocationDataFor("handleConsumer1FailedMessage", 5, TimeUnit.SECONDS);
        final SomePojo somePojo = (SomePojo) invocationData.getArguments()[0];
        assertThat(somePojo).usingRecursiveComparison().isEqualTo(SomePojo.builder().someAttribute("someValue").build());
    }

    @TestConfiguration
    static class DeadletterRoutingTestConfiguration {

        @RabbitListener(
                id = "handleConsumer1Message",
                queues = "${amqp.my-broker-1.consumers.consumer1.queue.name}",
                containerFactory = "myBroker1RabbitListenerContainerFactory",
                errorHandler = "handleConsumer1MessageErrorHandler")
        public void handleMessageAndFail(final SomePojo someObject) {
            throw new NullPointerException("Something failed, deliberately.");
        }

        @RabbitListener(
                id = "handleConsumer1FailedMessage",
                queues = "${amqp.my-broker-1.consumers.consumer1.deadletter-queue.name}",
                containerFactory = "myBroker1RabbitListenerContainerFactory")
        public void handleDeadletteredMessage(final SomePojo someObject) {
            //
        }

        @Bean
        public MessageConverter myBroker1MessageConverter() {
            // Specific one for this broker, has precedence over all others
            return new Jackson2JsonMessageConverter(new ObjectMapper());
        }

        @Bean
        public RabbitListenerErrorHandler handleConsumer1MessageErrorHandler() {
            return (amqpMessage, message, exception) -> {
                throw new AmqpRejectAndDontRequeueException(
                        format("Deliberately failing, sending to deadletter queue when retries are exhausted"),
                        exception);
            };
        }

        @Bean
        public RetryOperationsInterceptor myBroker1RetryOperationsInterceptor() {
            // Specific one for this broker, has precedence over all others
            return RetryInterceptorBuilder.stateless().backOffOptions(100, 1.0, 10000).maxAttempts(3).build();
        }
    }
}
