package domain.repository;

import domain.model.entities.User;
import domain.model.enums.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import tests.support.AbstractJpaIntegrationTest;
import tests.support.TestFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UserRepository (persistência)")
class UserRepositoryTest extends AbstractJpaIntegrationTest {

    @Nested
    @DisplayName("Operações herdadas de JpaRepository")
    class JpaRepository {

        @Test
        @DisplayName("save gera id sequencial e devolve a entidade preenchida")
        void saveAtribuiId() {
            User user = userRepository.saveAndFlush(
                    TestFixtures.user("Ana", "ana@teste.com", UserProfile.ANALIST));

            assertThat(user.getId()).isNotNull();
            assertThat(userRepository.findById(user.getId())).contains(user);
        }

        @Test
        @DisplayName("findById devolve vazio para id inexistente")
        void findByIdInexistente() {
            assertThat(userRepository.findById(9999L)).isEmpty();
        }

        @Test
        @DisplayName("findAll devolve todos os usuários gravados")
        void findAll() {
            userRepository.saveAndFlush(TestFixtures.user("Ana", "ana@teste.com", UserProfile.ANALIST));
            userRepository.saveAndFlush(TestFixtures.user("Bia", "bia@teste.com", UserProfile.SUPERVISOR));

            assertThat(userRepository.findAll()).hasSize(2)
                    .extracting(User::getEmail)
                    .containsExactlyInAnyOrder("ana@teste.com", "bia@teste.com");
        }

        @Test
        @DisplayName("count e exists refletem o estado real da tabela")
        void countEExists() {
            assertThat(userRepository.count()).isZero();
            assertThat(userRepository.existsById(1L)).isFalse();

            User saved = userRepository.saveAndFlush(
                    TestFixtures.user("Ana", "ana@teste.com", UserProfile.ANALIST));

            assertThat(userRepository.count()).isEqualTo(1);
            assertThat(userRepository.existsById(saved.getId())).isTrue();
        }

        @Test
        @DisplayName("saveAll persiste vários usuários de uma vez")
        void saveAll() {
            userRepository.saveAllAndFlush(java.util.List.of(
                    TestFixtures.user("Ana", "ana@teste.com", UserProfile.ANALIST),
                    TestFixtures.user("Bia", "bia@teste.com", UserProfile.SUPERVISOR),
                    TestFixtures.user("Cid", "cid@teste.com", UserProfile.ANALIST)));

            assertThat(userRepository.count()).isEqualTo(3);
        }

        @Test
        @DisplayName("deleteById remove o registro")
        void deleteById() {
            User saved = userRepository.saveAndFlush(
                    TestFixtures.user("Ana", "ana@teste.com", UserProfile.ANALIST));

            userRepository.deleteById(saved.getId());
            entityManager.flush();

            assertThat(userRepository.findById(saved.getId())).isEmpty();
        }

        @Test
        @DisplayName("save atualiza a entidade já existente em vez de inserir nova")
        void update() {
            User saved = userRepository.saveAndFlush(
                    TestFixtures.user("Ana", "ana@teste.com", UserProfile.ANALIST));

            saved.setName("Ana Paula");
            saved.setProfile(UserProfile.SUPERVISOR);
            userRepository.saveAndFlush(saved);
            entityManager.clear();

            User recarregado = userRepository.findById(saved.getId()).orElseThrow();
            assertThat(recarregado.getName()).isEqualTo("Ana Paula");
            assertThat(recarregado.getProfile()).isEqualTo(UserProfile.SUPERVISOR);
            assertThat(userRepository.count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findByEmail")
    class FindByEmail {

        @Test
        @DisplayName("devolve o usuário correspondente ao e-mail")
        void encontraPorEmail() {
            userRepository.saveAndFlush(
                    TestFixtures.user("Administrador", "admin@teste.com", UserProfile.SUPERVISOR));

            UserDetails encontrado = userRepository.findByEmail("admin@teste.com");

            assertThat(encontrado).isNotNull();
            assertThat(encontrado.getUsername()).isEqualTo("admin@teste.com");
            assertThat(((User) encontrado).getName()).isEqualTo("Administrador");
        }

        @Test
        @DisplayName("devolve null para e-mail inexistente")
        void emailInexistente() {
            assertThat(userRepository.findByEmail("nao.existe@teste.com")).isNull();
        }

        @Test
        @DisplayName("a pesquisa diferencia maiúsculas de minúsculas no PostgreSQL")
        void emailComMaiusculas() {
            userRepository.saveAndFlush(
                    TestFixtures.user("Administrador", "admin@teste.com", UserProfile.SUPERVISOR));

            assertThat(userRepository.findByEmail("ADMIN@teste.com")).isNull();
        }

        @Test
        @DisplayName("devolve a senha com hash tal como foi gravada")
        void devolveHashDaSenha() {
            userRepository.saveAndFlush(
                    TestFixtures.user("Administrador", "admin@teste.com", UserProfile.SUPERVISOR, "$2a$10$hash"));

            assertThat(userRepository.findByEmail("admin@teste.com").getPassword()).isEqualTo("$2a$10$hash");
        }

        @Test
        @DisplayName("devolve as autoridades derivadas do perfil")
        void devolveAutoridades() {
            userRepository.saveAndFlush(
                    TestFixtures.user("João", "joao@teste.com", UserProfile.ANALIST));

            assertThat(userRepository.findByEmail("joao@teste.com").getAuthorities())
                    .extracting(org.springframework.security.core.GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_ANALIST");
        }
    }

    @Nested
    @DisplayName("Mapeamento da tabela")
    class Mapeamento {

        @Test
        @DisplayName("o perfil é gravado como texto, não como ordinal")
        void perfilGravadoComoTexto() {
            User saved = userRepository.saveAndFlush(
                    TestFixtures.user("João", "joao@teste.com", UserProfile.ANALIST));
            entityManager.clear();

            Object perfilBruto = entityManager
                    .createNativeQuery("select profile from tb_user where id = :id")
                    .setParameter("id", saved.getId())
                    .getSingleResult();

            assertThat(perfilBruto).isEqualTo("ANALIST");
        }

        @Test
        @DisplayName("o e-mail tem restrição de unicidade no banco")
        void emailUnico() {
            userRepository.saveAndFlush(
                    TestFixtures.user("Ana", "ana@teste.com", UserProfile.ANALIST));

            assertThatThrownBy(() -> {
                userRepository.saveAndFlush(
                        TestFixtures.user("Outra Ana", "ana@teste.com", UserProfile.ANALIST));
                entityManager.flush();
            }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("o e-mail pode ser nulo em duplicidade, pois a coluna aceita null")
        void emailNuloAceito() {
            User user = TestFixtures.user("Sem email", null, UserProfile.ANALIST);

            assertThat(userRepository.saveAndFlush(user)).isNotNull();
            assertThat(userRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("os campos do perfil são lidos corretamente após recarga")
        void roundTripCompleto() {
            User saved = userRepository.saveAndFlush(
                    TestFixtures.user("Administrador", "admin@teste.com", UserProfile.SUPERVISOR, "hash123"));
            entityManager.clear();

            User recarregado = userRepository.findById(saved.getId()).orElseThrow();
            assertThat(recarregado.getName()).isEqualTo("Administrador");
            assertThat(recarregado.getEmail()).isEqualTo("admin@teste.com");
            assertThat(recarregado.getPassword()).isEqualTo("hash123");
            assertThat(recarregado.getProfile()).isEqualTo(UserProfile.SUPERVISOR);
        }
    }
}
