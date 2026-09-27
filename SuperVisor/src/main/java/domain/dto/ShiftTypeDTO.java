package domain.dto;

import domain.model.enums.ShiftType;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/**
 * Turno de fim de semana exposto ao frontend. O editor de alocações precisa de
 * todos os turnos para construir o selector, e como o horário e o dia são
 * invariantes do domínio, são servidos a partir do enum em vez de duplicados
 * em JavaScript.
 */
public record ShiftTypeDTO(
        String value,
        String acronym,
        LocalTime startTime,
        LocalTime endTime,
        String dayOfWeek,
        String rotulo
) {

    public static ShiftTypeDTO from(ShiftType turno) {
        return new ShiftTypeDTO(
                turno.name(),
                turno.getAcronym(),
                turno.getStartTime(),
                turno.getEndTime(),
                turno.getDayOfWeekLabel(),
                turno.getRotulo());
    }

    public static List<ShiftTypeDTO> todos() {
        return Arrays.stream(ShiftType.values()).map(ShiftTypeDTO::from).toList();
    }
}
