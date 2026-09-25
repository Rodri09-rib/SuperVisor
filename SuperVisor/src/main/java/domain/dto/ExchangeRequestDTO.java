package domain.dto;

public record ExchangeRequestDTO(
        Long originAllocationId,
        Long destinationAllocationId
) {
}
