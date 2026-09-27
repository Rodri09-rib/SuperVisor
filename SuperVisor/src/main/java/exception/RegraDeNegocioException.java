package exception;

/**
 * Regra de negócio violada: o pedido está bem formado, mas o que ele pede não
 * pode ser feito no estado atual dos dados.
 *
 * <p>Distingue-se de uma falha técnica (que é 500) e de uma recusa de
 * autorização (que é 403): aqui o problema é o pedido em si, pelo que a
 * resposta é 400. E a mensagem é escrita para o utilizador ler, porque é ela
 * que o frontend mostra no aviso do formulário — por isso não deve nunca
 * conter detalhe interno.
 */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
