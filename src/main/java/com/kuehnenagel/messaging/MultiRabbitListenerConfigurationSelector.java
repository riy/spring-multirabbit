package com.kuehnenagel.messaging;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.Ordered;
import org.springframework.core.type.AnnotationMetadata;

public class MultiRabbitListenerConfigurationSelector implements DeferredImportSelector, Ordered {

    @Override
    public String[] selectImports(final AnnotationMetadata importingClassMetadata) {
        return new String[] { MultiRabbitConfigurer.class.getName() };
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
