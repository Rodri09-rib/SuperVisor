package controller;

import domain.model.entities.User;
import domain.model.enums.UserProfile;
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
import service.ChangeTimeService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExchangeController")
class ExchangeControllerTest {

    @Mock
    private ChangeTimeService changeTimeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ExchangeController controller = new ExchangeController();
        ReflectionTestUtils.setField(controller, "changeTimeService", changeTimeService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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

    private User utilizador(Long id, String email) {
        return new User(id, "Utilizador", email, "hash", UserProfile.ANALIST);
    }

    @Nested
    @DisplayName("POST /api/v1/exchanges")
    class PedirTroca {

        @Test
        @DisplayName("devolve 200 sem conteúdo quando a solicitação é criada")
        void devolveSemConteudo() throws Exception {
            autenticarComo(utilizador(2L, "joao@teste.com"));

            mockMvc.perform(post("/api/v1/exchanges")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"originAllocationId":1,"destinationAllocationId":2}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(content().string(""));
        }

        @Test
        @DisplayName("encaminha as duas alocações e o id do utilizador autenticado ao serviço")
        void encaminhaIdsAoServico() throws Exception {
            autenticarComo(utilizador(2L, "joao@teste.com"));

            mockMvc.perform(post("/api/v1/exchanges")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"originAllocationId":10,"destinationAllocationId":11}
                            """));

            verify(changeTimeService).requestExchange(10L, 11L, 2L);
            verifyNoMoreInteractions(changeTimeService);
        }

        @Test
        @DisplayName("o id vem do utilizador autenticado, não do corpo da requisição")
        void idVemDaAutenticacao() throws Exception {
            autenticarComo(utilizador(99L, "admin@teste.com"));

            mockMvc.perform(post("/api/v1/exchanges")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"originAllocationId":10,"destinationAllocationId":11}
                            """));

            verify(changeTimeService).requestExchange(10L, 11L, 99L);
        }

        @Test
        @DisplayName("trocar a identidade autenticada muda o id enviado")
        void identidadeDiferenteMudaOId() throws Exception {
            autenticarComo(utilizador(1L, "admin@teste.com"));

            mockMvc.perform(post("/api/v1/exchanges")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"originAllocationId":10,"destinationAllocationId":11}
                            """));

            verify(changeTimeService).requestExchange(10L, 11L, 1L);
        }

        @Test
        @DisplayName("sem autenticação no contexto o controller falha ao procurar o principal")
        void semAutenticacao() {
            assertThatThrownBy(() -> mockMvc.perform(post("/api/v1/exchanges")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"originAllocationId":1,"destinationAllocationId":2}
                                    """)))
                    .getRootCause()
                    .isInstanceOf(NullPointerException.class);

            verifyNoInteractions(changeTimeService);
        }

        @Test
        @DisplayName("alocação de origem inexistente propaga o erro do serviço")
        void origemInexistente() {
            autenticarComo(utilizador(2L, "joao@teste.com"));
            org.mockito.Mockito.doThrow(new RuntimeException("Alocação de origem não encontrada."))
                    .when(changeTimeService).requestExchange(404L, 11L, 2L);

            assertThatThrownBy(() -> mockMvc.perform(post("/api/v1/exchanges")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"originAllocationId":404,"destinationAllocationId":11}
                                    """)))
                    .getRootCause()
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Alocação de origem não encontrada.");
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/exchanges/{id}/respond")
    class Responder {

        @Test
        @DisplayName("devolve 200 sem conteúdo ao aceitar")
        void aceitar() throws Exception {
            mockMvc.perform(patch("/api/v1/exchanges/100/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isAccepted":true}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(content().string(""));

            verify(changeTimeService).respondExchange(100L, true);
        }

        @Test
        @DisplayName("devolve 200 sem conteúdo ao recusar")
        void recusar() throws Exception {
            mockMvc.perform(patch("/api/v1/exchanges/100/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isAccepted":false}
                                    """))
                    .andExpect(status().isOk());

            verify(changeTimeService).respondExchange(100L, false);
        }

        @Test
        @DisplayName("o id vem do caminho da requisição")
        void idVemDoCaminho() throws Exception {
            mockMvc.perform(patch("/api/v1/exchanges/777/respond")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"isAccepted":true}
                            """));

            verify(changeTimeService).respondExchange(777L, true);
        }

        @Test
        @DisplayName("corpo sem isAccepted é interpretado como recusa, pelo default do Java")
        void corpoSemCampo() throws Exception {
            mockMvc.perform(patch("/api/v1/exchanges/100/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());

            verify(changeTimeService).respondExchange(100L, false);
        }

        @Test
        @DisplayName("não depende do utilizador autenticado")
        void semAutenticacaoNoContexto() throws Exception {
            mockMvc.perform(patch("/api/v1/exchanges/100/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isAccepted":true}
                                    """))
                    .andExpect(status().isOk());

            verify(changeTimeService).respondExchange(100L, true);
        }

        @Test
        @DisplayName("solicitação já respondida propaga o erro do serviço")
        void jaRespondida() {
            org.mockito.Mockito.doThrow(new RuntimeException("Esta solicitação já foi respondida."))
                    .when(changeTimeService).respondExchange(100L, true);

            assertThatThrownBy(() -> mockMvc.perform(patch("/api/v1/exchanges/100/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isAccepted":true}
                                    """)))
                    .getRootCause()
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Esta solicitação já foi respondida.");
        }

        @Test
        @DisplayName("solicitação inexistente propaga o erro do serviço")
        void solicitacaoInexistente() {
            org.mockito.Mockito.doThrow(new RuntimeException("Solicitação não encontrada."))
                    .when(changeTimeService).respondExchange(404L, true);

            assertThatThrownBy(() -> mockMvc.perform(patch("/api/v1/exchanges/404/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isAccepted":true}
                                    """)))
                    .getRootCause()
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Solicitação não encontrada.");
        }

        @Test
        @DisplayName("um id não numérico no caminho é rejeitado")
        void idNaoNumerico() throws Exception {
            mockMvc.perform(patch("/api/v1/exchanges/abc/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isAccepted":true}
                                    """))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(changeTimeService);
        }
    }

    @Nested
    @DisplayName("Contrato do recurso")
    class Contrato {

        @Test
        @DisplayName("não existe endpoint GET em /api/v1/exchanges")
        void getNaoMapeado() throws Exception {
            mockMvc.perform(get("/api/v1/exchanges"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("não existe endpoint POST em /api/v1/exchanges/{id}/respond")
        void postEmRespond() throws Exception {
            mockMvc.perform(post("/api/v1/exchanges/100/respond"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("DELETE em /api/v1/exchanges/{id} não está mapeado, logo devolve 404")
        void deleteNaoMapeado() throws Exception {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete("/api/v1/exchanges/100"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("a resposta de sucesso não tem corpo nem cabeçalho de conteúdo")
        void respostaVazia() throws Exception {
            mockMvc.perform(patch("/api/v1/exchanges/100/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isAccepted":true}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(content().string(""))
                    .andExpect(result -> assertThat(result.getResponse().getContentType()).isNull());
        }
    }
}
