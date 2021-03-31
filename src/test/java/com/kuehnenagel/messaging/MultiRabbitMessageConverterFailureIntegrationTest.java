package com.kuehnenagel.messaging;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.ErrorHandler;

import lombok.extern.slf4j.Slf4j;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import static com.kuehnenagel.messaging.MultiRabbitMessageConverterFailureIntegrationTest.MessageConverterFailureTestConfiguration;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("rabbitmq_docker")
@ContextConfiguration(
        initializers = ConfigDataApplicationContextInitializer.class,
        classes = { MultiRabbitTestConfiguration.class, MessageConverterFailureTestConfiguration.class })
@DirtiesContext
class MultiRabbitMessageConverterFailureIntegrationTest {

    @Autowired
    private RabbitTemplate myBroker1RabbitTemplate;

    @Autowired
    private TopicExchange myBroker1Producer1Exchange;

    @MockBean
    private ErrorHandler errorHandler;

    @Test
    void shouldWireDefaultMessageConverterAndFailOnBadJson() {
        // when
        myBroker1RabbitTemplate.convertAndSend(
                myBroker1Producer1Exchange.getName(),
                null,
                MultiRabbitTestConfiguration.SomePojo.builder().build(),
                m -> new Message("some bad json".getBytes(StandardCharsets.UTF_8), m.getMessageProperties()));

        // then
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            verify(errorHandler, atLeastOnce()).handleError(any());
        });
    }

    @Slf4j
    @TestConfiguration
    static class MessageConverterFailureTestConfiguration {

        @RabbitListener(
                id = "handleProducer1Message",
                queues = "${amqp.my-broker-1.producers.producer1.queue.name}",
                containerFactory = "myBroker1RabbitListenerContainerFactory")
        public void handleMessage(final MultiRabbitTestConfiguration.SomePojo someObject) {
            // will never make it here
        }

        @Bean
        public MessageConverter messageConverter() {
            // Default, will be used since a specific one is not found
            return new Jackson2JsonMessageConverter(new ObjectMapper());
        }

        @Bean
        public RetryOperationsInterceptor retryOperationsInterceptor() {
            // Default, will be used since a specific one is not found
            return RetryInterceptorBuilder.stateless().backOffOptions(100, 1.0, 10000).maxAttempts(3).build();
        }
    }
}
