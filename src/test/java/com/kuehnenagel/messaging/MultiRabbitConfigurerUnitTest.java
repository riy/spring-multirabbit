package com.kuehnenagel.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MultiRabbitConfigurerUnitTest {

    @Mock
    private AmqpProperties amqpProperties;

    @InjectMocks
    private MultiRabbitConfigurer multiRabbitConfigurer;

    @Test
    void shouldHandleMissingConfiguration() {
        // given
        given(amqpProperties.getAmqp()).willReturn(null);

        // when... then...
        assertThatCode(() -> multiRabbitConfigurer.registerBeanDefinitions(null, new SimpleBeanDefinitionRegistry()))
                .doesNotThrowAnyException();
    }
}
