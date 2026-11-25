package br.edu.atitus.authapi.configs;

import br.edu.atitus.authapi.entities.UserEntity;
import br.edu.atitus.authapi.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Cria o administrador na primeira execução. A senha vem da configuração central (admin.password); sem ela, nada é criado. */
@Configuration
public class AdminSeed {
    @Bean
    CommandLineRunner seedAdmin(UserRepository repo, PasswordEncoder encoder,
                                @Value("${admin.password:}") String adminPassword,
                                @Value("${admin.email:admin@admin.dev}") String adminEmail) {
        return args -> {
            if (!adminPassword.isBlank() && !repo.existsByEmail(adminEmail)) {
                var u = new UserEntity();
                u.setName("Administrador");
                u.setEmail(adminEmail);
                u.setPassword(encoder.encode(adminPassword));
                u.setType(UserEntity.ADMIN);
                repo.save(u);
            }
        };
    }
}
