package security;

import domain.model.entities.User;
import domain.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Autentica o pedido a partir do token, se houver um token válido.
 *
 * <p>Um token bem assinado só autentica se a conta existir e estiver ativa. Uma
 * conta desativada fica sem autenticação, e é por isso que o pedido acaba em 401
 * e não em 403: quem não tem conta ativa não é um utilizador reconhecido, e o
 * pedido nem chega a ser autorizado. O token em si continua válido até expirar,
 * pelo que desativar a conta trava logo o acesso, sem esperar pela expiração.
 */
@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        var token = recoverToken(request);
        if (token != null) {
            var subject = tokenService.validarToken(token);
            if (subject != null && !subject.isEmpty()) {
                User user = userRepository.findByEmail(subject);
                if (user != null && user.isActive()) {
                    var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String recoverToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7).trim();
        if (token.isEmpty() || token.startsWith("Bearer ")) { // Rejeita formato inválido ou duplo Bearer
            return null;
        }
        return token;
    }
}