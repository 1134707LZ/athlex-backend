package br.edu.atitus.orderapi.configs;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestConfig {
    /**
     * `lb://`-style: http://product-api é resolvido pelo Consul + Spring Cloud LoadBalancer.
     * Aplica os customizers do Spring Boot (observabilidade) para propagar o trace entre serviços.
     */
    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder(ObjectProvider<RestClientCustomizer> customizers) {
        RestClient.Builder builder = RestClient.builder();
        customizers.orderedStream().forEach(c -> c.customize(builder));
        return builder;
    }
}
