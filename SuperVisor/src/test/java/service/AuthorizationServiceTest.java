package service;

import domain.model.entities.User;
import domain.model.enums.UserProfile;
import domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthorizationService")
class AuthorizationServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthorizationService authorizationService;

    @Test
    @DisplayName("é um UserDetailsService utilizável pelo DaoAuthenticationProvider")
    void implementaUserDetailsService() {
        assertThat(UserDetailsService.class).isAssignableFrom(AuthorizationService.class);
    }

    @Test
    @DisplayName("devolve o usuário encontrado pelo e-mail")
    void devolveUtilizadorEncontrado() {
        User user = new User(1L, "Administrador", "admin@teste.com", "hash", UserProfile.SUPERVISOR);
        when(userRepository.findByEmail("admin@teste.com")).thenReturn(user);

        UserDetails resultado = authorizationService.loadUserByUsername("admin@teste.com");

        assertThat(resultado).isSameAs(user);
        assertThat(resultado.getUsername()).isEqualTo("admin@teste.com");
        verify(userRepository).findByEmail("admin@teste.com");
    }

    @Test
    void emailDesconhecidoDevolveNull() {
        org.junit.jupiter.api.Assertions.assertThrows(UsernameNotFoundException.class, () -> {
            authorizationService.loadUserByUsername("desconhecido@teste.com");
        });
    }

    @Test
    void emailVazioDevolveNull() {
        org.junit.jupiter.api.Assertions.assertThrows(UsernameNotFoundException.class, () -> {
            authorizationService.loadUserByUsername("");
        });
    }
}