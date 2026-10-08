package domain.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Ajuste manual do saldo de compensação de um colaborador.
 *
 * <p>{@code deltaDays} é assinado de propósito: um valor negativo abate a
 * dívida (o colaborador compensou, por exemplo vindo num dia de home office)
 * e um valor positivo acrescenta-a (uma troca não autorizada que gera um dia
 * de dívida). Um só campo em vez de dois botões porque a operação é a mesma —
 * mover o saldo — e dois endpoints espelhados dividiriam uma regra única em
 * duas rotas com validações que podiam divergir.
 *
 * <p>O corte em zero é do serviço e não aqui: um pedido que tentasse pôr a
 * dívida negativa está bem formado, e a resposta explicará que o saldo não
 * ficaria abaixo de zero em vez de o pedido ser rejeitado como malformado.
 */
public record CompensationAdjustRequestDTO(
        @NotNull(message = "O número de dias a ajustar é obrigatório.")
        BigDecimal deltaDays
) {
}
