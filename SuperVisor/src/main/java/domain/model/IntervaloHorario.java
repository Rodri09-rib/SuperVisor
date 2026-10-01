package domain.model;

import java.time.LocalTime;

/**
 * Intervalo de tempo dentro de um dia, medido em minutos desde a meia-noite.
 *
 * <p>Existe para tirar a aritmética de minutos de dentro do {@code ShiftType} e
 * para que a regra de sobreposição possa ser testada sem passar pelo enum. O
 * {@code T5} termina às 00h00, ou seja no início do dia seguinte: como o valor
 * é {@link LocalTime#MIDNIGHT}, uma comparação direta diria que o turno acaba
 * <em>antes</em> de começar, e qualquer regra de contenção ou sobreposição
 * ficaria invertida. Aqui a meia-noite conta como o minuto 1440, e o intervalo
 * de um turno que a atravessa é um intervalo normal, com o fim à direita do dia.
 *
 * <p>A comparação é feita sempre em minutos, e nunca com {@code LocalTime},
 * pelo mesmo motivo.
 *
 * <p>Os intervalos são <em>meios-abertos</em>: o fim não pertence ao intervalo.
 * É o que faz {@code T1} (08h00-12h00) e {@code T3} (12h00-16h00) serem turnos
 * seguidos e não sobrepostos, que é como operacionalmente se pensam: quem
 * acaba ao meio-dia pode entrar no turno seguinte sem estar em dois sítios.
 */
public record IntervaloHorario(int inicioMin, int fimMin) {

    /** Minutos de um dia inteiro: a meia-noite conta como 24h00. */
    private static final int MINUTOS_NO_DIA = 24 * 60;

    /**
     * Constrói um intervalo a partir de horas locais, tratando a meia-noite
     * como 24h00.
     *
     * @throws IllegalArgumentException se algum dos extremos for nulo
     */
    public static IntervaloHorario de(LocalTime inicio, LocalTime fim) {
        if (inicio == null || fim == null) {
            throw new IllegalArgumentException("Um intervalo precisa do início e do fim.");
        }
        return new IntervaloHorario(minutos(inicio), minutos(fim));
    }

    /** Minutos desde a meia-noite, com a meia-noite a contar como 24h00. */
    private static int minutos(LocalTime valor) {
        return valor.equals(LocalTime.MIDNIGHT)
                ? MINUTOS_NO_DIA
                : valor.getHour() * 60 + valor.getMinute();
    }

    /**
     * Verdadeiro quando este intervalo é um período sem duração útil, isto é,
     * quando o fim não é depois do início.
     *
     * <p>É o caso que {@code T1} rejeita ao receber 11h00-09h00, e o que
     * impede que essa aritmética estranha passe a ser lida como uma sobreposição.
     */
    public boolean invertido() {
        return fimMin <= inicioMin;
    }

    /**
     * Verdadeiro quando os dois intervalos partilham algum tempo.
     *
     * <p>Meios-abertos, como a classe: intervalos que só se tocam na fronteira
     * — o fim de um é o início do outro — não se sobrepõem. T1 e T3 são o
     * exemplo: 08h00-12h00 e 12h00-16h00 podem ser cobertos pela mesma pessoa,
     * e sem esta regra a aplicação recusaria uma escala perfeitamente possível.
     *
     * <p>Um intervalo invertido não se sobrepõe a nada, nem a si próprio: é um
     * período sem duração, e a aritmética em minutos não tem como dizer que
     * 11h00-09h00 passa pelas mesmas horas que 08h00-12h00.
     */
    public boolean sobrepoe(IntervaloHorario outro) {
        if (outro == null || invertido() || outro.invertido()) {
            return false;
        }
        return inicioMin < outro.fimMin && outro.inicioMin < fimMin;
    }

    /**
     * Verdadeiro quando este intervalo contém o outro, extremidades incluído.
     *
     * <p>Um intervalo invertido não cabe em nada, incluindo em si próprio, pela
     * mesma razão de {@link #sobrepoe(IntervaloHorario)}.
     */
    public boolean contem(IntervaloHorario outro) {
        if (outro == null || invertido() || outro.invertido()) {
            return false;
        }
        return inicioMin <= outro.inicioMin && outro.fimMin <= fimMin;
    }

    /** "08h00-12h00", o mesmo formato usado nos rótulos dos turnos. */
    public String formatado() {
        return hora(inicioMin) + "-" + hora(fimMin);
    }

    private static String hora(int minutos) {
        int dentroDoDia = minutos == MINUTOS_NO_DIA ? 0 : minutos % MINUTOS_NO_DIA;
        return String.format("%02dh%02d", dentroDoDia / 60, dentroDoDia % 60);
    }
}
