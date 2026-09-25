package tests.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tests.support.AbstractApiIntegrationTest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("API de trocas de turno — /api/v1/exchanges")
class ExchangeApiIntegrationTest extends AbstractApiIntegrationTest {

    private domain.model.entities.User admin;
    private domain.model.entities.User joao;
    private domain.model.entities.EditionScale escala;
    private domain.model.entities.ShiftScheduling alocacaoAdmin;
    private domain.model.entities.ShiftScheduling alocacaoJoao;
    private String tokenAdmin;
    private String tokenJoao;

    private void prepararCenario() {
        admin = criarUsuario("Administrador", "admin@teste.com",
                domain.model.enums.UserProfile.SUPERVISOR);
        joao = criarUsuario("João", "joao@teste.com", domain.model.enums.UserProfile.ANALIST);
        tokenAdmin = tokenService.gerarToken(admin);
        tokenJoao = tokenService.gerarToken(joao);

        escala = criarEscala("Escala Outubro", admin);
        alocacaoAdmin = criarAlocacao(escala, admin);
        alocacaoJoao = criarAlocacao(escala, joao);
    }

    private String pedirTrocaJson(Long origem, Long destino) {
        return """
                {"originAllocationId":%d,"destinationAllocationId":%d}
                """.formatted(origem, destino);
    }

    private Long pedirTroca(String token, Long origem, Long destino) throws Exception {
        mockMvc.perform(post("/api/v1/exchanges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedirTrocaJson(origem, destino)))
                .andExpect(status().isOk());

        sincronizar();
        return exchangeRequestRepository.findAll().get(0).getId();
    }

    private void responder(Long pedidoId, String token, boolean aceitar) throws Exception {
        mockMvc.perform(patch("/api/v1/exchanges/" + pedidoId + "/respond")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(aceitar ? """
                                {"isAccepted":true}
                                """ : """
                                {"isAccepted":false}
                                """))
                .andExpect(status().isOk());
    }

    @Nested
    @DisplayName("Pedir troca")
    class Pedir {

        @Test
        @DisplayName("regista a solicitação em PENDING e devolve 200")
        void criaSolicitacaoPendente() throws Exception {
            prepararCenario();

            mockMvc.perform(post("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenJoao)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedirTrocaJson(alocacaoAdmin.getId(), alocacaoJoao.getId())))
                    .andExpect(status().isOk());

            sincronizar();
            var request = exchangeRequestRepository.findAll().get(0);
            assertThat(request.getStatus()).isEqualTo("PENDING");
            assertThat(request.getRequestingUser().getId()).isEqualTo(joao.getId());
            assertThat(request.getSourceAllocation().getId()).isEqualTo(alocacaoAdmin.getId());
            assertThat(request.getDestinationAllocation().getId()).isEqualTo(alocacaoJoao.getId());
            assertThat(request.getCreationDate()).isNotNull();
        }

        @Test
        @DisplayName("o pedido não altera as alocações: a troca só ocorre na resposta")
        void pedidoNaoAlteraAlocacoes() throws Exception {
            prepararCenario();

            pedirTroca(tokenJoao, alocacaoAdmin.getId(), alocacaoJoao.getId());
            sincronizar();

            assertThat(recarregarAlocacao(alocacaoAdmin.getId()).getUser().getId())
                    .isEqualTo(admin.getId());
            assertThat(recarregarAlocacao(alocacaoJoao.getId()).getUser().getId())
                    .isEqualTo(joao.getId());
        }

        @Test
        @DisplayName("o requisitante é o utilizador autenticado, não o dono da alocação de origem")
        void requisitanteVemDoToken() throws Exception {
            prepararCenario();

            pedirTroca(tokenJoao, alocacaoAdmin.getId(), alocacaoJoao.getId());
            sincronizar();

            assertThat(exchangeRequestRepository.findAll().get(0).getRequestingUser().getEmail())
                    .isEqualTo("joao@teste.com");
        }

        @Test
        @DisplayName("alocação de origem inexistente devolve 400 com o corpo de erro estruturado")
        void origemInexistente() throws Exception {
            prepararCenario();

            var resposta = mockMvc.perform(post("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenJoao)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedirTrocaJson(9999L, alocacaoJoao.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("Alocação de origem não encontrada."))
                    .andReturn();

            assertThat(resposta.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .contains("\"error\":\"Bad Request\"")
                    .contains("Alocação de origem não encontrada.");
            assertThat(exchangeRequestRepository.count()).isZero();
        }

        @Test
        @DisplayName("alocação de destino inexistente devolve 400 com o corpo de erro estruturado")
        void destinoInexistente() throws Exception {
            prepararCenario();

            var resposta = mockMvc.perform(post("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenJoao)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedirTrocaJson(alocacaoAdmin.getId(), 9999L)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("Alocação de destino não encontrada."))
                    .andReturn();

            assertThat(resposta.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .contains("\"error\":\"Bad Request\"")
                    .contains("Alocação de destino não encontrada.");
            assertThat(exchangeRequestRepository.count()).isZero();
        }

        @Test
        @DisplayName("pedir a mesma alocação como origem e destino é recusado com 400, e nada é gravado")
        void origemIgualDestino() throws Exception {
            prepararCenario();

            var resposta = mockMvc.perform(post("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenJoao)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedirTrocaJson(alocacaoAdmin.getId(), alocacaoAdmin.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message")
                            .value("Alocação de origem e destino não podem ser iguais."))
                    .andReturn();

            assertThat(resposta.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .contains("\"error\":\"Bad Request\"")
                    .contains("Alocação de origem e destino não podem ser iguais.");

            sincronizar();
            assertThat(exchangeRequestRepository.count()).isZero();
        }

        @Test
        @DisplayName("vários pedidos podem coexistir em estado pendente")
        void variosPedidosPendentes() throws Exception {
            prepararCenario();

            pedirTroca(tokenJoao, alocacaoAdmin.getId(), alocacaoJoao.getId());
            mockMvc.perform(post("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedirTrocaJson(alocacaoJoao.getId(), alocacaoAdmin.getId())))
                    .andExpect(status().isOk());

            assertThat(exchangeRequestRepository.findAll())
                    .hasSize(2)
                    .allSatisfy(r -> assertThat(r.getStatus()).isEqualTo("PENDING"));
        }
    }

    @Nested
    @DisplayName("Aceitar a troca")
    class Aceitar {

        @Test
        @DisplayName("troca os utilizadores entre as duas alocações")
        void trocaUtilizadores() throws Exception {
            prepararCenario();
            Long pedido = pedirTroca(tokenJoao, alocacaoAdmin.getId(), alocacaoJoao.getId());

            responder(pedido, tokenAdmin, true);
            sincronizar();

            assertThat(recarregarAlocacao(alocacaoAdmin.getId()).getUser().getId()).isEqualTo(joao.getId());
            assertThat(recarregarAlocacao(alocacaoJoao.getId()).getUser().getId()).isEqualTo(admin.getId());
        }

        @Test
        @DisplayName("marca a solicitação como ACCEPTED")
        void marcaAceita() throws Exception {
            prepararCenario();
            Long pedido = pedirTroca(tokenJoao, alocacaoAdmin.getId(), alocacaoJoao.getId());

            responder(pedido, tokenAdmin, true);
            sincronizar();

            assertThat(recarregarSolicitacao(pedido).getStatus()).isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("qualquer utilizador autenticado pode responder, por não haver verificação de autoria")
        void qualquerUmPodeResponder() throws Exception {
            prepararCenario();
            var terceiro = criarUsuario("Terceiro", "terceiro@teste.com",
                    domain.model.enums.UserProfile.ANALIST);
            Long pedido = pedirTroca(tokenJoao, alocacaoAdmin.getId(), alocacaoJoao.getId());

            responder(pedido, tokenService.gerarToken(terceiro), true);
            sincronizar();

            assertThat(recarregarSolicitacao(pedido).getStatus()).isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("o próprio requisitante pode aceitar o próprio pedido")
        void requisitanteAceitaOProprioPedido() throws Exception {
            prepararCenario();
            Long pedido = pedirTroca(tokenJoao, alocacaoAdmin.getId(), alocacaoJoao.getId());

            responder(pedido, tokenJoao, true);
            sincronizar();

            assertThat(recarregarSolicitacao(pedido).getStatus()).isEqualTo("ACCEPTED");
        }
    }

    @Nested
    @DisplayName("Recusar a troca")
    class Recusar {

        @Test
        @DisplayName("marca a solicitação como REJECTED sem tocar nas alocações")
        void recusaNaoAlteraAlocacoes() throws Exception {
            prepararCenario();
            Long pedido = pedirTroca(tokenJoao, alocacaoAdmin.getId(), alocacaoJoao.getId());

            responder(pedido, tokenAdmin, false);
            sincronizar();

            assertThat(recarregarSolicitacao(pedido).getStatus()).isEqualTo("REJECTED");
            assertThat(recarregarAlocacao(alocacaoAdmin.getId()).getUser().getId()).isEqualTo(admin.getId());
            assertThat(recarregarAlocacao(alocacaoJoao.getId()).getUser().getId()).isEqualTo(joao.getId());
        }

        @Test
        @DisplayName("uma troca recusada pode ser pedida novamente")
        void permiteNovoPedidoAposRecusa() throws Exception {
            prepararCenario();
            Long pedido = pedirTroca(tokenJoao, alocacaoAdmin.getId(), alocacaoJoao.getId());
            responder(pedido, tokenAdmin, false);

            mockMvc.perform(post("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenJoao)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedirTrocaJson(alocacaoAdmin.getId(), alocacaoJoao.getId())))
                    .andExpect(status().isOk());
            sincronizar();

            assertThat(exchangeRequestRepository.findAll())
                    .hasSize(2)
                    .extracting(r -> r.getStatus())
                    .containsExactlyInAnyOrder("REJECTED", "PENDING");
        }
    }

    @Nested
    @DisplayName("Regras de estado da solicitação")
    class Estados {

        @Test
        @DisplayName("uma solicitação já aceite não pode ser respondida outra vez")
        void naoRespondeDuasVezes() throws Exception {
            prepararCenario();
            Long pedido = pedirTroca(tokenJoao, alocacaoAdmin.getId(), alocacaoJoao.getId());
            responder(pedido, tokenAdmin, true);

            var resposta = mockMvc.perform(
                            patch("/api/v1/exchanges/" + pedido + "/respond")
                                    .header("Authorization", "Bearer " + tokenAdmin)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"isAccepted":true}
                                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("Esta solicitação já foi respondida."))
                    .andReturn();

            assertThat(resposta.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .contains("\"error\":\"Bad Request\"")
                    .contains("Esta solicitação já foi respondida.");
        }

        @Test
        @DisplayName("uma segunda resposta não volta a trocar os utilizadores")
        void segundaRespostaNaoTrocaNovamente() throws Exception {
            prepararCenario();
            Long pedido = pedirTroca(tokenJoao, alocacaoAdmin.getId(), alocacaoJoao.getId());
            responder(pedido, tokenAdmin, true);

            erroDaRequisicao(() -> mockMvc.perform(
                    patch("/api/v1/exchanges/" + pedido + "/respond")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isAccepted":true}
                                    """)));
            sincronizar();

            assertThat(recarregarAlocacao(alocacaoAdmin.getId()).getUser().getId()).isEqualTo(joao.getId());
            assertThat(recarregarAlocacao(alocacaoJoao.getId()).getUser().getId()).isEqualTo(admin.getId());
        }

        @Test
        @DisplayName("uma solicitação recusada não pode ser aceite depois")
        void recusaEhDefinitiva() throws Exception {
            prepararCenario();
            Long pedido = pedirTroca(tokenJoao, alocacaoAdmin.getId(), alocacaoJoao.getId());
            responder(pedido, tokenAdmin, false);

            var resposta = mockMvc.perform(
                            patch("/api/v1/exchanges/" + pedido + "/respond")
                                    .header("Authorization", "Bearer " + tokenAdmin)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"isAccepted":true}
                                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("Esta solicitação já foi respondida."))
                    .andReturn();

            assertThat(resposta.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .contains("\"error\":\"Bad Request\"")
                    .contains("Esta solicitação já foi respondida.");
            sincronizar();

            assertThat(recarregarSolicitacao(pedido).getStatus()).isEqualTo("REJECTED");
            assertThat(recarregarAlocacao(alocacaoAdmin.getId()).getUser().getId()).isEqualTo(admin.getId());
        }

        @Test
        @DisplayName("responder a uma solicitação inexistente devolve 400 com o corpo de erro estruturado")
        void solicitacaoInexistente() throws Exception {
            prepararCenario();

            var resposta = mockMvc.perform(
                            patch("/api/v1/exchanges/9999/respond")
                                    .header("Authorization", "Bearer " + tokenAdmin)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"isAccepted":true}
                                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("Solicitação não encontrada."))
                    .andReturn();

            assertThat(resposta.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .contains("\"error\":\"Bad Request\"")
                    .contains("Solicitação não encontrada.");
        }

        @Test
        @DisplayName("a criação de um pedido grava PENDING, nunca o valor por omissão PENDENTE")
        void pendenteEOPendente() throws Exception {
            prepararCenario();

            pedirTroca(tokenJoao, alocacaoAdmin.getId(), alocacaoJoao.getId());
            sincronizar();

            assertThat(exchangeRequestRepository.findAll())
                    .singleElement()
                    .extracting(r -> r.getStatus())
                    .isEqualTo("PENDING");
        }
    }

    @Nested
    @DisplayName("Ciclo completo")
    class CicloCompleto {

        @Test
        @DisplayName("pedir, aceitar e confirmar a troca persistida")
        void cicloHappyPath() throws Exception {
            prepararCenario();

            mockMvc.perform(post("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenJoao)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedirTrocaJson(alocacaoAdmin.getId(), alocacaoJoao.getId())))
                    .andExpect(status().isOk());

            Long pedido = exchangeRequestRepository.findAll().get(0).getId();

            mockMvc.perform(patch("/api/v1/exchanges/" + pedido + "/respond")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"isAccepted":true}
                                    """))
                    .andExpect(status().isOk());
            sincronizar();

            var request = recarregarSolicitacao(pedido);
            assertThat(request.getStatus()).isEqualTo("ACCEPTED");
            assertThat(recarregarAlocacao(alocacaoAdmin.getId()).getUser().getEmail())
                    .isEqualTo("joao@teste.com");
            assertThat(recarregarAlocacao(alocacaoJoao.getId()).getUser().getEmail())
                    .isEqualTo("admin@teste.com");
        }

        @Test
        @DisplayName("uma troca entre alocações de escalas diferentes é recusada com 400, e nada é gravado")
        void trocaAtravessaEscalas() throws Exception {
            prepararCenario();
            var outraEscala = criarEscala("Escala Novembro", admin);
            var alocacaoOutra = criarAlocacao(outraEscala, joao);

            var resposta = mockMvc.perform(post("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenJoao)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedirTrocaJson(alocacaoAdmin.getId(), alocacaoOutra.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message")
                            .value("As alocações pertencem a escalas diferentes."))
                    .andReturn();

            assertThat(resposta.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .contains("\"error\":\"Bad Request\"")
                    .contains("As alocações pertencem a escalas diferentes.");

            sincronizar();
            assertThat(exchangeRequestRepository.count()).isZero();
            assertThat(recarregarAlocacao(alocacaoAdmin.getId()).getUser().getId()).isEqualTo(admin.getId());
            assertThat(recarregarAlocacao(alocacaoOutra.getId()).getUser().getId()).isEqualTo(joao.getId());
        }
    }
}
