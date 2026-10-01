package domain.dto;

import java.time.LocalDate;

/**
 * Pedido de geração da escala de presencialidade.
 *
 * <p>Todos os campos são opcionais, e o motivo é operacional: a escala é
 * normalmente gerada pela supervisão no início de cada semana, e exigir um
 * corpo com uma data o transformaria num formulário. Um {@code POST} sem corpo
 * usa a semana corrente.
 *
 * <p>A referência é uma data qualquer da semana pretendida, não o primeiro dia.
 * Pedir a escala de quinta-feira é a operação mais comum — é quando se percebe
 * que a semana ainda não foi feita — e exigir que o usuário calculasse a
 * segunda-feira da semana seria trabalho inútil.
 *
 * <p>A data chega como {@link LocalDate} e o Jackson trata-a em ISO-8601, que é
 * o único formato aceite. Um {@code String} com a data obrigaria a aplicação a
 * decidir entre formatos, e o erro dessa decisão — aceitar "07-03-2026" como
 * 7 de março quando o pretendido era 3 de julho — geraria uma escala errada sem
 * nenhum aviso.
 */
public record GenerateWorkModalityDTO(LocalDate dataReferencia) {
}
