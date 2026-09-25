package domain.model.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Entidade ExchangeRequest")
class ExchangeRequestTest {

    @Test
    @DisplayName("nova solicitação nasce com o status textual PENDENTE")
    void statusPadrao() {
        ExchangeRequest request = new ExchangeRequest();

        assertThat(request.getStatus()).isEqualTo("PENDENTE");
    }

    @Test
    @DisplayName("construtor completo preserva todos os campos")
    void construtorCompleto() {
        ShiftScheduling origem = new ShiftScheduling();
        ShiftScheduling destino = new ShiftScheduling();
        User requisitante = new User();
        OffsetDateTime agora = OffsetDateTime.now();

        ExchangeRequest request = new ExchangeRequest(
                9L, origem, destino, requisitante, "PENDING", agora);

        assertThat(request.getId()).isEqualTo(9L);
        assertThat(request.getSourceAllocation()).isSameAs(origem);
        assertThat(request.getDestinationAllocation()).isSameAs(destino);
        assertThat(request.getRequestingUser()).isSameAs(requisitante);
        assertThat(request.getStatus()).isEqualTo("PENDING");
        assertThat(request.getCreationDate()).isEqualTo(agora);
    }

    @Test
    @DisplayName("construtor vazio deixa apenas o status preenchido")
    void construtorVazio() {
        ExchangeRequest request = new ExchangeRequest();

        assertThat(request.getId()).isNull();
        assertThat(request.getSourceAllocation()).isNull();
        assertThat(request.getDestinationAllocation()).isNull();
        assertThat(request.getRequestingUser()).isNull();
        assertThat(request.getCreationDate()).isNull();
        assertThat(request.getStatus()).isEqualTo("PENDENTE");
    }

    @Test
    @DisplayName("setters cobrem todos os campos")
    void setters() {
        ExchangeRequest request = new ExchangeRequest();
        ShiftScheduling origem = new ShiftScheduling();
        ShiftScheduling destino = new ShiftScheduling();
        User requisitante = new User();
        OffsetDateTime agora = OffsetDateTime.now();

        request.setId(2L);
        request.setSourceAllocation(origem);
        request.setDestinationAllocation(destino);
        request.setRequestingUser(requisitante);
        request.setStatus("ACCEPTED");
        request.setCreationDate(agora);

        assertThat(request.getId()).isEqualTo(2L);
        assertThat(request.getSourceAllocation()).isSameAs(origem);
        assertThat(request.getDestinationAllocation()).isSameAs(destino);
        assertThat(request.getRequestingUser()).isSameAs(requisitante);
        assertThat(request.getStatus()).isEqualTo("ACCEPTED");
        assertThat(request.getCreationDate()).isEqualTo(agora);
    }

    @Test
    @DisplayName("o status é String, não enum: os valores usados são PENDING/ACCEPTED/REJECTED")
    void statusEhTextoNaoEnum() throws NoSuchFieldException {
        assertThat(ExchangeRequest.class.getDeclaredField("status").getType()).isEqualTo(String.class);
    }
}
