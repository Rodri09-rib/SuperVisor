package domain.model.entities;

import domain.model.enums.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Entidade User")
class UserTest {

    @Nested
    @DisplayName("Construção e acessores")
    class ConstrucaoEAcessores {

        @Test
        @DisplayName("construtor completo preserva todos os campos")
        void construtorCompleto() {
            User user = new User(7L, "Maria", "maria@teste.com", "senha", UserProfile.SUPERVISOR);

            assertThat(user.getId()).isEqualTo(7L);
            assertThat(user.getName()).isEqualTo("Maria");
            assertThat(user.getEmail()).isEqualTo("maria@teste.com");
            assertThat(user.getPassword()).isEqualTo("senha");
            assertThat(user.getProfile()).isEqualTo(UserProfile.SUPERVISOR);
        }

        @Test
        @DisplayName("construtor vazio deixa todos os campos nulos")
        void construtorVazio() {
            User user = new User();

            assertThat(user.getId()).isNull();
            assertThat(user.getName()).isNull();
            assertThat(user.getEmail()).isNull();
            assertThat(user.getPassword()).isNull();
            assertThat(user.getProfile()).isNull();
        }

        @Test
        @DisplayName("setters alteram cada campo individualmente")
        void setters() {
            User user = new User();

            user.setId(1L);
            user.setName("João");
            user.setEmail("joao@teste.com");
            user.setPassword("123456");
            user.setProfile(UserProfile.ANALIST);

            assertThat(user.getId()).isEqualTo(1L);
            assertThat(user.getName()).isEqualTo("João");
            assertThat(user.getEmail()).isEqualTo("joao@teste.com");
            assertThat(user.getPassword()).isEqualTo("123456");
            assertThat(user.getProfile()).isEqualTo(UserProfile.ANALIST);
        }
    }

    @Nested
    @DisplayName("Implementação de UserDetails")
    class ImplementacaoUserDetails {

        @Test
        @DisplayName("getUsername devolve o e-mail, pois o e-mail é o identificador de login")
        void getUsernameDevolveEmail() {
            User user = new User(1L, "João", "joao@teste.com", "123456", UserProfile.ANALIST);

            assertThat(user.getUsername()).isEqualTo("joao@teste.com");
        }

        @Test
        @DisplayName("getPassword devolve a senha armazenada (hash BCrypt na prática)")
        void getPassword() {
            User user = new User();
            user.setPassword("$2a$10$hashQualquer");

            assertThat(user.getPassword()).isEqualTo("$2a$10$hashQualquer");
        }

        @Test
        @DisplayName("a conta é sempre considerada ativa, não expirada e desbloqueada")
        void flagsDeConta() {
            User user = new User(1L, "João", "joao@teste.com", "123456", UserProfile.ANALIST);

            assertThat(user.isEnabled()).isTrue();
            assertThat(user.isAccountNonLocked()).isTrue();
            assertThat(user.isAccountNonExpired()).isTrue();
            assertThat(user.isCredentialsNonExpired()).isTrue();
        }

        @Test
        @DisplayName("a entidade realmente é um UserDetails")
        void implementsUserDetails() {
            assertThat(UserDetails.class).isAssignableFrom(User.class);
        }
    }

    @Nested
    @DisplayName("Mapeamento de perfis para autoridades")
    class Autoridades {

        @Test
        @DisplayName("perfil SUPERVISOL gera ROLE_SUPERVISOR")
        void perfilSupervisor() {
            User user = new User(1L, "Admin", "admin@teste.com", "x", UserProfile.SUPERVISOR);

            assertThat(user.getAuthorities())
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_SUPERVISOR");
        }

        @Test
        @DisplayName("perfil ANALIST gera ROLE_ANALIST")
        void perfilAnalist() {
            User user = new User(2L, "João", "joao@teste.com", "x", UserProfile.ANALIST);

            assertThat(user.getAuthorities())
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_ANALIST");
        }

        @Test
        @DisplayName("perfil nulo cai no fallback ROLE_USER em vez de quebrar")
        void perfilNuloUsaFallback() {
            User user = new User();
            user.setProfile(null);

            assertThat(user.getAuthorities())
                    .extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_USER");
        }

        @Test
        @DisplayName("cada utilizador tem exatamente uma autoridade")
        void umaUnicaAutoridade() {
            Collection<? extends GrantedAuthority> authorities =
                    new User(1L, "A", "a@t.com", "x", UserProfile.ANALIST).getAuthorities();

            assertThat(authorities).hasSize(1);
        }
    }
}
