package br.edu.atitus.productapi.controllers;

import br.edu.atitus.productapi.services.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints INTERNOS (service-to-service, usados pelo order-api).
 * O gateway NÃO possui rota para /internal/**, portanto não são acessíveis pelo cliente externo.
 */
@RestController
@RequestMapping("/internal/products")
public class InternalProductController {
    private final ProductService service;
    public InternalProductController(ProductService service) { this.service = service; }

    @PostMapping("/{id}/reserve")
    public ResponseEntity<Void> reserve(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        service.reserve(id, body.getOrDefault("quantity", 0));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<Void> release(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        service.release(id, body.getOrDefault("quantity", 0));
        return ResponseEntity.noContent().build();
    }
}
