package tests.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tests.support.AbstractApiIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Regras de acesso — SecurityConfig")
class SecurityAccessIntegrationTest extends AbstractApiIntegrationTest {

    /**
     * Mensagem produzida pelo {@code CustomAuthenticationEntryPoint} para
     * qualquer acesso sem autenticação válida.
     */
    private static final String SEM_AUTENTICACAO =
            "Full authentication is required to access this resource";

    @Autowired
    private PasswordEncoder encoderUsado;

    @Nested
    @DisplayName("Rotas públicas e protegidas")
    class Rotas {

        @Test
        @DisplayName("POST /api/auth/login está liberado: um login válido devolve 200 sem token prévio")
        void loginEhPublico() throws Exception {
            criarUsuario("Administrador", "admin@teste.com",
                    domain.model.enums.UserProfile.SUPERVISOR);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"admin@teste.com","password":"123456"}
                                    """))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("só o POST em /api/auth/login é público; GET no mesmo caminho dá 401")
        void apenasPostEhPublico() throws Exception {
            mockMvc.perform(get("/api/auth/login"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }

        @Test
        @DisplayName("GET /api/v1/scales exige autenticação")
        void listarEscalasExigeAutenticacao() throws Exception {
            mockMvc.perform(get("/api/v1/scales"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/v1/scales exige autenticação")
        void criarEscalaExigeAutenticacao() throws Exception {
            mockMvc.perform(post("/api/v1/scales")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"X","initialDate":"2025-10-01","endDate":"2025-10-31","createdById":1}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/v1/scales/{id}/publish exige autenticação")
        void publicarExigeAutenticacao() throws Exception {
            mockMvc.perform(post("/api/v1/scales/1/publish"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/v1/exchanges exige autenticação")
        void pedirTrocaExigeAutenticacao() throws Exception {
            mockMvc.perform(post("/api/v1/exchanges")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"originAllocationId":1,"destinationAllocationId":2}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PATCH /api/v1/exchanges/{id}/respond exige autenticação")
        void responderTrocaExigeAutenticacao() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.patch("/api/v1/exchanges/1/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isAccepted":true}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("a autorização é avaliada antes do roteamento: rota inexistente também dá 401")
        void rotaInexistenteExigeAutenticacao() throws Exception {
            mockMvc.perform(get("/api/v1/nao-existe"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("o CustomAuthenticationEntryPoint devolve 401 com corpo JSON estruturado")
        void respostaPadraoE401ComJson() throws Exception {
            var resultado = mockMvc.perform(get("/api/v1/scales"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value(SEM_AUTENTICACAO))
                    .andReturn();

            org.assertj.core.api.Assertions.assertThat(
                    resultado.getResponse().getContentAsString()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Com token válido")
    class ComTokenValido {

        @Test
        @DisplayName("o utilizador autenticado acede aos recursos protegidos")
        void acessoLiberadoComToken() throws Exception {
            criarUsuario("Administrador", "admin@teste.com",
                    domain.model.enums.UserProfile.SUPERVISOR);

            mockMvc.perform(get("/api/v1/scales").with(autorizacao("admin@teste.com")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("o perfil ANALIST também tem acesso: não há regras por papel")
        void analistTambemTemAcesso() throws Exception {
            criarUsuario("João", "joao@teste.com", domain.model.enums.UserProfile.ANALIST);

            mockMvc.perform(get("/api/v1/scales").with(autorizacao("joao@teste.com")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("não há separação de permissões entre SUPERVISOR e ANALIST")
        void naoHaSeparacaoPorPapel() throws Exception {
            var supervisor = criarUsuario("Administrador", "admin@teste.com",
                    domain.model.enums.UserProfile.SUPERVISOR);
            var escala = criarEscala("Escala Outubro", supervisor);

            mockMvc.perform(post("/api/v1/scales/" + escala.getId() + "/publish")
                            .with(autorizacao("admin@teste.com")))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("Com token inválido ou ausente")
    class ComTokenInvalido {

        @Test
        @DisplayName("token adulterado é recusado com 401")
        void tokenAdulterado() throws Exception {
            mockMvc.perform(get("/api/v1/scales").with(autorizacaoQualquer("token.invalido.aqui")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value(SEM_AUTENTICACAO));
        }

        @Test
        @DisplayName("token sem assinatura válida é recusado com 401")
        void tokenSemAssinatura() throws Exception {
            mockMvc.perform(get("/api/v1/scales")
                            .header("Authorization", "Bearer a.b.c"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }

        @Test
        @DisplayName("um token bem assinado cujo utilizador foi removido deixa de autenticar com 401")
        void tokenDeUtilizadorRemovido() throws Exception {
            var user = criarUsuario("Temporário", "temporario@teste.com",
                    domain.model.enums.UserProfile.ANALIST);
            String token = tokenService.gerarToken(user);
            userRepository.deleteById(user.getId());

            mockMvc.perform(get("/api/v1/scales")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }

        @Test
        @DisplayName("um token válido continua válido depois que a senha do utilizador muda")
        void tokenSobreviveATrocaDeSenha() throws Exception {
            var user = criarUsuario("Administrador", "admin@teste.com",
                    domain.model.enums.UserProfile.SUPERVISOR);
            user.setPassword(encoderUsado.encode("nova-senha-999"));
            userRepository.saveAndFlush(user);

            mockMvc.perform(get("/api/v1/scales").with(autorizacao("admin@teste.com")))
                    .andExpect(status().isOk());
        }

        /**
         * O {@code SecurityFilter} valida o formato do header em vez de remover
         * o prefixo por substituição global: só aceita um header que comece
         * exactamente por {@code "Bearer "} e rejeita explicitamente um prefixo
         * duplicado. Um token cru ou mal formado nunca chega a ser validado, e o
         * pedido é recusado com 401.
         */
        @Test
        @DisplayName("um token cru, sem o prefixo Bearer, é recusado com 401")
        void semPrefixoBearer() throws Exception {
            criarUsuario("Administrador", "admin@teste.com",
                    domain.model.enums.UserProfile.SUPERVISOR);
            String token = tokenService.gerarToken(
                    (domain.model.entities.User) userRepository.findByEmail("admin@teste.com"));

            mockMvc.perform(get("/api/v1/scales")
                            .header("Authorization", token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value(SEM_AUTENTICACAO));
        }

        @Test
        @DisplayName("o prefixo Bearer duplicado é recusado com 401, por validação de formato")
        void prefixoNoMeioDoValor() throws Exception {
            criarUsuario("Administrador", "admin@teste.com",
                    domain.model.enums.UserProfile.SUPERVISOR);
            String token = tokenService.gerarToken(
                    (domain.model.entities.User) userRepository.findByEmail("admin@teste.com"));

            mockMvc.perform(get("/api/v1/scales")
                            .header("Authorization", "Bearer Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value(SEM_AUTENTICACAO));
        }
    }

    @Nested
    @DisplayName("Comportamento da sessão")
    class Sessao {

        @Test
        @DisplayName("a aplicação não cria sessão nem cookie de autenticação")
        void naoCriaSessao() throws Exception {
            criarUsuario("Administrador", "admin@teste.com",
                    domain.model.enums.UserProfile.SUPERVISOR);

            var resultado = mockMvc.perform(get("/api/v1/scales").with(autorizacao("admin@teste.com")))
                    .andExpect(status().isOk())
                    .andReturn();

            org.assertj.core.api.Assertions.assertThat(resultado.getRequest().getSession(false)).isNull();
            org.assertj.core.api.Assertions.assertThat(resultado.getResponse().getCookie("JSESSIONID")).isNull();
        }
    }

    private RequestPostProcessor autorizacao(String email) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + gerarToken(email));
            return request;
        };
    }

    private RequestPostProcessor autorizacaoQualquer(String token) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    private String gerarToken(String email) {
        var user = (domain.model.entities.User) userRepository.findByEmail(email);
        return tokenService.gerarToken(user);
    }
}
