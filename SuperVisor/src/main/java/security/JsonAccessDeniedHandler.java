package security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Resposta 403 em JSON para as negações que acontecem dentro da cadeia de
 * filtros, antes de o pedido chegar a um controlador.
 *
 * <p>É o par de {@link CustomAuthenticationEntryPoint}, que trata o 401: sem
 * este handler o Spring devolve uma página HTML em branco e o frontend, que
 * espera JSON, não consegue mostrar a mensagem ao utilizador.
 */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write("{\"error\": \"Forbidden\", \"message\": \""
                + accessDeniedException.getMessage() + "\"}");
    }
}
