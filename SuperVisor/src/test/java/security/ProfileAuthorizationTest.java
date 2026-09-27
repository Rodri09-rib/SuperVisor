package security;

import domain.model.entities.User;
import domain.model.enums.UserProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tests.support.TestFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProfileAuthorization")
class ProfileAuthorizationTest {

    private static final String MENSAGEM =
            "Apenas o perfil SUPERVISOR pode executar esta opera\u00e7\u00e3o.";

    private ProfileAuthorization profileAuthorization;

    @BeforeEach
    void setUp() {
        profileAuthorization = new ProfileAuthorization();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(User user) {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Nested
    @DisplayName("exigirSupervisor")
    class ExigirSupervisor {

        @Test
        @DisplayName("deixa passar um SUPERVISOR")
        void supervisorPassa() {
            autenticarComo(TestFixtures.supervisor());

            assertThatCode(() -> profileAuthorization.exigirSupervisor())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("recusa um ANALIST com a mensagem de falta de permissao")
        void analistNegado() {
            autenticarComo(TestFixtures.analyst());

            assertThatThrownBy(() -> profileAuthorization.exigirSupervisor())
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage(MENSAGEM);
        }

        @Test
        @DisplayName("recusa quando nao ha autenticacao nenhuma")
        void semAutenticacaoNegado() {
            assertThatThrownBy(() -> profileAuthorization.exigirSupervisor())
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage(MENSAGEM);
        }

        @Test
        @DisplayName("recusa um principal que nao e um User, para um token valido nao contornar a regra")
        void principalDesconhecidoNegado() {
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    "nao-e-um-utilizador", null, TestFixtures.analyst().getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            assertThatThrownBy(() -> profileAuthorization.exigirSupervisor())
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage(MENSAGEM);
        }

        @Test
        @DisplayName("recusa um utilizador sem perfil atribuido")
        void semPerfilNegado() {
            User semPerfil = TestFixtures.analyst();
            semPerfil.setProfile(null);
            autenticarComo(semPerfil);

            assertThatThrownBy(() -> profileAuthorization.exigirSupervisor())
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("exigirPerfil")
    class ExigirPerfil {

        @Test
        @DisplayName("aceita quando o perfil pedido e o do utilizador")
        void perfilIgualPassa() {
            autenticarComo(TestFixtures.analyst());

            assertThatCode(() -> profileAuthorization.exigirPerfil(UserProfile.ANALIST))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("recusa quando o perfil pedido e diferente do do utilizador")
        void perfilDiferenteNegado() {
            autenticarComo(TestFixtures.analyst());

            assertThatThrownBy(() -> profileAuthorization.exigirPerfil(UserProfile.SUPERVISOR))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage(MENSAGEM);
        }
    }

    @Test
    @DisplayName("a comparacao de perfil nao usa igualdade de texto, para nao passar por engano")
    void comparacaoEstrita() {
        User user = TestFixtures.analyst();
        assertThat(user.getProfile()).isNotEqualTo(UserProfile.SUPERVISOR);
    }
}
