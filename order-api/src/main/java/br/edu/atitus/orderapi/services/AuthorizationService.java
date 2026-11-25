package br.edu.atitus.orderapi.services;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** type: 0 = administrador, 1 = comprador, 2 = vendedor. */
@Service
public class AuthorizationService {
    public static final int ADMIN = 0;
    public static final int BUYER = 1;
    public static final int SELLER = 2;

    public boolean isAdmin(Integer type) { return type != null && type == ADMIN; }

    /** Painel/histórico de vendas: vendedor (ou administrador). */
    public void requireSeller(Integer type) {
        if (type == null || (type != SELLER && type != ADMIN))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas vendedores acessam o histórico de vendas");
    }

    public void requireOwnerOrAdmin(Long ownerId, Long userId, Integer type) {
        if (!isAdmin(type) && (ownerId == null || !ownerId.equals(userId)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não é o dono deste recurso");
    }
}
