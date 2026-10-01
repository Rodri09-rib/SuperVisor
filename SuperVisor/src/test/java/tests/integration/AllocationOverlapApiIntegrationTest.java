package tests.integration;

import domain.model.entities.EditionScale;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.ShiftType;
import domain.model.enums.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tests.support.AbstractApiIntegrationTest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API de alocações, com foco na regra que não existia: a mesma pessoa não pode
 * ficar com dois turnos que se cruzem.
 *
 * <p>Os testes unitários de {@code AllocationService} provam a regra com
 * repositórios simulados. Estes provam o que só a base de dados pode provar: que a
 * consulta derivada usada pela regra devolve mesmo as alocações da pessoa, e
 * que a sobreposição é recusada com o mesmo código e a mesma mensagem com que o
 * frontend lida.
 */
@DisplayName("Sobreposição de alocações — /api/v1/allocations")
class AllocationOverlapApiIntegrationTest extends AbstractApiIntegrationTest {

    private User supervisor;
    private User joao;
    private EditionScale escala;
    private String tokenSupervisor;

    /** Sábado e domingo de outubro de 2025, para as datas específicas. */
    private static final java.time.LocalDate SABADO_4_OUTUBRO = java.time.LocalDate.of(2025, 10, 4);

    private static final java.time.LocalDate SABADO_11_OUTUBRO = java.time.LocalDate.of(2025, 10, 11);

    @BeforeEach
    void prepararCenario() {
        supervisor = criarUsuario("Administrador", "admin@teste.com", UserProfile.SUPERVISOR);
        joao = criarUsuario("João", "joao@teste.com", UserProfile.ANALIST);
        tokenSupervisor = tokenService.gerarToken(supervisor);
        escala = criarEscala("Escala Outubro", supervisor);
    }

    private String pedido(ShiftType turno, java.time.LocalDate data) {
        String corpo = """
                {"editionScaleId":%d,"userId":%d,"shift":"%s","specificDate":%s%s}"""
                .formatted(escala.getId(), joao.getId(), turno.name(),
                        data == null ? "null" : "\"" + data + "\"",
                        data == null ? "" : "");
        return corpo;
    }

    private String pedido(ShiftType turno) {
        return pedido(turno, null);
    }

    private void alocar(ShiftType turno, java.time.LocalDate data) throws Exception {
        mockMvc.perform(post("/api/v1/allocations")
                        .header("Authorization", "Bearer " + tokenSupervisor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pedido(turno, data)))
                .andExpect(status().isOk());
        sincronizar();
    }

    private ShiftScheduling primeiraAlocacaoDeJoao() {
        return shiftSchedulingRepository.findByEditionScaleIdAndUserIdOrderByIdAsc(
                escala.getId(), joao.getId()).get(0);
    }

    @Nested
    @DisplayName("Sobreposições recusadas")
    class Recusadas {

        @Test
        @DisplayName("T2 é recusado quando a pessoa já tem T1, porque 11h00-15h00 cruza 08h00-12h00")
        void recusaTurnoQueCruza() throws Exception {
            alocar(ShiftType.T1_SAB, null);

            var resposta = mockMvc.perform(post("/api/v1/allocations")
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido(ShiftType.T2_SAB)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andReturn();

            assertThat(resposta.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .contains("já tem o turno")
                    .contains("T1 - Sábado")
                    .contains("sobrepõem-se");

            sincronizar();
            assertThat(shiftSchedulingRepository
                    .findByEditionScaleIdAndUserIdOrderByIdAsc(escala.getId(), joao.getId()))
                    .hasSize(1);
        }

        @Test
        @DisplayName("o mesmo turno duas vezes no mesmo dia é recusado")
        void recusaDuplicadoNoMesmoDia() throws Exception {
            alocar(ShiftType.T1_SAB, SABADO_4_OUTUBRO);

            mockMvc.perform(post("/api/v1/allocations")
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido(ShiftType.T1_SAB, SABADO_4_OUTUBRO)))
                    .andExpect(status().isBadRequest());

            sincronizar();
            assertThat(shiftSchedulingRepository
                    .findByEditionScaleIdAndUserIdOrderByIdAsc(escala.getId(), joao.getId()))
                    .hasSize(1);
        }

        @Test
        @DisplayName("uma alteração que cairia em cima de outro turno é recusada")
        void recusaAlteracaoQueCriaSobreposicao() throws Exception {
            // O que está protegendo: T1 e T3 juntos são legítimos porque só se
            // tocam, mas empurrar o T3 para T2 cruza o T1 que já lá está. A
            // sobreposição tem de ser vista também na alteração, e não só na
            // criação — senão a regra só valia para o primeiro turno.
            alocar(ShiftType.T1_SAB, null);
            alocar(ShiftType.T3_SAB, null);

            ShiftScheduling oT3 = shiftSchedulingRepository
                    .findByEditionScaleIdAndUserIdOrderByIdAsc(escala.getId(), joao.getId())
                    .get(1);

            mockMvc.perform(put("/api/v1/allocations/" + oT3.getId())
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido(ShiftType.T2_SAB)))
                    .andExpect(status().isBadRequest());

            sincronizar();
            assertThat(recarregarAlocacao(oT3.getId()).getShift())
                    .isEqualTo(ShiftType.T3_SAB);
        }

        @Test
        @DisplayName("mover a única alocação da pessoa para outro turno é aceite")
        void moverAUnicaAlocacaoNaoEncontraNada() throws Exception {
            // O contrário do teste anterior, e igualmente importante: mudar o
            // turno de T1 para T2 não sobrepõe nada, porque já não fica nenhum
            // T1. Se a alocação em edição fosse comparada consigo própria, esta
            // alteração seria recusada sem qualquer motivo.
            alocar(ShiftType.T1_SAB, null);
            ShiftScheduling oT1 = primeiraAlocacaoDeJoao();

            mockMvc.perform(put("/api/v1/allocations/" + oT1.getId())
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido(ShiftType.T2_SAB)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.shift").value("T2_SAB"));

            sincronizar();
            assertThat(recarregarAlocacao(oT1.getId()).getShift())
                    .isEqualTo(ShiftType.T2_SAB);
        }

        @Test
        @DisplayName("alterar o turno de outra pessoa não gera conflito com os turnos desta")
        void naoComparaComOutrasPessoas() throws Exception {
            // A regra é por pessoa: o supervisor pode estar no T2 ao mesmo tempo
            // que o João está no T1 sem que isso seja um problema.
            alocar(ShiftType.T1_SAB, null);

            mockMvc.perform(post("/api/v1/allocations")
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"editionScaleId":%d,"userId":%d,"shift":"T2_SAB"}"""
                            .formatted(escala.getId(), supervisor.getId())))
                    .andExpect(status().isOk());

            sincronizar();
            assertThat(shiftSchedulingRepository.findByEditionScaleIdOrderByIdAsc(escala.getId()))
                    .hasSize(2);
        }
    }

    @Nested
    @DisplayName("Situações aceites")
    class Aceites {

        @Test
        @DisplayName("T1 e T3 podem ser da mesma pessoa: só se tocam às 12h00")
        void aceitaTurnosSeguidos() throws Exception {
            // Quem acaba ao meio-dia entra no turno seguinte. Contar a fronteira
            // como sobreposição tornaria impossível cobrir T1 e T3 com uma só
            // pessoa, que é metade do que uma escala de fim de semana é.
            alocar(ShiftType.T1_SAB, null);

            mockMvc.perform(post("/api/v1/allocations")
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido(ShiftType.T3_SAB)))
                    .andExpect(status().isOk());

            sincronizar();
            assertThat(shiftSchedulingRepository
                    .findByEditionScaleIdAndUserIdOrderByIdAsc(escala.getId(), joao.getId()))
                    .hasSize(2);
        }

        @Test
        @DisplayName("T1 e T6 podem ser da mesma pessoa: têm as mesmas horas, mas dias diferentes")
        void aceitaSabadoEDomingo() throws Exception {
            alocar(ShiftType.T1_SAB, null);

            mockMvc.perform(post("/api/v1/allocations")
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido(ShiftType.T6_DOM)))
                    .andExpect(status().isOk());

            sincronizar();
            assertThat(shiftSchedulingRepository
                    .findByEditionScaleIdAndUserIdOrderByIdAsc(escala.getId(), joao.getId()))
                    .hasSize(2);
        }

        @Test
        @DisplayName("o mesmo turno em dois sábados diferentes é aceite")
        void aceitaMesmoTurnoEmDiasDiferentes() throws Exception {
            alocar(ShiftType.T1_SAB, SABADO_4_OUTUBRO);

            mockMvc.perform(post("/api/v1/allocations")
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido(ShiftType.T1_SAB, SABADO_11_OUTUBRO)))
                    .andExpect(status().isOk());

            sincronizar();
            assertThat(shiftSchedulingRepository
                    .findByEditionScaleIdAndUserIdOrderByIdAsc(escala.getId(), joao.getId()))
                    .hasSize(2);
        }

        @Test
        @DisplayName("alterar uma alocação sem mexer no turno continua a ser possível")
        void alterarSemMudarOTurno() throws Exception {
            // A alocação não entra em conflito consigo própria. Sem a exclusão,
            // nenhuma edição seria possível e a escala ficaria congelada depois
            // da primeira alocação.
            alocar(ShiftType.T1_SAB, null);
            ShiftScheduling existente = primeiraAlocacaoDeJoao();

            mockMvc.perform(put("/api/v1/allocations/" + existente.getId())
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido(ShiftType.T1_SAB)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(existente.getId()));

            sincronizar();
            assertThat(shiftSchedulingRepository
                    .findByEditionScaleIdAndUserIdOrderByIdAsc(escala.getId(), joao.getId()))
                    .hasSize(1);
        }

        @Test
        @DisplayName("depois de apagar a alocação que bloqueava, a nova é aceite")
        void apagarLibertaOTurno() throws Exception {
            // A regra olha para o que está gravado agora: um conflito criado por
            // uma alocação que já não existe deixou de ser um conflito.
            alocar(ShiftType.T1_SAB, null);
            ShiftScheduling aApagar = primeiraAlocacaoDeJoao();

            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .delete("/api/v1/allocations/" + aApagar.getId())
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post("/api/v1/allocations")
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(pedido(ShiftType.T2_SAB)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("a mesma pessoa pode ter turnos sobrepostos em escalas diferentes")
        void escalasDiferentesNaoConflitam() throws Exception {
            // O conflito é dentro da escala: quem cobre T1 num mês e T2 no mês
            // seguinte não está em dois lugares ao mesmo tempo.
            alocar(ShiftType.T1_SAB, null);
            EditionScale outra = criarEscala("Escala Novembro", supervisor);

            mockMvc.perform(post("/api/v1/allocations")
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"editionScaleId":%d,"userId":%d,"shift":"T2_SAB"}"""
                                    .formatted(outra.getId(), joao.getId())))
                    .andExpect(status().isOk());
        }
    }
}
