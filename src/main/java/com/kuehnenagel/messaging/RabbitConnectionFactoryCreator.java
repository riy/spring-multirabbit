package com.kuehnenagel.messaging;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.amqp.rabbit.connection.RabbitConnectionFactoryBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.stereotype.Component;

@Component("rabbitConnectionFactoryCreator")
public class RabbitConnectionFactoryCreator {

    /* based on org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration.RabbitConnectionFactoryCreator */
    public CachingConnectionFactory rabbitConnectionFactory(
            final RabbitProperties rabbitProperties,
            final ObjectProvider<ConnectionNameStrategy> connectionNameStrategy) {
        final PropertyMapper map = PropertyMapper.get();
        final RabbitConnectionFactoryBean rabbitConnectionFactory = new RabbitConnectionFactoryBean();

        map.from(rabbitProperties::determineHost).whenNonNull().to(rabbitConnectionFactory::setHost);
        map.from(rabbitProperties::determinePort).whenNonNull().to(rabbitConnectionFactory::setPort);

        map.from(rabbitProperties::determineUsername).whenNonNull().to(rabbitConnectionFactory::setUsername);
        map.from(rabbitProperties::determinePassword).whenNonNull().to(rabbitConnectionFactory::setPassword);
        map.from(rabbitProperties::determineVirtualHost).whenNonNull().to(rabbitConnectionFactory::setVirtualHost);
        map.from(rabbitProperties::getRequestedHeartbeat)
                .whenNonNull()
                .asInt(Duration::getSeconds)
                .to(rabbitConnectionFactory::setRequestedHeartbeat);

        final RabbitProperties.Ssl ssl = rabbitProperties.getSsl();
        if (ssl.determineEnabled()) {
            rabbitConnectionFactory.setUseSSL(true);
            map.from(ssl::getAlgorithm).whenNonNull().to(rabbitConnectionFactory::setSslAlgorithm);
            map.from(ssl::getKeyStoreType).to(rabbitConnectionFactory::setKeyStoreType);
            map.from(ssl::getKeyStore).to(rabbitConnectionFactory::setKeyStore);
            map.from(ssl::getKeyStorePassword).to(rabbitConnectionFactory::setKeyStorePassphrase);
            map.from(ssl::getTrustStoreType).to(rabbitConnectionFactory::setTrustStoreType);
            map.from(ssl::getTrustStore).to(rabbitConnectionFactory::setTrustStore);
            map.from(ssl::getTrustStorePassword).to(rabbitConnectionFactory::setTrustStorePassphrase);
            map.from(ssl::isValidateServerCertificate)
                    .to((validate) -> rabbitConnectionFactory.setSkipServerCertificateValidation(!validate));
            map.from(ssl::getVerifyHostname).to(rabbitConnectionFactory::setEnableHostnameVerification);
        }
        map.from(rabbitProperties::getConnectionTimeout)
                .whenNonNull()
                .asInt(Duration::toMillis)
                .to(rabbitConnectionFactory::setConnectionTimeout);
        rabbitConnectionFactory.afterPropertiesSet();

        final CachingConnectionFactory cachingConnectionFactory;
        // CHECKSTYLE:OFF: IllegalCatch
        try {
            cachingConnectionFactory = new CachingConnectionFactory(rabbitConnectionFactory.getObject());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // CHECKSTYLE:ON: IllegalCatch

        map.from(isPublisherConfirms(rabbitProperties))
                .whenTrue()
                .to(s -> cachingConnectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.SIMPLE));
        map.from(isPublisherConfirms(rabbitProperties))
                .whenFalse()
                .to(s -> cachingConnectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.NONE));
        map.from(rabbitProperties::isPublisherReturns).to(cachingConnectionFactory::setPublisherReturns);
        final RabbitProperties.Cache.Channel channel = rabbitProperties.getCache().getChannel();
        map.from(channel::getSize).whenNonNull().to(cachingConnectionFactory::setChannelCacheSize);
        map.from(channel::getCheckoutTimeout)
                .whenNonNull()
                .as(Duration::toMillis)
                .to(cachingConnectionFactory::setChannelCheckoutTimeout);
        final RabbitProperties.Cache.Connection connection = rabbitProperties.getCache().getConnection();
        map.from(connection::getMode).whenNonNull().to(cachingConnectionFactory::setCacheMode);
        map.from(connection::getSize).whenNonNull().to(cachingConnectionFactory::setConnectionCacheSize);
        map.from(connectionNameStrategy::getIfUnique).whenNonNull().to(cachingConnectionFactory::setConnectionNameStrategy);

        return cachingConnectionFactory;
    }

    private Supplier<Boolean> isPublisherConfirms(final RabbitProperties rabbitProperties) {
        return () -> CachingConnectionFactory.ConfirmType.CORRELATED.equals(rabbitProperties.getPublisherConfirmType());
    }
}
