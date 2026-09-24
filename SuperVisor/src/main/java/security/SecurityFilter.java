package security;

import domain.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import security.TokenService;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // 1. Tenta extrair o token do cabeçalho da requisição
        var token = recuperarToken(request);

        if (token != null) {
            // 2. Se o token existir, tenta ler o e-mail de dentro dele
            var email = tokenService.validarToken(token);

            // 3. Busca o utilizador completo na base de dados
            UserDetails usuario = userRepository.findByEmail(email);

            if (usuario != null) {
                // 4. Cria o "Cartão de Acesso" interno do Spring e regista que o utilizador está logado
                var authentication = new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // 5. Deixa a requisição seguir o seu caminho (para o Controller ou para ser bloqueada)
        filterChain.doFilter(request, response);
    }

    private String recuperarToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null) return null;

        // Corta a palavra "Bearer " e devolve apenas o código do token
        return authHeader.replace("Bearer ", "");
    }
}