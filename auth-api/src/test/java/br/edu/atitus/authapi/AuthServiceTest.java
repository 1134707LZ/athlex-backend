package br.edu.atitus.authapi;

import br.edu.atitus.authapi.components.JwtService;
import br.edu.atitus.authapi.dtos.Dtos.SigninRequest;
import br.edu.atitus.authapi.dtos.Dtos.SignupRequest;
import br.edu.atitus.authapi.entities.UserEntity;
import br.edu.atitus.authapi.repositories.UserRepository;
import br.edu.atitus.authapi.services.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    UserRepository repo = mock(UserRepository.class);
    List<UserEntity> saved = new ArrayList<>();
    AuthService service;

    @BeforeEach
    void setup() {
        when(repo.save(any())).thenAnswer(i -> { UserEntity u = i.getArgument(0); u.setId(10L); saved.add(u); return u; });
        service = new AuthService(repo, new BCryptPasswordEncoder(), new JwtService("chaveSuperSecretaParaJWTdeExemplo!@#123", 5));
    }

    private SignupRequest req(String profile) {
        return new SignupRequest("Ana Souza", "ana@x.com", "(51) 99999-0000", "segredo1", profile);
    }

    @Test void cadastroSemPerfilCriaComprador() {
        assertEquals(UserEntity.BUYER, service.signup(req(null)).type());
    }

    @Test void cadastroComoVendedor() {
        assertEquals(UserEntity.SELLER, service.signup(req("VENDEDOR")).type());
    }

    @Test void cadastroPublicoNuncaCriaAdministrador() {
        var e = assertThrows(ResponseStatusException.class, () -> service.signup(req("ADMIN")));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertTrue(saved.isEmpty());
    }

    @Test void senhaEhArmazenadaComHash() {
        service.signup(req("COMPRADOR"));
        assertNotEquals("segredo1", saved.get(0).getPassword());
        assertTrue(new BCryptPasswordEncoder().matches("segredo1", saved.get(0).getPassword()));
    }

    @Test void emailDuplicadoRetorna409() {
        when(repo.existsByEmail("ana@x.com")).thenReturn(true);
        var e = assertThrows(ResponseStatusException.class, () -> service.signup(req(null)));
        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
    }

    @Test void loginComSenhaErradaRetorna401() {
        service.signup(req(null));
        when(repo.findByEmail(anyString())).thenReturn(Optional.of(saved.get(0)));
        var e = assertThrows(ResponseStatusException.class, () -> service.signin(new SigninRequest("ana@x.com", "errada")));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }

    @Test void loginValidoGeraJwtComPerfil() {
        service.signup(req("VENDEDOR"));
        when(repo.findByEmail("ana@x.com")).thenReturn(Optional.of(saved.get(0)));
        var r = service.signin(new SigninRequest("ANA@x.com", "segredo1"));
        assertNotNull(r.token());
        assertEquals(UserEntity.SELLER, r.user().type());
    }
}
