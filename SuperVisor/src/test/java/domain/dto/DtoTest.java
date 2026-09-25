package domain.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DTOs de entrada")
class DtoTest {

    @Nested
    @DisplayName("LoginDTO")
    class Login {

        @Test
        @DisplayName("expõe e-mail e senha")
        void acessores() {
            LoginDTO dto = new LoginDTO("admin@teste.com", "123456");

            assertThat(dto.email()).isEqualTo("admin@teste.com");
            assertThat(dto.password()).isEqualTo("123456");
        }

        @Test
        @DisplayName("aceita campos nulos, pois não há @NotNull no projeto")
        void aceitaNulos() {
            LoginDTO dto = new LoginDTO(null, null);

            assertThat(dto.email()).isNull();
            assertThat(dto.password()).isNull();
        }

        @Test
        @DisplayName("dois DTOs com os mesmos campos são iguais")
        void igualdade() {
            assertThat(new LoginDTO("a@t.com", "123"))
                    .isEqualTo(new LoginDTO("a@t.com", "123"));
        }
    }

    @Nested
    @DisplayName("CreateScaleDTO")
    class CreateScale {

        @Test
        @DisplayName("expõe nome, datas e o id do criador")
        void acessores() {
            CreateScaleDTO dto = new CreateScaleDTO(
                    "Escala Outubro", LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 31), 1L);

            assertThat(dto.name()).isEqualTo("Escala Outubro");
            assertThat(dto.initialDate()).isEqualTo(LocalDate.of(2025, 10, 1));
            assertThat(dto.endDate()).isEqualTo(LocalDate.of(2025, 10, 31));
            assertThat(dto.createdById()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("ExchangeRequestDTO")
    class ExchangeRequest {

        @Test
        @DisplayName("expõe as duas alocações envolvidas na troca")
        void acessores() {
            ExchangeRequestDTO dto = new ExchangeRequestDTO(1L, 2L);

            assertThat(dto.originAllocationId()).isEqualTo(1L);
            assertThat(dto.destinationAllocationId()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("RespondExchangeDTO")
    class RespondExchange {

        @Test
        @DisplayName("isAccepted devolve true quando o pedido é aceito")
        void aceito() {
            assertThat(new RespondExchangeDTO(true).isAccepted()).isTrue();
        }

        @Test
        @DisplayName("isAccepted devolve false quando o pedido é recusado")
        void recusado() {
            assertThat(new RespondExchangeDTO(false).isAccepted()).isFalse();
        }

        @Test
        @DisplayName("o campo ausente no JSON faz o Jackson usar o default false")
        void defaultDoJackson() throws Exception {
            RespondExchangeDTO dto = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue("{}", RespondExchangeDTO.class);

            assertThat(dto.isAccepted()).isFalse();
        }

        @Test
        @DisplayName("o JSON é desserializado corretamente a partir de isAccepted")
        void desserializacao() throws Exception {
            RespondExchangeDTO dto = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue("{\"isAccepted\":true}", RespondExchangeDTO.class);

            assertThat(dto.isAccepted()).isTrue();
        }
    }
}
