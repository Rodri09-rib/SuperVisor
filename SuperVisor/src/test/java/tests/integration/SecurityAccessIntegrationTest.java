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
        @DisplayName("nas rotas de escalas o perfil ANALIST também tem acesso")
        void analistTambemTemAcesso() throws Exception {
            criarUsuario("João", "joao@teste.com", domain.model.enums.UserProfile.ANALIST);

            mockMvc.perform(get("/api/v1/scales").with(autorizacao("joao@teste.com")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("nas rotas de escalas não há separação entre SUPERVISOR e ANALIST")
        void naoHaSeparacaoPorPapelNasEscalas() throws Exception {
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

    @Nested
    @DisplayName("Escrita de alocações restrita ao SUPERVISOR")
    class EscritaDeAlocacoes {

        private static final String SEM_PERFIL =
                "Apenas o perfil SUPERVISOR pode executar esta opera\u00e7\u00e3o.";

        private domain.model.entities.User supervisor;
        private domain.model.entities.User analist;
        private domain.model.entities.User inativo;
        private domain.model.entities.EditionScale escala;
        private domain.model.entities.ShiftScheduling alocacao;

        @org.junit.jupiter.api.BeforeEach
        void prepararCenario() {
            supervisor = criarUsuario("Administrador", "admin@teste.com",
                    domain.model.enums.UserProfile.SUPERVISOR);
            analist = criarUsuario("João", "joao@teste.com", domain.model.enums.UserProfile.ANALIST);
            inativo = criarUsuario("Inativo", "inativo@teste.com",
                    domain.model.enums.UserProfile.ANALIST);
            inativo.setActive(false);
            userRepository.saveAndFlush(inativo);
            escala = criarEscala("Escala Outubro", supervisor);
            alocacao = criarAlocacao(escala, analist,
                    domain.model.enums.ShiftType.T1_SAB);
        }

        private String pedido(long escalaId, long userId) {
            return """
                    {"editionScaleId":%d,"userId":%d,"shift":"T1_SAB",\
                    "assignments":["REDES_SOCIAIS"]}
                    """.formatted(escalaId, userId);
        }

        @Test
        @DisplayName("sem token, a escrita dá 401 e não 403: a autenticação vem antes do perfil")
        void escritaSemTokenDa401() throws Exception {
            mockMvc.perform(post("/api/v1/allocations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido(escala.getId(), analist.getId())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/allocations continua aberto ao ANALIST")
        void analistPodeLer() throws Exception {
            mockMvc.perform(get("/api/v1/allocations").with(autorizacao("joao@teste.com")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].userId").value(analist.getId()));
        }

        @Test
        @DisplayName("POST com ANALIST dá 403 com corpo JSON estruturado")
        void postDeAnalistDa403() throws Exception {
            mockMvc.perform(post("/api/v1/allocations")
                            .with(autorizacao("joao@teste.com"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido(escala.getId(), analist.getId())))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"))
                    .andExpect(jsonPath("$.message").value(SEM_PERFIL));
        }

        @Test
        @DisplayName("PUT com ANALIST dá 403 e não altera a alocação")
        void putDeAnalistDa403() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.put("/api/v1/allocations/" + alocacao.getId())
                            .with(autorizacao("joao@teste.com"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido(escala.getId(), supervisor.getId())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(SEM_PERFIL));

            var recarregada = recarregarAlocacao(alocacao.getId());
            org.assertj.core.api.Assertions.assertThat(recarregada.getUser().getId())
                    .isEqualTo(analist.getId());
        }

        @Test
        @DisplayName("DELETE com ANALIST dá 403 e a alocação continua na base de dados")
        void deleteDeAnalistDa403() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/allocations/" + alocacao.getId())
                            .with(autorizacao("joao@teste.com")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(SEM_PERFIL));

            org.assertj.core.api.Assertions
                    .assertThat(recarregarAlocacao(alocacao.getId())).isNotNull();
        }

        @Test
        @DisplayName("POST com SUPERVISOR é aceite")
        void postDeSupervisorEAceite() throws Exception {
            mockMvc.perform(post("/api/v1/allocations")
                            .with(autorizacao("admin@teste.com"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido(escala.getId(), analist.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(analist.getId()));
        }

        @Test
        @DisplayName("DELETE com SUPERVISOR remove mesmo a alocação")
        void deleteDeSupervisorRemove() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/allocations/" + alocacao.getId())
                            .with(autorizacao("admin@teste.com")))
                    .andExpect(status().isNoContent());

            org.assertj.core.api.Assertions
                    .assertThat(shiftSchedulingRepository.findById(alocacao.getId()))
                    .isEmpty();
        }

        @Test
        @DisplayName("um ANALIST desativado recebe 401, e não 403: sem conta ativa não há utilizador reconhecido")
        void inativoNaoAutentica() throws Exception {
            mockMvc.perform(post("/api/v1/allocations")
                            .with(autorizacao("inativo@teste.com"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido(escala.getId(), analist.getId())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"));
        }

        @Test
        @DisplayName("um SUPERVISOR desativado também não escreve: o perfil não ressuscita a conta")
        void supervisorDesativadoNaoEscreve() throws Exception {
            domain.model.entities.User inativoSupervisor = criarUsuario("Administrador Inativo",
                    "inativo_sup@teste.com", domain.model.enums.UserProfile.SUPERVISOR);
            inativoSupervisor.setActive(false);
            userRepository.saveAndFlush(inativoSupervisor);

            mockMvc.perform(post("/api/v1/allocations")
                            .with(autorizacao("inativo_sup@teste.com"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido(escala.getId(), analist.getId())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("um SUPERVISOR desativado também não lê, e não apaga alocações existentes")
        void supervisorDesativadoNaoLeNemApaga() throws Exception {
            domain.model.entities.User inativoSupervisor = criarUsuario("Administrador Inativo",
                    "inativo_sup@teste.com", domain.model.enums.UserProfile.SUPERVISOR);
            inativoSupervisor.setActive(false);
            userRepository.saveAndFlush(inativoSupervisor);

            mockMvc.perform(get("/api/v1/allocations").with(autorizacao("inativo_sup@teste.com")))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/allocations/" + alocacao.getId())
                            .with(autorizacao("inativo_sup@teste.com")))
                    .andExpect(status().isUnauthorized());

            org.assertj.core.api.Assertions
                    .assertThat(recarregarAlocacao(alocacao.getId())).isNotNull();
        }
    }

    /**
     * Criar um utilizador é a operação que define a password de alguém, e por
     * isso é restrita ao perfil {@code SUPERVISOR} — o mesmo motivo pelo qual
     * a escrita de alocações o é.
     */
    @Nested
    @DisplayName("Criação de utilizadores restrita ao SUPERVISOR")
    class CriacaoDeUtilizadores {

        private static final String NOVO = "nova.pessoa@teste.com";

        private static final String PASSWORD_EM_CLARO = "segredo123";

        private static final String PEDIDO = """
                {"name":"Pessoa Nova","email":"%s","password":"%s","profile":"ANALIST"}"""
                .formatted(NOVO, PASSWORD_EM_CLARO);

        @Test
        @DisplayName("SUPERVISOR cria o utilizador, recebe 201, e a password fica encriptada")
        void supervisorCriaComPasswordEncriptada() throws Exception {
            criarUsuario("Administrador", "admin@teste.com",
                    domain.model.enums.UserProfile.SUPERVISOR);

            var resultado = mockMvc.perform(post("/api/v1/users")
                            .with(autorizacao("admin@teste.com"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.email").value(NOVO))
                    .andExpect(jsonPath("$.name").value("Pessoa Nova"))
                    .andExpect(jsonPath("$.profile").value("ANALIST"))
                    .andReturn();

            // A resposta é um DTO e não a entidade: se a password viesse no
            // corpo, o hash de toda a gente estaria a circular pelo browser.
            org.assertj.core.api.Assertions
                    .assertThat(resultado.getResponse().getContentAsString())
                    .doesNotContain("password");

            sincronizar();
            var gravado = (domain.model.entities.User) userRepository.findByEmail(NOVO);
            org.assertj.core.api.Assertions.assertThat(gravado).isNotNull();
            org.assertj.core.api.Assertions.assertThat(gravado.getPassword())
                    .isNotEqualTo(PASSWORD_EM_CLARO)
                    .startsWith("$2");
            org.assertj.core.api.Assertions
                    .assertThat(encoderUsado.matches(PASSWORD_EM_CLARO, gravado.getPassword()))
                    .isTrue();
        }

        @Test
        @DisplayName("o utilizador criado nasce ativo e pode entrar logo a seguir")
        void utilizadorCriadoNasceAtivo() throws Exception {
            criarUsuario("Administrador", "admin@teste.com",
                    domain.model.enums.UserProfile.SUPERVISOR);

            mockMvc.perform(post("/api/v1/users")
                            .with(autorizacao("admin@teste.com"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO))
                    .andExpect(status().isCreated());

            // Se a conta nascesse desativada, o token de quem acabou de a criar
            // seria recusado a seguir; o login confirma que a conta está viva.
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s","password":"%s"}"""
                                    .formatted(NOVO, PASSWORD_EM_CLARO)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/users/me").with(autorizacao(NOVO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(NOVO));
        }

        @Test
        @DisplayName("a conta nova aparece na lista de utilizadores ativos")
        void apareceNaListaDeAtivos() throws Exception {
            criarUsuario("Administrador", "admin@teste.com",
                    domain.model.enums.UserProfile.SUPERVISOR);

            mockMvc.perform(post("/api/v1/users")
                            .with(autorizacao("admin@teste.com"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO))
                    .andExpect(status().isCreated());

            // A lista vem por nome, e "Administrador" ordena antes de "Pessoa
            // Nova", pelo que a conta nova é a segunda e última.
            mockMvc.perform(get("/api/v1/users").with(autorizacao("admin@teste.com")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[1].email").value(NOVO));
        }

        @Test
        @DisplayName("o ANALIST recebe 403 e a conta não é criada")
        void analistRecebe403() throws Exception {
            criarUsuario("João", "joao@teste.com", domain.model.enums.UserProfile.ANALIST);

            mockMvc.perform(post("/api/v1/users")
                            .with(autorizacao("joao@teste.com"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"))
                    .andExpect(jsonPath("$.message").value(
                            "Apenas o perfil SUPERVISOR pode executar esta operação."));

            sincronizar();
            org.assertj.core.api.Assertions
                    .assertThat(userRepository.findByEmail(NOVO)).isNull();
        }

        @Test
        @DisplayName("sem token a criação dá 401, e não 403: a autenticação vem antes do perfil")
        void semTokenDa401() throws Exception {
            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Unauthorized"));

            sincronizar();
            org.assertj.core.api.Assertions
                    .assertThat(userRepository.findByEmail(NOVO)).isNull();
        }

        @Test
        @DisplayName("e-mail duplicado dá 400 com a mensagem, e não grava um segundo registo")
        void emailDuplicadoDa400() throws Exception {
            criarUsuario("Administrador", "admin@teste.com",
                    domain.model.enums.UserProfile.SUPERVISOR);
            criarUsuario("Já Existia", NOVO, domain.model.enums.UserProfile.ANALIST);

            mockMvc.perform(post("/api/v1/users")
                            .with(autorizacao("admin@teste.com"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("E-mail já cadastrado."));

            sincronizar();
            org.assertj.core.api.Assertions
                    .assertThat(userRepository.findByEmail(NOVO).getName())
                    .isEqualTo("Já Existia");
        }

        @Test
        @DisplayName("um corpo incompleto dá 400 e nomeia o campo, antes de tocar na base de dados")
        void corpoInvalidoDa400() throws Exception {
            criarUsuario("Administrador", "admin@teste.com",
                    domain.model.enums.UserProfile.SUPERVISOR);

            mockMvc.perform(post("/api/v1/users")
                            .with(autorizacao("admin@teste.com"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"","email":"nao-e-email","password":"123","profile":"ANALIST"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.name").exists())
                    .andExpect(jsonPath("$.fields.email").exists())
                    .andExpect(jsonPath("$.fields.password").exists());

            sincronizar();
            org.assertj.core.api.Assertions
                    .assertThat(userRepository.findByEmail("nao-e-email")).isNull();
        }

        @Test
        @DisplayName("um SUPERVISOR desativado não cadastra ninguém: o perfil não ressuscita a conta")
        void supervisorDesativadoNaoCria() throws Exception {
            var inativo = criarUsuario("Administrador Inativo", "inativo_sup@teste.com",
                    domain.model.enums.UserProfile.SUPERVISOR);
            inativo.setActive(false);
            userRepository.saveAndFlush(inativo);

            mockMvc.perform(post("/api/v1/users")
                            .with(autorizacao("inativo_sup@teste.com"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO))
                    .andExpect(status().isUnauthorized());
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
