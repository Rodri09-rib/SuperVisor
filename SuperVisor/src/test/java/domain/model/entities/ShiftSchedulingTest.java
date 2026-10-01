package domain.model.entities;

import domain.model.enums.AllocationStatus;
import domain.model.enums.AssignmentType;
import domain.model.enums.ShiftType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Entidade ShiftScheduling")
class ShiftSchedulingTest {

    @Test
    @DisplayName("nova alocação nasce com o estado de aceitação PENDING")
    void estadoDeAceitacaoPadrao() {
        ShiftScheduling allocation = new ShiftScheduling();

        assertThat(allocation.getAnalystAcceptanceStatus()).isEqualTo(AllocationStatus.PENDING);
    }

    @Test
    @DisplayName("nova alocação nasce sem atribuições, para não compartilhar a coleção")
    void atribuicoesPadrao() {
        ShiftScheduling allocation = new ShiftScheduling();

        assertThat(allocation.getAssignments()).isEmpty();
    }

    @Test
    @DisplayName("construtor completo preserva todos os campos")
    void construtorCompleto() {
        EditionScale scale = new EditionScale();
        User user = new User();
        LocalDate date = LocalDate.of(2025, 10, 15);

        ShiftScheduling allocation = new ShiftScheduling(
                5L, scale, ShiftType.T3_SAB, user, date, AllocationStatus.ACCEPTED);

        assertThat(allocation.getId()).isEqualTo(5L);
        assertThat(allocation.getEditionScale()).isSameAs(scale);
        assertThat(allocation.getShift()).isEqualTo(ShiftType.T3_SAB);
        assertThat(allocation.getUser()).isSameAs(user);
        assertThat(allocation.getSpecificDate()).isEqualTo(date);
        assertThat(allocation.getAnalystAcceptanceStatus()).isEqualTo(AllocationStatus.ACCEPTED);
    }

    @Test
    @DisplayName("setters cobrem todos os campos")
    void setters() {
        ShiftScheduling allocation = new ShiftScheduling();
        EditionScale scale = new EditionScale();
        User user = new User();

        allocation.setId(1L);
        allocation.setEditionScale(scale);
        allocation.setUser(user);
        allocation.setShift(ShiftType.T4_SAB);
        allocation.setSpecificDate(LocalDate.of(2025, 10, 1));
        allocation.setAnalystAcceptanceStatus(AllocationStatus.REJECTED);

        assertThat(allocation.getId()).isEqualTo(1L);
        assertThat(allocation.getEditionScale()).isSameAs(scale);
        assertThat(allocation.getUser()).isSameAs(user);
        assertThat(allocation.getShift()).isEqualTo(ShiftType.T4_SAB);
        assertThat(allocation.getSpecificDate()).isEqualTo(LocalDate.of(2025, 10, 1));
        assertThat(allocation.getAnalystAcceptanceStatus()).isEqualTo(AllocationStatus.REJECTED);
    }

    @Test
    @DisplayName("as atribuições especiais são guardadas tal como foram informadas")
    void atribuicoesGuardadas() {
        ShiftScheduling allocation = new ShiftScheduling();

        allocation.setAssignments(Set.of(AssignmentType.REDES_SOCIAIS, AssignmentType.CELULAR_MARINAS));

        assertThat(allocation.getAssignments())
                .containsExactlyInAnyOrder(AssignmentType.REDES_SOCIAIS, AssignmentType.CELULAR_MARINAS);
    }

    @Test
    @DisplayName("definir atribuições a null deixa a coleção vazia, não nula")
    void atribuicoesNulas() {
        ShiftScheduling allocation = new ShiftScheduling();

        allocation.setAssignments(null);

        assertThat(allocation.getAssignments()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("horário especial só é considerado quando início e fim existem")
    void horarioCustomizadoCompleto() {
        ShiftScheduling allocation = new ShiftScheduling();
        allocation.setShift(ShiftType.T6_DOM);

        assertThat(allocation.temHorarioCustomizado()).isFalse();

        allocation.setCustomStartTime(LocalTime.of(10, 30));
        assertThat(allocation.temHorarioCustomizado()).isFalse();

        allocation.setCustomEndTime(LocalTime.of(14, 30));
        assertThat(allocation.temHorarioCustomizado()).isTrue();
    }

    @Test
    @DisplayName("o horário efetivo usa o customizado quando existe e o do turno caso contrário")
    void horarioEfetivo() {
        ShiftScheduling allocation = new ShiftScheduling();
        allocation.setShift(ShiftType.T1_SAB);

        assertThat(allocation.getInicioEfetivo()).isEqualTo(LocalTime.of(8, 0));
        assertThat(allocation.getFimEfetivo()).isEqualTo(LocalTime.of(12, 0));

        allocation.setCustomStartTime(LocalTime.of(10, 30));
        allocation.setCustomEndTime(LocalTime.of(14, 30));

        assertThat(allocation.getInicioEfetivo()).isEqualTo(LocalTime.of(10, 30));
        assertThat(allocation.getFimEfetivo()).isEqualTo(LocalTime.of(14, 30));
    }

    @Test
    @DisplayName("sem turno nem horário customizado não há horário efetivo")
    void horarioEfetivoSemTurno() {
        ShiftScheduling allocation = new ShiftScheduling();

        assertThat(allocation.getInicioEfetivo()).isNull();
        assertThat(allocation.getFimEfetivo()).isNull();
    }

    @Test
    @DisplayName("o usuário da alocação pode ser trocado, base da troca de turno")
    void utilizadorPodeSerTrocado() {
        User original = new User(1L, "A", "a@t.com", "x", null);
        User novo = new User(2L, "B", "b@t.com", "x", null);

        ShiftScheduling allocation = new ShiftScheduling();
        allocation.setUser(original);
        allocation.setUser(novo);

        assertThat(allocation.getUser()).isSameAs(novo);
    }

    @Test
    @DisplayName("a alocação cobre todas as atribuições especiais do domínio")
    void todasAsAtribuicoesSaoConhecidas() {
        assertThat(List.of(AssignmentType.values()))
                .containsExactly(AssignmentType.REDES_SOCIAIS, AssignmentType.CELULAR_MARINAS);
    }

    /** Sábado e outubro de 2025: datas concretas para os testes de conflito. */
    private static final LocalDate SABADO_4_OUTUBRO = LocalDate.of(2025, 10, 4);

    private static final LocalDate SABADO_11_OUTUBRO = LocalDate.of(2025, 10, 11);

    private ShiftScheduling alocacao(ShiftType turno) {
        ShiftScheduling alocacao = new ShiftScheduling();
        alocacao.setShift(turno);
        return alocacao;
    }

    private ShiftScheduling alocacaoEm(ShiftType turno, LocalDate data) {
        ShiftScheduling alocacao = alocacao(turno);
        alocacao.setSpecificDate(data);
        return alocacao;
    }

    @Nested
    @DisplayName("Dia a que se aplica")
    class DiaAplicavel {

        @Test
        @DisplayName("sem data específica, vale para todos os dias do dia da semana do turno")
        void valeParaOTurnoInteiro() {
            ShiftScheduling alocacao = alocacao(ShiftType.T1_SAB);

            assertThat(alocacao.cobreDia(SABADO_4_OUTUBRO)).isTrue();
            assertThat(alocacao.cobreDia(SABADO_11_OUTUBRO)).isTrue();
            assertThat(alocacao.cobreDia(LocalDate.of(2025, 10, 5))).isFalse();
        }

        @Test
        @DisplayName("com data específica, vale só para esse dia")
        void valeSoParaODia() {
            ShiftScheduling alocacao = alocacaoEm(ShiftType.T1_SAB, SABADO_4_OUTUBRO);

            assertThat(alocacao.cobreDia(SABADO_4_OUTUBRO)).isTrue();
            assertThat(alocacao.cobreDia(SABADO_11_OUTUBRO)).isFalse();
        }

        @Test
        @DisplayName("o dia da semana vem sempre do turno, mesmo com data específica")
        void diaDaSemanaVemDoTurno() {
            assertThat(alocacaoEm(ShiftType.T6_DOM, SABADO_4_OUTUBRO).diaDaSemana())
                    .isEqualTo(DayOfWeek.SUNDAY);
        }

        @Test
        @DisplayName("sem turno não há dia da semana nem dia coberto")
        void semTurnoNaoCobreNada() {
            ShiftScheduling alocacao = new ShiftScheduling();

            assertThat(alocacao.diaDaSemana()).isNull();
            assertThat(alocacao.cobreDia(SABADO_4_OUTUBRO)).isFalse();
            assertThat(alocacao.cobreDia(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Intervalo efetivo")
    class IntervaloEfetivo {

        @Test
        @DisplayName("usa o horário do turno quando não há horário especial")
        void horarioDoTurno() {
            assertThat(alocacao(ShiftType.T1_SAB).intervaloEfetivo().formatado())
                    .isEqualTo("08h00-12h00");
        }

        @Test
        @DisplayName("usa o horário especial quando existe")
        void horarioEspecial() {
            ShiftScheduling alocacao = alocacao(ShiftType.T1_SAB);
            alocacao.setCustomStartTime(LocalTime.of(9, 0));
            alocacao.setCustomEndTime(LocalTime.of(11, 0));

            assertThat(alocacao.intervaloEfetivo().formatado()).isEqualTo("09h00-11h00");
        }

        @Test
        @DisplayName("T5 é lido até às 24h00, e não até às 00h00")
        void t5AteAMeiaNoite() {
            assertThat(alocacao(ShiftType.T5_SAB).intervaloEfetivo().formatado())
                    .isEqualTo("20h00-00h00");
        }

        @Test
        @DisplayName("sem turno nem horário especial não há intervalo")
        void semTurnoNaoHaIntervalo() {
            assertThat(new ShiftScheduling().intervaloEfetivo()).isNull();
        }
    }

    @Nested
    @DisplayName("Conflito entre alocações da mesma pessoa")
    class Conflito {

        @Test
        @DisplayName("T2 cruza um T1 do mesmo dia")
        void t2SobreT1() {
            assertThat(alocacao(ShiftType.T2_SAB).conflitaCom(alocacao(ShiftType.T1_SAB))).isTrue();
        }

        @Test
        @DisplayName("T1 e T3 não conflitam: são turnos seguidos")
        void turnosSeguidosNaoConflitam() {
            assertThat(alocacao(ShiftType.T1_SAB).conflitaCom(alocacao(ShiftType.T3_SAB))).isFalse();
        }

        @Test
        @DisplayName("T1 e T6 não conflitam apesar do mesmo horário, por serem dias diferentes")
        void diasDiferentesNaoConflitam() {
            assertThat(alocacao(ShiftType.T1_SAB).conflitaCom(alocacao(ShiftType.T6_DOM))).isFalse();
        }

        @Test
        @DisplayName("o mesmo turno duas vezes no mesmo dia é conflito")
        void mesmoTurnoNoMesmoDia() {
            assertThat(alocacao(ShiftType.T1_SAB).conflitaCom(alocacao(ShiftType.T1_SAB))).isTrue();
        }

        @Test
        @DisplayName("o mesmo turno em dias específicos diferentes não é conflito")
        void mesmoTurnoEmDiasDiferentes() {
            assertThat(alocacaoEm(ShiftType.T1_SAB, SABADO_4_OUTUBRO)
                    .conflitaCom(alocacaoEm(ShiftType.T1_SAB, SABADO_11_OUTUBRO))).isFalse();
        }

        @Test
        @DisplayName("o mesmo turno no mesmo dia específico é conflito")
        void mesmoTurnoNoMesmoDiaEspecifico() {
            assertThat(alocacaoEm(ShiftType.T1_SAB, SABADO_4_OUTUBRO)
                    .conflitaCom(alocacaoEm(ShiftType.T1_SAB, SABADO_4_OUTUBRO))).isTrue();
        }

        @Test
        @DisplayName("um T1 sem data entra em conflito com um T1 marcado para um sábado")
        void dataDeUmLadoSo() {
            assertThat(alocacao(ShiftType.T1_SAB)
                    .conflitaCom(alocacaoEm(ShiftType.T1_SAB, SABADO_4_OUTUBRO))).isTrue();
            assertThat(alocacaoEm(ShiftType.T1_SAB, SABADO_4_OUTUBRO)
                    .conflitaCom(alocacao(ShiftType.T1_SAB))).isTrue();
        }

        @Test
        @DisplayName("a data específica tem de cair no dia do turno para haver conflito")
        void dataDeUmLadoSoForaDoDiaDoTurno() {
            // T1 é de sábado, e o dia marcado é um domingo: a data concreta e o
            // turno não apontam para o mesmo dia, logo não se cruzam.
            LocalDate domingo = LocalDate.of(2025, 10, 5);

            assertThat(alocacao(ShiftType.T1_SAB)
                    .conflitaCom(alocacaoEm(ShiftType.T1_SAB, domingo))).isFalse();
        }

        @Test
        @DisplayName("o conflito usa o horário especial quando existe")
        void comHorarioEspecial() {
            ShiftScheduling t1AteOnze = alocacao(ShiftType.T1_SAB);
            t1AteOnze.setCustomStartTime(LocalTime.of(8, 0));
            t1AteOnze.setCustomEndTime(LocalTime.of(11, 0));

            // T2 arranca às 11h00. Com o T1 inteiro (08h00-12h00) cruzava; com
            // este T1 a acabar às 11h00 já não.
            assertThat(alocacao(ShiftType.T2_SAB).conflitaCom(t1AteOnze)).isFalse();
            assertThat(alocacao(ShiftType.T2_SAB).conflitaCom(alocacao(ShiftType.T1_SAB))).isTrue();
        }

        @Test
        @DisplayName("T5 não conflita com o turno da manhã do dia seguinte")
        void t5NaoConflitaComOManha() {
            assertThat(alocacao(ShiftType.T5_SAB).conflitaCom(alocacao(ShiftType.T6_DOM))).isFalse();
        }

        @Test
        @DisplayName("nunca há conflito sem turno, e nunca há conflito com nulo")
        void semTurnoOuNulo() {
            assertThat(alocacao(ShiftType.T1_SAB).conflitaCom(null)).isFalse();
            assertThat(new ShiftScheduling().conflitaCom(alocacao(ShiftType.T1_SAB))).isFalse();
        }
    }
}
