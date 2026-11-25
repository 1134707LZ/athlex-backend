package br.edu.atitus.productapi.services;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Autorização (o gateway já fez a autenticação e propagou X-User-*).
 * type: 0 = administrador, 1 = comprador, 2 = vendedor.
 */
@Service
public class AuthorizationService {
    public static final int ADMIN = 0;
    public static final int BUYER = 1;
    public static final int SELLER = 2;

    public void requireAuthenticated(Long userId) {
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identidade ausente");
    }

    public void requireAdmin(Integer type) {
        if (type == null || type != ADMIN)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário sem permissão para esta operação");
    }

    /** Manutenção de produtos: somente vendedor (ou administrador). Comprador recebe 403. */
    public void requireSeller(Integer type) {
        if (type == null || (type != SELLER && type != ADMIN))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Compradores não podem fazer manutenção de produtos");
    }

    /** Dono do recurso OU administrador. */
    public void requireOwnerOrAdmin(Long ownerId, Long userId, Integer type) {
        boolean admin = type != null && type == ADMIN;
        if (!admin && (ownerId == null || !ownerId.equals(userId)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não é o dono deste recurso");
    }
}
