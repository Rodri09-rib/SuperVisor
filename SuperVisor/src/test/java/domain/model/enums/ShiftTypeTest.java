package domain.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ShiftType")
class ShiftTypeTest {

    private static final String SABADO = "S\u00e1bado";
    private static final String DOMINGO = "Domingo";

    @Nested
    @DisplayName("Invariantes dos turnos")
    class Invariantes {

        @Test
        @DisplayName("contém seis turnos, de sábado a domingo")
        void turnosDoFimDeSemana() {
            assertThat(ShiftType.values()).hasSize(6);
            assertThat(ShiftType.T1_SAB.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
            assertThat(ShiftType.T6_DOM.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        }

        @Test
        @DisplayName("T2 sobrepoe T1 e T3 de proposito")
        void t2SobrepoeT1ET3() {
            assertThat(ShiftType.T2_SAB.getStartTime()).isBefore(ShiftType.T1_SAB.getEndTime());
            assertThat(ShiftType.T2_SAB.getEndTime()).isAfter(ShiftType.T3_SAB.getStartTime());
        }

        @Test
        @DisplayName("T5 vai das 20h00 a meia-noite")
        void t5TerminaAMeiaNoite() {
            assertThat(ShiftType.T5_SAB.getStartTime()).isEqualTo(LocalTime.of(20, 0));
            assertThat(ShiftType.T5_SAB.getEndTime()).isEqualTo(LocalTime.MIDNIGHT);
        }

        @Test
        @DisplayName("os rotulos seguem o formato usado no frontend")
        void rotulos() {
            assertThat(ShiftType.T1_SAB.getIntervalo()).isEqualTo("08h00-12h00");
            assertThat(ShiftType.T5_SAB.getIntervalo()).isEqualTo("20h00-00h00");
            assertThat(ShiftType.T1_SAB.getAcronym()).isEqualTo("T1");
            assertThat(ShiftType.T1_SAB.getDayOfWeekLabel()).isEqualTo(SABADO);
            assertThat(ShiftType.T1_SAB.getRotulo())
                    .isEqualTo("T1 (08h00-12h00) - " + SABADO);
            assertThat(ShiftType.T6_DOM.getRotuloCurto())
                    .isEqualTo("T6 - " + DOMINGO);
        }
    }

    @Nested
    @DisplayName("Horarios especiais")
    class HorariosEspeciais {

        @Test
        @DisplayName("aceita um horario dentro do turno")
        void dentroDoTurno() {
            assertThat(ShiftType.T1_SAB.contem(LocalTime.of(9, 0), LocalTime.of(11, 0))).isTrue();
        }

        @Test
        @DisplayName("aceita o proprio horario do turno")
        void horarioCompletoDoTurno() {
            assertThat(ShiftType.T3_SAB.contem(LocalTime.of(12, 0), LocalTime.of(16, 0))).isTrue();
        }

        @Test
        @DisplayName("rejeita um horario que comeca antes do turno")
        void antesDoTurno() {
            assertThat(ShiftType.T1_SAB.contem(LocalTime.of(7, 0), LocalTime.of(11, 0))).isFalse();
        }

        @Test
        @DisplayName("rejeita um horario que termina depois do turno")
        void depoisDoTurno() {
            assertThat(ShiftType.T1_SAB.contem(LocalTime.of(9, 0), LocalTime.of(13, 0))).isFalse();
        }

        @Test
        @DisplayName("rejeita um horario em que o fim e anterior ao inicio")
        void horarioInvertido() {
            assertThat(ShiftType.T1_SAB.contem(LocalTime.of(11, 0), LocalTime.of(9, 0))).isFalse();
        }

        @Test
        @DisplayName("aceita 21h00-00h00 em T5, apesar de o fim parecer anterior ao inicio")
        void t5AtravessaAMeiaNoite() {
            assertThat(ShiftType.T5_SAB.contem(LocalTime.of(21, 0), LocalTime.MIDNIGHT)).isTrue();
        }

        @Test
        @DisplayName("rejeita em T5 um horario que passa da meia-noite")
        void t5ParaAlemDaMeiaNoite() {
            assertThat(ShiftType.T5_SAB.contem(LocalTime.of(23, 0), LocalTime.of(1, 0))).isFalse();
        }

        @Test
        @DisplayName("rejeita em T1 um horario que atravessa a meia-noite")
        void t1NaoAtravessaAMeiaNoite() {
            assertThat(ShiftType.T1_SAB.contem(LocalTime.of(23, 0), LocalTime.MIDNIGHT)).isFalse();
        }

        @Test
        @DisplayName("devolve true quando falta um dos horarios, para a obrigatoriedade ser validada a parte")
        void horarioIncompleto() {
            assertThat(ShiftType.T1_SAB.contem(null, LocalTime.of(11, 0))).isTrue();
            assertThat(ShiftType.T1_SAB.contem(LocalTime.of(9, 0), null)).isTrue();
            assertThat(ShiftType.T1_SAB.contem(null, null)).isTrue();
        }
    }
}
