package com.kuehnenagel.messaging;

import java.io.Serializable;

import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@TestConfiguration
@RequiredArgsConstructor
@Import(RabbitConnectionFactoryCreator.class)
@EnableConfigurationProperties(AmqpProperties.class)
@EnableMultiRabbit
@EnableAutoConfiguration
public class MultiRabbitTestConfiguration {

    private final RabbitConnectionFactoryCreator rabbitConnectionFactoryCreator;
    private final AmqpProperties amqpProperties;

    @Bean
    public RabbitMQContainer rabbitMQContainer() {
        final RabbitMQContainer rabbitMQContainer = new RabbitMQContainer(
                DockerImageName.parse("rabbitmq:3.7.25-management-alpine")).withVhost("vhost1")
                        .withVhost("vhost2")
                        .withVhost("vhost3")
                        .withVhost("vhost4")
                        .withVhost("vhost5")
                        .withUser("user1", "secret")
                        .withUser("user2", "secret")
                        .withUser("user3", "secret")
                        .withUser("user4", "secret")
                        .withUser("user5", "secret")
                        .withPermission("vhost1", "user1", ".*", ".*", ".*")
                        .withPermission("vhost2", "user2", ".*", ".*", ".*")
                        .withPermission("vhost3", "user3", ".*", ".*", ".*")
                        .withPermission("vhost4", "user4", ".*", ".*", ".*")
                        .withPermission("vhost5", "user5", ".*", ".*", ".*");
        rabbitMQContainer.start();
        return rabbitMQContainer;
    }

    @Bean
    public CachingConnectionFactory myBroker1RabbitConnectionFactory(
            final ObjectProvider<ConnectionNameStrategy> connectionNameStrategy,
            final RabbitMQContainer rabbitMQContainer) {
        final RabbitProperties rabbitProperties = amqpProperties.getAmqp().get("my-broker-1").getRabbitmq();
        rabbitProperties.setHost(rabbitMQContainer.getContainerIpAddress());
        rabbitProperties.setPort(rabbitMQContainer.getMappedPort(rabbitProperties.getPort()));
        return rabbitConnectionFactoryCreator.rabbitConnectionFactory(rabbitProperties, connectionNameStrategy);
    }

    @Bean
    public CachingConnectionFactory myBroker2RabbitConnectionFactory(
            final ObjectProvider<ConnectionNameStrategy> connectionNameStrategy,
            final RabbitMQContainer rabbitMQContainer) {
        final RabbitProperties rabbitProperties = amqpProperties.getAmqp().get("my-broker-2").getRabbitmq();
        rabbitProperties.setHost(rabbitMQContainer.getContainerIpAddress());
        rabbitProperties.setPort(rabbitMQContainer.getMappedPort(rabbitProperties.getPort()));
        return rabbitConnectionFactoryCreator.rabbitConnectionFactory(rabbitProperties, connectionNameStrategy);
    }

    @Bean
    public CachingConnectionFactory namedBrokerRabbitConnectionFactory(
            final ObjectProvider<ConnectionNameStrategy> connectionNameStrategy,
            final RabbitMQContainer rabbitMQContainer) {
        final RabbitProperties rabbitProperties = amqpProperties.getAmqp().get("named-broker").getRabbitmq();
        rabbitProperties.setHost(rabbitMQContainer.getContainerIpAddress());
        rabbitProperties.setPort(rabbitMQContainer.getMappedPort(rabbitProperties.getPort()));
        return rabbitConnectionFactoryCreator.rabbitConnectionFactory(rabbitProperties, connectionNameStrategy);
    }

    @Bean
    public CachingConnectionFactory codeCoverageRabbitmq2RabbitConnectionFactory(
            final ObjectProvider<ConnectionNameStrategy> connectionNameStrategy,
            final RabbitMQContainer rabbitMQContainer) {
        final RabbitProperties rabbitProperties = amqpProperties.getAmqp().get("code-coverage-rabbitmq-2").getRabbitmq();
        rabbitProperties.setHost(rabbitMQContainer.getContainerIpAddress());
        rabbitProperties.setPort(rabbitMQContainer.getMappedPort(rabbitProperties.getPort()));
        return rabbitConnectionFactoryCreator.rabbitConnectionFactory(rabbitProperties, connectionNameStrategy);
    }

    @Getter
    @Builder
    static class SomePojo implements Serializable {

        private final String someAttribute;
    }
}
