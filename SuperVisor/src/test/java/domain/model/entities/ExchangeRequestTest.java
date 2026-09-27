package domain.model.entities;

import domain.model.enums.ExchangeStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Entidade ExchangeRequest")
class ExchangeRequestTest {

    @Test
    @DisplayName("nova solicitação nasce PENDING, num valor que o serviço reconhece como pendente")
    void statusPadrao() {
        ExchangeRequest request = new ExchangeRequest();

        // O valor anterior era "PENDENTE", em português, e não coincidia com o
        // "PENDING" que o serviço comparava ao responder. Um pedido construído
        // sem estado explícito ficava assim impossível de responder.
        assertThat(request.getStatus()).isEqualTo(ExchangeStatus.PENDING);
        assertThat(request.getStatus().isPendente()).isTrue();
    }

    @Test
    @DisplayName("construtor completo preserva todos os campos")
    void construtorCompleto() {
        ShiftScheduling origem = new ShiftScheduling();
        ShiftScheduling destino = new ShiftScheduling();
        User requisitante = new User();
        OffsetDateTime agora = OffsetDateTime.now();

        ExchangeRequest request = new ExchangeRequest(
                9L, origem, destino, requisitante, ExchangeStatus.PENDING, agora);

        assertThat(request.getId()).isEqualTo(9L);
        assertThat(request.getSourceAllocation()).isSameAs(origem);
        assertThat(request.getDestinationAllocation()).isSameAs(destino);
        assertThat(request.getRequestingUser()).isSameAs(requisitante);
        assertThat(request.getStatus()).isEqualTo(ExchangeStatus.PENDING);
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
        assertThat(request.getStatus()).isEqualTo(ExchangeStatus.PENDING);
    }

    @Test
    @DisplayName("um pedido novo ainda não foi respondido, logo a data de resposta é nula")
    void dataDeRespostaNulaQuandoPendente() {
        ExchangeRequest request = new ExchangeRequest();

        assertThat(request.getApprovalDate()).isNull();
    }

    @Test
    @DisplayName("o colega pedido e a data de resposta são preenchíveis")
    void camposDeResposta() {
        ExchangeRequest request = new ExchangeRequest();
        User colega = new User();
        OffsetDateTime resposta = OffsetDateTime.now();

        request.setRequestedUser(colega);
        request.setStatus(ExchangeStatus.APPROVED);
        request.setApprovalDate(resposta);

        assertThat(request.getRequestedUser()).isSameAs(colega);
        assertThat(request.getStatus()).isEqualTo(ExchangeStatus.APPROVED);
        assertThat(request.getApprovalDate()).isEqualTo(resposta);
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
        request.setStatus(ExchangeStatus.APPROVED);
        request.setCreationDate(agora);

        assertThat(request.getId()).isEqualTo(2L);
        assertThat(request.getSourceAllocation()).isSameAs(origem);
        assertThat(request.getDestinationAllocation()).isSameAs(destino);
        assertThat(request.getRequestingUser()).isSameAs(requisitante);
        assertThat(request.getStatus()).isEqualTo(ExchangeStatus.APPROVED);
        assertThat(request.getCreationDate()).isEqualTo(agora);
    }

    @Test
    @DisplayName("o status é um enum, e não texto livre")
    void statusEhEnum() throws NoSuchFieldException {
        // Com String, o compilador aceitava qualquer valor e o erro só
        // aparecia em tempo de execução, como uma troca impossível de responder.
        // O enum transforma esse erro num erro de compilação.
        assertThat(ExchangeRequest.class.getDeclaredField("status").getType())
                .isEqualTo(ExchangeStatus.class);
    }

    @Test
    @DisplayName("APPROVED não é o valor neutro que existia antes: aceitar chamava-se ACCEPTED")
    void estadoAprovado() {
        assertThat(ExchangeStatus.APPROVED.name()).isEqualTo("APPROVED");
        assertThat(ExchangeStatus.values())
                .containsExactlyInAnyOrder(ExchangeStatus.PENDING,
                        ExchangeStatus.APPROVED, ExchangeStatus.REJECTED);
    }
}
