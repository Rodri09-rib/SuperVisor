package domain.dto;

import domain.model.entities.WorkModalitySchedule;
import domain.model.enums.AttendanceStatus;
import domain.model.enums.TeamGroup;
import domain.model.enums.WorkModality;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Célula da escala de presencialidade: a modalidade de uma pessoa num dia.
 *
 * <p>Traz o valor do enum e o seu rótulo, como no {@link AllocationDTO}. O
 * frontend usa o enum para decidir a cor e o rótulo para o texto legível, e
 * querer que os dois venham da mesma fonte evita a divergência clássica de a
 * aplicação dizer "Home Office" e a grelha escrever "Home-office".
 *
 * <p>{@code attendanceStatus} e {@code notes} são o registo de falta da célula:
 * o estado vem com rótulo como a modalidade, e a observação acompanha a célula
 * para que a grelha possa mostrá-la sem uma segunda chamada à API.
 *
 * <p>{@code weekdayLabel} e {@code weekNumber} não são dados do domínio: existem
 * para a grelha se orientar sem recalcular a semana em JavaScript e sem
 * depender do fuso do navegador para decidir se uma semana é par ou ímpar.
 */
public record WorkModalityScheduleDTO(
        Long id,
        Long userId,
        String userName,
        TeamGroup teamGroup,
        String teamLabel,
        LocalDate date,
        String weekdayLabel,
        int weekNumber,
        boolean weekOdd,
        WorkModality modality,
        String modalityLabel,
        AttendanceStatus attendanceStatus,
        String attendanceLabel,
        String notes
) {

    public static WorkModalityScheduleDTO from(WorkModalitySchedule registo) {
        var utilizador = registo.getUser();
        var equipa = registo.getTeamGroup();
        var modalidade = registo.getModality();
        var presenca = registo.getAttendanceStatus() == null
                ? AttendanceStatus.PRESENT
                : registo.getAttendanceStatus();
        LocalDate data = registo.getDate();
        int semana = semanaIsoDe(data);

        return new WorkModalityScheduleDTO(
                registo.getId(),
                utilizador == null ? null : utilizador.getId(),
                utilizador == null ? null : utilizador.getName(),
                equipa,
                equipa == null ? null : equipa.getRotulo(),
                data,
                rotuloDia(data),
                semana,
                semana % 2 == 1,
                modalidade,
                modalidade == null ? null : modalidade.getRotulo(),
                presenca,
                presenca.getRotulo(),
                registo.getNotes());
    }

    private static int semanaIsoDe(LocalDate data) {
        return data.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
    }

    private static String rotuloDia(LocalDate data) {
        DayOfWeek dia = data.getDayOfWeek();
        switch (dia) {
            case MONDAY:
                return "Segunda";
            case TUESDAY:
                return "Terça";
            case WEDNESDAY:
                return "Quarta";
            case THURSDAY:
                return "Quinta";
            case FRIDAY:
                return "Sexta";
            case SATURDAY:
                return "Sábado";
            case SUNDAY:
                return "Domingo";
            default:
                return dia.name();
        }
    }
}
