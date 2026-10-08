package service;

import domain.model.entities.EditionScale;
import domain.model.entities.Holiday;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.AssignmentType;
import domain.model.enums.EditionStatus;
import domain.model.enums.ShiftType;
import domain.model.enums.UserProfile;
import domain.repository.HolidayRepository;
import domain.repository.ShiftSchedulingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScaleRewardsService — crédito de folgas por turno trabalhado")
class ScaleRewardsServiceTest {

    @Mock
    private ShiftSchedulingRepository shiftSchedulingRepository;

    @Mock
    private HolidayRepository holidayRepository;

    @InjectMocks
    private ScaleRewardsService scaleRewardsService;

    private User utilizador(long id) {
        return new User(id, "Colaborador " + id, "c" + id + "@teste.com",
                "123456", UserProfile.ANALIST);
    }

    private EditionScale escala() {
        EditionScale escala = new EditionScale();
        escala.setId(10L);
        escala.setName("Escala Outubro");
        escala.setInitialDate(LocalDate.of(2025, 10, 1));
        escala.setEndDate(LocalDate.of(2025, 10, 31));
        escala.setStatus(EditionStatus.PUBLISHED);
        return escala;
    }

    private ShiftScheduling alocacao(User utilizador, ShiftType turno,
                                     AssignmentType... atribuicoes) {
        ShiftScheduling alocacao = new ShiftScheduling();
        alocacao.setEditionScale(escala());
        alocacao.setUser(utilizador);
        alocacao.setShift(turno);
        alocacao.setAssignments(atribuicoes.length == 0
                ? new java.util.LinkedHashSet<>()
                : new java.util.LinkedHashSet<>(List.of(atribuicoes)));
        return alocacao;
    }

    /**
     * Alocação com data concreta, como na escala publicada — é o que permite
     * comparar com as datas de feriado.
     */
    private ShiftScheduling alocacaoEm(LocalDate data, User utilizador, ShiftType turno,
                                       AssignmentType... atribuicoes) {
        ShiftScheduling alocacao = alocacao(utilizador, turno, atribuicoes);
        alocacao.setSpecificDate(data);
        return alocacao;
    }

    private void devolver(List<ShiftScheduling> alocacoes) {
        devolver(alocacoes, List.of());
    }

    private void devolver(List<ShiftScheduling> alocacoes, List<Holiday> feriados) {
        lenient().when(shiftSchedulingRepository.findByEditionScaleIdOrderByIdAsc(10L))
                .thenReturn(alocacoes);
        lenient().when(holidayRepository.findAll()).thenReturn(feriados);
    }

    @Nested
    @DisplayName("Regras por alocação")
    class Regras {

        @Test
        @DisplayName("turno de domingo rende meio dia (0.5)")
        void domingoRendeMeioDia() {
            var alocacao = alocacao(utilizador(1L), ShiftType.T6_DOM);

            assertThat(scaleRewardsService.creditoDe(alocacao))
                    .isEqualByComparingTo(BigDecimal.valueOf(0.5));
        }

        @Test
        @DisplayName("Celular da Marinas rende um dia inteiro, mesmo em sábado")
        void celularRendeUmDia() {
            var alocacao = alocacao(utilizador(1L), ShiftType.T1_SAB,
                    AssignmentType.CELULAR_MARINAS);

            assertThat(scaleRewardsService.creditoDe(alocacao))
                    .isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        @DisplayName("domingo com celular acumula as duas regras: 1.5 dias")
        void domingoComCelularAcumula() {
            var alocacao = alocacao(utilizador(1L), ShiftType.T6_DOM,
                    AssignmentType.CELULAR_MARINAS);

            assertThat(scaleRewardsService.creditoDe(alocacao))
                    .isEqualByComparingTo(BigDecimal.valueOf(1.5));
        }

        @Test
        @DisplayName("sábado sem celular não rende nada")
        void sabadoSemCelularNaoRende() {
            var alocacao = alocacao(utilizador(1L), ShiftType.T5_SAB);

            assertThat(scaleRewardsService.creditoDe(alocacao)).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Redes Sociais não é Celular da Marinas e não rende nada")
        void redesSociaisNaoRende() {
            var alocacao = alocacao(utilizador(1L), ShiftType.T1_SAB,
                    AssignmentType.REDES_SOCIAIS);

            assertThat(scaleRewardsService.creditoDe(alocacao)).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("nenhum outro turno de fim de semana rende, por mais atribuições que tenha")
        void sabadosNaoRendem() {
            for (ShiftType turno : ShiftType.values()) {
                if (turno == ShiftType.T6_DOM) {
                    continue;
                }
                var alocacao = alocacao(utilizador(1L), turno,
                        AssignmentType.REDES_SOCIAIS);

                assertThat(scaleRewardsService.creditoDe(alocacao))
                        .as("turno %s", turno)
                        .isEqualByComparingTo(BigDecimal.ZERO);
            }
        }
    }

    @Nested
    @DisplayName("Aplicação ao saldo")
    class Aplicacao {

        @Test
        @DisplayName("credita cada utilizador e devolve quantos receberam")
        void creditaCadaUtilizador() {
            User domingo = utilizador(1L);
            domingo.setAccumulatedLeaves(BigDecimal.ZERO);
            User celular = utilizador(2L);
            celular.setAccumulatedLeaves(BigDecimal.ZERO);
            User semDireito = utilizador(3L);
            semDireito.setAccumulatedLeaves(BigDecimal.ZERO);

            devolver(List.of(
                    alocacao(domingo, ShiftType.T6_DOM),
                    alocacao(celular, ShiftType.T1_SAB, AssignmentType.CELULAR_MARINAS),
                    alocacao(semDireito, ShiftType.T1_SAB)));

            int creditados = scaleRewardsService.aplicar(escala());

            assertThat(creditados).isEqualTo(2);
            assertThat(domingo.getAccumulatedLeaves()).isEqualByComparingTo("0.5");
            assertThat(celular.getAccumulatedLeaves()).isEqualByComparingTo("1.0");
            assertThat(semDireito.getAccumulatedLeaves()).isEqualByComparingTo("0.0");
        }

        @Test
        @DisplayName("as duas regras na mesma alocação entram uma só vez, como 1.5")
        void regrasCumulativasNumaAlocacao() {
            User utilizador = utilizador(1L);
            utilizador.setAccumulatedLeaves(BigDecimal.ZERO);

            devolver(List.of(alocacao(utilizador, ShiftType.T6_DOM,
                    AssignmentType.CELULAR_MARINAS)));

            scaleRewardsService.aplicar(escala());

            assertThat(utilizador.getAccumulatedLeaves()).isEqualByComparingTo("1.5");
        }

        @Test
        @DisplayName("várias alocações do mesmo utilizador somam num só crédito")
        void alocacoesDoMesmoUtilizadorSomam() {
            User utilizador = utilizador(1L);
            utilizador.setAccumulatedLeaves(BigDecimal.ZERO);

            devolver(List.of(
                    alocacao(utilizador, ShiftType.T6_DOM),
                    alocacao(utilizador, ShiftType.T6_DOM),
                    alocacao(utilizador, ShiftType.T1_SAB)));

            int creditados = scaleRewardsService.aplicar(escala());

            assertThat(creditados).isEqualTo(1);
            assertThat(utilizador.getAccumulatedLeaves()).isEqualByComparingTo("1.0");
        }

        @Test
        @DisplayName("acrescenta ao que já lá está, sem substituir o saldo")
        void somaAoSaldoExistente() {
            User utilizador = utilizador(1L);
            utilizador.setAccumulatedLeaves(new BigDecimal("2.0"));

            devolver(List.of(alocacao(utilizador, ShiftType.T6_DOM)));

            scaleRewardsService.aplicar(escala());

            assertThat(utilizador.getAccumulatedLeaves()).isEqualByComparingTo("2.5");
        }

        @Test
        @DisplayName("um registo antigo sem saldo é tratado como zero, não rebenta")
        void saldoNuloTratadoComoZero() {
            User utilizador = utilizador(1L);
            utilizador.setAccumulatedLeaves(null);

            devolver(List.of(alocacao(utilizador, ShiftType.T6_DOM)));

            scaleRewardsService.aplicar(escala());

            assertThat(utilizador.getAccumulatedLeaves()).isEqualByComparingTo("0.5");
        }

        @Test
        @DisplayName("escala sem trabalho de fim de semana não credita ninguém")
        void escalaSemDireitoNaoCredita() {
            User utilizador = utilizador(1L);
            utilizador.setAccumulatedLeaves(BigDecimal.ZERO);

            devolver(List.of(alocacao(utilizador, ShiftType.T1_SAB)));

            int creditados = scaleRewardsService.aplicar(escala());

            assertThat(creditados).isZero();
            assertThat(utilizador.getAccumulatedLeaves()).isEqualByComparingTo("0.0");
        }

        @Test
        @DisplayName("escala sem alocações devolve zero sem tocar em saldos")
        void escalaVazia() {
            User utilizador = utilizador(1L);
            utilizador.setAccumulatedLeaves(new BigDecimal("3.5"));

            devolver(List.of());

            int creditados = scaleRewardsService.aplicar(escala());

            assertThat(creditados).isZero();
            assertThat(utilizador.getAccumulatedLeaves()).isEqualByComparingTo("3.5");
        }
    }

    @Nested
    @DisplayName("Regra do feriado")
    class Feriado {

        private final LocalDate NATAL = LocalDate.of(2025, 12, 25);
        private final LocalDate ANO_VELHO = LocalDate.of(2025, 12, 31);

        @Test
        @DisplayName("cenário A — trabalhar num feriado rende 1.0 dia")
        void feriadoRendeUmDia() {
            User utilizador = utilizador(1L);
            utilizador.setAccumulatedLeaves(BigDecimal.ZERO);

            devolver(
                    List.of(alocacaoEm(NATAL, utilizador, ShiftType.T1_SAB)),
                    List.of(new Holiday(NATAL, "Natal")));

            int creditados = scaleRewardsService.aplicar(escala());

            assertThat(creditados).isEqualTo(1);
            assertThat(utilizador.getAccumulatedLeaves()).isEqualByComparingTo("1.0");
        }

        @Test
        @DisplayName("cenário B — feriado com Celular da Marinas acumula: 2.0 dias")
        void feriadoComCelularAcumula() {
            User utilizador = utilizador(1L);
            utilizador.setAccumulatedLeaves(BigDecimal.ZERO);

            devolver(
                    List.of(alocacaoEm(NATAL, utilizador, ShiftType.T1_SAB,
                            AssignmentType.CELULAR_MARINAS)),
                    List.of(new Holiday(NATAL, "Natal")));

            scaleRewardsService.aplicar(escala());

            assertThat(utilizador.getAccumulatedLeaves()).isEqualByComparingTo("2.0");
        }

        @Test
        @DisplayName("cenário C — dois subturnos no mesmo feriado valem 1.0, não 2.0")
        void doisSubturnosNoMesmoFeriadoValemUmDia() {
            User utilizador = utilizador(1L);
            utilizador.setAccumulatedLeaves(BigDecimal.ZERO);

            devolver(
                    List.of(
                            alocacaoEm(NATAL, utilizador, ShiftType.T1_SAB),
                            alocacaoEm(NATAL, utilizador, ShiftType.T2_SAB)),
                    List.of(new Holiday(NATAL, "Natal")));

            int creditados = scaleRewardsService.aplicar(escala());

            assertThat(creditados).isEqualTo(1);
            assertThat(utilizador.getAccumulatedLeaves()).isEqualByComparingTo("1.0");
        }

        @Test
        @DisplayName("feriados diferentes somam dia a dia")
        void feriadosDiferentesSomam() {
            User utilizador = utilizador(1L);
            utilizador.setAccumulatedLeaves(BigDecimal.ZERO);

            devolver(
                    List.of(
                            alocacaoEm(NATAL, utilizador, ShiftType.T1_SAB),
                            alocacaoEm(ANO_VELHO, utilizador, ShiftType.T1_SAB)),
                    List.of(new Holiday(NATAL, "Natal"), new Holiday(ANO_VELHO, "Ano Velho")));

            scaleRewardsService.aplicar(escala());

            assertThat(utilizador.getAccumulatedLeaves()).isEqualByComparingTo("2.0");
        }

        @Test
        @DisplayName("trabalho no próprio dia do feriado de outro utilizador não credita quem não trabalhou")
        void soCreditaQuemTrabalhou() {
            User quemTrabalhou = utilizador(1L);
            quemTrabalhou.setAccumulatedLeaves(BigDecimal.ZERO);
            User quemNaoTrabalhou = utilizador(2L);
            quemNaoTrabalhou.setAccumulatedLeaves(BigDecimal.ZERO);

            devolver(
                    List.of(alocacaoEm(NATAL, quemTrabalhou, ShiftType.T1_SAB)),
                    List.of(new Holiday(NATAL, "Natal")));

            int creditados = scaleRewardsService.aplicar(escala());

            assertThat(creditados).isEqualTo(1);
            assertThat(quemNaoTrabalhou.getAccumulatedLeaves()).isEqualByComparingTo("0.0");
        }

        @Test
        @DisplayName("sábado normal em dia de feriado não registado não rende nada")
        void sabadoSemFeriadoNaoRende() {
            User utilizador = utilizador(1L);
            utilizador.setAccumulatedLeaves(BigDecimal.ZERO);

            devolver(List.of(alocacaoEm(NATAL, utilizador, ShiftType.T1_SAB)), List.of());

            int creditados = scaleRewardsService.aplicar(escala());

            assertThat(creditados).isZero();
            assertThat(utilizador.getAccumulatedLeaves()).isEqualByComparingTo("0.0");
        }

        @Test
        @DisplayName("alocação sem data específica não conta como feriado, mesmo com o dia registado")
        void alocacaoSemDataNaoConta() {
            User utilizador = utilizador(1L);
            utilizador.setAccumulatedLeaves(BigDecimal.ZERO);

            ShiftScheduling semData = alocacao(utilizador, ShiftType.T1_SAB);
            devolver(List.of(semData), List.of(new Holiday(NATAL, "Natal")));

            int creditados = scaleRewardsService.aplicar(escala());

            assertThat(creditados).isZero();
            assertThat(utilizador.getAccumulatedLeaves()).isEqualByComparingTo("0.0");
        }

        @Test
        @DisplayName("o feriado é aplicado na aplicação ao saldo, não no crédito base da alocação")
        void creditoBaseNaoIncluiFeriado() {
            ShiftScheduling alocacao = alocacaoEm(NATAL, utilizador(1L), ShiftType.T1_SAB);

            assertThat(scaleRewardsService.creditoDe(alocacao)).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("domingo que é também feriado soma as duas regras: 1.5 dias")
        void domingoQueEReFeriadoAcumula() {
            User utilizador = utilizador(1L);
            utilizador.setAccumulatedLeaves(BigDecimal.ZERO);

            devolver(
                    List.of(alocacaoEm(NATAL, utilizador, ShiftType.T6_DOM)),
                    List.of(new Holiday(NATAL, "Natal")));

            scaleRewardsService.aplicar(escala());

            assertThat(utilizador.getAccumulatedLeaves()).isEqualByComparingTo("1.5");
        }
    }
}
