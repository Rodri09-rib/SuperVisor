package controller;

import domain.model.entities.User;
import domain.model.enums.ExchangeStatus;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        return new User(id, "Usuário", email, "hash", UserProfile.ANALIST);
    }

    private User supervisor(Long id, String email) {
        return new User(id, "Administrador", email, "hash", UserProfile.SUPERVISOR);
    }

    @Nested
    @DisplayName("GET /api/v1/exchanges")
    class Historico {

        @Test
        @DisplayName("devolve 200 com a lista do histórico")
        void devolveLista() throws Exception {
            autenticarComo(supervisor(1L, "admin@teste.com"));
            when(changeTimeService.listarHistorico(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/exchanges"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("sem filtros, o serviço recebe todos os parâmetros a null e o usuário autenticado")
        void semFiltros() throws Exception {
            User logado = supervisor(1L, "admin@teste.com");
            autenticarComo(logado);
            when(changeTimeService.listarHistorico(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/exchanges"));

            verify(changeTimeService).listarHistorico(null, null, null, null, logado);
            verifyNoMoreInteractions(changeTimeService);
        }

        @Test
        @DisplayName("traduz os quatro filtros para o serviço")
        void comFiltros() throws Exception {
            User logado = supervisor(1L, "admin@teste.com");
            autenticarComo(logado);
            when(changeTimeService.listarHistorico(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/exchanges")
                            .param("status", "APPROVED")
                            .param("userId", "7")
                            .param("dataInicial", "2026-03-01")
                            .param("dataFim", "2026-03-31"))
                    .andExpect(status().isOk());

            verify(changeTimeService).listarHistorico(
                    ExchangeStatus.APPROVED, 7L,
                    LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), logado);
        }

        @Test
        @DisplayName("as datas são lidas em ISO, e não no formato curto do teclado")
        void datasEmIso() throws Exception {
            autenticarComo(supervisor(1L, "admin@teste.com"));
            when(changeTimeService.listarHistorico(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/exchanges")
                            .param("dataInicial", "2026-03-09")
                            .param("dataFim", "2026-03-13"))
                    .andExpect(status().isOk());

            // 9 de março de 2026 é uma segunda-feira: o teste fixaria a
            // conversão para ISO, e não a data em si.
            verify(changeTimeService).listarHistorico(
                    null, null, LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 13),
                    supervisorLogadoDaSessao());
        }

        private User supervisorLogadoDaSessao() {
            return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        }

        @Test
        @DisplayName("um estado fora do enum é recusado com 400, e não ignorado")
        void estadoInvalido() throws Exception {
            autenticarComo(supervisor(1L, "admin@teste.com"));

            mockMvc.perform(get("/api/v1/exchanges").param("status", "ACEITE"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(changeTimeService);
        }

        @Test
        @DisplayName("uma data mal formada é recusada com 400")
        void dataInvalida() throws Exception {
            autenticarComo(supervisor(1L, "admin@teste.com"));

            mockMvc.perform(get("/api/v1/exchanges").param("dataInicial", "01-03-2026"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(changeTimeService);
        }

        @Test
        @DisplayName("o histórico é pedido com o usuário autenticado, que decide o que é visível")
        void visibilidadeDelegadaAoServico() throws Exception {
            // Não há @PreAuthorize nesta rota: quem vê o quê depende de quem
            // pergunta, e a rota limita-se a entregar o principal ao serviço. O
            // teste fixa que a rota não aplica um filtro de perfil próprio.
            User logado = utilizador(2L, "joao@teste.com");
            autenticarComo(logado);
            when(changeTimeService.listarHistorico(any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/exchanges"))
                    .andExpect(status().isOk());

            verify(changeTimeService).listarHistorico(null, null, null, null, logado);
        }
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
        @DisplayName("encaminha as duas alocações e o id do usuário autenticado ao serviço")
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
        @DisplayName("o id vem do usuário autenticado, não do corpo da requisição")
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
        @DisplayName("um motivo informado é encaminhado ao serviço")
        void comMotivo() throws Exception {
            autenticarComo(utilizador(2L, "joao@teste.com"));

            mockMvc.perform(post("/api/v1/exchanges")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"originAllocationId":10,"destinationAllocationId":11,"reason":"Consulta"}
                            """));

            verify(changeTimeService).requestExchange(10L, 11L, 2L, "Consulta");
        }

        @Test
        @DisplayName("um motivo em branco segue pelo caminho sem motivo, e não é encaminhado")
        void motivoEmBranco() throws Exception {
            autenticarComo(utilizador(2L, "joao@teste.com"));

            mockMvc.perform(post("/api/v1/exchanges")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"originAllocationId":10,"destinationAllocationId":11,"reason":"   "}
                            """));

            verify(changeTimeService).requestExchange(10L, 11L, 2L);
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
            autenticarComo(utilizador(5L, "colega@teste.com"));

            mockMvc.perform(patch("/api/v1/exchanges/100/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isAccepted":true}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(content().string(""));

            verify(changeTimeService).respondExchange(100L, true, 5L);
        }

        @Test
        @DisplayName("devolve 200 sem conteúdo ao recusar")
        void recusar() throws Exception {
            autenticarComo(utilizador(5L, "colega@teste.com"));

            mockMvc.perform(patch("/api/v1/exchanges/100/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isAccepted":false}
                                    """))
                    .andExpect(status().isOk());

            verify(changeTimeService).respondExchange(100L, false, 5L);
        }

        @Test
        @DisplayName("o id vem do caminho da requisição")
        void idVemDoCaminho() throws Exception {
            autenticarComo(utilizador(5L, "colega@teste.com"));

            mockMvc.perform(patch("/api/v1/exchanges/777/respond")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"isAccepted":true}
                            """));

            verify(changeTimeService).respondExchange(777L, true, 5L);
        }

        @Test
        @DisplayName("quem responde é o usuário autenticado, e não um id do corpo")
        void respondeQuemEstaAutenticado() throws Exception {
            // A rota não recebe um id de quem responde. Antes de o serviço
            // verificar quem tem direito a responder, o controlador tinha de
            // saber quem está autenticado — e sem isso qualquer usuário
            // autenticado respondia ao pedido de outra pessoa.
            autenticarComo(utilizador(42L, "terceira@teste.com"));

            mockMvc.perform(patch("/api/v1/exchanges/100/respond")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"isAccepted":true,"loggedUserId":5}
                            """));

            verify(changeTimeService).respondExchange(100L, true, 42L);
        }

        @Test
        @DisplayName("corpo sem isAccepted é interpretado como recusa, pelo default do Java")
        void corpoSemCampo() throws Exception {
            autenticarComo(utilizador(5L, "colega@teste.com"));

            mockMvc.perform(patch("/api/v1/exchanges/100/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());

            verify(changeTimeService).respondExchange(100L, false, 5L);
        }

        @Test
        @DisplayName("sem autenticação no contexto, responder falha antes de chegar ao serviço")
        void semAutenticacaoNoContexto() {
            assertThatThrownBy(() -> mockMvc.perform(patch("/api/v1/exchanges/100/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isAccepted":true}
                                    """)))
                    .getRootCause()
                    .isInstanceOf(NullPointerException.class);

            verifyNoInteractions(changeTimeService);
        }

        @Test
        @DisplayName("a negação de autorização do serviço propaga-se como AccessDeniedException")
        void negacaoDeAutorizacao() {
            // O usuário tem de estar autenticado: o controller lê o principal
            // para saber quem está respondendo, e sem isso rebentava com um NPE
            // antes de o serviço ter oportunidade de recusar.
            autenticarComo(utilizador(7L, "terceiro@teste.com"));
            org.mockito.Mockito.doThrow(new org.springframework.security.access.AccessDeniedException(
                            "Apenas a pessoa a quem a troca foi pedida pode responder ao pedido."))
                    .when(changeTimeService).respondExchange(100L, true, 7L);

            assertThatThrownBy(() -> mockMvc.perform(patch("/api/v1/exchanges/100/respond")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isAccepted":true}
                                    """)))
                    .getRootCause()
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }

        @Test
        @DisplayName("solicitação já respondida propaga o erro do serviço")
        void jaRespondida() {
            autenticarComo(supervisor(1L, "admin@teste.com"));
            org.mockito.Mockito.doThrow(new RuntimeException("Esta solicitação já foi respondida."))
                    .when(changeTimeService).respondExchange(eq(100L), eq(true), any());

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
            autenticarComo(supervisor(1L, "admin@teste.com"));
            org.mockito.Mockito.doThrow(new RuntimeException("Solicitação não encontrada."))
                    .when(changeTimeService).respondExchange(eq(404L), eq(true), any());

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
            autenticarComo(utilizador(5L, "colega@teste.com"));

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
