package controller;

import domain.dto.LoginDTO;
import domain.model.entities.User;
import domain.model.enums.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController — POST /api/auth/login")
class AuthControllerTest {

    private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.token.assinatura";

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private security.TokenService tokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "authenticationManager", authenticationManager);
        ReflectionTestUtils.setField(controller, "tokenService", tokenService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private User utilizadorAutenticado() {
        return new User(1L, "Administrador", "admin@teste.com", "hash", UserProfile.SUPERVISOR);
    }

    private void autenticarComSucesso() {
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(
                        utilizadorAutenticado(), null, utilizadorAutenticado().getAuthorities()));
        when(tokenService.gerarToken(any(User.class))).thenReturn(TOKEN);
    }

    private String corpoLogin(String email, String senha) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, senha);
    }

    @Nested
    @DisplayName("Autenticação bem sucedida")
    class Sucesso {

        @Test
        @DisplayName("devolve 200 com o token JWT em texto puro")
        void devolveTokenCru() throws Exception {
            autenticarComSucesso();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoLogin("admin@teste.com", "123456")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(TOKEN));
        }

        @Test
        @DisplayName("não devolve JSON embrulhado, apenas o token cru")
        void respostaNaoEhJson() throws Exception {
            autenticarComSucesso();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoLogin("admin@teste.com", "123456")))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
        }

        @Test
        @DisplayName("encaminha o e-mail e a senha ao gerenciador de autenticação")
        void encaminhaCredenciaisAoAuthenticationManager() throws Exception {
            autenticarComSucesso();

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corpoLogin("admin@teste.com", "123456")));

            ArgumentCaptor<Authentication> captor = ArgumentCaptor.forClass(Authentication.class);
            verify(authenticationManager).authenticate(captor.capture());
            assertThat(captor.getValue().getPrincipal()).isEqualTo("admin@teste.com");
            assertThat(captor.getValue().getCredentials()).isEqualTo("123456");
        }

        @Test
        @DisplayName("gera o token a partir do usuário devolvido pela autenticação")
        void geraTokenComOPrincipal() throws Exception {
            autenticarComSucesso();

            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(corpoLogin("admin@teste.com", "123456")));

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(tokenService).gerarToken(captor.capture());
            assertThat(captor.getValue().getEmail()).isEqualTo("admin@teste.com");
        }

        @Test
        @DisplayName("aceita o perfil ANALIST tal como aceita o SUPERVISOR")
        void aceitaQualquerPerfil() throws Exception {
            User analyst = new User(2L, "João", "joao@teste.com", "hash", UserProfile.ANALIST);
            when(authenticationManager.authenticate(any()))
                    .thenReturn(new UsernamePasswordAuthenticationToken(
                            analyst, null, analyst.getAuthorities()));
            when(tokenService.gerarToken(any(User.class))).thenReturn(TOKEN);

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoLogin("joao@teste.com", "123456")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(TOKEN));
        }
    }

    @Nested
    @DisplayName("Falhas de autenticação")
    class Falhas {

        @Test
        @DisplayName("credenciais inválidas deixam propagar BadCredentialsException, sem tratamento de erro")
        void credenciaisInvalidas() {
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoLogin("admin@teste.com", "senha-errada"))))
                    .getRootCause()
                    .isInstanceOf(BadCredentialsException.class);

            verify(tokenService, never()).gerarToken(any());
        }

        @Test
        @DisplayName("e-mail inexistente também propaga a falha de autenticação")
        void emailInexistente() {
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoLogin("nao.existe@teste.com", "123456"))))
                    .getRootCause()
                    .isInstanceOf(BadCredentialsException.class);
        }

        @Test
        @DisplayName("corpo malformado devolve 400 e nem chega à autenticação")
        void corpoMalformado() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{isto-nao-e-json"))
                    .andExpect(status().isBadRequest());

            verify(authenticationManager, never()).authenticate(any());
        }
    }

    @Nested
    @DisplayName("Contrato do endpoint")
    class Contrato {

        @Test
        @DisplayName("só aceita POST neste caminho")
        void apenasPost() throws Exception {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/auth/login"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("um caminho diferente sob /api/auth é rejeitado")
        void caminhoDesconhecido() throws Exception {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpoLogin("admin@teste.com", "123456")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("o DTO de login é lido com o nome de campo email")
        void nomeDoCampo() throws Exception {
            autenticarComSucesso();

            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"admin@teste.com","password":"123456","extra":"ignorado"}
                                    """))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("campos ausentes geram credenciais nulas, sem validação de entrada")
        void camposAusentes() throws Exception {
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("null credentials"));

            assertThatThrownBy(() -> mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}")))
                    .getRootCause()
                    .isInstanceOf(BadCredentialsException.class);
        }
    }

    @Test
    @DisplayName("o corpo da requisição é mapeado a partir do record LoginDTO")
    void loginDtoTemOsDoisCampos() {
        LoginDTO dto = new LoginDTO("admin@teste.com", "123456");

        assertThat(dto.email()).isEqualTo("admin@teste.com");
        assertThat(dto.password()).isEqualTo("123456");
    }
}
