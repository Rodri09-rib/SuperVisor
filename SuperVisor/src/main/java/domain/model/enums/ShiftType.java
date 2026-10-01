package domain.model.enums;

import domain.model.IntervaloHorario;

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
        return getIntervaloHorario().formatado();
    }

    /** "T1 - Sábado", para tabelas onde o horário já é apresentado à parte. */
    public String getRotuloCurto() {
        return acronym + " - " + dayOfWeekLabel;
    }

    /**
     * O turno como intervalo, com a meia-noite de T5 a contar como 24h00.
     *
     * <p>É o que permite comparar dois turnos sem repetir a aritmética de
     * minutos em cada sitio, e é o que a regra de sobreposição usa.
     */
    public IntervaloHorario getIntervaloHorario() {
        return IntervaloHorario.de(startTime, endTime);
    }

    /**
     * Verdadeiro quando dois turnos partilham algum tempo no mesmo dia.
     *
     * <p>É por isto que T2 se sobrepõe a T1 e a T3, e por isso que a mesma
     * pessoa não pode ficar com os dois no mesmo dia. A comparação é feita entre
     * dias da semana: T1 (sábado 08h00-12h00) e T6 (domingo 08h00-12h00) têm
     * horários idênticos e mesmo assim não se cruzam, porque ninguém está em
     * dois sítios ao mesmo tempo num sábado e num domingo.
     */
    public boolean sobrepoe(ShiftType outro) {
        return outro != null
                && dayOfWeek == outro.dayOfWeek
                && getIntervaloHorario().sobrepoe(outro.getIntervaloHorario());
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

        return getIntervaloHorario().contem(IntervaloHorario.de(inicio, fim));
    }

    /** Texto para os <option> e listas: "T1 (08h00-12h00) - Sábado". */
    public String getRotulo() {
        return acronym + " (" + getIntervalo() + ") - " + dayOfWeekLabel;
    }
}
