package service;

import domain.dto.ActiveUserDTO;
import domain.model.entities.User;
import domain.model.enums.UserProfile;
import domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Compensação — CompensationService")
class CompensationServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CompensationService compensationService;

    private User joao;

    @BeforeEach
    void setUp() {
        joao = tests.support.TestFixtures.analyst();
        joao.setId(2L);
    }

    @Nested
    @DisplayName("Aplicar um delta")
    class Aplicar {

        @Test
        @DisplayName("um delta positivo soma dívida ao saldo")
        void somaDivida() {
            joao.setPendingCompensationDays(new BigDecimal("1.5"));

            compensationService.aplicar(joao, BigDecimal.ONE);

            assertThat(joao.getPendingCompensationDays()).isEqualByComparingTo("2.5");
        }

        @Test
        @DisplayName("um delta negativo abate dívida")
        void abateDivida() {
            joao.setPendingCompensationDays(new BigDecimal("3"));

            compensationService.aplicar(joao, new BigDecimal("-1.5"));

            assertThat(joao.getPendingCompensationDays()).isEqualByComparingTo("1.5");
        }

        @Test
        @DisplayName("o saldo nunca fica negativo: abater a mais deixa tudo em zero")
        void cortaEmZero() {
            joao.setPendingCompensationDays(BigDecimal.ONE);

            compensationService.aplicar(joao, new BigDecimal("-5"));

            // Um abate maior que a dívida não é crédito a favor de ninguém: é
            // um ajuste errado, e a única leitura segura é zero. Deixar -4
            // diria que o colaborador tem direito a quatro dias de falta.
            assertThat(joao.getPendingCompensationDays()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("um saldo ainda não gravado começa em zero, não em nulo")
        void saldoAusenteComecaEmZero() {
            joao.setPendingCompensationDays(null);

            compensationService.aplicar(joao, new BigDecimal("0.5"));

            assertThat(joao.getPendingCompensationDays()).isEqualByComparingTo("0.5");
        }

        @Test
        @DisplayName("um delta nulo ou zero não mexe no saldo")
        void deltaNeutro() {
            joao.setPendingCompensationDays(BigDecimal.ONE);

            compensationService.aplicar(joao, null);
            compensationService.aplicar(joao, BigDecimal.ZERO);

            assertThat(joao.getPendingCompensationDays()).isEqualByComparingTo(BigDecimal.ONE);
        }

        @Test
        @DisplayName("sem utilizador, não há nada a mover — e não há exceção")
        void semUtilizador() {
            compensationService.aplicar(null, BigDecimal.ONE);
        }
    }

    @Nested
    @DisplayName("Ajuste manual do supervisor")
    class Ajustar {

        @Test
        @DisplayName("devolve o colaborador com o saldo já atualizado")
        void devolveSaldoAtualizado() {
            joao.setPendingCompensationDays(new BigDecimal("2"));
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));
            when(userRepository.save(any())).thenAnswer(resposta -> resposta.getArgument(0));

            ActiveUserDTO dto = compensationService.ajustar(2L, new BigDecimal("-0.5"));

            assertThat(dto.pendingCompensationDays()).isEqualByComparingTo("1.5");
            assertThat(dto.id()).isEqualTo(2L);
            verify(userRepository).save(joao);
        }

        @Test
        @DisplayName("um ajuste que excede a dívida devolve zero, não saldo negativo")
        void ajusteQueExcedeADivida() {
            joao.setPendingCompensationDays(BigDecimal.ONE);
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));
            when(userRepository.save(any())).thenAnswer(resposta -> resposta.getArgument(0));

            ActiveUserDTO dto = compensationService.ajustar(2L, new BigDecimal("-10"));

            assertThat(dto.pendingCompensationDays()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("um utilizador inexistente dá erro, e nada é gravado")
        void utilizadorInexistente() {
            when(userRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> compensationService.ajustar(404L, BigDecimal.ONE))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Usuário não encontrado.");

            verify(userRepository, never()).save(any());
        }
    }
}
