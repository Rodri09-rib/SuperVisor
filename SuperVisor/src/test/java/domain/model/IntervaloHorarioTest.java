package domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IntervaloHorario")
class IntervaloHorarioTest {

    private static final LocalTime MEIA_NOITE = LocalTime.MIDNIGHT;

    @Nested
    @DisplayName("Construção a partir de horas locais")
    class Construcao {

        @Test
        @DisplayName("converte as horas em minutos desde a meia-noite")
        void converteParaMinutos() {
            IntervaloHorario intervalo = IntervaloHorario.de(LocalTime.of(8, 0), LocalTime.of(12, 0));

            assertThat(intervalo.inicioMin()).isEqualTo(480);
            assertThat(intervalo.fimMin()).isEqualTo(720);
        }

        @Test
        @DisplayName("a meia-noite conta como 24h00, e não como 0h00")
        void meiaNoiteE24horas() {
            // Sem isto, T5 (20h00-00h00) seria um intervalo a terminar em 0 e
            // qualquer regra de contenção ou sobreposição ficaria invertida.
            IntervaloHorario intervalo = IntervaloHorario.de(LocalTime.of(20, 0), MEIA_NOITE);

            assertThat(intervalo.fimMin()).isEqualTo(1440);
            assertThat(intervalo.invertido()).isFalse();
        }

        @Test
        @DisplayName("recusa um extremo nulo")
        void recusaExtremoNulo() {
            assertThatThrownBy(() -> IntervaloHorario.de(null, LocalTime.of(12, 0)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("início e do fim");
        }
    }

    @Nested
    @DisplayName("Sobreposição")
    class Sobreposicao {

        @Test
        @DisplayName("08h00-12h00 cruza 11h00-15h00")
        void cruzaAoMeio() {
            IntervaloHorario t1 = IntervaloHorario.de(LocalTime.of(8, 0), LocalTime.of(12, 0));
            IntervaloHorario t2 = IntervaloHorario.de(LocalTime.of(11, 0), LocalTime.of(15, 0));

            assertThat(t1.sobrepoe(t2)).isTrue();
            assertThat(t2.sobrepoe(t1)).isTrue();
        }

        @Test
        @DisplayName("intervalos que só se tocam na fronteira não se sobrepõem")
        void fronteiraNaoSobrepoe() {
            // T1 e T3 são turnos seguidos: quem acaba ao meio-dia entra no
            // turno seguinte. Contar a fronteira como sobreposição recusaria uma
            // escala perfeitamente possível.
            IntervaloHorario t1 = IntervaloHorario.de(LocalTime.of(8, 0), LocalTime.of(12, 0));
            IntervaloHorario t3 = IntervaloHorario.de(LocalTime.of(12, 0), LocalTime.of(16, 0));

            assertThat(t1.sobrepoe(t3)).isFalse();
            assertThat(t3.sobrepoe(t1)).isFalse();
        }

        @Test
        @DisplayName("um intervalo não se sobrepõe a si próprio quando está invertido")
        void invertidoNaoSobrepoe() {
            IntervaloHorario invertido = IntervaloHorario.de(LocalTime.of(11, 0), LocalTime.of(9, 0));
            IntervaloHorario t1 = IntervaloHorario.de(LocalTime.of(8, 0), LocalTime.of(12, 0));

            assertThat(invertido.invertido()).isTrue();
            assertThat(invertido.sobrepoe(t1)).isFalse();
        }

        @Test
        @DisplayName("20h00-00h00 não cruza 08h00-12h00 do dia seguinte")
        void t5NaoCruzaOManhaSeguinte() {
            IntervaloHorario t5 = IntervaloHorario.de(LocalTime.of(20, 0), MEIA_NOITE);
            IntervaloHorario manha = IntervaloHorario.de(LocalTime.of(8, 0), LocalTime.of(12, 0));

            assertThat(t5.sobrepoe(manha)).isFalse();
            assertThat(manha.sobrepoe(t5)).isFalse();
        }

        @Test
        @DisplayName("nulo nunca sobrepõe")
        void nuloNaoSobrepoe() {
            IntervaloHorario t1 = IntervaloHorario.de(LocalTime.of(8, 0), LocalTime.of(12, 0));

            assertThat(t1.sobrepoe(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Contenção")
    class Contencao {

        @Test
        @DisplayName("aceita um horário estritamente dentro do turno")
        void dentro() {
            IntervaloHorario turno = IntervaloHorario.de(LocalTime.of(8, 0), LocalTime.of(12, 0));

            assertThat(turno.contem(IntervaloHorario.de(LocalTime.of(9, 0), LocalTime.of(11, 0)))).isTrue();
        }

        @Test
        @DisplayName("aceita o próprio horário do turno, extremidades incluídas")
        void igualAoTurno() {
            IntervaloHorario turno = IntervaloHorario.de(LocalTime.of(8, 0), LocalTime.of(12, 0));

            assertThat(turno.contem(turno)).isTrue();
        }

        @Test
        @DisplayName("recusa um horário que começa antes ou acaba depois")
        void fora() {
            IntervaloHorario turno = IntervaloHorario.de(LocalTime.of(8, 0), LocalTime.of(12, 0));

            assertThat(turno.contem(IntervaloHorario.de(LocalTime.of(7, 0), LocalTime.of(11, 0)))).isFalse();
            assertThat(turno.contem(IntervaloHorario.de(LocalTime.of(9, 0), LocalTime.of(13, 0)))).isFalse();
        }

        @Test
        @DisplayName("aceita 21h00-00h00 em 20h00-00h00")
        void dentroATravessarAMeiaNoite() {
            IntervaloHorario t5 = IntervaloHorario.de(LocalTime.of(20, 0), MEIA_NOITE);

            assertThat(t5.contem(IntervaloHorario.de(LocalTime.of(21, 0), MEIA_NOITE))).isTrue();
        }

        @Test
        @DisplayName("recusa 23h00-01h00 em 20h00-00h00")
        void paraALemDaMeiaNoite() {
            IntervaloHorario t5 = IntervaloHorario.de(LocalTime.of(20, 0), MEIA_NOITE);

            assertThat(t5.contem(IntervaloHorario.de(LocalTime.of(23, 0), LocalTime.of(1, 0)))).isFalse();
        }
    }

    @Nested
    @DisplayName("Apresentação")
    class Apresentacao {

        @Test
        @DisplayName("formata no formato dos rótulos dos turnos")
        void formatoDosRotulos() {
            assertThat(IntervaloHorario.de(LocalTime.of(8, 0), LocalTime.of(12, 0)).formatado())
                    .isEqualTo("08h00-12h00");
        }

        @Test
        @DisplayName("a meia-noite escreve-se 00h00, e não 24h00")
        void meiaNoiteEscreve00() {
            // O rótulo que se lê é 20h00-00h00; 24h00 seria um horário que não
            // existe e apareceria no ecrã do supervisor.
            assertThat(IntervaloHorario.de(LocalTime.of(20, 0), MEIA_NOITE).formatado())
                    .isEqualTo("20h00-00h00");
        }
    }
}
