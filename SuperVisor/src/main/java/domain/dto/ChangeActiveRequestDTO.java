package domain.dto;

/**
 * Pedido de comutação do estado de uma conta.
 *
 * <p>Um booleano simples em vez de um recurso aninhado porque a operação é
 * uma comutação, não uma edição parcial: um campo booleano simples não pode ser
 * "omitido", portanto nunca há a dúvida de se a omissão significava "ativa" ou
 * "não sabes".
 */
public record ChangeActiveRequestDTO(boolean active) {
}
