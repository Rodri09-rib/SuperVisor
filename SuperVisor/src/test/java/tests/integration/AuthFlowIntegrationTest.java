package tests.integration;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tests.support.AbstractApiIntegrationTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Fluxo de autenticação — POST /api/auth/login")
class AuthFlowIntegrationTest extends AbstractApiIntegrationTest {

    private static final String SEGREDO = "chave-de-teste-super-visor-0123456789-abcdef";

    private String login(String email, String senha) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, senha);
    }

    @Nested
    @DisplayName("Login bem sucedido")
    class Sucesso {

        @Test
        @DisplayName("devolve 200 e um JWT utilizável nos recursos protegidos")
        void tokenPermiteAcesso() throws Exception {
            criarUsuario("Administrador", "admin@teste.com", domain.model.enums.UserProfile.SUPERVISOR);

            String token = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(login("admin@teste.com", "123456")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(token).isNotBlank();

            mockMvc.perform(get("/api/v1/scales").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("o token devolve o e-mail como assunto e o emissor da API")
        void conteudoDoToken() throws Exception {
            criarUsuario("João", "joao@teste.com", domain.model.enums.UserProfile.ANALIST);

            String token = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(login("joao@teste.com", "123456")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            var decoded = JWT.decode(token);
            assertThat(decoded.getSubject()).isEqualTo("joao@teste.com");
            assertThat(decoded.getIssuer()).isEqualTo("supervisor-api");
        }

        @Test
        @DisplayName("a resposta é o token em texto puro, sem envelope JSON")
        void respostaEhTextoPuro() throws Exception {
            criarUsuario("Administrador", "admin@teste.com", domain.model.enums.UserProfile.SUPERVISOR);

            MvcResult resultado = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(login("admin@teste.com", "123456")))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(resultado.getResponse().getContentAsString().trim())
                    .isEqualTo(resultado.getResponse().getContentAsString().trim())
                    .contains(".");
            assertThat(resultado.getResponse().getContentAsString()).doesNotStartWith("{");
        }

        @Test
        @DisplayName("a senha é comparada pelo hash BCrypt, não em texto puro")
        void senhaEhHashada() throws Exception {
            criarUsuario("Administrador", "admin@teste.com", domain.model.enums.UserProfile.SUPERVISOR);

            var user = (domain.model.entities.User) userRepository.findByEmail("admin@teste.com");
            assertThat(user.getPassword())
                    .startsWith("$2")
                    .isNotEqualTo("123456")
                    .matches("\\$2[aby]\\$\\d{2}\\$.+");

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(login("admin@teste.com", "123456")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("o perfil ANALIST autentica com o mesmo fluxo do SUPERVISOR")
        void analistAutentica() throws Exception {
            criarUsuario("João", "joao@teste.com", domain.model.enums.UserProfile.ANALIST);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(login("joao@teste.com", "123456")))
                    .andExpect(status().isOk());
        }

        /**
         * O token carrega um identificador único ({@code jti}) e o instante de
         * emissão ({@code iat}), pelo que dois logins do mesmo utilizador no
         * mesmo segundo produzem tokens distintos. Sem estes claims, o valor do
         * token seria determinístico e não seria possível revogá-lo
         * individualmente nem distinguish duas sessões.
         */
        @Test
        @DisplayName("o token tem claim jti único, para distinguir tokens emitidos no mesmo segundo")
        void naoHaClaimJti() throws Exception {
            criarUsuario("Administrador", "admin@teste.com", domain.model.enums.UserProfile.SUPERVISOR);

            String primeiro = obterToken("admin@teste.com", "123456");
            String segundo = obterToken("admin@teste.com", "123456");

            assertThat(JWT.decode(primeiro).getId()).isNotNull();
            assertThat(JWT.decode(segundo).getId()).isNotNull();
            assertThat(JWT.decode(primeiro).getId())
                    .isNotEqualTo(JWT.decode(segundo).getId());
            assertThat(JWT.decode(primeiro).getIssuedAtAsInstant()).isNotNull();
            assertThat(JWT.decode(primeiro).getSubject())
                    .isEqualTo(JWT.decode(segundo).getSubject());
        }

        @Test
        @DisplayName("a validade é de duas horas a partir da emissão")
        void validadeDeDuasHoras() throws Exception {
            criarUsuario("Administrador", "admin@teste.com", domain.model.enums.UserProfile.SUPERVISOR);

            Instant antes = Instant.now();
            String token = obterToken("admin@teste.com", "123456");
            var decoded = JWT.decode(token);

            assertThat(decoded.getExpiresAtAsInstant())
                    .isBetween(antes.plus(2, ChronoUnit.HOURS).minusSeconds(30),
                            Instant.now().plus(2, ChronoUnit.HOURS).plusSeconds(30));
        }
    }

    @Nested
    @DisplayName("Login recusado")
    class Recusado {

        @Test
        @DisplayName("senha errada devolve 401 com corpo JSON estruturado")
        void senhaErrada() throws Exception {
            criarUsuario("Administrador", "admin@teste.com", domain.model.enums.UserProfile.SUPERVISOR);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(login("admin@teste.com", "senha-errada")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value("Bad credentials"));
        }

        @Test
        @DisplayName("e-mail inexistente devolve 401 com a mesma mensagem da senha errada")
        void emailInexistente() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(login("nao.existe@teste.com", "123456")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value("Bad credentials"));
        }

        @Test
        @DisplayName("e-mail existente com senha vazia é recusado com 401")
        void senhaVazia() throws Exception {
            criarUsuario("Administrador", "admin@teste.com", domain.model.enums.UserProfile.SUPERVISOR);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(login("admin@teste.com", "")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value("Bad credentials"));
        }

        /**
         * A fuga de informação que existia foi corrigida:
         * {@code AuthorizationService.loadUserByUsername} deixou de devolver
         * {@code null} e passou a lançar {@code UsernameNotFoundException}, que o
         * provedor de autenticação converte em {@code BadCredentialsException} e
         * o handler normaliza para a mesma mensagem. As respostas para "e-mail
         * existente com senha errada" e "e-mail inexistente" são agora idênticas,
         * pelo que já não é possível enumerar contas válidas. O e-mail introduzido
         * nunca é devolvido ao cliente.
         */
        @Test
        @DisplayName("as respostas para e-mail existente e inexistente são iguais (sem fuga de informação)")
        void fugaDeInformacaoNaMensagemDeErro() throws Exception {
            criarUsuario("Administrador", "admin@teste.com", domain.model.enums.UserProfile.SUPERVISOR);

            String comUtilizador = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(login("admin@teste.com", "errada")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andReturn().getResponse().getContentAsString();

            String semUtilizador = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(login("nao.existe@teste.com", "errada")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(comUtilizador).isEqualTo(semUtilizador)
                    .contains("Bad credentials")
                    .doesNotContain("admin@teste.com");
            assertThat(semUtilizador)
                    .isEqualTo(comUtilizador)
                    .contains("Bad credentials")
                    .doesNotContain("nao.existe@teste.com")
                    .doesNotContain("UserDetailsService returned null");
        }

        @Test
        @DisplayName("corpo JSON inválido devolve 400")
        void corpoInvalido() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{nao-e-json"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Validade do token")
    class Validade {

        @Test
        @DisplayName("um token expirado é recusado nos recursos protegidos com 401")
        void tokenExpirado() throws Exception {
            criarUsuario("Administrador", "admin@teste.com", domain.model.enums.UserProfile.SUPERVISOR);
            String expirado = JWT.create()
                    .withIssuer("supervisor-api")
                    .withSubject("admin@teste.com")
                    .withExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                    .sign(Algorithm.HMAC256(SEGREDO));

            mockMvc.perform(get("/api/v1/scales").header("Authorization", "Bearer " + expirado))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message")
                            .value("Full authentication is required to access this resource"));
        }

        @Test
        @DisplayName("um token assinado com outro segredo é recusado com 401")
        void tokenAssinadoComOutroSegredo() throws Exception {
            criarUsuario("Administrador", "admin@teste.com", domain.model.enums.UserProfile.SUPERVISOR);
            String alheio = JWT.create()
                    .withIssuer("supervisor-api")
                    .withSubject("admin@teste.com")
                    .withExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                    .sign(Algorithm.HMAC256("outro-segredo-completamente-diferente-000"));

            mockMvc.perform(get("/api/v1/scales").header("Authorization", "Bearer " + alheio))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }

        @Test
        @DisplayName("um token de outro emissor é recusado com 401")
        void tokenDeOutroEmissor() throws Exception {
            criarUsuario("Administrador", "admin@teste.com", domain.model.enums.UserProfile.SUPERVISOR);
            String alheio = JWT.create()
                    .withIssuer("outra-api")
                    .withSubject("admin@teste.com")
                    .withExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                    .sign(Algorithm.HMAC256(SEGREDO));

            mockMvc.perform(get("/api/v1/scales").header("Authorization", "Bearer " + alheio))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }
    }

    private String obterToken(String email, String senha) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login(email, senha)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
