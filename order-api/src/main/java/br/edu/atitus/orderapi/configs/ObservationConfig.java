package br.edu.atitus.orderapi.configs;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservationConfig {
    /** Não gera traces para /actuator (health checks do Consul e coleta do Prometheus), que poluiriam o Zipkin. */
    @Bean
    ObservationPredicate ignoreActuator() {
        return (name, context) -> !(context instanceof org.springframework.http.server.observation.ServerRequestObservationContext c
                && c.getCarrier().getRequestURI().startsWith("/actuator"));
    }
}
