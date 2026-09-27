package exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Corpo de erro comum, para que o frontend tenha sempre o mesmo formato,
     * independentemente de qual das mãos apanhou a exceção.
     */
    private static Map<String, Object> corpoDeErro(HttpStatus estado, String mensagem) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", estado.value());
        body.put("error", estado.getReasonPhrase());
        body.put("message", mensagem);

        return body;
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(corpoDeErro(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    /**
     * Regra de negócio violada, por exemplo um e-mail já cadastrado.
     *
     * <p>Fica explícita para deixar claro que a resposta é 400 por decisão
     * própria, e não por cair dentro do {@code RuntimeException} genérico. O
     * corpo é o mesmo nos dois casos, mas o tipo de exceção é o que diz ao
     * chamador se um 400 é um dado inválido ou um conflito com o que já está
     * gravado.
     */
    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<Map<String, Object>> handleRegraDeNegocio(RegraDeNegocioException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(corpoDeErro(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    /**
     * Corpo que não passa nas restrições do {@code @Valid}, como um
     * nome em branco ou uma password com menos de seis caracteres.
     *
     * <p>{@code MethodArgumentNotValidException} é uma exceção verificada, pelo
     * que escapa ao {@code RuntimeException} acima: sem este tratamento o
     * pedido inválido seria respondido com 500 e o frontend mostraria uma
     * falha genérica em vez do que o utilizador tem de corrigir.
     *
     * <p>A resposta traz {@code message} com todas as queixas em conjunto, para
     * o aviso do formulário poder mostrá-las de uma vez, e {@code fields} com
     * o detalhe por campo, para o formulário poder assinalar o input errado.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidacao(MethodArgumentNotValidException ex) {
        Map<String, String> porCampo = new LinkedHashMap<>();
        for (FieldError erro : ex.getBindingResult().getFieldErrors()) {
            porCampo.putIfAbsent(erro.getField(), erro.getDefaultMessage());
        }

        String mensagem = porCampo.values().stream()
                .filter(msg -> msg != null && !msg.isBlank())
                .distinct()
                .collect(Collectors.joining("; "));
        if (mensagem.isBlank()) {
            mensagem = "Pedido inválido: reveja os campos assinalados.";
        }

        Map<String, Object> body = corpoDeErro(HttpStatus.BAD_REQUEST, mensagem);
        body.put("fields", porCampo);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(corpoDeErro(HttpStatus.FORBIDDEN, ex.getMessage()));
    }

    /**
     * Falhas de autenticação são 401, e não o 400 genérico. Apanha a família
     * {@link org.springframework.security.core.AuthenticationException} para
     * que uma conta desativada ({@code DisabledException}), que só passou a
     * existir quando {@code isEnabled()} passou a ler a propriedade
     * {@code active}, não seja respondida como erro de pedido: o problema é a
     * credencial apresentada, não o corpo enviado.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationExceptions(Exception ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Unauthorized");
        body.put("message", "Bad credentials");

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }
}