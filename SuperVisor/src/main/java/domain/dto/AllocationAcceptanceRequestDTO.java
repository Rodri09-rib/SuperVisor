package domain.dto;

import domain.model.enums.AllocationStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Resposta do analista ao turno que lhe foi escalado.
 *
 * <p>O corpo traz só o estado. Quem responde e sobre que turno não vem no
 * pedido: vem do caminho e do contexto de segurança, para que ninguém possa
 * responder por outro nem responder sobre um turno que não é seu.
 */
public record AllocationAcceptanceRequestDTO(

        @NotNull(message = "Indique se aceita ou recusa o turno.")
        AllocationStatus status
) {
}
