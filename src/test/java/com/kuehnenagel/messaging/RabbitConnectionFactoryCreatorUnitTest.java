package com.kuehnenagel.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties.Cache.Connection;

import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RabbitConnectionFactoryCreatorUnitTest {

    @Mock
    private AmqpProperties properties;

    @Mock
    private RabbitProperties rabbitProperties;

    @Mock
    private ObjectProvider<ConnectionNameStrategy> objectProvider;

    @Mock
    private RabbitProperties.Ssl ssl;

    @Mock
    private RabbitProperties.Cache cache;

    @Mock
    private RabbitProperties.Cache.Channel channel;

    @Mock
    private Connection connection;

    @InjectMocks
    private RabbitConnectionFactoryCreator rabbitConnectionFactoryCreator;

    @BeforeEach
    void beforeEach() {
        // given
        given(rabbitProperties.getCache()).willReturn(cache);
        given(rabbitProperties.getSsl()).willReturn(ssl);
        given(ssl.determineEnabled()).willReturn(true);
        given(cache.getChannel()).willReturn(channel);
        given(cache.getConnection()).willReturn(connection);
        given(channel.getSize()).willReturn(1);
        given(connection.getSize()).willReturn(1);
    }

    @Test
    void shouldCoverSSLDisabled() throws Exception {
        // given
        given(ssl.determineEnabled()).willReturn(false);

        // when... then...
        rabbitConnectionFactoryCreator.rabbitConnectionFactory(rabbitProperties, objectProvider);
    }

    @Test
    void shouldCoverSettingSSLConfigurationWithValidateVertificate() throws Exception {
        // given
        given(ssl.determineEnabled()).willReturn(true);
        given(ssl.isValidateServerCertificate()).willReturn(true);

        // when... then...
        rabbitConnectionFactoryCreator.rabbitConnectionFactory(rabbitProperties, objectProvider);
    }

    @Test
    void shouldCoverSettingSSLConfigurationWithoutValidateVertificate() throws Exception {
        // given
        given(ssl.determineEnabled()).willReturn(true);
        given(ssl.isValidateServerCertificate()).willReturn(false);

        // when... then...
        rabbitConnectionFactoryCreator.rabbitConnectionFactory(rabbitProperties, objectProvider);
    }
}
