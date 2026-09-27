package domain.dto;

import domain.model.entities.ExchangeRequest;
import domain.model.entities.ShiftScheduling;
import domain.model.enums.ExchangeStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Linha do histórico de trocas, achatada para consumo pelo frontend.
 *
 * <p>Existe pela mesma razão do {@link AllocationDTO}: serializar a entidade
 * {@link ExchangeRequest} embute {@code User} — e com ele a senha — e chega a
 * uma escala, o que rebenta o {@code Jackson} com uma referência cíclica.
 *
 * <p>{@code requesterId} e {@code requestedId} não estavam no pedido, mas sem
 * eles a grelha do frontend não consegue distinguir "esta linha é uma troca
 * minha" de "esta linha é uma troca entre duas pessoas que não sou eu": só há
 * o nome para comparar, e comparar nomes é comparar cadeiras de valores que
 * podem coincidir. Os dois identificadores são o que permite assinalar a linha
 * sem adivinar.

 */
public record ShiftExchangeDTO(
        Long id,
        Long requesterId,
        String requesterName,
        Long requestedId,
        String requestedName,
        String originalShift,
        String targetShift,
        LocalDate originalShiftDate,
        LocalDate targetShiftDate,
        ExchangeStatus status,
        OffsetDateTime requestDate,
        OffsetDateTime approvalDate,
        String reason
) {

    public static ShiftExchangeDTO from(ExchangeRequest pedido) {
        ShiftScheduling origem = pedido.getSourceAllocation();
        ShiftScheduling destino = pedido.getDestinationAllocation();

        var requerente = pedido.getRequestingUser();
        var solicitado = pedido.getRequestedUser();

        return new ShiftExchangeDTO(
                pedido.getId(),
                requerente == null ? null : requerente.getId(),
                requerente == null ? null : requerente.getName(),
                solicitado == null ? null : solicitado.getId(),
                solicitado == null ? null : solicitado.getName(),
                rotuloTurno(origem),
                rotuloTurno(destino),
                dataTurno(origem),
                dataTurno(destino),
                pedido.getStatus(),
                pedido.getCreationDate(),
                pedido.getApprovalDate(),
                pedido.getReason());
    }

    /**
     * "T1 (08h00-12h00) - Sábado", mais a data concreta quando a alocação tem
     * uma. A data é a informação que distingue duas trocas do mesmo turno: sem
     * ela, duas escalas de sábado diferentes aparecem como linhas iguais no
     * histórico.
     */
    private static String rotuloTurno(ShiftScheduling alocacao) {
        if (alocacao == null) {
            return null;
        }
        var turno = alocacao.getShift();
        if (turno == null) {
            return null;
        }
        return turno.getRotulo();
    }

    private static LocalDate dataTurno(ShiftScheduling alocacao) {
        if (alocacao == null) {
            return null;
        }
        return alocacao.getSpecificDate();
    }
}
