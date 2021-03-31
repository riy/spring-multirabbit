package com.kuehnenagel.messaging;

import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.test.RabbitListenerTest;
import org.springframework.amqp.rabbit.test.RabbitListenerTestHarness;
import org.springframework.amqp.rabbit.test.RabbitListenerTestHarness.InvocationData;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.ErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;

import static com.kuehnenagel.messaging.MultiRabbitTopicExchangeIntegrationTest.ListenerTestConfiguration;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("rabbitmq_docker")
@ContextConfiguration(
        initializers = ConfigDataApplicationContextInitializer.class,
        classes = { MultiRabbitTestConfiguration.class, ListenerTestConfiguration.class })
@RabbitListenerTest(spy = false, capture = true)
class MultiRabbitTopicExchangeIntegrationTest {

    @Autowired
    private RabbitTemplate myBroker1Consumer1ExchangeRabbitTemplate;

    @Autowired
    private RabbitListenerTestHarness harness;

    @Test
    void shouldReceiveAndConvertJsonPayload() throws InterruptedException {
        // when
        myBroker1Consumer1ExchangeRabbitTemplate
                .convertAndSend(MultiRabbitTestConfiguration.SomePojo.builder().someAttribute("someValue").build());

        // then
        final InvocationData invocationData = harness.getNextInvocationDataFor("handleConsumer1Message", 5, TimeUnit.SECONDS);
        final MultiRabbitTestConfiguration.SomePojo somePojo = (MultiRabbitTestConfiguration.SomePojo) invocationData
                .getArguments()[0];
        assertThat(somePojo).usingRecursiveComparison()
                .isEqualTo(MultiRabbitTestConfiguration.SomePojo.builder().someAttribute("someValue").build());
    }

    @TestConfiguration
    static class ListenerTestConfiguration {

        @RabbitListener(
                id = "handleConsumer1Message",
                queues = "${amqp.my-broker-1.consumers.consumer1.queue.name}",
                containerFactory = "myBroker1RabbitListenerContainerFactory")
        public void handleMessage(final MultiRabbitTestConfiguration.SomePojo someObject) {
            //
        }

        @Bean
        public MessageConverter myBroker1MessageConverter() {
            return new Jackson2JsonMessageConverter(new ObjectMapper());
        }

        @Bean
        public MessageConverter myBroker1Consumer1ExchangeMessageConverter() {
            return new Jackson2JsonMessageConverter(new ObjectMapper());
        }

        @Bean
        public ErrorHandler myBroker1ErrorHandler() {
            return t -> {
                throw new RuntimeException(t);
            };
        }
    }
}
