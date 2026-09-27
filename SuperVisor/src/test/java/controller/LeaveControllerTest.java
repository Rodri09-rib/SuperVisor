package controller;

import domain.dto.UserLeaveDTO;
import domain.model.entities.User;
import domain.model.enums.UserProfile;
import exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import security.ProfileAuthorization;
import service.LeaveService;
import tests.support.TestFixtures;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaveController")
class LeaveControllerTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 3, 2);
    private static final LocalDate FIM = LocalDate.of(2026, 3, 6);

    @Mock
    private LeaveService leaveService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LeaveController controller = new LeaveController();
        ReflectionTestUtils.setField(controller, "leaveService", leaveService);
        // A autorização real, não um mock: o que se quer testar aqui é que a
        // escrita está fechada ao analista, e um ProfileAuthorization mockado
        // que aceitasse tudo provaria exatamente o contrário do que o nome do
        // teste afirma.
        ReflectionTestUtils.setField(controller, "profileAuthorization", new ProfileAuthorization());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
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

    private User joao() {
        // O id é posto à mão porque a fixture é um objeto novo, não uma linha
        // lida da base: sem ele o DTO sairia com userId a null e a asserção
        // estaria a testar o fixture, não o controller.
        User joao = TestFixtures.analyst();
        joao.setId(2L);
        return joao;
    }

    private UserLeaveDTO dto(User dono, UserProfile pedidoPor) {
        return UserLeaveDTO.from(TestFixtures.leave(dono, INICIO, FIM, "Ferro"), pedidoPor);
    }

    private String corpoValido() {
        return """
                {
                  "userId": 2,
                  "startDate": "2026-03-02",
                  "endDate": "2026-03-06",
                  "reason": "Ferro"
                }
                """;
    }

    @Nested
    @DisplayName("Listar")
    class Listar {

        @Test
        @DisplayName("devolve a lista e o perfil de quem pediu, para o canEdit vir certo")
        void devolveListaComOPerfilDeQuemPediu() throws Exception {
            var joao = joao();
            when(leaveService.listar(null, UserProfile.ANALIST))
                    .thenReturn(List.of(dto(joao, UserProfile.ANALIST)));
            autenticarComo(joao);

            mockMvc.perform(get("/api/v1/leaves"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].userId").value(2))
                    .andExpect(jsonPath("$[0].durationDays").value(5))
                    .andExpect(jsonPath("$[0].canEdit").value(false));
        }

        @Test
        @DisplayName("o supervisor recebe canEdit a true no mesmo payload")
        void supervisorRecebeCanEdit() throws Exception {
            var joao = joao();
            var admin = TestFixtures.supervisor();
            when(leaveService.listar(null, UserProfile.SUPERVISOR))
                    .thenReturn(List.of(dto(joao, UserProfile.SUPERVISOR)));
            autenticarComo(admin);

            mockMvc.perform(get("/api/v1/leaves"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].canEdit").value(true));
        }

        @Test
        @DisplayName("com userId, o filtro é passado ao serviço")
        void passaOFiltro() throws Exception {
            var admin = TestFixtures.supervisor();
            when(leaveService.listar(7L, UserProfile.SUPERVISOR)).thenReturn(List.of());
            autenticarComo(admin);

            mockMvc.perform(get("/api/v1/leaves").param("userId", "7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());

            verify(leaveService).listar(7L, UserProfile.SUPERVISOR);
        }
    }

    @Nested
    @DisplayName("Criar")
    class Criar {

        @Test
        @DisplayName("o supervisor cria e recebe 201 com a folga")
        void supervisorCria() throws Exception {
            var admin = TestFixtures.supervisor();
            var joao = joao();
            when(leaveService.criar(any(), eq(UserProfile.SUPERVISOR)))
                    .thenReturn(dto(joao, UserProfile.SUPERVISOR));
            autenticarComo(admin);

            mockMvc.perform(post("/api/v1/leaves")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoValido()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.userId").value(2))
                    .andExpect(jsonPath("$.reason").value("Ferro"))
                    .andExpect(jsonPath("$.canEdit").value(true));
        }

        @Test
        @DisplayName("o analista é recusado com 403, e o serviço nunca é chamado")
        void analistaRecusado() throws Exception {
            autenticarComo(TestFixtures.analyst());

            mockMvc.perform(post("/api/v1/leaves")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoValido()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message")
                            .value("Apenas o perfil SUPERVISOR pode executar esta operação."));

            // A folha tem de ficar por gravar: o canEdit do DTO é só o que a
            // interface usa para esconder o botão, e a escrita já devia ter
            // sido recusada antes de chegar aqui.
            verifyNoInteractions(leaveService);
        }

        @Test
        @DisplayName("sem autenticação, também 403, e nada é gravado")
        void semAutenticacaoRecusado() throws Exception {
            mockMvc.perform(post("/api/v1/leaves")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoValido()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(leaveService);
        }

        @Test
        @DisplayName("um corpo sem userId é 400 com a mensagem do campo, e não 500")
        void corpoInvalido() throws Exception {
            autenticarComo(TestFixtures.supervisor());

            mockMvc.perform(post("/api/v1/leaves")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "startDate": "2026-03-02",
                                      "endDate": "2026-03-06"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("O utilizador da folga é obrigatório."))
                    .andExpect(jsonPath("$.fields.userId").exists());

            verify(leaveService, never()).criar(any(), any());
        }

        @Test
        @DisplayName("um intervalo invertido é 400, com a regra do serviço")
        void intervaloInvertido() throws Exception {
            autenticarComo(TestFixtures.supervisor());
            when(leaveService.criar(any(), any()))
                    .thenThrow(new IllegalArgumentException(
                            "A data de fim da folga não pode ser anterior à data de início."));

            mockMvc.perform(post("/api/v1/leaves")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "userId": 2,
                                      "startDate": "2026-03-06",
                                      "endDate": "2026-03-02"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("A data de fim da folga não pode ser anterior à data de início."));
        }

        @Test
        @DisplayName("uma folga inexistente ao editar é 400, não 404")
        void editarInexistente() throws Exception {
            autenticarComo(TestFixtures.supervisor());
            when(leaveService.atualizar(eq(404L), any(), any()))
                    .thenThrow(new RuntimeException("Folga não encontrada."));

            mockMvc.perform(put("/api/v1/leaves/404")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoValido()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Folga não encontrada."));
        }
    }

    @Nested
    @DisplayName("Atualizar")
    class Atualizar {

        @Test
        @DisplayName("o supervisor atualiza e recebe 200")
        void supervisorAtualiza() throws Exception {
            var admin = TestFixtures.supervisor();
            var joao = joao();
            when(leaveService.atualizar(eq(100L), any(), eq(UserProfile.SUPERVISOR)))
                    .thenReturn(dto(joao, UserProfile.SUPERVISOR));
            autenticarComo(admin);

            mockMvc.perform(put("/api/v1/leaves/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoValido()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.canEdit").value(true));
        }

        @Test
        @DisplayName("o analista é recusado com 403")
        void analistaRecusado() throws Exception {
            autenticarComo(TestFixtures.analyst());

            mockMvc.perform(put("/api/v1/leaves/100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoValido()))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(leaveService);
        }
    }

    @Nested
    @DisplayName("Apagar")
    class Apagar {

        @Test
        @DisplayName("o supervisor apaga e recebe 204")
        void supervisorApaga() throws Exception {
            autenticarComo(TestFixtures.supervisor());

            mockMvc.perform(delete("/api/v1/leaves/100"))
                    .andExpect(status().isNoContent());

            verify(leaveService).apagar(100L);
        }

        @Test
        @DisplayName("o analista é recusado com 403, e nada é apagado")
        void analistaRecusado() throws Exception {
            autenticarComo(TestFixtures.analyst());

            mockMvc.perform(delete("/api/v1/leaves/100"))
                    .andExpect(status().isForbidden());

            verify(leaveService, never()).apagar(any());
        }

        @Test
        @DisplayName("apagar uma folga que não existe é 400")
        void inexistente() throws Exception {
            autenticarComo(TestFixtures.supervisor());
            org.mockito.Mockito.doThrow(new RuntimeException("Folga não encontrada."))
                    .when(leaveService).apagar(404L);

            mockMvc.perform(delete("/api/v1/leaves/404"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Folga não encontrada."));
        }
    }
}
