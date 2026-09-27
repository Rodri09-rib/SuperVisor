package domain.model.enums;

/**
 * Estado de um pedido de troca de turno.
 *
 * <p>Substitui a coluna de texto solto que existia em {@code ExchangeRequest},
 * onde os valores apareciam em três dialects diferentes: o inicializador do
 * campo escrevia {@code "PENDENTE"}, o serviço ao criar escrevia
 * {@code "PENDING"} e o serviço ao responder escrevia {@code "ACCEPTED"} /
 * {@code "REJECTED"}. Um pedido criado pelo caminho que não definisse o estado
 * ficava assim com um valor que nenhuma comparação apanhava: respondê-lo
 * respondia "já foi respondida" para sempre. Um enum fecha essa porta, porque
 * passa a haver um único conjunto de valores válidos.
 *
 * <p>{@code APPROVED} chama-se assim, e não {@code ACCEPTED}, porque é o que o
 * pedido representa: a troca passou a valer e as alocações já foram trocadas.
 */
public enum ExchangeStatus {

    /** Pedido feito, ainda sem resposta do colega. */
    PENDING,

    /** aceite: as duas alocações trocaram de dono. */
    APPROVED,

    /** recusado: nada mudou nas alocações. */
    REJECTED;

    /** Verdadeiro enquanto o pedido ainda espera resposta. */
    public boolean isPendente() {
        return this == PENDING;
    }
}
