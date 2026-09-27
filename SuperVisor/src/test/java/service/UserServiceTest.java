package service;

import domain.dto.ActiveUserDTO;
import domain.dto.CreateUserRequestDTO;
import domain.model.entities.User;
import domain.model.enums.UserProfile;
import domain.repository.UserRepository;
import exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import tests.support.TestFixtures;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService — criação de utilizadores")
class UserServiceTest {

    private static final String EMAIL_LIVRE = "nova.pessoa@teste.com";

    @Mock
    private UserRepository userRepository;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService();
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        // O BCrypt real, e nao um mock: o que se verifica aqui e a encriptacao
        // propriamente dita, e um encoder simulado confirmaria sempre o que
        // lhe fosse pedido.
        ReflectionTestUtils.setField(service, "passwordEncoder", new BCryptPasswordEncoder());
    }

    private CreateUserRequestDTO pedido() {
        return new CreateUserRequestDTO(
                "João Silva", EMAIL_LIVRE, "segredo123", UserProfile.ANALIST, null);
    }

    /** Faz o e-mail parecer livre e o save devolver a própria entidade. */
    private void emailLivre() {
        when(userRepository.findByEmail(EMAIL_LIVRE)).thenReturn(null);
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    /** As entidades que chegaram ao repositório, por ordem de chamada. */
    private List<User> gravados() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        return captor.getAllValues();
    }

    @Nested
    @DisplayName("Utilizador novo")
    class Sucesso {

        @Test
        @DisplayName("grava a senha encriptada, nunca em claro")
        void encriptaAPassword() {
            emailLivre();

            service.criar(pedido());

            String hash = gravados().get(0).getPassword();
            assertThat(hash).isNotEqualTo("segredo123").startsWith("$2");
        }

        @Test
        @DisplayName("a senha gravada deixa o BCrypt reconhecer a password original")
        void passwordGravadaAutentica() {
            emailLivre();

            service.criar(pedido());

            PasswordEncoder encoder = new BCryptPasswordEncoder();
            assertThat(encoder.matches("segredo123", gravados().get(0).getPassword())).isTrue();
        }

        @Test
        @DisplayName("duas passwords iguais dão hashes diferentes, por causa do sal do BCrypt")
        void hashNaoEhDeterministico() {
            emailLivre();

            service.criar(pedido());
            service.criar(pedido());

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(2)).save(captor.capture());
            assertThat(captor.getAllValues().get(0).getPassword())
                    .isNotEqualTo(captor.getAllValues().get(1).getPassword());
        }

        @Test
        @DisplayName("guarda o nome sem espaços nas pontas e o e-mail tal como foi enviado")
        void limpaONome() {
            emailLivre();

            service.criar(new CreateUserRequestDTO(
                    "  João Silva  ", EMAIL_LIVRE, "segredo123", UserProfile.ANALIST, null));

            User gravado = gravados().get(0);
            assertThat(gravado.getName()).isEqualTo("João Silva");
            assertThat(gravado.getEmail()).isEqualTo(EMAIL_LIVRE);
        }

        @Test
        @DisplayName("um pedido sem o campo active grava a conta ativa")
        void activePorOmissao() {
            emailLivre();

            service.criar(pedido());

            assertThat(gravados().get(0).isActive()).isTrue();
        }

        @Test
        @DisplayName("active=false no pedido é respeitado, e não substituído pelo predefinido")
        void activeFalseERespeitado() {
            emailLivre();

            service.criar(new CreateUserRequestDTO(
                    "João Silva", EMAIL_LIVRE, "segredo123", UserProfile.ANALIST, false));

            assertThat(gravados().get(0).isActive()).isFalse();
        }

        @Test
        @DisplayName("devolve o utilizador criado, sem a senha no resultado")
        void respostaNaoIncluiASenha() {
            when(userRepository.findByEmail(EMAIL_LIVRE)).thenReturn(null);
            when(userRepository.save(any(User.class)))
                    .thenAnswer(invocacao -> {
                        User user = invocacao.getArgument(0);
                        user.setId(7L);
                        return user;
                    });

            ActiveUserDTO resposta = service.criar(pedido());

            assertThat(resposta.id()).isEqualTo(7L);
            assertThat(resposta.name()).isEqualTo("João Silva");
            assertThat(resposta.email()).isEqualTo(EMAIL_LIVRE);
            assertThat(resposta.profile()).isEqualTo(UserProfile.ANALIST);
        }
    }

    @Nested
    @DisplayName("E-mail já cadastrado")
    class EmailEmUso {

        @Test
        @DisplayName("lança RegraDeNegocioException com mensagem para o utilizador")
        void lancaRegraDeNegocio() {
            when(userRepository.findByEmail(EMAIL_LIVRE))
                    .thenReturn(TestFixtures.supervisor());

            assertThatThrownBy(() -> service.criar(pedido()))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("E-mail já cadastrado.");
        }

        @Test
        @DisplayName("não chega a gravar, para não bater na restrição unique da coluna")
        void naoGrava() {
            when(userRepository.findByEmail(EMAIL_LIVRE))
                    .thenReturn(TestFixtures.supervisor());

            assertThatThrownBy(() -> service.criar(pedido()))
                    .isInstanceOf(RegraDeNegocioException.class);

            verify(userRepository, never()).save(any(User.class));
        }
    }
}
