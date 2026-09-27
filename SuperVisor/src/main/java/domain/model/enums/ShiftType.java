package domain.model.enums;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Turnos de fim de semana. O dominio so trabalha sabados e domingos, pelo que
 * o turno deixou de ser uma entidade com horarios em base de dados e passou a
 * ser um valor fechado: sigla, horario e dia sao invariantes e derivam do
 * proprio enum.
 *
 * <p>T2 (11h-15h) sobrepoe T1 (08h-12h) e T3 (12h-16h) de proposito: e o turno
 * intermedirio, usado quando ha sobreposicao de equipas. T5 termina a meia-noite
 * (00h00), ou seja, no inicio do domingo.
 */
public enum ShiftType {

    T1_SAB("T1", LocalTime.of(8, 0), LocalTime.of(12, 0), DayOfWeek.SATURDAY, "Sábado"),
    T2_SAB("T2", LocalTime.of(11, 0), LocalTime.of(15, 0), DayOfWeek.SATURDAY, "Sábado"),
    T3_SAB("T3", LocalTime.of(12, 0), LocalTime.of(16, 0), DayOfWeek.SATURDAY, "Sábado"),
    T4_SAB("T4", LocalTime.of(16, 0), LocalTime.of(20, 0), DayOfWeek.SATURDAY, "Sábado"),
    T5_SAB("T5", LocalTime.of(20, 0), LocalTime.of(0, 0), DayOfWeek.SATURDAY, "Sábado"),
    T6_DOM("T6", LocalTime.of(8, 0), LocalTime.of(12, 0), DayOfWeek.SUNDAY, "Domingo");

    /** Minutos num dia inteiro: usado para tratar a meia-noite como 24h00. */
    private static final int MINUTOS_NO_DIA = 24 * 60;

    private final String acronym;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final DayOfWeek dayOfWeek;
    private final String dayOfWeekLabel;

    ShiftType(String acronym, LocalTime startTime, LocalTime endTime,
              DayOfWeek dayOfWeek, String dayOfWeekLabel) {
        this.acronym = acronym;
        this.startTime = startTime;
        this.endTime = endTime;
        this.dayOfWeek = dayOfWeek;
        this.dayOfWeekLabel = dayOfWeekLabel;
    }

    public String getAcronym() {
        return acronym;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public String getDayOfWeekLabel() {
        return dayOfWeekLabel;
    }

    /** "08h00-12h00", no mesmo formato usado pelo frontend. */
    public String getIntervalo() {
        return hora(startTime) + "-" + hora(endTime);
    }

    /** "T1 - Sábado", para tabelas onde o horário já é apresentado à parte. */
    public String getRotuloCurto() {
        return acronym + " - " + dayOfWeekLabel;
    }

    /**
     * Verdadeiro quando um horário especial cabe dentro deste turno.
     *
     * <p>As comparações são feitas em minutos desde a meia-noite porque T5
     * termina às 00h00: esse valor passa a contar como 24h00, o que permite
     * aceitar um horário customizado 21h00-00h00 nesse turno em vez de o
     * rejeitar por o fim parecer anterior ao início.
     *
     * <p>Um dos horários a {@code null} devolve {@code true}: a obrigatoriedade
     * de informar os dois em conjunto é validada à parte.
     */
    public boolean contem(LocalTime inicio, LocalTime fim) {
        if (inicio == null || fim == null) {
            return true;
        }

        int minutosInicio = minutos(inicio);
        int minutosFim = minutos(fim);

        if (minutosFim <= minutosInicio) {
            return false;
        }

        return minutosInicio >= minutos(startTime)
                && minutosFim <= minutosAteFimDoTurno();
    }

    private int minutosAteFimDoTurno() {
        return endTime.equals(LocalTime.MIDNIGHT) ? MINUTOS_NO_DIA : minutos(endTime);
    }

    private static int minutos(LocalTime valor) {
        if (valor.equals(LocalTime.MIDNIGHT)) {
            return MINUTOS_NO_DIA;
        }
        return valor.getHour() * 60 + valor.getMinute();
    }

    /** Texto para os <option> e listas: "T1 (08h00-12h00) - Sábado". */
    public String getRotulo() {
        return acronym + " (" + getIntervalo() + ") - " + dayOfWeekLabel;
    }

    private static String hora(LocalTime valor) {
        return String.format("%02dh%02d", valor.getHour(), valor.getMinute());
    }
}
