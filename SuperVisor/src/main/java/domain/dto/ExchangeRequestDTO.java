package domain.dto;

public record ExchangeRequestDTO(
        Long originAllocationId,
        Long destinationAllocationId,
        String reason
) {

    /**
     * Pedido sem motivo informado: mantém a construção a dois campos usada
     * internamente e por clientes que não conhecem o motivo.
     */
    public ExchangeRequestDTO(Long originAllocationId, Long destinationAllocationId) {
        this(originAllocationId, destinationAllocationId, null);
    }
}
