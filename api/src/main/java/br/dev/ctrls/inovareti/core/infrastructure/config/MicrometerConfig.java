package br.dev.ctrls.inovareti.core.infrastructure.config;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracao central de observabilidade para o Inovare-TI.
 * Adiciona o tag global module em todas as observacoes originadas por @Observed.
 */
@Configuration
public class MicrometerConfig {

    private static final String MODULE_KEY = "module";
    private static final String CLASS_KEY = "class";
    private static final String MODULE_PREFIX = ".modules.";

    @Bean
    ObservationRegistryCustomizer<ObservationRegistry> moduleTagCustomizer() {
        return registry -> registry.observationConfig().observationFilter(context -> {
            if (hasTag(context, MODULE_KEY)) {
                return context;
            }

            String className = findClassName(context);
            if (className == null) {
                return context;
            }

            String module = resolveModule(className);
            if (module != null && !module.isBlank()) {
                context.addLowCardinalityKeyValue(KeyValue.of(MODULE_KEY, module));
            }
            return context;
        });
    }

    @Bean
    ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    private boolean hasTag(Observation.Context context, String key) {
        return context.getLowCardinalityKeyValues().stream()
                .anyMatch(keyValue -> Objects.equals(keyValue.getKey(), key));
    }

    private String findClassName(Observation.Context context) {
        Optional<String> classTag = context.getLowCardinalityKeyValues().stream()
                .filter(keyValue -> CLASS_KEY.equals(keyValue.getKey()))
                .map(KeyValue::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();

        if (classTag.isPresent()) {
            return classTag.get();
        }

        return context.getContextualName();
    }

    private String resolveModule(String className) {
        int modulesIndex = className.indexOf(MODULE_PREFIX);
        if (modulesIndex >= 0) {
            String remainder = className.substring(modulesIndex + MODULE_PREFIX.length());
            int separatorIndex = remainder.indexOf('.');
            if (separatorIndex > 0) {
                return remainder.substring(0, separatorIndex);
            }
        }

        if (className.contains(".core.")) {
            return "core";
        }

        return "unknown";
    }
}
