package domain.model.enums;

/**
 * Modalidade de trabalho de um colaborador num dia.
 *
 * <p>Só existem dias úteis no calendário: a escala de presencialidade é uma
 * grelha de segunda a sexta, e sábado e domingo não têm sentido num modelo de
 * home office.
 */
public enum WorkModality {

    PRESENCIAL("Presencial"),
    HOME_OFFICE("Home Office");

    private final String rotulo;

    WorkModality(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    public boolean isPresencial() {
        return this == PRESENCIAL;
    }
}
