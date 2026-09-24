package security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import domain.model.entities.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

    @Service
    public class TokenService {

        // Vai buscar a senha secreta ao application.yml
        @Value("${api.security.token.secret}")
        private String secret;

        public String gerarToken(User user) {
            try {
                Algorithm algoritmo = Algorithm.HMAC256(secret);
                return JWT.create()
                        .withIssuer("supervisor-api")
                        .withSubject(user.getEmail())
                        .withExpiresAt(dataExpiracao())
                        .sign(algoritmo);
            } catch (JWTCreationException exception) {
                throw new RuntimeException("Erro ao gerar token JWT", exception);
            }
        }

        public String validarToken(String token) {
            try {
                Algorithm algoritmo = Algorithm.HMAC256(secret);
                return JWT.require(algoritmo)
                        .withIssuer("supervisor-api")
                        .build()
                        .verify(token)
                        .getSubject();
            } catch (JWTVerificationException exception) {
                return ""; // Retorna vazio se o token for falso ou expirado
            }
        }

        private Instant dataExpiracao() {
            // O token expira em 2 horas.
            return LocalDateTime.now().plusHours(2).toInstant(ZoneOffset.of("-03:00"));
        }
    }

