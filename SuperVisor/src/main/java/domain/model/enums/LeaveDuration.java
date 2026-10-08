package domain.model.enums;

/**
 * Duração pedida numa folga.
 *
 * <p>{@code FULL_DAY} cobre o intervalo inteiro — uma folga de segunda a
 * sexta são cinco dias —, enquanto as outras duas valem só metade de um dia
 * e por isso só fazem sentido numa folga de um só dia, regra que o
 * {@code LeaveService} valida antes de gravar.
 *
 * <p>Os rótulos repetem as horas porque são elas que dizem ao colega o que
 * está a cobrir: "apenas manhã" sem horas deixa aberta a questão de saber se
 * a manhã acaba ao almoço ou às 12h, e a escala trabalha das 08h às 17h.
 */
public enum LeaveDuration {

    FULL_DAY("Dia completo"),
    MORNING_SHIFT("Apenas manhã (08h-12h)"),
    AFTERNOON_SHIFT("Apenas tarde (12h-17h)");

    private final String rotulo;

    LeaveDuration(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Meia jornada: custa meio dia, seja de manhã ou de tarde. */
    public boolean isMeiaJornada() {
        return this != FULL_DAY;
    }
}
