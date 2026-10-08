package domain.model.enums;

import java.math.BigDecimal;

/**
 * Estado de presença de um colaborador num dia da escala de presencialidade.
 *
 * <p>A escala é gerada com todos os colaboradores presentes (ou em home
 * office), e este enum é o que o supervisor altera quando alguém falta. Só
 * há quatro estados porque são os quatro casos que a grelha distingue: quem
 * veio, quem não veio, e quem não veio metade do dia.
 *
 * <p>{@link #custoEmDias()} é a regra de compensação escrita uma só vez: uma
 * falta completa deve um dia inteiro e uma falta parcial deve meio dia. Estar
 * no enum e não no serviço é propositado — o serviço calcula a diferença entre
 * dois estados, e se a regra vivesse no serviço haveria dois sítios (o valor
 * de cada estado e a transição entre eles) onde o meio expediente podia ser
 * esquecido.
 */
public enum AttendanceStatus {

    PRESENT("Presente"),
    ABSENT_FULL("Falta completa"),
    ABSENT_MORNING("Falta de manhã (08h-12h)"),
    ABSENT_AFTERNOON("Falta de tarde (12h-17h)");

    /** Meio expediente: meia jornada vale meio dia de dívida. */
    public static final BigDecimal MEIO_EXPEDIENTE = BigDecimal.valueOf(0.5);

    private final String rotulo;

    AttendanceStatus(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    public boolean isFalta() {
        return this != PRESENT;
    }

    /**
     * Dias de compensação que o estado representa.
     *
     * <p>Presente não deve nada, falta completa deve um dia e as duas faltas
     * parciais valem meio dia cada. Os valores são constantes exatas em
     * {@link BigDecimal}: 0.5 não é representável em ponto flutuante, e uma
     * dívida de meio dia que oscilasse entre 0.4999 e 0.5001 apareceria na
     * grelha como um saldo a flutuar sem ninguém ter mexido nela.
     */
    public BigDecimal custoEmDias() {
        switch (this) {
            case ABSENT_FULL:
                return BigDecimal.ONE;
            case ABSENT_MORNING:
            case ABSENT_AFTERNOON:
                return MEIO_EXPEDIENTE;
            default:
                return BigDecimal.ZERO;
        }
    }
}
