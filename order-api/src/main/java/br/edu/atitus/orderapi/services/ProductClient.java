package br.edu.atitus.orderapi.services;

import br.edu.atitus.orderapi.dtos.Dtos.ProductInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Cliente do product-api com resiliência:
 * <ul>
 *   <li><b>Timeout</b>: conexão e leitura limitados (evita threads presas);</li>
 *   <li><b>Retry</b>: só em leituras (GET, idempotente) e só para falha de rede/5xx;
 *       POSTs de estoque NÃO são repetidos (poderiam baixar o estoque duas vezes);</li>
 *   <li><b>Circuit Breaker</b> (Resilience4j): abre após falhas seguidas e responde 503 rápido;
 *       erros 4xx de negócio não contam como falha e são repassados.</li>
 * </ul>
 */
@Component
public class ProductClient {
    static final String CB_NAME = "product-api";

    private final RestClient http;
    private final CircuitBreaker breaker;
    private final RetryTemplate retry;

    @Autowired
    public ProductClient(RestClient.Builder lbBuilder,
                         CircuitBreakerRegistry breakers,
                         @Value("${product-client.connect-timeout-ms:1000}") long connectMs,
                         @Value("${product-client.read-timeout-ms:3000}") long readMs,
                         @Value("${product-client.retry.max:2}") int maxRetries,
                         @Value("${product-client.retry.delay-ms:200}") long retryDelayMs) {
        this("http://product-api", lbBuilder, breakers, connectMs, readMs, maxRetries, retryDelayMs);
    }

    /** Visível para testes (permite apontar para um servidor falso). */
    public ProductClient(String baseUrl, RestClient.Builder builder, CircuitBreakerRegistry breakers,
                  long connectMs, long readMs, int maxRetries, long retryDelayMs) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectMs)).build());
        factory.setReadTimeout(Duration.ofMillis(readMs));
        this.http = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.breaker = breakers.circuitBreaker(CB_NAME);
        this.retry = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(maxRetries)
                .delay(Duration.ofMillis(retryDelayMs))
                .includes(ResourceAccessException.class, HttpServerErrorException.class)
                .build());
    }

    public ProductInfo find(Long id) {
        return protect(() -> withRetry(() ->
                http.get().uri("/products/{id}", id).retrieve().body(ProductInfo.class)));
    }

    public void reserve(Long id, int qty) {
        protect(() -> {
            http.post().uri("/internal/products/{id}/reserve", id).body(Map.of("quantity", qty)).retrieve().toBodilessEntity();
            return null;
        });
    }

    public void release(Long id, int qty) {
        try {
            protect(() -> {
                http.post().uri("/internal/products/{id}/release", id).body(Map.of("quantity", qty)).retrieve().toBodilessEntity();
                return null;
            });
        } catch (RuntimeException ignored) { /* compensação em melhor esforço */ }
    }

    private <T> T protect(Supplier<T> call) {
        try {
            return breaker.executeSupplier(call);   // mesma thread: o trace é mantido
        } catch (HttpClientErrorException | ResponseStatusException e) {
            throw e;                                 // erro de negócio (4xx): repassa
        } catch (CallNotPermittedException e) {
            throw unavailable();                     // circuito aberto: responde rápido
        } catch (RuntimeException e) {
            throw unavailable();                     // timeout / rede / 5xx: sem vazar detalhes
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Serviço de produtos indisponível no momento. Tente novamente em instantes.");
    }

    private <T> T withRetry(Supplier<T> call) {
        try {
            return retry.execute(call::get);
        } catch (RetryException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException r) throw r;
            throw new IllegalStateException(c);
        }
    }
}
