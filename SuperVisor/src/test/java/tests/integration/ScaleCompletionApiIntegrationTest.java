package tests.integration;

import domain.model.entities.EditionScale;
import domain.model.entities.Holiday;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.AssignmentType;
import domain.model.enums.EditionStatus;
import domain.model.enums.ShiftType;
import domain.model.enums.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tests.support.AbstractApiIntegrationTest;
import tests.support.TestFixtures;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fecho de escala com crédito automático de folgas.
 *
 * <p>As regras em teste são as do negócio: domingo (T6_DOM) = 0.5, Celular da
 * Marinas = 1.0, feriado trabalhado = 1.0 (uma vez por dia, por mais subturnos
 * que se façam), acumuláveis no mesmo turno, aplicadas uma única vez por
 * fecho. O caminho é o fim a fim — endpoint, serviço, saldo gravado na base —
 * porque é aí que a idempotência e a transação têm de aguentar: um saldo
 * creditado não se corrige com um revert.
 */
@DisplayName("Conclusão de escala com crédito de folgas — POST /api/v1/scales/{id}/complete")
class ScaleCompletionApiIntegrationTest extends AbstractApiIntegrationTest {

    private User supervisor;
    private String tokenSupervisor;

    private void prepararUtilizador() {
        supervisor = criarUsuario("Supervisora", "supervisora@teste.com", UserProfile.SUPERVISOR);
        tokenSupervisor = tokenService.gerarToken(supervisor);
    }

    private EditionScale escalaPublicada() {
        return criarEscala("Escala Outubro", supervisor, EditionStatus.PUBLISHED);
    }

    private ShiftScheduling alocar(EditionScale escala, User utilizador,
                                   ShiftType turno, AssignmentType... atribuicoes) {
        return shiftSchedulingRepository.saveAndFlush(TestFixtures.allocationCompleta(
                escala, utilizador, turno, null, List.of(atribuicoes), null, null));
    }

    private ShiftScheduling alocarEm(EditionScale escala, User utilizador,
                                     ShiftType turno, LocalDate data,
                                     AssignmentType... atribuicoes) {
        return shiftSchedulingRepository.saveAndFlush(TestFixtures.allocationCompleta(
                escala, utilizador, turno, data, List.of(atribuicoes), null, null));
    }

    private void concluir(EditionScale escala) throws Exception {
        mockMvc.perform(post("/api/v1/scales/" + escala.getId() + "/complete")
                        .header("Authorization", "Bearer " + tokenSupervisor))
                .andExpect(status().isNoContent());
        sincronizar();
    }

    private BigDecimal saldoDe(Long utilizadorId) {
        return userRepository.findById(utilizadorId).orElseThrow().getAccumulatedLeaves();
    }

    @Nested
    @DisplayName("Regras de crédito")
    class Regras {

        @Test
        @DisplayName("trabalhar o domingo de T6 credita 0.5 dias")
        void domingoCreditaMeioDia() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();
            User trabalhador = criarUsuario("Domingueiro", "domingo@teste.com", UserProfile.ANALIST);
            alocar(escala, trabalhador, ShiftType.T6_DOM);

            concluir(escala);

            assertThat(saldoDe(trabalhador.getId())).isEqualByComparingTo("0.5");
        }

        @Test
        @DisplayName("levar o Celular da Marinas credita 1.0 dia, mesmo num sábado")
        void celularCreditaUmDia() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();
            User trabalhador = criarUsuario("Celular", "celular@teste.com", UserProfile.ANALIST);
            alocar(escala, trabalhador, ShiftType.T1_SAB, AssignmentType.CELULAR_MARINAS);

            concluir(escala);

            assertThat(saldoDe(trabalhador.getId())).isEqualByComparingTo("1.0");
        }

        @Test
        @DisplayName("domingo com celular na mesma alocação credita 1.5 dias")
        void domingoComCelularCreditaUmECinco() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();
            User trabalhador = criarUsuario("Acumulador", "acumula@teste.com", UserProfile.ANALIST);
            alocar(escala, trabalhador, ShiftType.T6_DOM, AssignmentType.CELULAR_MARINAS);

            concluir(escala);

            assertThat(saldoDe(trabalhador.getId())).isEqualByComparingTo("1.5");
        }

        @Test
        @DisplayName("sábado sem celular não credita nada")
        void sabadoSemCelularNaoCredita() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();
            User trabalhador = criarUsuario("Sabadista", "sabado@teste.com", UserProfile.ANALIST);
            alocar(escala, trabalhador, ShiftType.T1_SAB, AssignmentType.REDES_SOCIAIS);

            concluir(escala);

            assertThat(saldoDe(trabalhador.getId())).isEqualByComparingTo("0.0");
        }

        @Test
        @DisplayName("vários domingos na mesma escala somam: dois turnos = 1.0 dia")
        void doisDomingosSomam() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();
            User trabalhador = criarUsuario("Frequente", "frequente@teste.com", UserProfile.ANALIST);
            alocar(escala, trabalhador, ShiftType.T6_DOM);
            alocar(escala, trabalhador, ShiftType.T6_DOM);

            concluir(escala);

            assertThat(saldoDe(trabalhador.getId())).isEqualByComparingTo("1.0");
        }

        @Test
        @DisplayName("acrescenta ao saldo que já existia, sem o substituir")
        void somaAoSaldoExistente() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();
            User trabalhador = criarUsuario("Poupanca", "poupanca@teste.com", UserProfile.ANALIST);
            trabalhador.setAccumulatedLeaves(new BigDecimal("2.0"));
            userRepository.saveAndFlush(trabalhador);
            alocar(escala, trabalhador, ShiftType.T6_DOM);

            concluir(escala);

            assertThat(saldoDe(trabalhador.getId())).isEqualByComparingTo("2.5");
        }

        @Test
        @DisplayName("só quem tem trabalho qualificado é creditado: os restantes ficam como estavam")
        void soTrabalhoQualificadoEhCreditado() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();
            User direito = criarUsuario("Direito", "direito@teste.com", UserProfile.ANALIST);
            User semDireito = criarUsuario("SemDireito", "semdireito@teste.com", UserProfile.ANALIST);
            alocar(escala, direito, ShiftType.T6_DOM);
            alocar(escala, semDireito, ShiftType.T2_SAB);

            concluir(escala);

            assertThat(saldoDe(direito.getId())).isEqualByComparingTo("0.5");
            assertThat(saldoDe(semDireito.getId())).isEqualByComparingTo("0.0");
        }

        @Test
        @DisplayName("trabalhar num feriado credita 1.0 dia")
        void feriadoCreditaUmDia() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();
            User trabalhador = criarUsuario("Feriadista", "feriado@teste.com", UserProfile.ANALIST);
            LocalDate natal = LocalDate.of(2025, 12, 25);
            holidayRepository.saveAndFlush(new Holiday(natal, "Natal"));
            alocarEm(escala, trabalhador, ShiftType.T1_SAB, natal);

            concluir(escala);

            assertThat(saldoDe(trabalhador.getId())).isEqualByComparingTo("1.0");
        }

        @Test
        @DisplayName("sábado em dia que não está registado como feriado não credita")
        void dataSemFeriadoNaoCredita() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();
            User trabalhador = criarUsuario("Comum", "comum@teste.com", UserProfile.ANALIST);
            alocarEm(escala, trabalhador, ShiftType.T1_SAB, LocalDate.of(2025, 10, 11));

            concluir(escala);

            assertThat(saldoDe(trabalhador.getId())).isEqualByComparingTo("0.0");
        }

        @Test
        @DisplayName("dois subturnos no mesmo feriado creditam 1.0 dia, não 2.0")
        void doisSubturnosNoMesmoFeriadoCreditamUmDia() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();
            User trabalhador = criarUsuario("Feriadista", "feriado@teste.com", UserProfile.ANALIST);
            LocalDate natal = LocalDate.of(2025, 12, 25);
            holidayRepository.saveAndFlush(new Holiday(natal, "Natal"));
            alocarEm(escala, trabalhador, ShiftType.T1_SAB, natal);
            alocarEm(escala, trabalhador, ShiftType.T2_SAB, natal);

            concluir(escala);

            assertThat(saldoDe(trabalhador.getId())).isEqualByComparingTo("1.0");
        }

        @Test
        @DisplayName("feriado com Celular da Marinas no mesmo turno credita 2.0 dias")
        void feriadoComCelularCreditaDois() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();
            User trabalhador = criarUsuario("Feriadista", "feriado@teste.com", UserProfile.ANALIST);
            LocalDate natal = LocalDate.of(2025, 12, 25);
            holidayRepository.saveAndFlush(new Holiday(natal, "Natal"));
            alocarEm(escala, trabalhador, ShiftType.T1_SAB, natal, AssignmentType.CELULAR_MARINAS);

            concluir(escala);

            assertThat(saldoDe(trabalhador.getId())).isEqualByComparingTo("2.0");
        }
    }

    @Nested
    @DisplayName("Idempotência")
    class Idempotencia {

        @Test
        @DisplayName("concluir duas vezes devolve 204 nas duas e não dobra o saldo")
        void concluirDuasVezesNaoDobra() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();
            User trabalhador = criarUsuario("Repetidor", "repetidor@teste.com", UserProfile.ANALIST);
            alocar(escala, trabalhador, ShiftType.T6_DOM);

            concluir(escala);
            assertThat(saldoDe(trabalhador.getId())).isEqualByComparingTo("0.5");

            // O segundo pedido é uma repetição por falha de rede, não um erro:
            // responde 204 e não credita nada de novo.
            concluir(escala);

            assertThat(saldoDe(trabalhador.getId())).isEqualByComparingTo("0.5");
            assertThat(editionScaleRepository.findById(escala.getId()).orElseThrow())
                    .extracting(EditionScale::getStatus, EditionScale::isRewardsProcessed)
                    .isEqualTo(java.util.List.of(EditionStatus.COMPLETED, true));
        }

        @Test
        @DisplayName("a flag de crédito fica registada na própria escala")
        void flagRegistada() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();

            concluir(escala);

            assertThat(editionScaleRepository.findById(escala.getId()).orElseThrow()
                    .isRewardsProcessed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Exposição no frontend")
    class ExibicaoNoFrontend {

        @Test
        @DisplayName("após a conclusão, /users/me devolve o saldo creditado — é o que o cartão «O seu saldo de folgas» lê")
        void meDevolveSaldoCreditado() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();
            alocar(escala, supervisor, ShiftType.T6_DOM);

            concluir(escala);

            mockMvc.perform(get("/api/v1/users/me")
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accumulatedLeaves").value(0.5))
                    .andExpect(jsonPath("$.pendingCompensationDays").value(0));
        }

        @Test
        @DisplayName("sem trabalho qualificado o campo vem a zero, nunca ausente nem null")
        void meDevolveZeroNuncaNulo() throws Exception {
            prepararUtilizador();

            mockMvc.perform(get("/api/v1/users/me")
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accumulatedLeaves").value(0));
        }

        @Test
        @DisplayName("a lista de utilizadores também traz o saldo creditado — é a origem dos badges da tabela")
        void listaExpoeSaldoCreditado() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();
            User trabalhador = criarUsuario("Domingueiro", "domingo@teste.com", UserProfile.ANALIST);
            alocar(escala, trabalhador, ShiftType.T6_DOM);

            concluir(escala);

            var resposta = mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isOk())
                    .andReturn();

            // O filtro do JsonPath devolve uma lista e não um valor, pelo que a
            // leitura é feita aqui: procura-se a linha do trabalhador pelas
            // suas credenciais e lê-se o saldo como número decimal.
            var lista = objectMapper.readTree(
                    resposta.getResponse().getContentAsString());
            var doTrabalhador = lista.findValuesAsText("id").indexOf(
                    String.valueOf(trabalhador.getId()));

            assertThat(doTrabalhador).isNotNegative();
            var registo = lista.get(doTrabalhador);
            assertThat(registo.get("accumulatedLeaves").decimalValue())
                    .isEqualByComparingTo("0.5");
            assertThat(registo.get("pendingCompensationDays").decimalValue())
                    .isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("Estados e erros")
    class Estados {

        @Test
        @DisplayName("escala em rascunho devolve 400 e não credita ninguém")
        void rascunhoDevolve400() throws Exception {
            prepararUtilizador();
            var escala = criarEscala("Escala Rascunho", supervisor, EditionStatus.DRAFT);
            User trabalhador = criarUsuario("Rascunho", "rascunho@teste.com", UserProfile.ANALIST);
            alocar(escala, trabalhador, ShiftType.T6_DOM);

            mockMvc.perform(post("/api/v1/scales/" + escala.getId() + "/complete")
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Apenas escalas publicadas podem ser concluídas"));
            sincronizar();

            assertThat(saldoDe(trabalhador.getId())).isEqualByComparingTo("0.0");
            assertThat(editionScaleRepository.findById(escala.getId()).orElseThrow().getStatus())
                    .isEqualTo(EditionStatus.DRAFT);
        }

        @Test
        @DisplayName("escala inexistente devolve 400 com o corpo de erro estruturado")
        void escalaInexistente() throws Exception {
            prepararUtilizador();

            mockMvc.perform(post("/api/v1/scales/9999/complete")
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Edição de Escala não encontrada."));
        }

        @Test
        @DisplayName("publicar depois de concluída continua a ser recusado")
        void concluidaNaoVoltaAPublicar() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();

            concluir(escala);

            mockMvc.perform(post("/api/v1/scales/" + escala.getId() + "/publish")
                            .header("Authorization", "Bearer " + tokenSupervisor))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Apenas escalas em rascunho podem ser publicadas"));
        }
    }

    @Nested
    @DisplayName("Autorização")
    class Autorizacao {

        @Test
        @DisplayName("analista não pode concluir: 403 com a mensagem de perfil")
        void analistaDevolve403() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();
            var analista = criarUsuario("João", "joao@teste.com", UserProfile.ANALIST);

            mockMvc.perform(post("/api/v1/scales/" + escala.getId() + "/complete")
                            .header("Authorization", "Bearer " + tokenService.gerarToken(analista)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message")
                            .value("Apenas o perfil SUPERVISOR pode executar esta operação."));

            sincronizar();
            assertThat(editionScaleRepository.findById(escala.getId()).orElseThrow().getStatus())
                    .isEqualTo(EditionStatus.PUBLISHED);
        }

        @Test
        @DisplayName("sem token devolve 401")
        void semTokenDevolve401() throws Exception {
            prepararUtilizador();
            var escala = escalaPublicada();

            mockMvc.perform(post("/api/v1/scales/" + escala.getId() + "/complete")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }
    }
}
