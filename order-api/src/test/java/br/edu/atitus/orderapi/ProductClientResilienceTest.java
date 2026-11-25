package br.edu.atitus.orderapi;

import br.edu.atitus.orderapi.configs.ResilienceConfig;
import br.edu.atitus.orderapi.services.ProductClient;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Prova timeout, retry (só GET) e circuit breaker contra um product-api falso. */
class ProductClientResilienceTest {
    private HttpServer server;
    private final AtomicInteger getCalls = new AtomicInteger();
    private final AtomicInteger postCalls = new AtomicInteger();
    private volatile int getStatus = 200;
    private volatile long getDelayMs = 0;
    private volatile int failFirstGets = 0;

    private static final String JSON = "{\"id\":\"1\",\"nome\":\"Camisa\",\"preco\":100.0,\"personalizavel\":true,\"estoque\":5,\"vendedorId\":\"9\"}";

    @BeforeEach
    void up() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/products/1", ex -> {
            int n = getCalls.incrementAndGet();
            try { if (getDelayMs > 0) Thread.sleep(getDelayMs); } catch (InterruptedException ignored) {}
            int status = n <= failFirstGets ? 500 : getStatus;
            byte[] body = (status == 200 ? JSON : "{}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/internal/products/1/reserve", ex -> {
            postCalls.incrementAndGet();
            ex.sendResponseHeaders(500, -1);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void down() { server.stop(0); }

    private ProductClient client(long readMs, int retries) {
        var f = CircuitBreakerRegistry.of(ResilienceConfig.circuitConfig());
        return new ProductClient("http://127.0.0.1:" + server.getAddress().getPort(),
                RestClient.builder(), f, 500L, readMs, retries, 10L);
    }

    @Test
    void retryRecuperaFalhaTransitoriaEmLeitura() {
        failFirstGets = 2;                       // 2 falhas 5xx e depois 200
        var p = client(1000, 2).find(1L);
        assertEquals("Camisa", p.nome());
        assertEquals(3, getCalls.get());
    }

    @Test
    void timeoutViraServicoIndisponivel() {
        getDelayMs = 600;                        // maior que o read-timeout (200 ms)
        var e = assertThrows(ResponseStatusException.class, () -> client(200, 0).find(1L));
        assertEquals(503, e.getStatusCode().value());
    }

    @Test
    void erro4xxEPassadoAdianteENaoViraIndisponibilidade() {
        getStatus = 404;
        assertThrows(HttpClientErrorException.NotFound.class, () -> client(1000, 2).find(1L));
        assertEquals(1, getCalls.get());         // 4xx não faz retry
    }

    @Test
    void postDeEstoqueNaoFazRetry() {
        var c = client(1000, 2);
        assertThrows(ResponseStatusException.class, () -> c.reserve(1L, 1));
        assertEquals(1, postCalls.get());
    }

    @Test
    void circuitoAbreEResponde503SemChamarOServico() {
        getStatus = 500;
        var c = client(1000, 0);
        for (int i = 0; i < 5; i++) assertThrows(ResponseStatusException.class, () -> c.find(1L));
        int antes = getCalls.get();
        var e = assertThrows(ResponseStatusException.class, () -> c.find(1L));   // circuito aberto
        assertEquals(503, e.getStatusCode().value());
        assertEquals(antes, getCalls.get());     // nem chegou no servidor
    }
}
