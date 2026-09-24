package domain.dto;


import java.time.LocalDate;

public record CreateScaleDTO(
        String name,
        LocalDate initialDate,
        LocalDate endDate,
        Long createdById


) {
}
