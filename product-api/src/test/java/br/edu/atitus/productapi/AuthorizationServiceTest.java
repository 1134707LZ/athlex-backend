package br.edu.atitus.productapi;

import br.edu.atitus.productapi.services.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationServiceTest {
    AuthorizationService auth = new AuthorizationService();

    private HttpStatus statusOf(Runnable r) {
        return (HttpStatus) assertThrows(ResponseStatusException.class, r::run).getStatusCode();
    }

    @Test void vendedorPodeFazerManutencao() { assertDoesNotThrow(() -> auth.requireSeller(AuthorizationService.SELLER)); }
    @Test void adminPodeFazerManutencao() { assertDoesNotThrow(() -> auth.requireSeller(AuthorizationService.ADMIN)); }
    @Test void compradorRecebe403() { assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> auth.requireSeller(AuthorizationService.BUYER))); }
    @Test void tipoAusenteRecebe403() { assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> auth.requireSeller(null))); }

    @Test void donoPodeEditar() { assertDoesNotThrow(() -> auth.requireOwnerOrAdmin(5L, 5L, AuthorizationService.SELLER)); }
    @Test void adminPodeEditarDeQualquerUm() { assertDoesNotThrow(() -> auth.requireOwnerOrAdmin(5L, 9L, AuthorizationService.ADMIN)); }
    @Test void outroVendedorNaoPodeEditar() { assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> auth.requireOwnerOrAdmin(5L, 9L, AuthorizationService.SELLER))); }
    @Test void semIdentidadeRecebe401() { assertEquals(HttpStatus.UNAUTHORIZED, statusOf(() -> auth.requireAuthenticated(null))); }
}
