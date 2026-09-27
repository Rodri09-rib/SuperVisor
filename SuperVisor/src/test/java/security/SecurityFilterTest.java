package security;

import domain.model.entities.User;
import domain.model.enums.UserProfile;
import domain.repository.UserRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import tests.support.TestFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SecurityFilter")
class SecurityFilterTest {

    private TokenService tokenService;
    private UserRepository userRepository;
    private SecurityFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        tokenService = mock(TokenService.class);
        userRepository = mock(UserRepository.class);
        filter = new SecurityFilter();
        ReflectionTestUtils.setField(filter, "tokenService", tokenService);
        ReflectionTestUtils.setField(filter, "userRepository", userRepository);
        request = new MockHttpServletRequest("GET", "/api/v1/allocations");
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private void comToken(String token) {
        request.addHeader("Authorization", "Bearer " + token);
        when(tokenService.validarToken(token)).thenReturn("admin@teste.com");
    }

    private User contaDesativada(UserProfile perfil) {
        User user = TestFixtures.user("Desativado", "admin@teste.com", perfil);
        user.setActive(false);
        return user;
    }

    @Test
    @DisplayName("autentica quando o token e valido e a conta esta ativa")
    void autenticaContaAtiva() throws Exception {
        comToken("token.valido");
        when(userRepository.findByEmail("admin@teste.com")).thenReturn(TestFixtures.supervisor());

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("admin@teste.com");
    }

    @Test
    @DisplayName("nao autentica uma conta desativada, mesmo com token bem assinado")
    void naoAutenticaContaDesativada() throws Exception {
        comToken("token.valido");
        when(userRepository.findByEmail("admin@teste.com"))
                .thenReturn(contaDesativada(UserProfile.SUPERVISOR));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("nao autentica quando o token nao corresponde a nenhuma conta")
    void naoAutenticaTokenOrfao() throws Exception {
        comToken("token.valido");
        when(userRepository.findByEmail("admin@teste.com")).thenReturn(null);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("sem header, nao ha autenticacao nem consulta a base de dados")
    void semHeaderNaoConsulta() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("o header sem prefixo Bearer e ignorado")
    void semPrefixoBearerEIgnorado() throws Exception {
        request.addHeader("Authorization", "token.valido");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenService, never()).validarToken(anyString());
    }

    @Test
    @DisplayName("uma conta desativada nao impede a continuacao da cadeia: o 401 e decidido mais tarde")
    void contaDesativadaNaoBloqueiaACadeia() throws Exception {
        comToken("token.valido");
        when(userRepository.findByEmail("admin@teste.com"))
                .thenReturn(contaDesativada(UserProfile.ANALIST));

        filter.doFilter(request, response, chain);

        assertThat(((MockFilterChain) chain).getRequest()).isNotNull();
    }

    @Test
    @DisplayName("um perfil sem nome continua a autenticar: a falta de perfil nao e falta de conta")
    void perfilNuloAutentica() throws Exception {
        User semPerfil = TestFixtures.analyst();
        semPerfil.setProfile(null);
        comToken("token.valido");
        when(userRepository.findByEmail("admin@teste.com")).thenReturn(semPerfil);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("o valor default de active e true, para os registos existentes continuarem a entrar")
    void activePorDefeito() {
        User novo = new User(1L, "Novo", "novo@teste.com", "hash", UserProfile.ANALIST);

        assertThat(novo.isActive()).isTrue();
    }
}
