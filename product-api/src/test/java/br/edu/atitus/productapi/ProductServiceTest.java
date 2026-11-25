package br.edu.atitus.productapi;

import br.edu.atitus.productapi.dtos.Dtos.ProductRequest;
import br.edu.atitus.productapi.entities.ProductEntity;
import br.edu.atitus.productapi.repositories.ProductRepository;
import br.edu.atitus.productapi.services.AuthorizationService;
import br.edu.atitus.productapi.services.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductServiceTest {
    ProductRepository repo = mock(ProductRepository.class);
    ProductService service = new ProductService(repo, new AuthorizationService());
    static final int SELLER = AuthorizationService.SELLER, BUYER = AuthorizationService.BUYER;

    ProductRequest valid() {
        return new ProductRequest("Chuteira X", "Chuteira society leve", 199.9, "Chuteiras", List.of("40", "41"), List.of("#000000"), null, false, 10);
    }

    @BeforeEach void setup() {
        when(repo.save(any())).thenAnswer(i -> { ProductEntity p = i.getArgument(0); if (p.getId() == null) p.setId(1L); return p; });
    }

    HttpStatus status(Runnable r) { return (HttpStatus) assertThrows(ResponseStatusException.class, r::run).getStatusCode(); }

    @Test void vendedorCriaProdutoSendoDono() {
        var r = service.create(valid(), 7L, SELLER);
        assertEquals("7", r.vendedorId());
        assertEquals(10, r.estoque());
        assertEquals(List.of("40", "41"), r.tamanhos());
    }

    @Test void compradorNaoCriaProduto() {
        assertEquals(HttpStatus.FORBIDDEN, status(() -> service.create(valid(), 7L, BUYER)));
        verify(repo, never()).save(any());
    }

    @Test void validaPrecoEEstoque() {
        var semPreco = new ProductRequest("Nome", "Descrição ok", 0.0, "Bolas", null, null, null, false, 1);
        var estoqueNeg = new ProductRequest("Nome", "Descrição ok", 10.0, "Bolas", null, null, null, false, -1);
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.create(semPreco, 7L, SELLER)));
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.create(estoqueNeg, 7L, SELLER)));
    }

    @Test void categoriaInvalida() {
        var r = new ProductRequest("Nome", "Descrição ok", 10.0, "Inexistente", null, null, null, false, 1);
        assertEquals(HttpStatus.BAD_REQUEST, status(() -> service.create(r, 7L, SELLER)));
    }

    @Test void outroVendedorNaoEditaNemRemove() {
        var p = new ProductEntity(); p.setId(3L); p.setOwnerId(1L);
        when(repo.findById(3L)).thenReturn(Optional.of(p));
        assertEquals(HttpStatus.FORBIDDEN, status(() -> service.update(3L, valid(), 7L, SELLER)));
        assertEquals(HttpStatus.FORBIDDEN, status(() -> service.delete(3L, 7L, SELLER)));
        verify(repo, never()).delete(any());
    }

    @Test void donoRemoveProduto() {
        var p = new ProductEntity(); p.setId(3L); p.setOwnerId(7L);
        when(repo.findById(3L)).thenReturn(Optional.of(p));
        service.delete(3L, 7L, SELLER);
        verify(repo).delete(p);
    }

    @Test void reservaSemSaldoRetorna409() {
        when(repo.reserve(5L, 3)).thenReturn(0);
        var p = new ProductEntity(); p.setId(5L);
        when(repo.findById(5L)).thenReturn(Optional.of(p));
        assertEquals(HttpStatus.CONFLICT, status(() -> service.reserve(5L, 3)));
    }

    @Test void reservaComSaldoNaoLancaErro() {
        when(repo.reserve(5L, 3)).thenReturn(1);
        assertDoesNotThrow(() -> service.reserve(5L, 3));
    }
}
