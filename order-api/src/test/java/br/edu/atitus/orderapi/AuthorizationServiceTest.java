package br.edu.atitus.orderapi;

import br.edu.atitus.orderapi.services.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationServiceTest {
    AuthorizationService auth = new AuthorizationService();

    @Test void vendedorVeVendas() { assertDoesNotThrow(() -> auth.requireSeller(AuthorizationService.SELLER)); }
    @Test void adminVeVendas() { assertDoesNotThrow(() -> auth.requireSeller(AuthorizationService.ADMIN)); }
    @Test void compradorNaoVeVendas() {
        var e = assertThrows(ResponseStatusException.class, () -> auth.requireSeller(AuthorizationService.BUYER));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }
    @Test void terceiroNaoVePedidoAlheio() {
        assertThrows(ResponseStatusException.class, () -> auth.requireOwnerOrAdmin(1L, 2L, AuthorizationService.BUYER));
        assertDoesNotThrow(() -> auth.requireOwnerOrAdmin(1L, 1L, AuthorizationService.BUYER));
        assertDoesNotThrow(() -> auth.requireOwnerOrAdmin(1L, 2L, AuthorizationService.ADMIN));
    }
}
