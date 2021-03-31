package com.kuehnenagel.messaging;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.kuehnenagel.messaging.AmqpProperties.RabbitMQConfiguration;
import com.kuehnenagel.messaging.AmqpProperties.RabbitMQConfiguration.RabbitChannel;
import com.kuehnenagel.messaging.AmqpProperties.RabbitMQConfiguration.RabbitChannel.DeclarableExchange;
import com.kuehnenagel.messaging.AmqpProperties.RabbitMQConfiguration.RabbitChannel.DeclarableQueue;
import com.kuehnenagel.messaging.AmqpProperties.RabbitMQConfiguration.RabbitChannel.DeclarableQueue.BindingArguments;

import org.apache.commons.text.CaseUtils;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AbstractExchange;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.AbstractConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.actuate.amqp.RabbitHealthIndicator;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties.Retry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.listener.RetryListenerSupport;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.ErrorHandler;

import lombok.extern.slf4j.Slf4j;

import static org.springframework.amqp.core.BindingBuilder.bind;
import static org.springframework.amqp.core.ExchangeBuilder.headersExchange;
import static org.springframework.amqp.core.ExchangeBuilder.topicExchange;
import static org.springframework.amqp.core.QueueBuilder.durable;
import static org.springframework.util.ObjectUtils.isEmpty;

import static com.kuehnenagel.messaging.AmqpProperties.RabbitMQConfiguration.RabbitChannel.ExchangeType;
import static java.lang.String.valueOf;

@Slf4j
@Configuration
@EnableConfigurationProperties(AmqpProperties.class)
public class MultiRabbitConfigurer implements ImportBeanDefinitionRegistrar, EnvironmentAware, BeanFactoryAware {

    private static final String SUFFIX_RABBIT_PROPERTIES = "rabbit-properties";
    private static final String SUFFIX_RABBIT_CONNECTION_FACTORY = "rabbit-connection-factory";
    private static final String SUFFIX_RABBIT_ADMIN = "rabbit-admin";
    private static final String SUFFIX_RABBIT_TEMPLATE = "rabbit-template";
    private static final String SUFFIX_RABBIT_HEALTH_INDICATOR = "rabbit-health-indicator";
    private static final String SUFFIX_RABBIT_RETRY_TEMPLATE = "rabbit-retry-template";
    private static final String SUFFIX_RABBIT_LISTENER_CONTAINER_FACTORY = "rabbit-listener-container-factory";
    private static final String SUFFIX_RABBIT_MESSAGE_CONVERTER = "message-converter";
    private static final String SUFFIX_RABBIT_ERROR_HANDLER = "error-handler";
    private static final String SUFFIX_RABBIT_RETRY_OPERATIONS_INTERCEPTOR = "retry-operations-interceptor";
    private static final String SUFFIX_RABBIT_EXCHANGE = "exchange";
    private static final String SUFFIX_RABBIT_DEADLETTER_EXCHANGE = "deadletter-exchange";
    private static final String SUFFIX_RABBIT_QUEUE = "queue";
    private static final String SUFFIX_RABBIT_DEADLETTER_QUEUE = "deadletter-queue";
    private static final String SUFFIX_RABBIT_BINDING = "binding";
    private static final String SUFFIX_RABBIT_DEADLETTER_BINDING = "deadletter-binding";
    private static final String EMPTY_ROUTING_KEY = "";
    private static final String SUFFIX_METADATA = "metadata";

    private AmqpProperties amqpProperties;
    private DefaultListableBeanFactory beanFactory;
    private BeanDefinitionRegistry beanDefinitionRegistry;

    @Override
    public void setEnvironment(final Environment environment) {
        this.amqpProperties = Binder.get(environment).bind("", AmqpProperties.class).orElse(null);
    }

    @Override
    public void setBeanFactory(final BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (DefaultListableBeanFactory) beanFactory;
    }

    @Override
    public void registerBeanDefinitions(
            final AnnotationMetadata importingClassMetadata,
            final BeanDefinitionRegistry beanDefinitionRegistry) {
        this.beanDefinitionRegistry = beanDefinitionRegistry;

        final Map<String, RabbitMQConfiguration> amqp = Optional.ofNullable(amqpProperties)
                .map(AmqpProperties::getAmqp)
                .orElse(null);
        if (amqp != null) {
            for (final String brokerConfigurationName : amqp.keySet()) {
                final RabbitMQConfiguration rabbitMQConfiguration = amqp.get(brokerConfigurationName);

                registerRabbitProperties(brokerConfigurationName, rabbitMQConfiguration);

                final String cachingConnectionFactoryBeanName = createAndRegisterCachingConnectionFactoryBean(
                        brokerConfigurationName,
                        rabbitMQConfiguration);

                final String retryTemplateBeanName = createAndRegisterRetryTemplate(
                        brokerConfigurationName,
                        rabbitMQConfiguration);

                final String rabbitTemplateBeanName = createAndRegisterRabbitTemplate(
                        brokerConfigurationName,
                        null,
                        cachingConnectionFactoryBeanName,
                        retryTemplateBeanName,
                        null,
                        null);

                final String rabbitAdminBeanName = createAndRegisterRabbitAdmin(
                        brokerConfigurationName,
                        cachingConnectionFactoryBeanName);

                createAndRegisterRabbitHealthIndicator(brokerConfigurationName, rabbitTemplateBeanName);

                createAndRegisterRabbitListenerContainerFactory(
                        brokerConfigurationName,
                        rabbitMQConfiguration,
                        cachingConnectionFactoryBeanName);

                // Create and register all TopicExchanges, Queues and Bindings
                final Map<String, RabbitChannel> consumers = rabbitMQConfiguration.getConsumers();
                if (consumers != null) {
                    createChannels(
                            brokerConfigurationName,
                            rabbitAdminBeanName,
                            cachingConnectionFactoryBeanName,
                            retryTemplateBeanName,
                            consumers);
                } else {
                    log.info("No consumers configured for RabbitMQ configuration '{}'", brokerConfigurationName);
                }

                final Map<String, RabbitChannel> producers = rabbitMQConfiguration.getProducers();
                if (producers != null) {
                    createChannels(
                            brokerConfigurationName,
                            rabbitAdminBeanName,
                            cachingConnectionFactoryBeanName,
                            retryTemplateBeanName,
                            producers);
                } else {
                    log.info("No producers configured for RabbitMQ configuration '{}'", brokerConfigurationName);
                }
            }
        } else {
            log.error("No top-level configuration key named 'amqp' found.");
        }
    }

    private void registerRabbitProperties(
            final String brokerConfigurationName,
            final RabbitMQConfiguration rabbitMQConfiguration) {
        final Supplier<RabbitProperties> rabbitPropertiesSupplier = rabbitMQConfiguration::getRabbitmq;

        registerBeanDefinition(
                createBeanDefinitionSupplier(RabbitProperties.class, rabbitPropertiesSupplier),
                brokerConfigurationName,
                SUFFIX_RABBIT_PROPERTIES);
    }

    private String createAndRegisterCachingConnectionFactoryBean(
            final String brokerConfigurationName,
            final RabbitMQConfiguration rabbitMQConfiguration) {
        final Supplier<AbstractConnectionFactory> cachingConnectionFactorySupplier = () -> {
            final RabbitConnectionFactoryCreator rabbitConnectionFactoryCreator = beanFactory
                    .getBean(RabbitConnectionFactoryCreator.class);
            final ObjectProvider<ConnectionNameStrategy> connectionNameStrategy = beanFactory
                    .getBeanProvider(ConnectionNameStrategy.class);
            return rabbitConnectionFactoryCreator
                    .rabbitConnectionFactory(rabbitMQConfiguration.getRabbitmq(), connectionNameStrategy);
        };

        return registerBeanDefinition(
                createBeanDefinitionSupplier(AbstractConnectionFactory.class, cachingConnectionFactorySupplier),
                brokerConfigurationName,
                SUFFIX_RABBIT_CONNECTION_FACTORY);
    }

    private String createAndRegisterRabbitTemplate(
            final String brokerConfigurationName,
            final String specificPrefix,
            final String cachingConnectionFactoryBeanName,
            final String retryTemplateBeanName,
            final String exchangeName,
            final String routingKey) {
        final Supplier<RabbitTemplate> rabbitTemplateSupplier = () -> {
            final RabbitTemplate rabbitTemplate = new RabbitTemplate(
                    (ConnectionFactory) beanFactory.getBean(cachingConnectionFactoryBeanName));
            if (exchangeName != null) {
                rabbitTemplate.setExchange(exchangeName);
                rabbitTemplate.setRoutingKey(routingKey);
            }
            rabbitTemplate.setMessageConverter(findMessageConverter(brokerConfigurationName, specificPrefix));
            rabbitTemplate.setRetryTemplate((RetryTemplate) beanFactory.getBean(retryTemplateBeanName));
            return rabbitTemplate;
        };

        return registerBeanDefinition(
                createBeanDefinitionSupplier(RabbitTemplate.class, rabbitTemplateSupplier),
                (specificPrefix == null) ? brokerConfigurationName : null,
                specificPrefix,
                SUFFIX_RABBIT_TEMPLATE);
    }

    private String createAndRegisterRabbitAdmin(
            final String brokerConfigurationName,
            final String cachingConnectionFactoryBeanName) {
        final Supplier<RabbitAdmin> rabbitAdminSupplier = () -> {
            final ConnectionFactory connectionFactory = (ConnectionFactory) beanFactory
                    .getBean(cachingConnectionFactoryBeanName);
            final RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
            log.info(
                    "Configuring RabbitAdmin '{}' with ConnectionFactory '{}' and virtual-host '{}'",
                    camelCasedBeanName(brokerConfigurationName, SUFFIX_RABBIT_ADMIN),
                    cachingConnectionFactoryBeanName,
                    connectionFactory.getVirtualHost());
            rabbitAdmin.setAutoStartup(true);
            return rabbitAdmin;
        };

        return registerBeanDefinition(
                createBeanDefinitionSupplier(RabbitAdmin.class, rabbitAdminSupplier),
                brokerConfigurationName,
                SUFFIX_RABBIT_ADMIN);
    }

    private void createAndRegisterRabbitHealthIndicator(
            final String brokerConfigurationName,
            final String rabbitTemplateBeanName) {
        final Supplier<RabbitHealthIndicator> rabbitHealthIndicatorSupplier = () -> new RabbitHealthIndicator(
                (RabbitTemplate) beanFactory.getBean(rabbitTemplateBeanName));

        registerBeanDefinition(
                createBeanDefinitionSupplier(RabbitHealthIndicator.class, rabbitHealthIndicatorSupplier),
                brokerConfigurationName,
                SUFFIX_RABBIT_HEALTH_INDICATOR);
    }

    private String createAndRegisterRetryTemplate(
            final String brokerConfigurationName,
            final RabbitMQConfiguration rabbitMQConfiguration) {
        final Supplier<RetryTemplate> retryTemplateSupplier = () -> {
            Retry retry = new Retry();
            if (rabbitMQConfiguration.getRabbitmq().getTemplate().getRetry().isEnabled()) {
                retry = rabbitMQConfiguration.getRabbitmq().getTemplate().getRetry();
                log.info(
                        "Configuring a RetryTemplate based on the 'amqp.{}.rabbitmq.template.retry.*' configuration values",
                        brokerConfigurationName);
            } else {
                log.info(
                        "Configuring a default RetryTemplate (initialInterval 1.000, multiplier 2.0, maxInterval 10.000 and maxAttempts 3) for the connection name '{}'",
                        brokerConfigurationName);
            }

            final RetryPolicy policy = createRetryPolicy(retry);
            final RetryTemplate retryTemplate = new RetryTemplate();
            retryTemplate.setRetryPolicy(policy);

            final ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
            final PropertyMapper map = PropertyMapper.get();
            map.from(retry::getInitialInterval).whenNonNull().as(Duration::toMillis).to(backOffPolicy::setInitialInterval);
            map.from(retry::getMultiplier).to(backOffPolicy::setMultiplier);
            map.from(retry::getMaxInterval).whenNonNull().as(Duration::toMillis).to(backOffPolicy::setMaxInterval);

            retryTemplate.setBackOffPolicy(backOffPolicy);
            retryTemplate.registerListener(createRetryListener());
            return retryTemplate;
        };

        return registerBeanDefinition(
                createBeanDefinitionSupplier(RetryTemplate.class, retryTemplateSupplier),
                brokerConfigurationName,
                SUFFIX_RABBIT_RETRY_TEMPLATE);
    }

    private void createAndRegisterRabbitListenerContainerFactory(
            final String brokerConfigurationName,
            final RabbitMQConfiguration rabbitMQConfiguration,
            final String cachingConnectionFactoryBeanName) {
        final Supplier<SimpleRabbitListenerContainerFactory> rabbitListenerContainerFactorySupplier = () -> {
            final SimpleRabbitListenerContainerFactory rabbitFactory = new SimpleRabbitListenerContainerFactory();
            rabbitFactory.setConnectionFactory((ConnectionFactory) beanFactory.getBean(cachingConnectionFactoryBeanName));
            rabbitFactory.setChannelTransacted(true);
            PropertyMapper.get()
                    .from(rabbitMQConfiguration.getPrefetchCount())
                    .whenNonNull()
                    .to(rabbitFactory::setPrefetchCount);
            rabbitFactory.setMessageConverter(findMessageConverter(brokerConfigurationName, brokerConfigurationName));
            rabbitFactory.setErrorHandler(findErrorHandler(brokerConfigurationName));
            rabbitFactory.setAdviceChain(findRetryOperationsInterceptor(brokerConfigurationName, rabbitMQConfiguration));
            return rabbitFactory;
        };

        registerBeanDefinition(
                createBeanDefinitionSupplier(
                        SimpleRabbitListenerContainerFactory.class,
                        rabbitListenerContainerFactorySupplier),
                brokerConfigurationName,
                SUFFIX_RABBIT_LISTENER_CONTAINER_FACTORY);
    }

    private MessageConverter findMessageConverter(final String brokerConfigurationName, final String specificPrefix) {
        if (specificPrefix != null) {
            final String specificMessageConverter = camelCasedBeanName(specificPrefix, SUFFIX_RABBIT_MESSAGE_CONVERTER);
            if (beanDefinitionRegistry.containsBeanDefinition(specificMessageConverter)) {
                log.info(
                        "Found a specific MessageConverter named '{}' in '{}'",
                        specificMessageConverter,
                        beanDefinitionRegistry.getBeanDefinition(specificMessageConverter).getFactoryBeanName());
                return (MessageConverter) beanFactory.getBean(specificMessageConverter);
            }
        }

        final String brokerSpecificMessageConverter = camelCasedBeanName(
                brokerConfigurationName,
                SUFFIX_RABBIT_MESSAGE_CONVERTER);
        if (beanDefinitionRegistry.containsBeanDefinition(brokerSpecificMessageConverter)) {
            log.info(
                    "Found a broker-specific MessageConverter named '{}' in '{}'",
                    brokerSpecificMessageConverter,
                    beanDefinitionRegistry.getBeanDefinition(brokerSpecificMessageConverter).getFactoryBeanName());
            return (MessageConverter) beanFactory.getBean(brokerSpecificMessageConverter);
        }

        final Map<String, MessageConverter> messageConverters = beanFactory.getBeansOfType(MessageConverter.class);
        final List<String> allAmqpBrokers = amqpProperties.getAmqp()
                .keySet()
                .stream()
                .map(this::camelCasedBeanName)
                .collect(Collectors.toList());
        messageConverters.keySet().removeIf(beanName -> allAmqpBrokers.stream().anyMatch(beanName::startsWith));
        if (messageConverters.size() == 1) {
            final MessageConverter messageConverter = messageConverters.values().iterator().next();
            log.info(
                    "Found a single non-specific MessageConverter in '{}'",
                    Optional.ofNullable(messageConverter.getClass().getDeclaringClass())
                            .map(Class::getName)
                            .orElse("(unknown)"));
            return messageConverter;
        }

        log.info(
                "No unique or specific MessageConverter found, creating a default MessageConverter of type SimpleMessageConverter for connection name '{}'",
                brokerConfigurationName);
        return new SimpleMessageConverter();
    }

    private ErrorHandler findErrorHandler(final String brokerConfigurationName) {
        final String amqpSpecificErrorHandler = camelCasedBeanName(brokerConfigurationName, SUFFIX_RABBIT_ERROR_HANDLER);
        if (beanDefinitionRegistry.containsBeanDefinition(amqpSpecificErrorHandler)) {
            log.info(
                    "Found a specific ErrorHandler named '{}' in '{}'",
                    amqpSpecificErrorHandler,
                    beanDefinitionRegistry.getBeanDefinition(amqpSpecificErrorHandler).getFactoryBeanName());
            return (ErrorHandler) beanFactory.getBean(amqpSpecificErrorHandler);
        }

        final List<String> allErrorHandlerBeanNames = amqpProperties.getAmqp()
                .keySet()
                .stream()
                .map(amqpName -> camelCasedBeanName(amqpName, SUFFIX_RABBIT_ERROR_HANDLER))
                .collect(Collectors.toList());
        final Map<String, ErrorHandler> errorHandlers = beanFactory.getBeansOfType(ErrorHandler.class);
        errorHandlers.keySet().removeAll(allErrorHandlerBeanNames);
        if (errorHandlers.size() == 1) {
            final ErrorHandler errorHandler = errorHandlers.values().iterator().next();
            log.info(
                    "Found a single non-specific ErrorHandler in '{}'",
                    Optional.ofNullable(errorHandler.getClass().getDeclaringClass()).map(Class::getName).orElse("(unknown)"));
            return errorHandler;
        }

        log.info(
                "No unique or specific ErrorHandler found, creating a default ErrorHandler of type ConditionalRejectingErrorHandler for connection name '{}'",
                brokerConfigurationName);
        return new ConditionalRejectingErrorHandler();
    }

    private RetryOperationsInterceptor findRetryOperationsInterceptor(
            final String brokerConfigurationName,
            final RabbitMQConfiguration rabbitMQConfiguration) {
        final String amqpSpecificRetryOperationsInterceptor = camelCasedBeanName(
                brokerConfigurationName,
                SUFFIX_RABBIT_RETRY_OPERATIONS_INTERCEPTOR);
        if (beanDefinitionRegistry.containsBeanDefinition(amqpSpecificRetryOperationsInterceptor)) {
            log.info(
                    "Found a specific RetryOperationsInterceptor named '{}' in '{}'",
                    amqpSpecificRetryOperationsInterceptor,
                    beanDefinitionRegistry.getBeanDefinition(amqpSpecificRetryOperationsInterceptor).getFactoryBeanName());
            return (RetryOperationsInterceptor) beanFactory.getBean(amqpSpecificRetryOperationsInterceptor);
        }

        final List<String> allRetryOperationsInterceptorBeanNames = amqpProperties.getAmqp()
                .keySet()
                .stream()
                .map(amqpName -> camelCasedBeanName(amqpName, SUFFIX_RABBIT_RETRY_OPERATIONS_INTERCEPTOR))
                .collect(Collectors.toList());
        final Map<String, RetryOperationsInterceptor> retryOperationsInterceptors = beanFactory
                .getBeansOfType(RetryOperationsInterceptor.class);
        retryOperationsInterceptors.keySet().removeAll(allRetryOperationsInterceptorBeanNames);
        if (retryOperationsInterceptors.size() == 1) {
            final RetryOperationsInterceptor retryOperationsInterceptor = retryOperationsInterceptors.values()
                    .iterator()
                    .next();
            log.info(
                    "Found a single non-specific RetryOperationsInterceptor in '{}'",
                    Optional.ofNullable(retryOperationsInterceptor.getClass().getDeclaringClass())
                            .map(Class::getName)
                            .orElse("(unknown)"));
            return retryOperationsInterceptor;
        }

        RabbitProperties.ListenerRetry listenerRetry = new RabbitProperties.ListenerRetry();
        if (rabbitMQConfiguration.getRabbitmq().getListener().getSimple().getRetry().isEnabled()) {
            listenerRetry = rabbitMQConfiguration.getRabbitmq().getListener().getSimple().getRetry();
            log.info(
                    "No unique or specific RetryOperationsInterceptor found, creating a stateless RetryOperationsInterceptor based on the 'amqp.{}.rabbitmq.listener.simple.retry.*' configuration values",
                    brokerConfigurationName);
        } else {
            log.info(
                    "No unique or specific RetryOperationsInterceptor found, creating a default stateless RetryOperationsInterceptor (initialInterval 1.000, multiplier 2.0, maxInterval 10.000 and maxAttempts 3) for connection name '{}'",
                    brokerConfigurationName);
        }

        return RetryInterceptorBuilder.stateless()
                .backOffOptions(
                        listenerRetry.getInitialInterval().toMillis(),
                        listenerRetry.getMultiplier(),
                        listenerRetry.getMaxInterval().toMillis())
                .maxAttempts(listenerRetry.getMaxAttempts())
                .build();
    }

    private RetryPolicy createRetryPolicy(final Retry retryProperties) {
        final SimpleRetryPolicy recoverablePolicy = new SimpleRetryPolicy();
        PropertyMapper.get().from(retryProperties.getMaxAttempts()).to(recoverablePolicy::setMaxAttempts);

        final Map<Class<? extends Throwable>, RetryPolicy> policyMap = new HashMap<>();
        policyMap.put(Exception.class, new NeverRetryPolicy());
        policyMap.put(AmqpException.class, recoverablePolicy);

        final ExceptionClassifierRetryPolicy retryPolicy = new ExceptionClassifierRetryPolicy();
        retryPolicy.setPolicyMap(policyMap);
        return retryPolicy;
    }

    private RetryListener createRetryListener() {
        return new RetryListenerSupport() {

            @Override
            public <T, E extends Throwable> void onError(
                    final RetryContext context,
                    final RetryCallback<T, E> callback,
                    final Throwable throwable) {
                log.warn("Exception occurred in AMQP operation: " + throwable.getMessage(), throwable);
            }

        };
    }

    private void createChannels(
            final String brokerConfigurationName,
            final String rabbitAdminBeanName,
            final String cachingConnectionFactoryBeanName,
            final String retryTemplateBeanName,
            final Map<String, RabbitChannel> configuration) {
        for (final String channelName : configuration.keySet()) {
            final RabbitChannel rabbitChannel = configuration.get(channelName);

            // Exchange, Queue and Binding
            final String exchangeBeanName = createExchange(
                    brokerConfigurationName,
                    channelName,
                    rabbitAdminBeanName,
                    rabbitChannel.getExchange(),
                    cachingConnectionFactoryBeanName,
                    retryTemplateBeanName,
                    SUFFIX_RABBIT_EXCHANGE);

            final String queueBeanName = createQueue(
                    brokerConfigurationName,
                    channelName,
                    rabbitAdminBeanName,
                    rabbitChannel.getQueue(),
                    rabbitChannel.getDeadletterExchange(),
                    SUFFIX_RABBIT_QUEUE);

            createBinding(
                    brokerConfigurationName,
                    channelName,
                    rabbitAdminBeanName,
                    rabbitChannel.getExchange(),
                    rabbitChannel.getQueue(),
                    exchangeBeanName,
                    queueBeanName,
                    SUFFIX_RABBIT_BINDING);

            // Deadletter Exchange, Queue and Binding
            final String deadletterExchangeBeanName = createExchange(
                    brokerConfigurationName,
                    channelName,
                    rabbitAdminBeanName,
                    rabbitChannel.getDeadletterExchange(),
                    cachingConnectionFactoryBeanName,
                    retryTemplateBeanName,
                    SUFFIX_RABBIT_DEADLETTER_EXCHANGE);

            final String deadletterQueueBeanName = createQueue(
                    brokerConfigurationName,
                    channelName,
                    rabbitAdminBeanName,
                    rabbitChannel.getDeadletterQueue(),
                    null,
                    SUFFIX_RABBIT_DEADLETTER_QUEUE);

            createBinding(
                    brokerConfigurationName,
                    channelName,
                    rabbitAdminBeanName,
                    rabbitChannel.getDeadletterExchange(),
                    rabbitChannel.getDeadletterQueue(),
                    deadletterExchangeBeanName,
                    deadletterQueueBeanName,
                    SUFFIX_RABBIT_DEADLETTER_BINDING);

            createMetadataMap(brokerConfigurationName, channelName, rabbitChannel);
        }
    }

    private String createExchange(
            final String brokerConfigurationName,
            final String channelName,
            final String rabbitAdminBeanName,
            final DeclarableExchange exchangeConfiguration,
            final String cachingConnectionFactoryBeanName,
            final String retryTemplateBeanName,
            final String beanNameSuffix) {
        if (exchangeConfiguration == null) {
            log.info(
                    "No {} configured for RabbitMQ configuration '{}' and consumer or producer '{}'",
                    beanNameSuffix,
                    brokerConfigurationName,
                    channelName);
            return null;
        }

        if (isEmpty(exchangeConfiguration.getName())) {
            log.error(
                    "No {} name configured for RabbitMQ configuration '{}' and consumer or producer '{}'",
                    beanNameSuffix,
                    brokerConfigurationName,
                    channelName);
            return null;
        }

        final Supplier<Exchange> exchangeSupplier = () -> {
            final RabbitAdmin rabbitAdmin = (RabbitAdmin) beanFactory.getBean(rabbitAdminBeanName);
            final AbstractExchange exchange;
            if (exchangeConfiguration.getType() == ExchangeType.topic) {
                exchange = topicExchange(exchangeConfiguration.getName()).build();
            } else { // Exchange.headers
                exchange = headersExchange(exchangeConfiguration.getName()).build();
            }
            exchange.setAdminsThatShouldDeclare(rabbitAdmin);
            exchange.setShouldDeclare(exchangeConfiguration.isShouldDeclare());
            if (exchangeConfiguration.isShouldDeclare()) {
                rabbitAdmin.declareExchange(exchange);
            }

            return exchange;
        };

        createAndRegisterRabbitTemplate(
                brokerConfigurationName,
                brokerConfigurationName + "-" + channelName + "-" + beanNameSuffix,
                cachingConnectionFactoryBeanName,
                retryTemplateBeanName,
                exchangeConfiguration.getName(),
                exchangeConfiguration.getRoutingKey());

        return registerBeanDefinition(
                createBeanDefinitionSupplier(Exchange.class, exchangeSupplier),
                brokerConfigurationName,
                channelName,
                beanNameSuffix);
    }

    private String createQueue(
            final String brokerConfigurationName,
            final String channelName,
            final String rabbitAdminBeanName,
            final DeclarableQueue queueConfiguration,
            final DeclarableExchange deadletterExchangeConfiguration,
            final String beanNameSuffix) {
        if (queueConfiguration == null) {
            log.info(
                    "No {} configured for RabbitMQ configuration '{}' and consumer or producer '{}'",
                    beanNameSuffix,
                    brokerConfigurationName,
                    channelName);
            return null;
        }

        if (isEmpty(queueConfiguration.getName())) {
            log.error(
                    "No {} name configured for RabbitMQ configuration '{}' and consumer or producer '{}'",
                    beanNameSuffix,
                    brokerConfigurationName,
                    channelName);
            return null;
        }

        final Supplier<Queue> queueSupplier = () -> {
            final RabbitAdmin rabbitAdmin = (RabbitAdmin) beanFactory.getBean(rabbitAdminBeanName);
            log.info(
                    "Declaring queue '{}' with RabbitAdmin '{}' and ConnectionFactory '{}'",
                    queueConfiguration.getName(),
                    rabbitAdmin.getBeanName(),
                    rabbitAdmin.getRabbitTemplate().getConnectionFactory());

            final QueueBuilder queueBuilder = durable(queueConfiguration.getName());

            final PropertyMapper mapper = PropertyMapper.get();

            // Deadlettering for this Queue only needs to be configured for the regular Queue
            if ((deadletterExchangeConfiguration != null) && (deadletterExchangeConfiguration.getName() != null)) {
                mapper.from(deadletterExchangeConfiguration.getName()).to(queueBuilder::deadLetterExchange);
                mapper.from(deadletterExchangeConfiguration.getRoutingKey())
                        .whenNonNull()
                        .to(queueBuilder::deadLetterRoutingKey);
            }
            mapper.from(queueConfiguration.getMaxLength()).whenNonNull().asInt(Long::intValue).to(queueBuilder::maxLength);
            mapper.from(queueConfiguration.getMaxLengthBytes())
                    .whenNonNull()
                    .asInt(Long::intValue)
                    .to(queueBuilder::maxLengthBytes);
            mapper.from(queueConfiguration.getOverflow()).whenNonNull().to(queueBuilder::overflow);

            final Queue queue = queueBuilder.build();
            queue.setAdminsThatShouldDeclare(rabbitAdmin);
            queue.setShouldDeclare(queueConfiguration.isShouldDeclare());
            if (queueConfiguration.isShouldDeclare()) {
                rabbitAdmin.declareQueue(queue);
            }
            return queue;
        };

        return registerBeanDefinition(
                createBeanDefinitionSupplier(Queue.class, queueSupplier),
                brokerConfigurationName,
                channelName,
                beanNameSuffix);
    }

    private void createBinding(
            final String brokerConfigurationName,
            final String channelName,
            final String rabbitAdminBeanName,
            final DeclarableExchange exchangeConfiguration,
            final DeclarableQueue queueConfiguration,
            final String exchangeBeanName,
            final String queueBeanName,
            final String bindingNameSuffix) {
        if (isEmpty(exchangeBeanName)) {
            log.info(
                    "No {} created for RabbitMQ configuration '{}' and consumer or producer '{}' because of missing exchange configuration",
                    bindingNameSuffix,
                    brokerConfigurationName,
                    channelName);
            return;
        }

        if (isEmpty(queueBeanName)) {
            log.info(
                    "No {} created for RabbitMQ configuration '{}' and consumer or producer '{}' because of missing queue configuration",
                    bindingNameSuffix,
                    brokerConfigurationName,
                    channelName);
            return;
        }

        if (exchangeConfiguration.getType() == ExchangeType.headers) {
            int index = 0;
            for (final BindingArguments bindingArgs : queueConfiguration.getBindings()) {
                final Supplier<Binding> bindingSupplier = () -> {
                    final RabbitAdmin rabbitAdmin = (RabbitAdmin) beanFactory.getBean(rabbitAdminBeanName);
                    final Exchange headersExchange = (HeadersExchange) beanFactory.getBean(exchangeBeanName);
                    final Queue queue = (Queue) beanFactory.getBean(queueBeanName);
                    final Map<String, Object> argumentMap = createArgumentsMap(bindingArgs);
                    final Binding binding = bind(queue).to(headersExchange).with(EMPTY_ROUTING_KEY).and(argumentMap);
                    binding.setAdminsThatShouldDeclare(rabbitAdmin);
                    binding.setShouldDeclare(queueConfiguration.isDeclareBinding());
                    if (queueConfiguration.isDeclareBinding()) {
                        rabbitAdmin.declareBinding(binding);
                    }
                    return binding;
                };

                registerBeanDefinition(
                        createBeanDefinitionSupplier(Binding.class, bindingSupplier),
                        brokerConfigurationName,
                        channelName,
                        bindingNameSuffix,
                        valueOf(index++));
            }
        } else { // Default: ExchangeType.topic
            final Supplier<Binding> bindingSupplier = () -> {
                final RabbitAdmin rabbitAdmin = (RabbitAdmin) beanFactory.getBean(rabbitAdminBeanName);
                final TopicExchange topicExchange = (TopicExchange) beanFactory.getBean(exchangeBeanName);
                final Queue queue = (Queue) beanFactory.getBean(queueBeanName);
                final Binding binding = bind(queue).to(topicExchange).with(exchangeConfiguration.getRoutingKey());
                binding.setAdminsThatShouldDeclare(rabbitAdmin);
                binding.setShouldDeclare(queueConfiguration.isDeclareBinding());
                if (queueConfiguration.isDeclareBinding()) {
                    rabbitAdmin.declareBinding(binding);
                }
                return binding;
            };

            registerBeanDefinition(
                    createBeanDefinitionSupplier(Binding.class, bindingSupplier),
                    brokerConfigurationName,
                    channelName,
                    bindingNameSuffix);
        }
    }

    private Map<String, Object> createArgumentsMap(final BindingArguments bindingArgs) {
        return bindingArgs.getArguments().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void createMetadataMap(
            final String brokerConfigurationName,
            final String channelName,
            final RabbitChannel rabbitChannel) {
        if (rabbitChannel.getMetadata() != null) {
            registerBeanDefinition(
                    createBeanDefinitionSupplier(Map.class, rabbitChannel::getMetadata),
                    brokerConfigurationName,
                    channelName,
                    SUFFIX_METADATA);
        }
    }

    private String registerBeanDefinition(
            final Supplier<AbstractBeanDefinition> beanDefinitionSupplier,
            final String... beanNameTokens) {
        final String camelCaseBeanName = camelCasedBeanName(beanNameTokens);
        if (!beanDefinitionRegistry.containsBeanDefinition(camelCaseBeanName)) {
            beanDefinitionRegistry.registerBeanDefinition(camelCaseBeanName, beanDefinitionSupplier.get());
            log.info("Registered bean '{}'", camelCaseBeanName);
        } else {
            log.info("Did not register bean '{}' as it already exists", camelCaseBeanName);
        }
        return camelCaseBeanName;
    }

    private <T> Supplier<AbstractBeanDefinition> createBeanDefinitionSupplier(
            final Class<T> clazz,
            final Supplier<T> supplier) {
        return () -> BeanDefinitionBuilder.genericBeanDefinition(clazz, supplier).getBeanDefinition();
    }

    private String camelCasedBeanName(final String... beanNameTokens) {
        return CaseUtils.toCamelCase(sanitizeBeanNameTokens(beanNameTokens), false, '-');
    }

    private String sanitizeBeanNameTokens(final String... beanNameTokens) {
        return Arrays.stream(beanNameTokens)
                .filter(Objects::nonNull)
                .map(beanNameToken -> beanNameToken.replaceAll("[^A-Za-z0-9]", "-"))
                .collect(Collectors.joining("-"));
    }
}
