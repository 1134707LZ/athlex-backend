package br.edu.atitus.orderapi.configs;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;

@Configuration
public class ResilienceConfig {
    /**
     * Circuito do product-api: janela de 10 chamadas; abre com >= 50% de falhas (mín. 5 chamadas);
     * fica aberto 10 s e testa com 3 chamadas (half-open). 4xx de negócio são ignorados.
     * Métricas publicadas em /actuator/prometheus (resilience4j_circuitbreaker_*).
     */
    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry(MeterRegistry meters) {
        var registry = CircuitBreakerRegistry.of(circuitConfig());
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(registry).bindTo(meters);
        return registry;
    }

    public static CircuitBreakerConfig circuitConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .ignoreExceptions(HttpClientErrorException.class)
                .build();
    }
}
