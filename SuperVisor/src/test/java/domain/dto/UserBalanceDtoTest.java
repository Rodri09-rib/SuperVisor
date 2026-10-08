package domain.dto;

import domain.model.entities.User;
import domain.model.enums.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Saldos nos DTOs do frontend.
 *
 * <p>O cartão «O seu saldo de folgas» e os badges da tabela leem estes campos
 * tal como chegam do JSON: um nulo ali é lido como zero pelo JavaScript, mas é
 * também sinal de que o backend mandou meio caminho. O contrato é número,
 * sempre — nulo converte-se em zero na origem.
 */
@DisplayName("DTOs de saldo — nulos convertem-se em zero")
class UserBalanceDtoTest {

    private User utilizadorComSaldos(BigDecimal accumulatedLeaves,
                                     BigDecimal pendingCompensationDays) {
        User user = new User(7L, "Joana", "joana@teste.com", "123456", UserProfile.ANALIST);
        user.setAccumulatedLeaves(accumulatedLeaves);
        user.setPendingCompensationDays(pendingCompensationDays);
        return user;
    }

    @Nested
    @DisplayName("CurrentUserDTO.from")
    class CurrentUser {

        @Test
        @DisplayName("saldo de folgas nulo é entregue como zero, não como null")
        void saldoNuloViraZero() {
            CurrentUserDTO dto = CurrentUserDTO.from(utilizadorComSaldos(null, null));

            assertThat(dto.accumulatedLeaves()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(dto.pendingCompensationDays()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("saldo existente é preservado tal como está")
        void saldoExistentePreservado() {
            CurrentUserDTO dto = CurrentUserDTO.from(
                    utilizadorComSaldos(new BigDecimal("1.5"), new BigDecimal("2.0")));

            assertThat(dto.accumulatedLeaves()).isEqualByComparingTo("1.5");
            assertThat(dto.pendingCompensationDays()).isEqualByComparingTo("2.0");
        }

        @Test
        @DisplayName("saldo negativo é preservado: quem antecipou folgas continua a dever")
        void saldoNegativoPreservado() {
            CurrentUserDTO dto = CurrentUserDTO.from(
                    utilizadorComSaldos(new BigDecimal("-5"), BigDecimal.ZERO));

            assertThat(dto.accumulatedLeaves()).isEqualByComparingTo("-5");
        }
    }

    @Nested
    @DisplayName("ActiveUserDTO.from")
    class ActiveUser {

        @Test
        @DisplayName("saldo de folgas nulo é entregue como zero, não como null")
        void saldoNuloViraZero() {
            ActiveUserDTO dto = ActiveUserDTO.from(utilizadorComSaldos(null, null));

            assertThat(dto.accumulatedLeaves()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(dto.pendingCompensationDays()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("saldo existente é preservado tal como está")
        void saldoExistentePreservado() {
            ActiveUserDTO dto = ActiveUserDTO.from(
                    utilizadorComSaldos(new BigDecimal("0.5"), BigDecimal.ZERO));

            assertThat(dto.accumulatedLeaves()).isEqualByComparingTo("0.5");
        }
    }
}
