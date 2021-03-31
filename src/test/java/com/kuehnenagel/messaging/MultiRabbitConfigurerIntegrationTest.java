package com.kuehnenagel.messaging;

import java.util.Map;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.amqp.RabbitHealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(SpringExtension.class)
@ActiveProfiles("rabbitmq_docker")
@ContextConfiguration(
        initializers = ConfigDataApplicationContextInitializer.class,
        classes = MultiRabbitTestConfiguration.class)
class MultiRabbitConfigurerIntegrationTest {

    @Autowired
    private AmqpProperties amqpProperties;

    @Autowired
    private CachingConnectionFactory myBroker1RabbitConnectionFactory;

    @Autowired
    private CachingConnectionFactory myBroker2RabbitConnectionFactory;

    @Autowired
    private CachingConnectionFactory namedBrokerRabbitConnectionFactory;

    @Autowired
    private RabbitAdmin myBroker1RabbitAdmin;

    @Autowired
    private RabbitTemplate myBroker1RabbitTemplate;

    @Autowired
    private RabbitHealthIndicator myBroker1RabbitHealthIndicator;

    @Autowired
    private RetryTemplate myBroker1RabbitRetryTemplate;

    @Autowired
    private RabbitListenerContainerFactory myBroker1RabbitListenerContainerFactory;

    @Autowired
    private TopicExchange myBroker1Consumer1Exchange;

    @Autowired
    private TopicExchange myBroker1Consumer1DeadletterExchange;

    @Autowired
    private Queue myBroker1Consumer1Queue;

    @Autowired
    private Queue myBroker1Consumer1DeadletterQueue;

    @Autowired
    private Binding myBroker1Consumer1Binding;

    @Autowired
    private Binding myBroker1Consumer1DeadletterBinding;

    @Autowired
    private Binding myBroker1Producer1Binding;

    @Autowired
    @Qualifier("myBroker1Consumer1Metadata") // Let's us inject Map
    private Map<String, String> myBroker1Consumer1Metadata;

    @Autowired
    private Queue namedBrokerConsumer1Queue;

    @Autowired
    private ApplicationContext applicationContext;

    @MockBean
    private Callable<Object> callable;

    @Test
    void shouldConnectToCustomRabbitConnectionFactory() {
        // then
        assertThat(myBroker1RabbitConnectionFactory.getHost()).isEqualTo("localhost");
        assertThat(myBroker2RabbitConnectionFactory.getHost()).isEqualTo("localhost");
        assertThat(namedBrokerRabbitConnectionFactory.getHost()).isEqualTo("localhost");
    }

    @Test
    void shouldCreateExpectedBeans() {
        // then
        assertThat(myBroker1RabbitAdmin).isNotNull();
        assertThat(myBroker1RabbitTemplate).isNotNull();
        assertThat(myBroker1RabbitHealthIndicator.getHealth(true).getStatus()).isEqualTo(Status.UP);
        assertThat(myBroker1RabbitRetryTemplate).isNotNull();
        assertThat(myBroker1RabbitListenerContainerFactory).isNotNull();
    }

    @Test
    void shouldCreateExpectedExchangeQueueAndBinding() {
        // then
        assertThat(myBroker1Consumer1Exchange).isNotNull();
        assertThat(myBroker1Consumer1Queue).isNotNull();
        assertThat(myBroker1Consumer1Binding.getExchange())
                .isEqualTo(amqpProperties.getAmqp().get("my-broker-1").getConsumers().get("consumer1").getExchange().getName());
        assertThat(myBroker1Consumer1Binding.getDestination())
                .isEqualTo(amqpProperties.getAmqp().get("my-broker-1").getConsumers().get("consumer1").getQueue().getName());
        assertThat(myBroker1Consumer1Binding.getRoutingKey()).isEqualTo(
                amqpProperties.getAmqp().get("my-broker-1").getConsumers().get("consumer1").getExchange().getRoutingKey());
    }

    @Test
    void shouldContainMetadata() {
        // then
        assertThat(myBroker1Consumer1Metadata.get("arbitrary-key")).isEqualTo("arbitrary-value");
    }

    @Test
    void shouldCreateExpectedDeadletterExchangeQueueAndBinding() {
        // then
        assertThat(myBroker1Consumer1DeadletterExchange).isNotNull();
        assertThat(myBroker1Consumer1DeadletterQueue).isNotNull();
        assertThat(myBroker1Consumer1DeadletterBinding.getExchange()).isEqualTo(
                amqpProperties.getAmqp().get("my-broker-1").getConsumers().get("consumer1").getDeadletterExchange().getName());
        assertThat(myBroker1Consumer1DeadletterBinding.getDestination()).isEqualTo(
                amqpProperties.getAmqp().get("my-broker-1").getConsumers().get("consumer1").getDeadletterQueue().getName());
        assertThat(myBroker1Consumer1DeadletterBinding.getRoutingKey()).isEqualTo(
                amqpProperties.getAmqp()
                        .get("my-broker-1")
                        .getConsumers()
                        .get("consumer1")
                        .getDeadletterExchange()
                        .getRoutingKey());
    }

    @Test
    void shouldFallbackToCatchAllRoutingKey() {
        // then
        assertThat(myBroker1Producer1Binding.getRoutingKey()).isEqualTo("#");
    }

    @Test
    void shouldNotCreateInsufficientlyOrUndefinedBeans() {
        // then
        assertThat(namedBrokerConsumer1Queue).isNotNull();
        assertThrows(NoSuchBeanDefinitionException.class, () -> applicationContext.getBean("namedBrokerConsumer1Binding"));
        assertThrows(NoSuchBeanDefinitionException.class, () -> applicationContext.getBean("myBroker2Consumer1Binding"));
    }

    @Test
    void shouldRetryInCaseOfAmqpException() throws Exception {
        // given
        // ... a correctly set up and started Spring context with above configuration
        assertThat(myBroker1RabbitRetryTemplate).isNotNull();
        // ... AmqpIoException being thrown
        given(callable.call()).willThrow(new AmqpIOException("Exception!", null));
        final RecoveryCallback<Object> recoveryCallback = null;

        // when
        final long start = System.currentTimeMillis();
        assertThrows(
                AmqpIOException.class,
                () -> myBroker1RabbitRetryTemplate.execute(context -> callable.call(), recoveryCallback));
        final long end = System.currentTimeMillis();

        // then
        verify(callable, times(3)).call();
        assertThat(end - start).isGreaterThanOrEqualTo(100);
    }
}
