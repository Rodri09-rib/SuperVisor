package domain.dto;

import domain.model.enums.AssignmentType;
import domain.model.enums.ShiftType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Pedido de criação ou de alteração de uma alocação. O utilizador e a escala
 * são referenciados por id e resolvidos no serviço: o cliente não envia
 * entidades, apenas identificadores e os valores que são mesmo livres.
 *
 * <p>Usado tanto no POST como no PUT, para que criar e editar partilhem a mesma
 * validação.
 */
public record AllocationRequestDTO(
        Long editionScaleId,
        Long userId,
        ShiftType shift,
        List<AssignmentType> assignments,
        LocalTime customStartTime,
        LocalTime customEndTime,
        LocalDate specificDate
) {
}
