package domain.dto;

import domain.model.enums.AssignmentType;

import java.util.Arrays;
import java.util.List;

/**
 * Atribuição especial exposta ao frontend, para as caixas de verificação do
 * editor de alocações. O {@code value} é o nome da constante, que é o que
 * persiste; o {@code rotulo} é apenas texto de apresentação.
 */
public record AssignmentTypeDTO(
        String value,
        String rotulo
) {

    public static AssignmentTypeDTO from(AssignmentType atribuicao) {
        return new AssignmentTypeDTO(atribuicao.name(), atribuicao.getRotulo());
    }

    public static List<AssignmentTypeDTO> todas() {
        return Arrays.stream(AssignmentType.values()).map(AssignmentTypeDTO::from).toList();
    }
}
