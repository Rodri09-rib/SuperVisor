package tests.integration;

import domain.model.entities.EditionScale;
import domain.model.entities.ExchangeRequest;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.ExchangeStatus;
import domain.model.enums.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tests.support.AbstractApiIntegrationTest;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filtros do histórico de trocas contra a base de dados a sério.
 *
 * <p>Estes testes existem porque os correspondentes com mocks não provam nada
 * sobre a consulta. Um {@code Specification} pode passar todos os testes de
 * unidade — o mock não distingue {@code requestingUser} de {@code requestedUser}
 * porque ambos são um ecrã vazio — e falhar na base por um nome de atributo
 * errado, por um {@code join} a um enum, ou por um limite de data deslocado.
 * Só a base real distingue essas coisas.
 */
@DisplayName("Histórico de trocas — filtros")
class ExchangeHistoryApiIntegrationTest extends AbstractApiIntegrationTest {

    private User admin;
    private User joao;
    private User maria;
    private EditionScale escala;
    private ShiftScheduling turnoAdmin;
    private ShiftScheduling turnoJoao;

    private String tokenAdmin;
    private String tokenJoao;

    /**
     * Fixo com uma data de criação explícita, para os limites de intervalo
     * terem algo com que se medir. O serviço carimba a data atual, e sem
     * escrever à mão todas as pedidos nasceriam no mesmo instante.
     */
    private ExchangeRequest pedido(ExchangeStatus status, User requerente, User colega,
                                   LocalDate dia) {
        ExchangeRequest pedido = new ExchangeRequest();
        pedido.setSourceAllocation(turnoDe(requerente));
        pedido.setDestinationAllocation(turnoDe(colega));
        pedido.setRequestingUser(requerente);
        pedido.setRequestedUser(colega);
        pedido.setStatus(status);
        pedido.setCreationDate(dia.atTime(14, 0).atZone(ZoneId.systemDefault()).toOffsetDateTime());
        if (status != ExchangeStatus.PENDING) {
            pedido.setApprovalDate(dia.plusDays(1).atTime(9, 0)
                    .atZone(ZoneId.systemDefault()).toOffsetDateTime());
        }
        return pedido;
    }

    private ShiftScheduling turnoDe(User user) {
        return user.getId().equals(admin.getId()) ? turnoAdmin : turnoJoao;
    }

    /**
     * Grava um pedido e devolve o id que a base lhe deu.
     */
    private Long gravar(ExchangeRequest pedido) {
        return exchangeRequestRepository.saveAndFlush(pedido).getId();
    }

    @BeforeEach
    void prepararCenario() {
        admin = criarUsuario("Administrador", "admin@teste.com", UserProfile.SUPERVISOR);
        joao = criarUsuario("João", "joao@teste.com", UserProfile.ANALIST);
        maria = criarUsuario("Maria", "maria@teste.com", UserProfile.ANALIST);
        tokenAdmin = tokenService.gerarToken(admin);
        tokenJoao = tokenService.gerarToken(joao);

        escala = criarEscala("Escala Novembro", admin);
        turnoAdmin = criarAlocacao(escala, admin);
        turnoJoao = criarAlocacao(escala, joao);
    }

    @Nested
    @DisplayName("Filtros")
    class Filtros {

        @Test
        @DisplayName("sem filtros, o supervisor vê todos os pedidos, do mais recente para o mais antigo")
        void supervisorVeTodosOrdenados() throws Exception {
            gravar(pedido(ExchangeStatus.PENDING, joao, admin, LocalDate.of(2026, 3, 10)));
            Long maisRecente = gravar(
                    pedido(ExchangeStatus.APPROVED, joao, admin, LocalDate.of(2026, 3, 20)));

            mockMvc.perform(get("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(maisRecente))
                    .andExpect(jsonPath("$[1].status").value("PENDING"));
        }

        @Test
        @DisplayName("o filtro por estado mantém apenas o estado pedido")
        void filtroPorEstado() throws Exception {
            gravar(pedido(ExchangeStatus.APPROVED, joao, admin, LocalDate.of(2026, 3, 10)));
            gravar(pedido(ExchangeStatus.PENDING, joao, admin, LocalDate.of(2026, 3, 11)));
            gravar(pedido(ExchangeStatus.REJECTED, joao, admin, LocalDate.of(2026, 3, 12)));

            for (ExchangeStatus estado : ExchangeStatus.values()) {
                mockMvc.perform(get("/api/v1/exchanges")
                                .header("Authorization", "Bearer " + tokenAdmin)
                                .param("status", estado.name()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()").value(1))
                        .andExpect(jsonPath("$[0].status").value(estado.name()));
            }
        }

        @Test
        @DisplayName("o filtro por pessoa devolve os pedidos em que essa pessoa é parte, dos dois lados")
        void filtroPorPessoaDosDoisLados() throws Exception {
            gravar(pedido(ExchangeStatus.PENDING, joao, admin, LocalDate.of(2026, 3, 10)));
            gravar(pedido(ExchangeStatus.PENDING, admin, maria, LocalDate.of(2026, 3, 11)));

            // admin é o colega no primeiro e o requerente no segundo: aparece
            // nos dois, e é isso que um filtro de "as minhas trocas" tem de
            // apanhar.
            mockMvc.perform(get("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .param("userId", String.valueOf(admin.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));

            mockMvc.perform(get("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .param("userId", String.valueOf(maria.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].requesterName").value("Administrador"));
        }

        @Test
        @DisplayName("o intervalo de datas é inclusivo: um pedido feito no último dia entra")
        void intervaloDeDatasInclusivo() throws Exception {
            gravar(pedido(ExchangeStatus.PENDING, joao, admin, LocalDate.of(2026, 3, 10)));
            gravar(pedido(ExchangeStatus.PENDING, joao, admin, LocalDate.of(2026, 3, 15)));
            gravar(pedido(ExchangeStatus.PENDING, joao, admin, LocalDate.of(2026, 3, 20)));

            // O pedido das 14h de dia 20 tem de entrar. Com a data final
            // usada como limite, às 00h00 de dia 20, ficaria de fora — e era
            // isso que tornava o filtro de datas enganador.
            mockMvc.perform(get("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .param("dataInicial", "2026-03-15")
                            .param("dataFim", "2026-03-20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("o intervalo de datas com um só dos limites também filtra")
        void intervaloDeUmLimiteSo() throws Exception {
            gravar(pedido(ExchangeStatus.PENDING, joao, admin, LocalDate.of(2026, 3, 10)));
            gravar(pedido(ExchangeStatus.PENDING, joao, admin, LocalDate.of(2026, 3, 20)));

            mockMvc.perform(get("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .param("dataInicial", "2026-03-15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));

            mockMvc.perform(get("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .param("dataFim", "2026-03-15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("os filtros combinam-se")
        void filtrosCombinados() throws Exception {
            gravar(pedido(ExchangeStatus.APPROVED, joao, admin, LocalDate.of(2026, 3, 10)));
            gravar(pedido(ExchangeStatus.PENDING, joao, admin, LocalDate.of(2026, 3, 12)));
            gravar(pedido(ExchangeStatus.PENDING, joao, maria, LocalDate.of(2026, 3, 12)));

            mockMvc.perform(get("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .param("status", "PENDING")
                            .param("userId", String.valueOf(admin.getId()))
                            .param("dataInicial", "2026-03-11")
                            .param("dataFim", "2026-03-15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].requesterName").value("João"));
        }

        @Test
        @DisplayName("os pedidos trazem os nomes das duas partes, e não os ids")
        void devolveNomesDasDuasPartes() throws Exception {
            gravar(pedido(ExchangeStatus.PENDING, joao, admin, LocalDate.of(2026, 3, 10)));

            mockMvc.perform(get("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].requesterName").value("João"))
                    .andExpect(jsonPath("$[0].requestedName").value("Administrador"))
                    .andExpect(jsonPath("$[0].requesterId").value(joao.getId()))
                    .andExpect(jsonPath("$[0].requestedId").value(admin.getId()))
                    // A senha nunca sai: é um DTO achatado, e não a entidade.
                    .andExpect(jsonPath("$[0].requesterPassword").doesNotExist())
                    .andExpect(jsonPath("$[0].requestingUser").doesNotExist());
        }

        @Test
        @DisplayName("um intervalo que não apanha nada devolve uma lista vazia, e não um erro")
        void intervaloSemResultados() throws Exception {
            gravar(pedido(ExchangeStatus.PENDING, joao, admin, LocalDate.of(2026, 3, 10)));

            mockMvc.perform(get("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .param("dataInicial", "2026-04-01")
                            .param("dataFim", "2026-04-30"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("Quem vê o quê")
    class Visibilidade {

        @Test
        @DisplayName("um analista vê os pedidos em que é parte, e só esses")
        void analistaVeApenasOsSeus() throws Exception {
            gravar(pedido(ExchangeStatus.PENDING, joao, admin, LocalDate.of(2026, 3, 10)));
            gravar(pedido(ExchangeStatus.PENDING, maria, admin, LocalDate.of(2026, 3, 11)));
            // Um pedido em que nenhum dos dois é parte, para confirmar que não
            // entra por ser do supervisor.
            gravar(pedido(ExchangeStatus.PENDING, admin, maria, LocalDate.of(2026, 3, 12)));

            mockMvc.perform(get("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenJoao))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].requesterName").value("João"));
        }

        @Test
        @DisplayName("um analista não escapa ao próprio filtro, nem pedindo o de outra pessoa")
        void analistaNaoSeFingeDeSupervisor() throws Exception {
            gravar(pedido(ExchangeStatus.PENDING, joao, admin, LocalDate.of(2026, 3, 10)));
            gravar(pedido(ExchangeStatus.PENDING, maria, admin, LocalDate.of(2026, 3, 11)));

            mockMvc.perform(get("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenJoao)
                            .param("userId", String.valueOf(maria.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].requesterName").value("João"));
        }

        @Test
        @DisplayName("um analista não vê o pedido de uma colega que não é parte")
        void analisarNaoVePedidosDeTerceiros() throws Exception {
            gravar(pedido(ExchangeStatus.PENDING, maria, admin, LocalDate.of(2026, 3, 11)));

            mockMvc.perform(get("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenJoao))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("sem token, o histórico não é servido a ninguém")
        void semTokenNaoVeNada() throws Exception {
            gravar(pedido(ExchangeStatus.PENDING, joao, admin, LocalDate.of(2026, 3, 10)));

            mockMvc.perform(get("/api/v1/exchanges"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("a lista não vaza a palavra-passe, nem a entidade, a nenhum dos lados")
        void naoVazaDadosSensiveis() throws Exception {
            gravar(pedido(ExchangeStatus.PENDING, joao, admin, LocalDate.of(2026, 3, 10)));

            var resposta = mockMvc.perform(get("/api/v1/exchanges")
                            .header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(resposta)
                    .doesNotContain("123456")
                    .doesNotContain("password")
                    .doesNotContain("sourceAllocation")
                    .doesNotContain("requestedUser");
        }
    }
}
