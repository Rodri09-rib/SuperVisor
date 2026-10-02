package domain.dto;

/**
 * O que a exclusão de uma escala vai levar consigo.
 *
 * <p>Existe para que a confirmação diga quanto é que se vai perder em vez de
 * perguntar «tem a certeza?». Um aviso genérico obriga quem lê a avançar até ao
 * fim para descobrir que havia doze turnos e três trocas em jogo, e a partir
 * daí a confirmação deixa de ser uma decisão e passa a ser um reflexo.
 *
 * <p>Os dois contadores são o que a exclusão apaga de facto, e por isso não há
 * aqui nada sobre a escala em si: o nome e o período já estão na linha da tabela
 * e repetiriam o que a pessoa está a ler.
 */
public record ScaleDeletionSummaryDTO(
        int alocacoes,
        int trocas
) {
}