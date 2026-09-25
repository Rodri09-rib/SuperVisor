package security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import domain.model.entities.User;
import domain.model.enums.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("TokenService (JWT)")
class TokenServiceTest {

    private static final String SEGREDO = "chave-de-teste-super-visor-0123456789-abcdef";
    private static final String OUTRO_SEGREDO = "outra-chave-totalmente-diferente-9876543210";

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService();
        ReflectionTestUtils.setField(tokenService, "secret", SEGREDO);
    }

    private User usuario(String email) {
        return new User(1L, "Admin", email, "123456", UserProfile.SUPERVISOR);
    }

    @Nested
    @DisplayName("gerarToken")
    class GerarToken {

        @Test
        @DisplayName("produz um token assinado com o segredo configurado")
        void assinaComOSegredo() {
            String token = tokenService.gerarToken(usuario("admin@teste.com"));

            assertThat(token).isNotBlank().doesNotContain(SEGREDO);
            assertThatCode(() -> JWT.require(Algorithm.HMAC256(SEGREDO))
                    .withIssuer("supervisor-api")
                    .build()
                    .verify(token)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("o emissor é supervisor-api e o assunto é o e-mail do utilizador")
        void emissorEAssunto() {
            DecodedJWT decoded = JWT.decode(tokenService.gerarToken(usuario("joao@teste.com")));

            assertThat(decoded.getIssuer()).isEqualTo("supervisor-api");
            assertThat(decoded.getSubject()).isEqualTo("joao@teste.com");
        }

        @Test
        @DisplayName("o token expira em cerca de 2 horas, conforme dataExpiracao()")
        void expiraEmDuasHoras() {
            Instant antes = Instant.now();
            DecodedJWT decoded = JWT.decode(tokenService.gerarToken(usuario("admin@teste.com")));
            Instant depois = Instant.now();

            assertThat(decoded.getExpiresAt().toInstant())
                    .isBetween(antes.plus(1, ChronoUnit.HOURS).plus(50, ChronoUnit.MINUTES),
                            depois.plus(2, ChronoUnit.HOURS).plus(5, ChronoUnit.MINUTES));
        }

        @Test
        @DisplayName("token não assinado com o segredo configurado é rejeitado")
        void tokenComOutroSegredoEhInvalido() {
            String token = JWT.create()
                    .withIssuer("supervisor-api")
                    .withSubject("admin@teste.com")
                    .withExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                    .sign(Algorithm.HMAC256(OUTRO_SEGREDO));

            assertThat(tokenService.validarToken(token)).isEmpty();
        }

        @Test
        @DisplayName("utilizador sem e-mail gera um token sem assunto, que não identifica ninguém")
        void utilizadorSemEmail() {
            String token = tokenService.gerarToken(usuario(null));

            assertThat(JWT.decode(token).getSubject()).isNull();
            assertThat(tokenService.validarToken(token)).isNull();
        }
    }

    @Nested
    @DisplayName("validarToken")
    class ValidarToken {

        @Test
        @DisplayName("devolve o e-mail (assunto) de um token válido")
        void devolveSubject() {
            String token = tokenService.gerarToken(usuario("joao@teste.com"));

            assertThat(tokenService.validarToken(token)).isEqualTo("joao@teste.com");
        }

        @Test
        @DisplayName("token expirado devolve string vazia em vez de lançar exceção")
        void tokenExpirado() {
            String expirado = JWT.create()
                    .withIssuer("supervisor-api")
                    .withSubject("admin@teste.com")
                    .withExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                    .sign(Algorithm.HMAC256(SEGREDO));

            assertThat(tokenService.validarToken(expirado)).isEmpty();
        }

        @Test
        @DisplayName("token sem o emissor esperado é recusado")
        void tokenDeOutroEmissor() {
            String token = JWT.create()
                    .withIssuer("outra-api")
                    .withSubject("admin@teste.com")
                    .withExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                    .sign(Algorithm.HMAC256(SEGREDO));

            assertThat(tokenService.validarToken(token)).isEmpty();
        }

        @Test
        @DisplayName("lixo em vez de token devolve string vazia")
        void tokenMalformado() {
            assertThat(tokenService.validarToken("isto-nao-e-um-jwt")).isEmpty();
        }

        @Test
        @DisplayName("token vazio devolve string vazia")
        void tokenVazio() {
            assertThat(tokenService.validarToken("")).isEmpty();
        }

        @Test
        @DisplayName("token com assinatura adulterada devolve string vazia")
        void assinaturaAdulterada() {
            String token = tokenService.gerarToken(usuario("admin@teste.com"));
            String adulterado = token.substring(0, token.length() - 3) + "AAA";

            assertThat(tokenService.validarToken(adulterado)).isEmpty();
        }

        @Test
        @DisplayName("token gerado com outro segredo não é aceito")
        void segredoDiferente() {
            TokenService outro = new TokenService();
            ReflectionTestUtils.setField(outro, "secret", OUTRO_SEGREDO);

            String token = outro.gerarToken(usuario("admin@teste.com"));

            assertThat(tokenService.validarToken(token)).isEmpty();
        }
    }

    @Test
    @DisplayName("ida e volta gerarToken -> validarToken preserva o e-mail")
    void idaEVolta() {
        for (String email : new String[]{"admin@teste.com", "joao@teste.com", "novo.usuario@teste.com"}) {
            assertThat(tokenService.validarToken(tokenService.gerarToken(usuario(email)))).isEqualTo(email);
        }
    }
}
