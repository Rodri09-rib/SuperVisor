package domain.dto;

import domain.model.entities.ShiftScheduling;
import domain.model.enums.AllocationStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Alocação de turno achatada para consumo pelo frontend. Existe para não
 * serializar a entidade {@link ShiftScheduling}, que embute {@code User} (e
 * portanto a senha) e {@code EditionScale} de forma cíclica.
 */
public record AllocationDTO(
        Long id,
        Long editionScaleId,
        String editionScaleName,
        Long userId,
        String userName,
        String userEmail,
        Long shiftId,
        String shiftAcronym,
        LocalTime shiftStartTime,
        LocalTime shiftEndTime,
        String shiftDayOfTheWeek,
        LocalDate specificDate,
        AllocationStatus analystAcceptanceStatus
) {

    public static AllocationDTO from(ShiftScheduling alocacao) {
        var escala = alocacao.getEditionScale();
        var utilizador = alocacao.getUser();
        var turno = alocacao.getShift();

        return new AllocationDTO(
                alocacao.getId(),
                escala == null ? null : escala.getId(),
                escala == null ? null : escala.getName(),
                utilizador == null ? null : utilizador.getId(),
                utilizador == null ? null : utilizador.getName(),
                utilizador == null ? null : utilizador.getEmail(),
                turno == null ? null : turno.getId(),
                turno == null ? null : turno.getAcronym(),
                turno == null ? null : turno.getStartTime(),
                turno == null ? null : turno.getEndTime(),
                turno == null ? null : turno.getDayiftheWeek(),
                alocacao.getSpecificDate(),
                alocacao.getAnalystAcceptanceStatus());
    }
}
