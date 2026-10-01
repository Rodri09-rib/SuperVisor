package controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import domain.dto.ActiveUserDTO;
import domain.dto.CreateUserRequestDTO;
import domain.dto.UpdateUserRequestDTO;
import domain.model.entities.User;
import domain.model.enums.TeamGroup;
import domain.model.enums.UserProfile;
import exception.GlobalExceptionHandler;
import exception.RegraDeNegocioException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import security.ProfileAuthorization;
import service.UserService;
import tests.support.TestFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController")
class UserControllerTest {

    private static final String SEM_PERFIL =
            "Apenas o perfil SUPERVISOR pode executar esta operação.";

    private static final String PEDIDO_JSON = """
            {"name":"João Silva","email":"joao.silva@teste.com",\
            "password":"segredo123","profile":"ANALIST"}""";

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserController controller = new UserController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        // A ProfileAuthorization real, e nao um mock, para o teste cobrir a
        // ligacao entre o perfil do principal e o 403 devolvido.
        ReflectionTestUtils.setField(controller, "profileAuthorization", new ProfileAuthorization());
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(User user) {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private ActiveUserDTO criado() {
        return new ActiveUserDTO(7L, "João Silva", "joao.silva@teste.com", UserProfile.ANALIST,
                null, "Sem equipa", true);
    }

    /** O mesmo utilizador, mas já desativado, para as respostas de estado. */
    private ActiveUserDTO desativado() {
        return new ActiveUserDTO(7L, "João Silva", "joao.silva@teste.com", UserProfile.ANALIST,
                null, "Sem equipa", false);
    }

    @Nested
    @DisplayName("Leitura")
    class Leitura {

        @Test
        @DisplayName("GET devolve 200 com a lista de utilizadores ativos")
        void listaAtivos() throws Exception {
            when(userService.listarAtivos()).thenReturn(java.util.List.of(criado()));

            mockMvc.perform(get("/api/v1/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].email").value("joao.silva@teste.com"));
        }

        @Test
        @DisplayName("GET não expõe a password de nenhum utilizador")
        void listaNaoExpõeASenha() throws Exception {
            when(userService.listarAtivos()).thenReturn(java.util.List.of(criado()));

            var resultado = mockMvc.perform(get("/api/v1/users"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(resultado.getResponse().getContentAsString())
                    .doesNotContain("password");
        }
    }

    @Nested
    @DisplayName("Criação restrita ao SUPERVISOR")
    class Criacao {

        @Test
        @DisplayName("o SUPERVISOR cria o utilizador e recebe 201")
        void supervisorCria() throws Exception {
            autenticarComo(TestFixtures.supervisor());
            when(userService.criar(any(CreateUserRequestDTO.class))).thenReturn(criado());

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value("joao.silva@teste.com"))
                    .andExpect(jsonPath("$.profile").value("ANALIST"));
        }

        @Test
        @DisplayName("a resposta não inclui a password, mesmo quando o serviço devolve a entidade")
        void respostaNaoExpõeASenha() throws Exception {
            autenticarComo(TestFixtures.supervisor());
            when(userService.criar(any(CreateUserRequestDTO.class))).thenReturn(criado());

            var resultado = mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO_JSON))
                    .andExpect(status().isCreated())
                    .andReturn();

            assertThat(resultado.getResponse().getContentAsString())
                    .doesNotContain("segredo123")
                    .doesNotContain("password");
        }

        @Test
        @DisplayName("encaminha os campos do pedido para o serviço, já com active=true")
        void encaminhaOPedido() throws Exception {
            autenticarComo(TestFixtures.supervisor());
            when(userService.criar(any(CreateUserRequestDTO.class))).thenReturn(criado());

            mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(PEDIDO_JSON));

            ArgumentCaptor<CreateUserRequestDTO> captor =
                    ArgumentCaptor.forClass(CreateUserRequestDTO.class);
            verify(userService).criar(captor.capture());
            assertThat(captor.getValue().name()).isEqualTo("João Silva");
            assertThat(captor.getValue().email()).isEqualTo("joao.silva@teste.com");
            assertThat(captor.getValue().password()).isEqualTo("segredo123");
            assertThat(captor.getValue().profile()).isEqualTo(UserProfile.ANALIST);
            assertThat(captor.getValue().active()).isTrue();
        }

        @Test
        @DisplayName("o ANALIST recebe 403 e o serviço nem é chamado")
        void analistRecebe403() throws Exception {
            autenticarComo(TestFixtures.analyst());

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"))
                    .andExpect(jsonPath("$.message").value(SEM_PERFIL));

            verify(userService, never()).criar(any(CreateUserRequestDTO.class));
        }

        @Test
        @DisplayName("sem principal autenticado também é 403: o perfil não é conferível")
        void semPrincipalRecebe403() throws Exception {
            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO_JSON))
                    .andExpect(status().isForbidden());

            verify(userService, never()).criar(any(CreateUserRequestDTO.class));
        }

        @Test
        @DisplayName("e-mail duplicado, levantado pelo serviço, vira 400 com a mensagem")
        void emailDuplicadoDa400() throws Exception {
            autenticarComo(TestFixtures.supervisor());
            when(userService.criar(any(CreateUserRequestDTO.class)))
                    .thenThrow(new RegraDeNegocioException("E-mail já cadastrado."));

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("E-mail já cadastrado."));
        }
    }

    @Nested
    @DisplayName("Validação do pedido")
    class Validacao {

        @Test
        @DisplayName("um campo em branco dá 400 e não chega ao serviço")
        void campoEmBranco() throws Exception {
            autenticarComo(TestFixtures.supervisor());

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"  ","email":"a@b.com","password":"segredo123","profile":"ANALIST"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.name").exists());

            verify(userService, never()).criar(any(CreateUserRequestDTO.class));
        }

        @Test
        @DisplayName("um e-mail inválido dá 400 e nomeia o campo")
        void emailInvalido() throws Exception {
            autenticarComo(TestFixtures.supervisor());

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"João","email":"nao-e-email","password":"segredo123","profile":"ANALIST"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.email").exists());

            verify(userService, never()).criar(any(CreateUserRequestDTO.class));
        }

        @Test
        @DisplayName("uma password com menos de 6 caracteres dá 400 e nomeia o campo")
        void passwordCurta() throws Exception {
            autenticarComo(TestFixtures.supervisor());

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"João","email":"a@b.com","password":"123","profile":"ANALIST"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.password").exists());

            verify(userService, never()).criar(any(CreateUserRequestDTO.class));
        }

        @Test
        @DisplayName("sem perfil dá 400, porque não há valor por omissão para ele")
        void semPerfil() throws Exception {
            autenticarComo(TestFixtures.supervisor());

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"João","email":"a@b.com","password":"segredo123"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.profile").exists());

            verify(userService, never()).criar(any(CreateUserRequestDTO.class));
        }

        @Test
        @DisplayName("um perfil fora do enum é recusado na desserialização, antes da validação")
        void perfilDesconhecido() throws Exception {
            autenticarComo(TestFixtures.supervisor());

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"João","email":"a@b.com","password":"segredo123","profile":"REI"}"""))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).criar(any(CreateUserRequestDTO.class));
        }

        @Test
        @DisplayName("o active é aceite explicitamente, sem replaces do predefinido")
        void activeExplicito() throws Exception {
            autenticarComo(TestFixtures.supervisor());
            when(userService.criar(any(CreateUserRequestDTO.class))).thenReturn(criado());

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"João","email":"a@b.com","password":"segredo123",\
                                    "profile":"SUPERVISOR","active":false}"""))
                    .andExpect(status().isCreated());

            ArgumentCaptor<CreateUserRequestDTO> captor =
                    ArgumentCaptor.forClass(CreateUserRequestDTO.class);
            verify(userService).criar(captor.capture());
            assertThat(captor.getValue().active()).isFalse();
        }

        @Test
        @DisplayName("a equipa pode vir na criação, que era o campo que não tinha forma de ser preenchida")
        void equipaNaCriacao() throws Exception {
            autenticarComo(TestFixtures.supervisor());
            when(userService.criar(any(CreateUserRequestDTO.class))).thenReturn(criado());

            mockMvc.perform(post("/api/v1/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"João","email":"a@b.com","password":"segredo123",\
                                    "profile":"ANALIST","teamGroup":"EQUIPE_A"}"""))
                    .andExpect(status().isCreated());

            ArgumentCaptor<CreateUserRequestDTO> captor =
                    ArgumentCaptor.forClass(CreateUserRequestDTO.class);
            verify(userService).criar(captor.capture());
            assertThat(captor.getValue().teamGroup()).isEqualTo(TeamGroup.EQUIPE_A);
        }
    }

    @Nested
    @DisplayName("Gestão de utilizadores, restrita ao SUPERVISOR")
    class Gestao {

        private static final String ID = "7";

        private String url(String sufixo) {
            return "/api/v1/users/" + ID + sufixo;
        }

        /** Supervisor com id, porque as regras de estado dependem de saber quem pede. */
        private void autenticarComoSupervisor() {
            User supervisor = TestFixtures.supervisor();
            supervisor.setId(1L);
            autenticarComo(supervisor);
        }

        private String alteracaoJson() {
            return """
                    {"name":"João Silva","profile":"ANALIST","teamGroup":"EQUIPE_A"}""";
        }

        @Test
        @DisplayName("PUT altera o cadastro e devolve 200 com o utilizador")
        void supervisorAlteraOCadastro() throws Exception {
            autenticarComo(TestFixtures.supervisor());
            when(userService.atualizar(any(Long.class), any(UpdateUserRequestDTO.class)))
                    .thenReturn(criado());

            mockMvc.perform(put(url(""))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(alteracaoJson()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("joao.silva@teste.com"));
        }

        @Test
        @DisplayName("PUT encaminha o id e o pedido para o serviço")
        void encaminhaIdEPedido() throws Exception {
            autenticarComo(TestFixtures.supervisor());
            when(userService.atualizar(any(Long.class), any(UpdateUserRequestDTO.class)))
                    .thenReturn(criado());

            mockMvc.perform(put(url(""))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(alteracaoJson()));

            ArgumentCaptor<Long> id = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<UpdateUserRequestDTO> pedido =
                    ArgumentCaptor.forClass(UpdateUserRequestDTO.class);
            verify(userService).atualizar(id.capture(), pedido.capture());
            assertThat(id.getValue()).isEqualTo(7L);
            assertThat(pedido.getValue().name()).isEqualTo("João Silva");
            assertThat(pedido.getValue().teamGroup()).isEqualTo(TeamGroup.EQUIPE_A);
        }

        @Test
        @DisplayName("PATCH desativa a conta e devolve o estado novo")
        void supervisorDesativa() throws Exception {
            autenticarComoSupervisor();
            when(userService.alterarEstado(any(Long.class), anyBoolean(), anyLong()))
                    .thenReturn(desativado());

            mockMvc.perform(patch(url("/estado"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));
        }

        @Test
        @DisplayName("PATCH encaminha o id de quem está a pedir, para a regra de autodesativação")
        void encaminhaOPrincipal() throws Exception {
            autenticarComoSupervisor();
            when(userService.alterarEstado(any(Long.class), anyBoolean(), anyLong()))
                    .thenReturn(desativado());

            mockMvc.perform(patch(url("/estado"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"active\":false}"));

            verify(userService).alterarEstado(7L, false, 1L);
        }

        @Test
        @DisplayName("POST na senha devolve 204, sem corpo")
        void redefineSenha() throws Exception {
            autenticarComo(TestFixtures.supervisor());

            mockMvc.perform(post(url("/senha"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"novasenha123\"}"))
                    .andExpect(status().isNoContent());

            verify(userService).redefinirSenha(7L, "novasenha123");
        }

        @Test
        @DisplayName("uma senha curta na redefinição dá 400 e não chega ao serviço")
        void senhaCurtaDa400() throws Exception {
            autenticarComo(TestFixtures.supervisor());

            mockMvc.perform(post(url("/senha"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"123\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.password").exists());

            verify(userService, never()).redefinirSenha(anyLong(), anyString());
        }

        @Test
        @DisplayName("GET /todos devolve também as contas desativadas")
        void listaTodos() throws Exception {
            autenticarComo(TestFixtures.supervisor());
            when(userService.listarTodos()).thenReturn(java.util.List.of(criado(), desativado()));

            mockMvc.perform(get("/api/v1/users/todos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[1].active").value(false));
        }

        @Test
        @DisplayName("o ANALIST recebe 403 em todas as operações de gestão, e o serviço não é chamado")
        void analistRecebe403EmTodoOGesto() throws Exception {
            autenticarComo(TestFixtures.analyst());

            mockMvc.perform(put(url(""))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(alteracaoJson()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(SEM_PERFIL));
            mockMvc.perform(patch(url("/estado"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":false}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(url("/senha"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"password\":\"novasenha123\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/users/todos"))
                    .andExpect(status().isForbidden());

            verify(userService, never()).atualizar(anyLong(), any(UpdateUserRequestDTO.class));
            verify(userService, never()).alterarEstado(anyLong(), anyBoolean(), anyLong());
            verify(userService, never()).redefinirSenha(anyLong(), anyString());
            verify(userService, never()).listarTodos();
        }

        @Test
        @DisplayName("uma equipa fora do enum é recusada na desserialização")
        void equipaDesconhecidaDa400() throws Exception {
            autenticarComo(TestFixtures.supervisor());

            mockMvc.perform(put(url(""))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"João","profile":"ANALIST","teamGroup":"EQUIPE_Z"}"""))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).atualizar(anyLong(), any(UpdateUserRequestDTO.class));
        }

        @Test
        @DisplayName("nome em branco no PUT dá 400 e nomeia o campo")
        void nomeEmBrancoDa400() throws Exception {
            autenticarComo(TestFixtures.supervisor());

            mockMvc.perform(put(url(""))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name":"   ","profile":"ANALIST"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.name").exists());

            verify(userService, never()).atualizar(anyLong(), any(UpdateUserRequestDTO.class));
        }

        @Test
        @DisplayName("utilizador inexistente, levantado pelo serviço, vira 400 com a mensagem")
        void inexistenteDa400() throws Exception {
            autenticarComo(TestFixtures.supervisor());
            when(userService.atualizar(any(Long.class), any(UpdateUserRequestDTO.class)))
                    .thenThrow(new RegraDeNegocioException("Utilizador não encontrado."));

            mockMvc.perform(put(url(""))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(alteracaoJson()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Utilizador não encontrado."));
        }
    }
}
