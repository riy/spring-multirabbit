# Spring MultiRabbit

## Overview

Spring MultiRabbit is a **highly opinionated** library that does a multitude of things that relieve you from many burdens:

* It lets you configuratively connect to multiple RabbitMQ brokers, as opposed to the one connection Spring's `RabbitAutoConfiguration` enables.
* For each broker it will auto-configure a separate:
  * a `ConnectionFactory`
  * a `RabbitTemplate`
  * a `RabbitAdmin`
  * a `RabbitHealthIndicator` 
  * a `RetryTemplate` 
  * a `RabbitListenerContainerFactory`
  * a `RetryOperationsInterceptor`

In addition to that you have the option to configure consumers and producers that are either set up on your RabbitMQ, or that you want to set up there.
This lets you automatically create or provide access to:

* a `TopicExchange`
* a `HeadersExchange`
* a `Queue`
* a `Binding`

All of the above will be configured with:

* a `MessageConverter`
* a `RetryOperationsInterceptor`
* and an `ErrorHandler`

Everything is declared (or provided to you) without a single line of code. Everything will be wired together, using the routing keys specified by you. This also goes for their deadletter equivalents.

Every one of them will be made available as a Spring `@Bean` under a specific name that you can autowire or override, if you need a custom setup.

When you're done you'll be relieved of writing unit or integration tests that check for the existence or proper wiring of Exchanges, Queues and Bindings. The Exchanges, Queues and Bindings will just be available, all due to the configuration you've specified, with no code written from your side.

## Integration

Add the following line to your `build.gradle`:

```gradle
  implementation "com.kuehnenagel.messaging:spring-multirabbit:<CURRENT-VERSION>"
```

Your Maven `pom.xml` will need to say:

```xml
  <dependency>
    <groupId>com.kuehnenagel.messaging</groupId>
    <artifactId>spring-multirabbit</artifactId>
    <version><CURRENT-VERSION></version>
  </dependency>
```

## Usage

As initially mentioned Spring MultiRabbit is highly opinionated and catered towards the needs of the environment at Kühne + Nagel AG & Co. KG it was originally conceived in, so some things may not yet be included, such as support for `DirectExchange` and `FanoutExchange`. It does not claim to be best-of-breed or follow best practices, so do your due diligence and make sure it does what you need. For instance it does not exactly replicate the auto-configuration mechanisms found in `RabbitAutoConfiguration`, which we didn't need in our specific environment. Also, as of now only all Queues that are created from the configuration are configured as `durable`.   

If there's anything you need please create an issue and explain what you think is missing and should be added. Also feel free to contribute and create pull requests.  

### Activation

Add ```@EnableMultiRabbit``` to your `@Configuration` and Spring MultiRabbit will kick in. It will read the `.yml` configuration, connect to the brokers and (optionally, if specified) create Exchanges, Queues and Bindings and provide access to them via autowireable Spring Beans:   

```java
@Configuration
@EnableMultiRabbit
public class MyConfiguration {
  ...
}

```

Please be aware that the `RabbitAutoConfiguration` is incompatible with this library, as it's expecting single instances of certain Bean types. Hence `@EnableMultiRabbit` excludes it via `@ImportAutoConfiguration(exclude = RabbitAutoConfiguration.class)`.  

So, again, you should be aware that the auto-configured Beans you're familiar with are not created anymore, so there is no `rabbitListenerContainerFactory` anymore, but a specific one for each broker, whose name is prefixed with the configured broker name. You now need to take into account, when you're annotating a method with `@RabbitListener`, that you will also need to set the `containerFactory` parameter to the specific one.  You'll find more information on the topic of "specific beans" in the sections below.

### Connecting to multiple brokers

The basis for the configuration is a key named `amqp` in your `.yml` configuration. Every key directly underneath it is considered a RabbitMQ broker, and underneath that comes its configuration.

Here is an example of how to specify multiple brokers to connect to. Please note that the keys starting at `rabbitmq:` represent the exact configuration `spring-boot-autoconfigure` expects, so you may look into Spring RabbitMQ for more information on the keys and values set there.

```yml
amqp:
  my-broker-1:
    prefetch-count: 250
    rabbitmq:
      host: some-host-somewhere
      port: 5672
      virtual-host: vhost1
      username: user1
      password: secret
      ssl:
        enabled: false
        validate-server-certificate: false
      connection-timeout: 5000ms
      listener:
        simple:
          retry:
            enabled: true
            initial-interval: 1s
            max-attempts: 3
            max-interval: 10s
            multiplier: 2
      template:
        retry:
          enabled: true
          initial-interval: 1s
          max-attempts: 3
          max-interval: 10s
          multiplier: 2
    my-broker-2:
      prefetch-count: 250
      rabbitmq:
        host: some-other-host-somewhere
        port: 5672
        virtual-host: vhost2
        username: user2
        password: secret
        ssl:
          enabled: false
          validate-server-certificate: false
        connection-timeout: 5000ms
        listener:
          simple:
            retry:
              enabled: true
              initial-interval: 3s
              max-attempts: 6
              max-interval: 10s
              multiplier: 2
```

This configuration specifies two RabbitMQ brokers to connect to: `my-broker-1` and `my-broker-2`. These are arbitrary names that you may choose yourself, and it's not necessary to number them or follow some form of naming pattern. 

Each one of the brokers is configured separately, according to the `RabbitProperties` that you're familiar with from Spring RabbitMQ.  
The `listener` and `template` properties will be used for all `RetryOperationsInterceptor` and `RabbitTemplate` instances that are being created. If you leave them out, and are still in need of retry handling in case of an Exception, you need to specify your own Spring Beans of type `RetryOperationsInterceptor` (for `RabbitListener`) and `RetryTemplate` (for `RabbitTemplate`). See below for more information on that.

### Auto-created Spring beans

Once the configuration is read Spring MultiRabbit will register the following Spring Beans that you may inject, or override by using the same (!) name as found in the log files on start up:

For the configuration based on the broker key name `my-broker-1` the following Spring Beans will be configured:

* a `myBroker1RabbitConnectionFactory`
* a `myBroker1RabbitTemplate`
* a `myBroker1RabbitAdmin`
* a `myBroker1RabbitHealthIndicator`
* a `myBroker1RabbitRetryTemplate`
* a `myBroker1RabbitListenerContainerFactory`

For the configuration based on the broker key name `my-broker-2`:

* a `myBroker2RabbitConnectionFactory`
* a `myBroker2RabbitTemplate`
* a `myBroker2RabbitAdmin`
* a `myBroker2RabbitHealthIndicator`
* a `myBroker2RabbitRetryTemplate`
* a `myBroker2RabbitListenerContainerFactory`

These are regular Spring Beans that you may inject into your code, like this:

```java
@Autowired
private CachingConnectionFactory myBroker1RabbitConnectionFactory;
```

Please note, that all generated Spring Bean names are sanitized and camelCased, so a broker name of `my-broker-1` will be turned into a prefix of `myBroker1`, yielding `myBroker1RabbitConnectionFactory`, `myBroker1RabbitTemplate` etc. as the Spring Bean names.

As with the regular `RabbitAutoConfiguration` these Spring Beans are autowired into each other where needed so that e.g. `myBroker1RabbitTemplate` and `myBroker1RabbitAdmin` are created using the `myBroker1RabbitConnectionFactory`, or the `myBroker1RabbitHealthIndicator` is created using the `myBroker1RabbitTemplate`. There's nothing you need to do here, if this auto-configuration fulfills your requirements.

### Read the log messages

When the application boots up it will print a bunch of `INFO` log messages from the `MultiRabbitConfigurer` class that explain the configuration that was created. It will print out Bean names, what was created, and if not why not etc..

You should configure Spring MultiRabbit and run your tests, to see what gets printed out. The log messages make it easier for you to make adjustments to your configuration where needed, then rinse and repeat.

Here is an example of such an output that is extremely helpful when trying to understand what has been created and what's missing.

```
...MultiRabbitConfigurer - Registered bean 'myBroker1RabbitProperties'
...MultiRabbitConfigurer - Did not register bean 'myBroker1RabbitConnectionFactory' as it already exists
...MultiRabbitConfigurer - Registered bean 'myBroker1RabbitRetryTemplate'
...MultiRabbitConfigurer - Registered bean 'myBroker1RabbitTemplate'
...MultiRabbitConfigurer - Registered bean 'myBroker1RabbitAdmin'
...MultiRabbitConfigurer - Registered bean 'myBroker1RabbitHealthIndicator'
...MultiRabbitConfigurer - Registered bean 'myBroker1RabbitListenerContainerFactory'
...MultiRabbitConfigurer - Registered bean 'myBroker1Consumer1ExchangeRabbitTemplate'
...MultiRabbitConfigurer - Registered bean 'myBroker1Consumer1Exchange'
...MultiRabbitConfigurer - Registered bean 'myBroker1Consumer1Queue'
...MultiRabbitConfigurer - Registered bean 'myBroker1Consumer1Binding'
...MultiRabbitConfigurer - Registered bean 'myBroker1Consumer1DeadletterExchangeRabbitTemplate'
...MultiRabbitConfigurer - Registered bean 'myBroker1Consumer1DeadletterExchange'
...MultiRabbitConfigurer - Registered bean 'myBroker1Consumer1DeadletterQueue'
...MultiRabbitConfigurer - Registered bean 'myBroker1Consumer1DeadletterBinding'
...MultiRabbitConfigurer - Registered bean 'myBroker1Consumer1Metadata'
...MultiRabbitConfigurer - Registered bean 'myBroker1Consumer2ExchangeRabbitTemplate'
...MultiRabbitConfigurer - Registered bean 'myBroker1Consumer2Exchange'
...MultiRabbitConfigurer - Registered bean 'myBroker1Consumer2Queue'
...MultiRabbitConfigurer - Registered bean 'myBroker1Consumer2Binding'
...MultiRabbitConfigurer - No deadletter-exchange configured for RabbitMQ configuration 'my-broker-1' and consumer or producer 'consumer2'
...MultiRabbitConfigurer - No deadletter-queue configured for RabbitMQ configuration 'my-broker-1' and consumer or producer 'consumer2'
...MultiRabbitConfigurer - No deadletter-binding created for RabbitMQ configuration 'my-broker-1' and consumer or producer 'consumer2' because of missing exchange configuration
...
...MultiRabbitConfigurer - Found a specific MessageConverter named 'myBroker1MessageConverter'
...MultiRabbitConfigurer - No unique or specific ErrorHandler found, creating a default ErrorHandler of type ConditionalRejectingErrorHandler for connection name 'my-broker-1'
...MultiRabbitConfigurer - Found a specific RetryOperationsInterceptor named 'myBroker1RetryOperationsInterceptor'
...
...MultiRabbitConfigurer - Configuring RabbitAdmin 'myBroker1RabbitAdmin' with ConnectionFactory 'myBroker1RabbitConnectionFactory' and virtual-host 'vhost1'
...MultiRabbitConfigurer - Declaring queue 'my-first.queue' with RabbitAdmin 'myBroker1RabbitAdmin' and ConnectionFactory 'CachingConnectionFactory [channelCacheSize=25, host=localhost, port=56128, active=true myBroker1RabbitConnectionFactory]'
...MultiRabbitConfigurer - Declaring queue 'my-first.queue.deadletter' with RabbitAdmin 'myBroker1RabbitAdmin' and ConnectionFactory 'CachingConnectionFactory [channelCacheSize=25, host=localhost, port=56128, active=true myBroker1RabbitConnectionFactory]'

```

### Configuration of the RabbitListenerContainerFactory

On startup the auto-configuration will try to find certain beans that are specific to the broker, in order to inject them into its `RabbitListenerContainerFactory`. In the above example, for the broker named `my-broker-1`, it will create a `myBroker1RabbitListenerContainerFactory` and during configuration it will look for:

* a Bean named `myBroker1ErrorHandler`
  * if there is none it will try to find exactly one (1) bean of type `ErrorHandler.class`
      * if none or more than one is found it will create a `ConditionalRejectingErrorHandler`
  * this is the bean you want to override, if you need special error handling on incoming messages
  * note: this is not the `errorHandler` you may set on your `@RabbitListener`
* a Bean named `myBroker1MessageConverter`
  * if there is none it will try to find exactly one (1) bean of type `MessageConverter`
      * if none or more than one is found it will create a `SimpleMessageConverter`
  * this is the bean you want to override, e.g. if you need to convert an incoming JSON message to an object using a `Jackson2JsonMessageConverter`
* a Bean named `myBroker1RetryOperationsInterceptor`
  * if there is none it will try to find exactly one (1) bean of type `RetryOperationsInterceptor`
      * if none or more than one is found it will create a stateless `RetryOperationsInterceptor` with an `initialInterval` of `1000`, a `multiplier` of `2.0`, a `maxInterval` of `10000` and `maxAttempts` set to `3`
  * this is the bean you want to override, so when your `RabbitListener` fails during message processing it should try again after a certain amount of time and for a certain number of times, e.g. try again after 1 second, then double the wait time until a maximum of 10 seconds and a maximum pause between each try of 10 seconds (important: make sure you throw an `AmqpRejectAndDontRequeueException` if the `@RabbitListener` fails for the `RetryOperationsInterceptor` to kick in)

Have a look at the [integration tests](src/test/java/kn/messaging/) for examples of the above.

### Overriding an auto-created Spring Bean
If one of the auto-created Spring Beans does not meet your requirements you may override it and specify your own version of it, by giving the Spring Bean the exact same name the library would have given it.

The following is an excerpt from [`MultiRabbitTestConfiguration.java`](src/test/java/kn/messaging/MultiRabbitTestConfiguration.java), where we boot up a Docker container and rewire the `myBroker1RabbitConnectionFactory` Spring Bean to point at the host and port of the Docker container that was started along with the test. The overriding here is necessary because the Docker port is randomly created and can't be put in the test's `.yml` file beforehand.

```java
@Bean
public CachingConnectionFactory myBroker1RabbitConnectionFactory(
        final ObjectProvider<ConnectionNameStrategy> connectionNameStrategy,
        final RabbitMQContainer rabbitMQContainer) {
    final RabbitProperties rabbitProperties = amqpProperties.getAmqp().get("myBroker1").getRabbitmq();
    rabbitProperties.setHost(rabbitMQContainer.getContainerIpAddress());
    rabbitProperties.setPort(rabbitMQContainer.getMappedPort(rabbitProperties.getPort()));
    return rabbitConnectionFactoryCreator.rabbitConnectionFactory(rabbitProperties, connectionNameStrategy);
}
```

So this example will lead to our very own hand-written `myBroker1RabbitConnectionFactory` Spring Bean and not the one Spring MultiRabbit creates for us. At the same time Spring MultiRabbit will use our custom `myBroker1RabbitConnectionFactory` for all other Spring Beans it creates, such as the `myBroker1RabbitTemplate` or the `myBroker1RabbitAdmin`.

Here's a `MessageConverter` that is not specific and will therefore be used for all brokers that don't have a specific `MessageConverter` bean configured:

```java
@Bean
public MessageConverter messageConverter() {
    return new Jackson2JsonMessageConverter(new ObjectMapper());
}
```

Here's a `MessageConverter` that is specific for the broker named `my-broker-1`, and it will not be used anywhere else but for this broker only:

```java
@Bean
public MessageConverter myBroker1MessageConverter() {
    return new Jackson2JsonMessageConverter(new MyMyBroker1SpecificObjectMapper());
}
```

Here's a `MessageConverter` that is specific for the broker named `my-broker-1` and the `RabbitTemplate` of the exchange named `customer-update`, and will not be used anywhere else but for this specific `RabbitTemplate` only:

```java
@Bean
public MessageConverter myBroker1CustomerUpdateExchangeMessageConverter() {
    return new Jackson2JsonMessageConverter(new MyMyBroker1CustomerUpdateSpecificObjectMapper());
}
```

Here's a specific `RetryOperationsInterceptor` that will only be used on the broker named `my-broker-1`:

```java
@Bean
public RetryOperationsInterceptor myBroker1RetryOperationsInterceptor() {
    // Specific one for this broker, has precedence over all others
    return RetryInterceptorBuilder.stateless().backOffOptions(100, 1.0, 10000).maxAttempts(3).build();
}
```

Here's an `ErrorHandler` used only on the broker named `my-broker-2` that will re-throw the exception wrapped in a `RuntimeException`:

```java
@Bean
public ErrorHandler myBroker2ErrorHandler() {
    return t -> {
        throw new RuntimeException(t);
    };
}
```

As mentioned earlier, please keep in mind that this is a Spring `ErrorHandler` and not a Spring RabbitMQ `RabbitListenerErrorHandler` that you may specify as the `errorHandler` parameter of a `@RabbitListener`.

### Auto-creating and -configuring Exchange, Queue and Binding and... more RabbitTemplates

While the above handles the connection part you may instruct Spring MultiRabbit to either declare (create) new or "connect" to existing Exchanges and Queues. This is purely optional, and you may choose not to use that feature, if you prefer to configure the Exchanges, Queues and Bindings using the good old Spring ways.

The following configuration layout declares a list of consumers and producers to create:

```yml
amqp:
  my-broker-1:
    rabbitmq:
      ...
    consumers:
      consumer1:
        ...
      arbitrary-consumer-name:
        ...
      some-other-consumer-name:
        ...
    producers:
      some-producer-name:
        ...
      another-producer:
        ...

```

### Sample configuration for a TopicExchange

Here is an example of such a configuration, done on the RabbitMQ broker named `my-broker-1`:

```yml
amqp:
  my-broker-1:
    rabbitmq:
      host: localhost
      port: 5672
      virtual-host: vhost1
      username: user1
      password: secret
      ...
    consumers:
      consumer1:
        exchange:
          type: topic # The default type if this attribute is omitted
          name: my-first.exchange # Bean name: myBroker1Consumer1Exchange
          should-declare: true
          routing-key: consumer1.routing-key # Bean name: myBroker1Consumer1ExchangeRabbitTemplate, pre-configured with Exchange name and Routing Key
        queue:
          name: my-first.queue # Bean name: myBroker1Consumer1Queue
          should-declare: true
          declare-binding: true # Bean name: myBroker1Consumer1Binding
          max-length: 42
          max-length-bytes: 16777216
          overflow: reject-publish
        deadletter-exchange:
          type: topic # The default type if this attribute is omitted
          name: my-first.exchange.deadletter # Bean name: myBroker1Consumer1DeadletterExchange
          should-declare: true
          routing-key: consumer1.routing-key.deadletter # Bean name: myBroker1Consumer1DeadletterExchangeRabbitTemplate, pre-configured with Exchange name and Routing Key
        deadletter-queue:
          name: my-first.queue.deadletter # Bean name: myBroker1Consumer1DeadletterQueue
          should-declare: true
          declare-binding: true # Bean name: myBroker1Consumer1DeadletterBinding
        metadata:
          arbitrary-key: arbitrary-value
          another-arbitrary-key: another-arbitrary-value
      consumer2:
        ...
```

This will create a `TopicExchange` named `my-first.exchange` and store it in the `ApplicationContext` as a Spring Bean `myBroker1Consumer1Exchange`. If you set `should-declare` to `false` it will assume the broker already contains an `Exchange` named `my-first.exchange` and not declare (read: create) it. The `TopicExchange` Spring Bean will still end up under the name of `myBroker1Consumer1Exchange` in the `ApplicationContext`.

Please note, that the `type` parameter has been specified as `topic`, but could have been omitted. Spring MultiRabbit will fall back to the default, which is `topic`.

It will then do the same for the deadletter exchange (created as a `TopicExchange` Spring Bean named `myBroker1Consumer1DeadletterExchange`), the queue named `my-first.queue` (created as a `Queue` Spring Bean named `myBroker1Consumer1Queue`) and the deadletter queue named `my-first.queue.deadletter` (created as a `Queue` Spring Bean named `myBroker1Consumer1DeadletterQueue`).

All of these will be wired appropriately, and at the end a `Binding` will be created that binds the `TopicExchange` to the `Queue` (same for the deadletter declarations), using the routing key `consumer1.routing-key` and `consumer1.routing-key.deadletter`. They will be stored as `Binding` Spring Beans named `myBroker1Consumer1Binding` and `myBroker1Consumer1DeadletterBinding`.

The values for `max-length`, `max-length-bytes` and `overflow` will become the `Queue` arguments `x-max-length`, `x-max-length-bytes` and `x-overflow`.

As a final step a `RabbitTemplate` for each Exchange will be created, named `myBroker1Consumer1ExchangeRabbitTemplate` (and `myBroker1Consumer1DeadletterExchangeRabbitTemplate` for the deadletter exchange). It will have the Exchange name and Routing Key wired into it, so you can make a simple call of `myBroker1Consumer1ExchangeRabbitTemplate.send(message)` and it will end up in the right Exchange with the right Routing Key attached to it.  
While creating this Spring Bean Spring MultiRabbit will look for a specific `MessageConverter` named `myBroker1Consumer1ExchangeMessageConverter` and wire it into `myBroker1Consumer1ExchangeRabbitTemplate `. If there is none it will try to find a `MessageConverter` specific for the broker that is named `myBroker1MessageConverter`, if that one is also not found it will look for exactly one (1) bean of type `MessageConverter` and if none, or more than one, is found it will create a `SimpleMessageConverter`.  
The `RetryPolicy` that the `RabbitTemplate` is configured with is a `SimpleRetryPolicy` for `AmqpException`, with `maxAttempts` taken from the `retry` configuration, and a `NeverRetryPolicy` for `Exception`. As of now this `RetryPolicy` can not be overridden with your own Spring Bean.

As mentioned previously, please refer to the log messages to understand what setup your configuration yields. This makes it easy to track errors and recognize missing keys in the configuration.

### Omitting keys

In the above example we could have omitted any of the values. Only what is complete will be created, so if there's an `exchange:` specified but no `queue:` then a `TopicExchange` Spring Bean will be created, but no `Queue` Spring Bean and therefore also no `Binding`.

As of now only exchanges of type `TopicExchange` and `HeadersExchange` may be created. The library does not support `DirectExchange` or `FanoutExchange` yet, but you are free to create them manually and, e.g. bind them to an auto-configured `Queue`. Spring MultiRabbit does not hinder you from adding anything else you need. It supports you where it can and stays out of your way for any custom configuration you prefer to make in your code.

### Sample configuration for a HeadersExchange

Here's what the configuration would look like if you needed a `HeadersExchange`:

```yml
amqp:
  my-broker-1:
    rabbitmq:
      host: localhost
      port: 5672
      virtual-host: vhost1
      username: user1
      password: secret
      ...
    consumers:
      consumer1:
        exchange:
          type: headers
          name: my-first.exchange # Bean names: myBroker1Consumer1Exchange and myBroker1Consumer1ExchangeRabbitTemplate
          should-declare: true
        queue:
          name: my-first.queue # Bean name: myBroker1Consumer1Queue
          should-declare: true
          declare-binding: true
          max-length: 42
          max-length-bytes: 16777216
          overflow: reject-publish
          bindings: # Create two Bindings between this exchange and queue, one with two arguments and one with just one
            - arguments: # Bean name: myBroker1Consumer1Binding0
                SomeHeaderKey1: SomeHeaderValue1
                SomeHeaderKey2: SomeHeaderValue2
            - arguments: # Bean name: myBroker1Consumer1Binding1
                AnotherHeaderKey: AnotherHeaderValue
        deadletter-exchange:
          type: headers
          name: my-first.exchange.deadletter # Bean names: myBroker1Consumer1DeadletterExchange and myBroker1Consumer1DeadletterExchangeRabbitTemplate
          should-declare: true
        deadletter-queue:
          name: my-first.queue.deadletter # Bean name: myBroker1Consumer1DeadletterQueue
          should-declare: true
          declare-binding: true
          bindings: # Create two Bindings between this exchange and queue, one with two arguments and one with just one
            - arguments: # Bean name: myBroker1Consumer1DeadletterBinding0
                SomeHeaderKey1: SomeHeaderValue1
                SomeHeaderKey2: SomeHeaderValue2
            - arguments: # Bean name: myBroker1Consumer1DeadletterBinding1
                AnotherHeaderKey: AnotherHeaderValue
        metadata:
          arbitrary-key: arbitrary-value
          another-arbitrary-key: another-arbitrary-value
      consumer2:
        ...
```

### Difference between consumers and producers

There is none.

As of now the `consumers` and `producers` keys are solely defined to configuratively differentiate between consuming and producing parts of the application, but the keys underneath them are identical. This means that both of them may contain specifications for Exchanges, Queues, Routing Keys etc., and technically there is no difference between what is generated from the `consumers` configuration values and the `producers` configuration values.

Just make sure the key names right underneath `consumers` and `producers` are unique if (theoretically) put into one list.

Check out the [`application-rabbitmq_docker.yml`](src/test/resources/config/application-rabbitmq_docker.yml) to see a more full-fledged example of such a configuration. It also contains more examples and comments on generated Spring Bean names and what happens in which case, e.g. if a queue name is missing or if a routing key has not been specified.

### Metadata

The key named `metadata` may be used if you want to add additional information to the consumer or producer, in order to make use of it in your custom code. What is entered here is provided as a Spring bean named `myBroker1Consumer1Metadata` and may be injected as a `Map`, using `@Qualifier("myBroker1Consumer1Metadata")` (which is kind of redundant, but apparently the only way to inject a `Map`, or as a `HashMap` without the `@Qualifier` (if your Checkstyle configuration permits direct usage of `HashMap` as class or instance variables).

The `metadata` has no effect whatsoever on the creation of Exchanges, Queues or Bindings and is solely available for your personal usage.

## Production use

This library is in production at Kühne + Nagel AG & Co. KG (Hamburg) in multiple applications and is processing millions of messages per day in a high performance environment. But as with any library found on the Internet you should run your own tests and make sure your technical requirements are met before putting it into production.

## How do I...?

### ... connect to an already-existing Exchange or Queue on my RabbitMQ?

Specify the configuration as in the examples above, but set `should-declare` and `declare-binding` to `false`, so the Exchange, Queue and Binding don't get created.

### ... not have an Exchange / a Queue / a Binding created?

If you provide all information on an Exchange it will be created, same goes for a Queue (as long as you also specify `should-declare: true` on it). If you omit one of them it will not be created, but the other will.  
Same for Bindings: they will only be created if you specify an Exchange **and** a Queue. Obviously, if any one of them is missing a Binding can not be created.

### ... only have multiple RabbitMQ connections made available to me, with no further magic?

Just skip the configuration sections for `consumers` and `producers` and all that will be created is a `CachingConnectionFactory` for each RabbitMQ instance. You may then proceed as before and create your Exchanges, Queues and Bindings yourself, via your Java code.

## Future plans

As mentioned above Spring MultiRabbit does not yet support a couple of features as there was no need for them at Kühne + Nagel.  

Here's a list of things that could be added in the future:

- support for `DirectExchange`
- support for `FanoutExchange`
- support for message/header properties for `RabbitTemplate`'s `send` operation
- non-durable Queues, could be made configurable at the `queue` parameter level
- additional parameters on a Queue, such as `ttl`, `exclusive` or `expires`
- make the `RetryPolicy` configurable as well, like the `MessageConverter`

Feel free to fork and create a pull request to add your desired features.

## Contributing

You only need a JDK 11, everything else comes with the build.

Build your code with: `./gradlew build`.

Please note that we're expecting 100% code coverage, hence we've specified the additional brokers named `code-coverage-rabbitmq-1` and `code-coverage-rabbitmq-2` in the [`application-rabbitmq_docker.yml`](src/test/resources/config/application-rabbitmq_docker.yml). They only make sure every part of the code is run.

You should also run `./gradlew spotlessApply` every now and then as otherwise the build may fail. This will format the code according to our coding style conventions.

In IntelliJ IDEA install the "Eclipse Code Formatter" plugin and import the [`eclipse_formatter.xml`](etc/config/ide/eclipse_formatter.xml) to have your code formatted according to our code style conventions. While you're there you should also import the [`project.importorder`](etc/config/ide/project.importorder) settings, so the imports also get formatted appropriately.
In Eclipse there's native support for importing the `eclipse_formatter.xml`.

## License

Apache License 2.0

## Credits

This library was developed by Kühne + Nagel (AG & Co.) KG.

**Original Author:**  
Rias A. Sherzad | [@Riyadh](https://twitter.com/Riyadh)
