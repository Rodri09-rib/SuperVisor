package domain.dto;

import domain.model.entities.ShiftScheduling;
import domain.model.enums.AllocationStatus;
import domain.model.enums.AssignmentType;
import domain.model.enums.ShiftType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * Alocação de turno achatada para consumo pelo frontend. Existe para não
 * serializar a entidade {@link ShiftScheduling}, que embute {@code User} (e
 * portanto a senha) e {@code EditionScale} de forma cíclica.
 *
 * <p>Os campos do turno derivam do {@link ShiftType}, já que o horário e o dia
 * são invariantes do enum. Os campos {@code custom*} são o horário especial
 * declarado na alocação e podem ser nulos; {@code shiftStartTime} e
 * {@code shiftEndTime} são o horário base do turno.
 */
public record AllocationDTO(
        Long id,
        Long editionScaleId,
        String editionScaleName,
        Long userId,
        String userName,
        String userEmail,
        ShiftType shift,
        String shiftAcronym,
        LocalTime shiftStartTime,
        LocalTime shiftEndTime,
        String shiftDayOfTheWeek,
        List<AssignmentType> assignments,
        List<String> assignmentLabels,
        LocalTime customStartTime,
        LocalTime customEndTime,
        boolean customSchedule,
        LocalDate specificDate,
        AllocationStatus analystAcceptanceStatus
) {

    public static AllocationDTO from(ShiftScheduling alocacao) {
        var escala = alocacao.getEditionScale();
        var utilizador = alocacao.getUser();
        var turno = alocacao.getShift();

        List<AssignmentType> atribuicoes = alocacao.getAssignments().stream()
                .sorted(Comparator.comparingInt(AssignmentType::ordinal))
                .toList();

        return new AllocationDTO(
                alocacao.getId(),
                escala == null ? null : escala.getId(),
                escala == null ? null : escala.getName(),
                utilizador == null ? null : utilizador.getId(),
                utilizador == null ? null : utilizador.getName(),
                utilizador == null ? null : utilizador.getEmail(),
                turno,
                turno == null ? null : turno.getAcronym(),
                turno == null ? null : turno.getStartTime(),
                turno == null ? null : turno.getEndTime(),
                turno == null ? null : turno.getDayOfWeekLabel(),
                atribuicoes,
                atribuicoes.stream().map(AssignmentType::getRotulo).toList(),
                alocacao.getCustomStartTime(),
                alocacao.getCustomEndTime(),
                alocacao.temHorarioCustomizado(),
                alocacao.getSpecificDate(),
                alocacao.getAnalystAcceptanceStatus());
    }
}
