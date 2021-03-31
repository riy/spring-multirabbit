package com.kuehnenagel.messaging;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({ RabbitConnectionFactoryCreator.class, AmqpProperties.class, MultiRabbitListenerConfigurationSelector.class })
@EnableRabbit
@Documented
@Inherited
@ImportAutoConfiguration(exclude = RabbitAutoConfiguration.class)
public @interface EnableMultiRabbit {
}
