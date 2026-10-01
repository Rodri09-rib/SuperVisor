package tests.integration;

import domain.model.entities.EditionScale;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.AllocationStatus;
import domain.model.enums.ExchangeStatus;
import domain.model.enums.ShiftType;
import domain.model.enums.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tests.support.AbstractApiIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Resposta do analista ao turno escalado, ponta a ponta.
 *
 * <p>O caminho feliz já vem coberto pelo {@code AllocationServiceTest}. O que
 * aqui se fixa e a parte que só aparece com a aplicação inteira: o token decide
 * quem pode responder, o estado gravado e lido de volta da base, e a recusa fica
 * mesmo barrada por uma troca pendente criada por outra via.
 */
@DisplayName("Aceitação de alocações - API")
class AllocationAcceptanceApiIntegrationTest extends AbstractApiIntegrationTest {

    private User supervisor;
    private User ana;
    private User bruno;
    private String tokenSupervisor;
    private String tokenAna;
    private String tokenBruno;
    private EditionScale escala;

    @BeforeEach
    void preparar() {
        supervisor = criarUsuario("Administrador", "admin@teste.com", UserProfile.SUPERVISOR);
        ana = criarUsuario("Ana", "ana@teste.com", UserProfile.ANALIST);
        bruno = criarUsuario("Bruno", "bruno@teste.com", UserProfile.ANALIST);
        tokenSupervisor = tokenService.gerarToken(supervisor);
        tokenAna = tokenService.gerarToken(ana);
        tokenBruno = tokenService.gerarToken(bruno);
        escala = criarEscala("Escala Outubro", supervisor);
    }

    private AllocationStatus estadoDe(Long alocacaoId) {
        sincronizar();
        return recarregarAlocacao(alocacaoId).getAnalystAcceptanceStatus();
    }

    /** Pedido de troca feito pela Ana, que fica a ser a solicitante. */
    private Long pedirTroca(ShiftScheduling origem, ShiftScheduling destino) throws Exception {
        mockMvc.perform(post("/api/v1/exchanges")
                        .header("Authorization", "Bearer " + tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originAllocationId":%d,"destinationAllocationId":%d}"""
                                .formatted(origem.getId(), destino.getId())))
                .andExpect(status().isOk());

        sincronizar();
        return exchangeRequestRepository.findAll().get(0).getId();
    }

    /** O Bruno e o dono do turno de destino, logo e a quem a troca e pedida. */
    private void responderTroca(Long pedidoId, boolean aceitar) throws Exception {
        mockMvc.perform(patch("/api/v1/exchanges/" + pedidoId + "/respond")
                        .header("Authorization", "Bearer " + tokenBruno)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(aceitar ? "{\"isAccepted\":true}" : "{\"isAccepted\":false}"))
                .andExpect(status().isOk());
    }

    private ResultActions responder(String token, Long alocacaoId, String estado) throws Exception {
        return mockMvc.perform(patch("/api/v1/allocations/" + alocacaoId + "/acceptance")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + estado + "\"}"));
    }

    @Nested
    @DisplayName("Quem responde")
    class QuemResponde {

        @Test
        @DisplayName("o dono aceita e o estado fica gravado como ACCEPTED")
        void donoAceita() throws Exception {
            ShiftScheduling alocacao = criarAlocacao(escala, ana, ShiftType.T1_SAB);

            mockMvc.perform(patch("/api/v1/allocations/" + alocacao.getId() + "/acceptance")
                            .header("Authorization", "Bearer " + tokenAna)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ACCEPTED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.analystAcceptanceStatus").value("ACCEPTED"));

            assertThat(estadoDe(alocacao.getId())).isEqualTo(AllocationStatus.ACCEPTED);
        }

        @Test
        @DisplayName("o dono recusa")
        void donoRecusa() throws Exception {
            ShiftScheduling alocacao = criarAlocacao(escala, ana, ShiftType.T1_SAB);

            mockMvc.perform(patch("/api/v1/allocations/" + alocacao.getId() + "/acceptance")
                            .header("Authorization", "Bearer " + tokenAna)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"REJECTED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.analystAcceptanceStatus").value("REJECTED"));

            assertThat(estadoDe(alocacao.getId())).isEqualTo(AllocationStatus.REJECTED);
        }

        @Test
        @DisplayName("outro analista recebe 403 e o estado não muda")
        void outroAnalistaDa403() throws Exception {
            ShiftScheduling alocacao = criarAlocacao(escala, ana, ShiftType.T1_SAB);

            responder(tokenBruno, alocacao.getId(), "ACCEPTED")
                    .andExpect(status().isForbidden());

            assertThat(estadoDe(alocacao.getId())).isEqualTo(AllocationStatus.PENDING);
        }

        @Test
        @DisplayName("a supervisao responde por cima, para desbloquear alguem indisponivel")
        void supervisorResponde() throws Exception {
            ShiftScheduling alocacao = criarAlocacao(escala, ana, ShiftType.T1_SAB);

            mockMvc.perform(patch("/api/v1/allocations/" + alocacao.getId() + "/acceptance")
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ACCEPTED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.analystAcceptanceStatus").value("ACCEPTED"));

            assertThat(estadoDe(alocacao.getId())).isEqualTo(AllocationStatus.ACCEPTED);
        }

        @Test
        @DisplayName("sem token e 401")
        void semTokenDa401() throws Exception {
            ShiftScheduling alocacao = criarAlocacao(escala, ana, ShiftType.T1_SAB);

            mockMvc.perform(patch("/api/v1/allocations/" + alocacao.getId() + "/acceptance")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ACCEPTED\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Regras do estado")
    class Regras {

        @Test
        @DisplayName("recusar um turno com troca pendente dá 400 e não grava")
        void recusaBarradaPorTrocaPendente() throws Exception {
            ShiftScheduling deAna = criarAlocacao(escala, ana, ShiftType.T1_SAB);
            ShiftScheduling deBruno = criarAlocacao(escala, bruno, ShiftType.T3_SAB);
            pedirTroca(deAna, deBruno);

            responder(tokenAna, deAna.getId(), "REJECTED")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("troca pendente")));

            assertThat(estadoDe(deAna.getId())).isEqualTo(AllocationStatus.PENDING);
        }

        @Test
        @DisplayName("aceitar um turno com troca pendente passa")
        void aceitePassaComTrocaPendente() throws Exception {
            ShiftScheduling deAna = criarAlocacao(escala, ana, ShiftType.T1_SAB);
            ShiftScheduling deBruno = criarAlocacao(escala, bruno, ShiftType.T3_SAB);
            pedirTroca(deAna, deBruno);

            responder(tokenAna, deAna.getId(), "ACCEPTED")
                    .andExpect(status().isOk());

            assertThat(estadoDe(deAna.getId())).isEqualTo(AllocationStatus.ACCEPTED);
        }

        @Test
        @DisplayName("depois de a troca ser recusada, a recusa do turno volta a ser possível")
        void recusaVoltaASerPossivel() throws Exception {
            ShiftScheduling deAna = criarAlocacao(escala, ana, ShiftType.T1_SAB);
            ShiftScheduling deBruno = criarAlocacao(escala, bruno, ShiftType.T3_SAB);
            Long pedido = pedirTroca(deAna, deBruno);

            responderTroca(pedido, false);

            responder(tokenAna, deAna.getId(), "REJECTED")
                    .andExpect(status().isOk());

            assertThat(estadoDe(deAna.getId())).isEqualTo(AllocationStatus.REJECTED);
        }

        @Test
        @DisplayName("responder o que já está respondido não dá erro")
        void idempotente() throws Exception {
            ShiftScheduling alocacao = criarAlocacao(escala, ana, ShiftType.T1_SAB);

            responder(tokenAna, alocacao.getId(), "ACCEPTED")
                    .andExpect(status().isOk());
            responder(tokenAna, alocacao.getId(), "ACCEPTED")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.analystAcceptanceStatus").value("ACCEPTED"));

            assertThat(estadoDe(alocacao.getId())).isEqualTo(AllocationStatus.ACCEPTED);
        }

        @Test
        @DisplayName("sem estado no pedido dá 400 e não chega ao serviço")
        void semEstadoDa400() throws Exception {
            ShiftScheduling alocacao = criarAlocacao(escala, ana, ShiftType.T1_SAB);

            mockMvc.perform(patch("/api/v1/allocations/" + alocacao.getId() + "/acceptance")
                            .header("Authorization", "Bearer " + tokenAna)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.status").exists());

            assertThat(estadoDe(alocacao.getId())).isEqualTo(AllocationStatus.PENDING);
        }

        @Test
        @DisplayName("uma alocação inexistente dá 400")
        void inexistenteDa400() throws Exception {
            responder(tokenAna, 999999L, "ACCEPTED")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("encontrada")));
        }
    }

    @Nested
    @DisplayName("O estado e invalidado quando o turno muda")
    class Invalidacao {

        @Test
        @DisplayName("editar o turno volta o aceite a pendente")
        void edicaoVoltaAPendente() throws Exception {
            ShiftScheduling alocacao = criarAlocacao(escala, ana, ShiftType.T1_SAB);
            responder(tokenAna, alocacao.getId(), "ACCEPTED")
                    .andExpect(status().isOk());

            mockMvc.perform(put("/api/v1/allocations/" + alocacao.getId())
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"editionScaleId":%d,"userId":%d,"shift":"T4_SAB"}"""
                                    .formatted(escala.getId(), ana.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.analystAcceptanceStatus").value("PENDING"));

            assertThat(estadoDe(alocacao.getId())).isEqualTo(AllocationStatus.PENDING);
        }

        @Test
        @DisplayName("aprovar uma troca volta os dois turnos a pendentes, porque mudou quem os tem")
        void trocaAprovadaVoltaOsDoisAPendentes() throws Exception {
            ShiftScheduling deAna = criarAlocacao(escala, ana, ShiftType.T1_SAB);
            ShiftScheduling deBruno = criarAlocacao(escala, bruno, ShiftType.T3_SAB);

            responder(tokenAna, deAna.getId(), "ACCEPTED").andExpect(status().isOk());
            responder(tokenBruno, deBruno.getId(), "ACCEPTED").andExpect(status().isOk());

            Long pedido = pedirTroca(deAna, deBruno);
            sincronizar();
            assertThat(recarregarSolicitacao(pedido).getStatus()).isEqualTo(ExchangeStatus.PENDING);

            responderTroca(pedido, true);

            // Nem a Ana nem o Bruno aceitaram os turnos que ficaram a ter: quem
            // aprovou a troca não estava aceitando por eles.
            assertThat(estadoDe(deAna.getId())).isEqualTo(AllocationStatus.PENDING);
            assertThat(estadoDe(deBruno.getId())).isEqualTo(AllocationStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("O estado e visivel na leitura")
    class Leitura {

        @Test
        @DisplayName("a lista de alocações traz o estado, para o editor mostrar o que está por responder")
        void listaTrazOEstado() throws Exception {
            ShiftScheduling alocacao = criarAlocacao(escala, ana, ShiftType.T1_SAB);
            responder(tokenAna, alocacao.getId(), "REJECTED")
                    .andExpect(status().isOk());
            sincronizar();

            mockMvc.perform(get("/api/v1/allocations?editionScaleId=" + escala.getId())
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == " + alocacao.getId() + ")]"
                            + ".analystAcceptanceStatus").value(
                            org.hamcrest.Matchers.contains("REJECTED")));
        }
    }
}
