package domain.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O preço de cada estado de presença, que é a regra em que assenta o saldo de
 * compensação.
 *
 * <p>Estes valores não são decorativos: a diferença entre o estado anterior e o
 * novo é o que a presencialidade abate ao saldo de um colaborador, e um erro
 * aqui não é um rótulo mal impresso — é alguém a ficar a dever um dia que não
 * faltou, ou a deixar de dever o que faltou.
 */
@DisplayName("Presença — AttendanceStatus")
class AttendanceStatusTest {

    @Test
    @DisplayName("quem está presente não custa nada")
    void presenteNaoCusta() {
        assertThat(AttendanceStatus.PRESENT.custoEmDias()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(AttendanceStatus.PRESENT.isFalta()).isFalse();
    }

    @Test
    @DisplayName("falta de dia inteiro custa um dia inteiro")
    void faltaInteiraCustaUmDia() {
        assertThat(AttendanceStatus.ABSENT_FULL.custoEmDias()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(AttendanceStatus.ABSENT_FULL.isFalta()).isTrue();
    }

    @Test
    @DisplayName("falta de meio expediente custa meio dia, seja de manhã ou de tarde")
    void faltaParcialCustaMeioDia() {
        assertThat(AttendanceStatus.ABSENT_MORNING.custoEmDias())
                .isEqualByComparingTo(new BigDecimal("0.5"));
        assertThat(AttendanceStatus.ABSENT_AFTERNOON.custoEmDias())
                .isEqualByComparingTo(new BigDecimal("0.5"));

        assertThat(AttendanceStatus.ABSENT_MORNING.isFalta()).isTrue();
        assertThat(AttendanceStatus.ABSENT_AFTERNOON.isFalta()).isTrue();
    }

    @Test
    @DisplayName("a diferença entre estados é o delta que a ausência soma ao saldo")
    void deltaEntreEstados() {
        // Falta inteira sobre presente: devia nascer uma dívida de um dia.
        assertThat(AttendanceStatus.ABSENT_FULL.custoEmDias()
                .subtract(AttendanceStatus.PRESENT.custoEmDias()))
                .isEqualByComparingTo(BigDecimal.ONE);

        // Meio expediente: meio dia, nem mais.
        assertThat(AttendanceStatus.ABSENT_MORNING.custoEmDias()
                .subtract(AttendanceStatus.PRESENT.custoEmDias()))
                .isEqualByComparingTo(new BigDecimal("0.5"));

        // Reverter uma falta inteira tem de devolver exatamente o que ela
        // cobrou — é esta simetria que torna a reversão um caso comum.
        assertThat(AttendanceStatus.PRESENT.custoEmDias()
                .subtract(AttendanceStatus.ABSENT_FULL.custoEmDias()))
                .isEqualByComparingTo(new BigDecimal("-1"));

        // Passar de falta de meio expediente para falta de dia inteiro custa
        // mais meio dia, e não mais um dia.
        assertThat(AttendanceStatus.ABSENT_FULL.custoEmDias()
                .subtract(AttendanceStatus.ABSENT_MORNING.custoEmDias()))
                .isEqualByComparingTo(new BigDecimal("0.5"));
    }
}
