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
import tests.support.TestFixtures;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Relatório de cobertura e conflitos, com a aplicação inteira carregada contra o
 * PostgreSQL.
 *
 * <p>Os testes criam as situações incoerentes por baixo da aplicação, com o
 * repositório, e não pela API. É deliberado: é o que o relatório existe para
 * apanhar. Pela API nenhuma delas se consegue montar, porque a regra de escrita
 * recusa a sobreposição e a folga é registada noutro sítio — e um teste que só
 * soubesse montar incoerências pela API estaria a testar que a API recusa, que
 * já está feito noutro lado.
 */
@DisplayName("Relatório de cobertura — API")
class ScaleCoverageApiIntegrationTest extends AbstractApiIntegrationTest {

    private User supervisor;
    private User analista;
    private String tokenSupervisor;
    private String tokenAnalista;
    private EditionScale escala;

    @BeforeEach
    void preparar() {
        supervisor = criarUsuario("Administrador", "admin@teste.com", UserProfile.SUPERVISOR);
        analista = criarUsuario("Ana", "ana@teste.com", UserProfile.ANALIST);
        tokenSupervisor = tokenService.gerarToken(supervisor);
        tokenAnalista = tokenService.gerarToken(analista);
        escala = criarEscala("Escala Outubro", supervisor);
    }

    /** Grava uma alocação por baixo da aplicação, contornando a regra de escrita. */
    private ShiftScheduling alocacaoIlegitima(User utilizador, ShiftType turno) {
        ShiftScheduling a = TestFixtures.allocation(escala, utilizador, turno, null);
        return shiftSchedulingRepository.saveAndFlush(a);
    }

    @Nested
    @DisplayName("Leitura do relatório")
    class Leitura {

        @Test
        @DisplayName("uma escala vazia vem com todos os slots por cobrir")
        void escalaVazia() throws Exception {
            mockMvc.perform(get("/api/v1/scales/" + escala.getId() + "/coverage")
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isOk())
                    // Outubro de 2025: quatro sábados com cinco turnos e quatro
                    // domingos com um.
                    .andExpect(jsonPath("$.totalSlots").value(24))
                    .andExpect(jsonPath("$.coveredSlots").value(0))
                    .andExpect(jsonPath("$.uncoveredSlots").value(24))
                    .andExpect(jsonPath("$.coveragePercent").value(0))
                    .andExpect(jsonPath("$.allocationsCount").value(0))
                    .andExpect(jsonPath("$.overlaps").isEmpty())
                    .andExpect(jsonPath("$.leaveConflicts").isEmpty())
                    .andExpect(jsonPath("$.temConflitos").value(false));
        }

        @Test
        @DisplayName("devolve o nome e o período da escala, para o relatório se identificar sozinho")
        void devolveIdentificacaoDaEscala() throws Exception {
            mockMvc.perform(get("/api/v1/scales/" + escala.getId() + "/coverage")
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.scaleId").value(escala.getId().intValue()))
                    .andExpect(jsonPath("$.scaleName").value("Escala Outubro"))
                    .andExpect(jsonPath("$.initialDate").value("2025-10-01"))
                    .andExpect(jsonPath("$.endDate").value("2025-10-31"));
        }

        @Test
        @DisplayName("conta a cobertura à medida que as alocações entram")
        void contaCoberturaReal() throws Exception {
            criarAlocacao(escala, analista, ShiftType.T1_SAB);
            criarAlocacao(escala, supervisor, ShiftType.T6_DOM);

            mockMvc.perform(get("/api/v1/scales/" + escala.getId() + "/coverage")
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.allocationsCount").value(2))
                    // Quatro sábados de T1 e quatro domingos de T6.
                    .andExpect(jsonPath("$.coveredSlots").value(8))
                    .andExpect(jsonPath("$.uncoveredSlots").value(16))
                    .andExpect(jsonPath("$.coveragePercent").value(33))
                    .andExpect(jsonPath("$.slots[?(@.date == '2025-10-04' && @.shift == 'T1')].people[0]")
                            .value("Ana"));
        }

        @Test
        @DisplayName("apanha a sobreposição que a aplicação não deixaria criar")
        void apanhaSobreposicaoLegada() throws Exception {
            alocacaoIlegitima(analista, ShiftType.T1_SAB);
            alocacaoIlegitima(analista, ShiftType.T2_SAB);

            mockMvc.perform(get("/api/v1/scales/" + escala.getId() + "/coverage")
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overlaps").isArray())
                    .andExpect(jsonPath("$.overlaps.length()").value(1))
                    .andExpect(jsonPath("$.overlaps[0].userName").value("Ana"))
                    .andExpect(jsonPath("$.overlaps[0].firstShift").value("T1"))
                    .andExpect(jsonPath("$.overlaps[0].secondShift").value("T2"))
                    .andExpect(jsonPath("$.overlaps[0].firstInterval").value("08h00-12h00"))
                    .andExpect(jsonPath("$.overlaps[0].secondInterval").value("11h00-15h00"))
                    .andExpect(jsonPath("$.overlaps[0].affectedDates.length()").value(4))
                    .andExpect(jsonPath("$.overlaps[0].affectedDates[0]").value("2025-10-04"))
                    .andExpect(jsonPath("$.temConflitos").value(true));
        }

        @Test
        @DisplayName("apanha a alocação que calhou a um dia de folga")
        void apanhaAlocacaoEmFolga() throws Exception {
            criarAlocacao(escala, analista, ShiftType.T1_SAB);
            userLeaveRepository.saveAndFlush(
                    TestFixtures.leave(analista, LocalDate.of(2025, 10, 11)));

            mockMvc.perform(get("/api/v1/scales/" + escala.getId() + "/coverage")
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.leaveConflicts.length()").value(1))
                    .andExpect(jsonPath("$.leaveConflicts[0].userName").value("Ana"))
                    .andExpect(jsonPath("$.leaveConflicts[0].date").value("2025-10-11"))
                    .andExpect(jsonPath("$.temConflitos").value(true));
        }

        @Test
        @DisplayName("o motivo da folga não sai, porque o relatório é visível a qualquer autenticação")
        void naoExpoeOMotivoDaFolga() throws Exception {
            criarAlocacao(escala, analista, ShiftType.T1_SAB);
            userLeaveRepository.saveAndFlush(TestFixtures.leave(
                    analista, LocalDate.of(2025, 10, 11), LocalDate.of(2025, 10, 11),
                    "Motivo reservado"));

            var resultado = mockMvc
                    .perform(get("/api/v1/scales/" + escala.getId() + "/coverage")
                            .header("Authorization", "Bearer " + tokenAnalista))
                    .andExpect(status().isOk())
                    .andReturn();

            org.assertj.core.api.Assertions.assertThat(
                    resultado.getResponse().getContentAsString())
                    .doesNotContain("Motivo reservado");
        }

        @Test
        @DisplayName("avisa quando alguém desativado continua escalado")
        void avisaDesativadoEscalado() throws Exception {
            criarAlocacao(escala, analista, ShiftType.T1_SAB);
            mockMvc.perform(patch("/api/v1/users/" + analista.getId() + "/estado")
                            .header("Authorization", "Bearer " + tokenSupervisor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"active\":false}"))
                    .andExpect(status().isOk());
            sincronizar();

            mockMvc.perform(get("/api/v1/scales/" + escala.getId() + "/coverage")
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.inactivePeopleScheduled").value(1));
        }

        @Test
        @DisplayName("uma escala com datas invertidas dá zeros, e não exception")
        void escalaComDatasInvertidas() throws Exception {
            EditionScale invertida = criarEscala("Escala Invertida", supervisor);
            invertida.setInitialDate(LocalDate.of(2025, 10, 31));
            invertida.setEndDate(LocalDate.of(2025, 10, 1));
            editionScaleRepository.saveAndFlush(invertida);

            mockMvc.perform(get("/api/v1/scales/" + invertida.getId() + "/coverage")
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSlots").value(0))
                    .andExpect(jsonPath("$.coveragePercent").value(0))
                    .andExpect(jsonPath("$.slots").isEmpty());
        }

        @Test
        @DisplayName("uma escala que não existe dá 400 com mensagem")
        void escalaInexistente() throws Exception {
            mockMvc.perform(get("/api/v1/scales/999999/coverage")
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Edição de Escala não encontrada."));
        }
    }

    @Nested
    @DisplayName("Autorização e rotas")
    class Rotas {

        @Test
        @DisplayName("quem está a ver a escala vê também o que está errado nela")
        void analistTambemLe() throws Exception {
            mockMvc.perform(get("/api/v1/scales/" + escala.getId() + "/coverage")
                            .header("Authorization", "Bearer " + tokenAnalista))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("sem token é 401")
        void semTokenDa401() throws Exception {
            mockMvc.perform(get("/api/v1/scales/" + escala.getId() + "/coverage"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("o relatório não invade a rota de detalhe da escala")
        void detalheContinuaAResponder() throws Exception {
            mockMvc.perform(get("/api/v1/scales/" + escala.getId())
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Escala Outubro"))
                    .andExpect(jsonPath("$.slots").doesNotExist());
        }

        @Test
        @DisplayName("um caminho que não é um número continua a dar 404, não 400")
        void caminhoNaoNumericoDa404() throws Exception {
            mockMvc.perform(get("/api/v1/scales/abc/coverage")
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isNotFound());
        }
    }
}
