package tests.integration;

import domain.model.entities.User;
import domain.model.enums.TeamGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tests.support.AbstractApiIntegrationTest;
import tests.support.TestFixtures;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Presencialidade e compensação contra a base de dados a série.
 *
 * <p>Estes testes cobrem o circuito completo — a falta escrita na grelha, a
 * dívida que ela gera no perfil do colaborador, o ajuste manual do supervisor
 * e a folga que consome o saldo de folgas — porque o que está em jogo não é a
 * resposta HTTP: é o número que fica gravado em quem faltou.
 *
 * <p>As permissões vêm em cada bloco de propósito. Registar faltas e mexer
 * saldos são as duas escritas que movem dinheiro de horas entre pessoas, e um
 * 403 que só aparecesse num teste próprio não impedia que uma das operações
 * ficasse aberta por esquecimento na outra.
 */
@DisplayName("Presencialidade e compensação — API")
class AttendanceCompensationApiIntegrationTest extends AbstractApiIntegrationTest {

    /** Segunda-feira da semana ISO 13 de 2026, que é ímpar. */
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 3, 23);

    private User admin;
    private User ana;

    private String tokenAdmin;
    private String tokenAna;

    @BeforeEach
    void prepararCenario() {
        admin = criarSupervisor();
        ana = criarComEquipa("Ana", "ana@teste.com");

        tokenAdmin = tokenService.gerarToken(admin);
        tokenAna = tokenService.gerarToken(ana);

        gerarEscala();
    }

    private User criarSupervisor() {
        var base = TestFixtures.supervisor();
        base.setPassword(passwordEncoder.encode(TestFixtures.RAW_PASSWORD));
        return userRepository.saveAndFlush(base);
    }

    private User criarComEquipa(String nome, String email) {
        var base = TestFixtures.userInTeam(nome, email, TestFixtures.analyst().getProfile(),
                TeamGroup.EQUIPE_A);
        base.setPassword(passwordEncoder.encode(TestFixtures.RAW_PASSWORD));
        return userRepository.saveAndFlush(base);
    }

    private void gerarEscala() {
        try {
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo("dataReferencia", SEGUNDA)))
                    .andExpect(status().isOk());
        } catch (Exception erro) {
            throw new IllegalStateException(erro);
        }
    }

    private String corpo(Object... paresChaveValor) {
        var objeto = new LinkedHashMap<String, Object>();
        for (int i = 0; i < paresChaveValor.length; i += 2) {
            objeto.put((String) paresChaveValor[i], paresChaveValor[i + 1]);
        }
        try {
            return objectMapper.writeValueAsString(objeto);
        } catch (Exception erro) {
            throw new IllegalStateException(erro);
        }
    }

    private Long idDaCelula(User user, LocalDate dia) {
        return workModalityScheduleRepository.listarIntervalo(dia, dia).stream()
                .filter(celula -> celula.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    /** Saldo recarregado da base, e não a cópia em memória do contexto. */
    private BigDecimal saldoCompensacao(User user) {
        sincronizar();
        return userRepository.findById(user.getId()).orElseThrow().getPendingCompensationDays();
    }

    private BigDecimal saldoDeFolgas(User user) {
        sincronizar();
        return userRepository.findById(user.getId()).orElseThrow().getAccumulatedLeaves();
    }

    private void registarPresenca(String token, Long celulaId, String estado, String observacao)
            throws Exception {
        mockMvc.perform(patch("/api/v1/work-modality-schedules/" + celulaId + "/attendance")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("attendanceStatus", estado, "notes", observacao)))
                .andExpect(status().isOk());
    }

    @Nested
    @DisplayName("Registrar presença")
    class Presenca {

        @Test
        @DisplayName("uma falta inteira fica na célula e gera um dia de dívida")
        void faltaInteiraGeraUmDia() throws Exception {
            Long celula = idDaCelula(ana, SEGUNDA);

            mockMvc.perform(patch("/api/v1/work-modality-schedules/" + celula + "/attendance")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo("attendanceStatus", "ABSENT_FULL")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.attendanceStatus").value("ABSENT_FULL"))
                    .andExpect(jsonPath("$.attendanceLabel").exists());

            assertThat(saldoCompensacao(ana)).isEqualByComparingTo("1");
        }

        @Test
        @DisplayName("uma falta de manhã custa meio dia, e não um")
        void faltaDeManhaCustaMeioDia() throws Exception {
            registarPresenca(tokenAdmin, idDaCelula(ana, SEGUNDA), "ABSENT_MORNING", "Consulta");

            assertThat(saldoCompensacao(ana)).isEqualByComparingTo("0.5");
        }

        @Test
        @DisplayName("corrigir a falta para presente devolve o dia: o saldo volta a zero")
        void correcaoDevolveODia() throws Exception {
            Long celula = idDaCelula(ana, SEGUNDA);
            registarPresenca(tokenAdmin, celula, "ABSENT_FULL", null);
            assertThat(saldoCompensacao(ana)).isEqualByComparingTo("1");

            registarPresenca(tokenAdmin, celula, "PRESENT", null);

            // A reversão é o que separa o sistema de um contador de faltas:
            // um engano corrigido na grelha não pode deixar dívida atrás.
            assertThat(saldoCompensacao(ana)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("a observação fica gravada e volta na lista da grelha")
        void observacaoFicaGravada() throws Exception {
            Long celula = idDaCelula(ana, SEGUNDA);
            registarPresenca(tokenAdmin, celula, "ABSENT_FULL", "  Atestado entregue  ");

            sincronizar();

            mockMvc.perform(get("/api/v1/work-modality-schedules")
                            .header("Authorization", "Bearer " + tokenAna)
                            .param("inicio", SEGUNDA.toString())
                            .param("fim", SEGUNDA.plusDays(4).toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].attendanceStatus").value("ABSENT_FULL"))
                    .andExpect(jsonPath("$[0].attendanceLabel").exists())
                    .andExpect(jsonPath("$[0].notes").value("Atestado entregue"));
        }

        @Test
        @DisplayName("o analista é recusado com 403, e a dívida não nasce")
        void analistaERecusado() throws Exception {
            mockMvc.perform(patch("/api/v1/work-modality-schedules/"
                            + idDaCelula(ana, SEGUNDA) + "/attendance")
                            .header("Authorization", "Bearer " + tokenAna)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo("attendanceStatus", "ABSENT_FULL")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message")
                            .value("Apenas o perfil SUPERVISOR pode executar esta operação."));

            assertThat(saldoCompensacao(ana)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("sem token, é 401")
        void semToken() throws Exception {
            mockMvc.perform(patch("/api/v1/work-modality-schedules/"
                            + idDaCelula(ana, SEGUNDA) + "/attendance")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo("attendanceStatus", "ABSENT_FULL")))
                    .andExpect(status().isUnauthorized());

            assertThat(saldoCompensacao(ana)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("uma célula que não existe é 400, com o motivo à vista")
        void celulaInexistente() throws Exception {
            mockMvc.perform(patch("/api/v1/work-modality-schedules/999999/attendance")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo("attendanceStatus", "ABSENT_FULL")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Registo de presencialidade não encontrado."));
        }

        @Test
        @DisplayName("sem o estado de presença, é 400")
        void semEstado() throws Exception {
            mockMvc.perform(patch("/api/v1/work-modality-schedules/"
                            + idDaCelula(ana, SEGUNDA) + "/attendance")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Ajuste manual de compensação")
    class Compensacao {

        @Test
        @DisplayName("o supervisor acrescenta dívida e vê o saldo atualizado na resposta")
        void acrescentaDivida() throws Exception {
            mockMvc.perform(patch("/api/v1/users/" + ana.getId() + "/compensation")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo("deltaDays", 2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ana.getId()))
                    .andExpect(jsonPath("$.pendingCompensationDays").value(2));

            assertThat(saldoCompensacao(ana)).isEqualByComparingTo("2");
        }

        @Test
        @DisplayName("um ajuste que excede a dívida deixa o saldo em zero, e não negativo")
        void abateAteZero() throws Exception {
            registarPresenca(tokenAdmin, idDaCelula(ana, SEGUNDA), "ABSENT_FULL", null);
            assertThat(saldoCompensacao(ana)).isEqualByComparingTo("1");

            mockMvc.perform(patch("/api/v1/users/" + ana.getId() + "/compensation")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo("deltaDays", -5)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pendingCompensationDays").value(0));

            assertThat(saldoCompensacao(ana)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("o analista é recusado com 403")
        void analistaERecusado() throws Exception {
            mockMvc.perform(patch("/api/v1/users/" + ana.getId() + "/compensation")
                            .header("Authorization", "Bearer " + tokenAna)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo("deltaDays", 1)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message")
                            .value("Apenas o perfil SUPERVISOR pode executar esta operação."));

            assertThat(saldoCompensacao(ana)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("sem token, é 401")
        void semToken() throws Exception {
            mockMvc.perform(patch("/api/v1/users/" + ana.getId() + "/compensation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo("deltaDays", 1)))
                    .andExpect(status().isUnauthorized());

            assertThat(saldoCompensacao(ana)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("um utilizador inexistente é 400")
        void utilizadorInexistente() throws Exception {
            mockMvc.perform(patch("/api/v1/users/999999/compensation")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo("deltaDays", 1)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Usuário não encontrado."));
        }
    }

    @Nested
    @DisplayName("O saldo de folgas")
    class SaldoDeFolgas {

        private static final LocalDate INICIO = LocalDate.of(2026, 3, 2);
        private static final LocalDate FIM = LocalDate.of(2026, 3, 6);

        private String folga(Long userId, LocalDate inicio, LocalDate fim, String duracao) {
            var corpo = new LinkedHashMap<String, Object>();
            corpo.put("userId", userId);
            corpo.put("startDate", inicio);
            corpo.put("endDate", fim);
            corpo.put("reason", "Férias");
            if (duracao != null) {
                corpo.put("leaveDuration", duracao);
            }
            try {
                return objectMapper.writeValueAsString(corpo);
            } catch (Exception erro) {
                throw new IllegalStateException(erro);
            }
        }

        @Test
        @DisplayName("criar uma folga debita o saldo, e o perfil atual mostra o resultado")
        void criarDebitaOSaldo() throws Exception {
            mockMvc.perform(post("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(ana.getId(), INICIO, FIM, null)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.costDays").value(5))
                    .andExpect(jsonPath("$.leaveDuration").value("FULL_DAY"));

            assertThat(saldoDeFolgas(ana)).isEqualByComparingTo("-5");

            // O cartão do topo da página de folgas lê o perfil, por isso é
            // ali que o saldo tem de aparecer.
            mockMvc.perform(get("/api/v1/users/me")
                            .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accumulatedLeaves").value(-5));
        }

        @Test
        @DisplayName("apagar a folga devolve os dias ao saldo")
        void apagarDevolveOSaldo() throws Exception {
            var resposta = mockMvc.perform(post("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(ana.getId(), INICIO, FIM, null)))
                    .andExpect(status().isCreated())
                    .andReturn();

            long folgaId = objectMapper.readTree(resposta.getResponse().getContentAsString())
                    .get("id").asLong();

            mockMvc.perform(delete("/api/v1/leaves/" + folgaId)
                            .header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(status().isNoContent());

            assertThat(saldoDeFolgas(ana)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("uma folga de meio dia num só dia consome meio dia")
        void folgaDeMeioDia() throws Exception {
            mockMvc.perform(post("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(ana.getId(), INICIO, INICIO, "MORNING_SHIFT")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.costDays").value(0.5))
                    .andExpect(jsonPath("$.leaveDurationLabel").exists());

            assertThat(saldoDeFolgas(ana)).isEqualByComparingTo("-0.5");
        }

        @Test
        @DisplayName("uma folga de meio dia em vários dias é recusada com 400")
        void folgaParcialEmVariosDias() throws Exception {
            mockMvc.perform(post("/api/v1/leaves")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(folga(ana.getId(), INICIO, FIM, "AFTERNOON_SHIFT")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Uma folga de meia jornada tem de ser num único dia: "
                                    + "escolha o mesmo dia de início e de fim."));

            assertThat(saldoDeFolgas(ana)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("a lista de utilizadores traz os dois saldos, para os cartões da página")
        void listaDeUtilizadoresTrazOsSaldos() throws Exception {
            registarPresenca(tokenAdmin, idDaCelula(ana, SEGUNDA), "ABSENT_FULL", null);

            // A lista vem ordenada por nome e o supervisor também lá está, por
            // isso a procura é pelo id: afirmar sobre a primeira linha seria
            // afirmar sobre quem chegar primeiro alfabeticamente.
            var lista = objectMapper.readTree(mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());

            var linha = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
            lista.forEach(linha::add);
            var daAna = linha.stream()
                    .filter(no -> no.get("id").asLong() == ana.getId())
                    .findFirst()
                    .orElseThrow();

            assertThat(daAna.get("pendingCompensationDays").decimalValue())
                    .isEqualByComparingTo("1");
            assertThat(daAna.get("accumulatedLeaves").decimalValue())
                    .isEqualByComparingTo("0");
        }
    }
}
