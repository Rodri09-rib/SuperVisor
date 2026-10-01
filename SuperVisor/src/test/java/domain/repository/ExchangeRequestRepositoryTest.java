package domain.repository;

import domain.model.entities.EditionScale;
import domain.model.entities.ExchangeRequest;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.ExchangeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tests.support.AbstractJpaIntegrationTest;
import tests.support.TestFixtures;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExchangeRequestRepository (persistência)")
class ExchangeRequestRepositoryTest extends AbstractJpaIntegrationTest {

    private User admin;
    private User joao;
    private EditionScale escala;
    private ShiftScheduling alocacaoAdmin;
    private ShiftScheduling alocacaoJoao;

    @BeforeEach
    void prepararBase() {
        admin = userRepository.saveAndFlush(
                TestFixtures.user("Administrador", "admin@teste.com", domain.model.enums.UserProfile.SUPERVISOR));
        joao = userRepository.saveAndFlush(
                TestFixtures.user("João", "joao@teste.com", domain.model.enums.UserProfile.ANALIST));
        escala = editionScaleRepository.saveAndFlush(TestFixtures.draftScale(admin));
        alocacaoAdmin = shiftSchedulingRepository.saveAndFlush(TestFixtures.allocation(escala, admin));
        alocacaoJoao = shiftSchedulingRepository.saveAndFlush(TestFixtures.allocation(escala, joao));
    }

    private ExchangeRequest novaPendente() {
        return novaPendenteEm(OffsetDateTime.now());
    }

    private ExchangeRequest novaPendenteEm(OffsetDateTime quando) {
        ExchangeRequest request = new ExchangeRequest();
        request.setSourceAllocation(alocacaoAdmin);
        request.setDestinationAllocation(alocacaoJoao);
        request.setRequestingUser(joao);
        request.setStatus(ExchangeStatus.PENDING);
        request.setCreationDate(quando);
        return request;
    }

    @Nested
    @DisplayName("Operações herdadas de JpaRepository")
    class JpaRepository {

        @Test
        @DisplayName("save persiste as três referências e a data de criação")
        void savePersisteReferencias() {
            ExchangeRequest request = exchangeRequestRepository.saveAndFlush(novaPendente());
            entityManager.clear();

            ExchangeRequest recarregada = exchangeRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(recarregada.getSourceAllocation().getId()).isEqualTo(alocacaoAdmin.getId());
            assertThat(recarregada.getDestinationAllocation().getId()).isEqualTo(alocacaoJoao.getId());
            assertThat(recarregada.getRequestingUser().getId()).isEqualTo(joao.getId());
            assertThat(recarregada.getCreationDate()).isNotNull();
        }

        @Test
        @DisplayName("a data de criação é preservada com o deslocamento de fuso enviado")
        void preservaFusoDaDataDeCriacao() {
            OffsetDateTime momento = OffsetDateTime.of(2025, 10, 15, 12, 30, 0, 0, ZoneOffset.ofHours(-3));
            ExchangeRequest request = novaPendente();
            request.setCreationDate(momento);

            exchangeRequestRepository.saveAndFlush(request);
            entityManager.clear();

            assertThat(exchangeRequestRepository.findById(request.getId()).orElseThrow().getCreationDate())
                    .isEqualTo(momento);
        }

        @Test
        @DisplayName("findById devolve vazio para id inexistente")
        void findByIdInexistente() {
            assertThat(exchangeRequestRepository.findById(9999L)).isEmpty();
        }

        @Test
        @DisplayName("findAll devolve todas as solicitações gravadas")
        void findAll() {
            exchangeRequestRepository.saveAndFlush(novaPendente());

            ExchangeRequest segunda = novaPendente();
            segunda.setRequestingUser(admin);
            exchangeRequestRepository.saveAndFlush(segunda);

            assertThat(exchangeRequestRepository.findAll())
                    .extracting(r -> r.getRequestingUser().getEmail())
                    .containsExactlyInAnyOrder("joao@teste.com", "admin@teste.com");
        }

        @Test
        @DisplayName("count reflete o número de solicitações")
        void count() {
            assertThat(exchangeRequestRepository.count()).isZero();

            exchangeRequestRepository.saveAndFlush(novaPendente());

            assertThat(exchangeRequestRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("a atualização de PENDING para APPROVED é persistida")
        void atualizaStatus() {
            ExchangeRequest request = exchangeRequestRepository.saveAndFlush(novaPendente());

            request.setStatus(ExchangeStatus.APPROVED);
            exchangeRequestRepository.saveAndFlush(request);
            entityManager.clear();

            assertThat(exchangeRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                    .isEqualTo(ExchangeStatus.APPROVED);
        }

        @Test
        @DisplayName("deleteById remove a solicitação")
        void deleteById() {
            ExchangeRequest request = exchangeRequestRepository.saveAndFlush(novaPendente());

            exchangeRequestRepository.deleteById(request.getId());
            entityManager.flush();

            assertThat(exchangeRequestRepository.findById(request.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Mapeamento da tabela")
    class Mapeamento {

        @Test
        @DisplayName("o estado é gravado como o nome do enum, e não como texto livre")
        void estadoEhEnum() {
            ExchangeRequest request = novaPendente();
            request.setStatus(ExchangeStatus.REJECTED);

            exchangeRequestRepository.saveAndFlush(request);
            entityManager.clear();

            assertThat(exchangeRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                    .isEqualTo(ExchangeStatus.REJECTED);

            // O valor gravado é o nome da constante. Era esta coluna que
            // aceitava "QUALQUER_COISA_ACEITA": nada impedia a aplicação de
            // gravar um estado que ela própria não sabia ler de volta.
            String gravado = (String) entityManager.createNativeQuery(
                            "select status from tb_exchange_request where id = :id")
                    .setParameter("id", request.getId())
                    .getSingleResult();
            assertThat(gravado).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("o valor por omissão da entidade é o PENDING que o serviço reconhece")
        void valorPadraoDaEntidadeAceito() {
            ExchangeRequest request = new ExchangeRequest();
            request.setSourceAllocation(alocacaoAdmin);
            request.setDestinationAllocation(alocacaoJoao);
            request.setRequestingUser(joao);
            request.setRequestedUser(admin);
            request.setCreationDate(OffsetDateTime.now());
            assertThat(request.getStatus()).isEqualTo(ExchangeStatus.PENDING);

            exchangeRequestRepository.saveAndFlush(request);
            entityManager.clear();

            assertThat(exchangeRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                    .isEqualTo(ExchangeStatus.PENDING);
        }

        @Test
        @DisplayName("a coluna do estado não aceita nulos")
        void estadoNaoPodeSerNulo() {
            ExchangeRequest request = exchangeRequestRepository.saveAndFlush(novaPendente());
            entityManager.clear();

            // Uma consulta nativa executada à mão não passa pelo tradutor de
            // exceções do Spring, por isso o que chega é a exceção do Hibernate
            // e não um `DataIntegrityViolationException`.
            assertThatThrownBy(() -> entityManager.createNativeQuery(
                            "update tb_exchange_request set status = null where id = :id")
                    .setParameter("id", request.getId())
                    .executeUpdate())
                    .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class)
                    .hasMessageContaining("not-null constraint");
        }

        @Test
        @DisplayName("a coluna do estado recusa um valor fora do enum")
        void estadoForaDoEnumRecusado() {
            // O enum protege a aplicação, mas não a base: sem a CHECK, um UPDATE
            // direto escreveria 'ACEITE' sem erro, e a linha deixaria de ser
            // legível pelo `@Enumerated(EnumType.STRING)` — o que rebentava a
            // página de histórico inteira, não só a linha.
            ExchangeRequest request = exchangeRequestRepository.saveAndFlush(novaPendente());
            entityManager.clear();

            assertThatThrownBy(() -> entityManager.createNativeQuery(
                            "update tb_exchange_request set status = 'ACEITE' where id = :id")
                    .setParameter("id", request.getId())
                    .executeUpdate())
                    .isInstanceOf(org.hibernate.exception.ConstraintViolationException.class)
                    // O nome da constraint fixa o teste à CHECK da V2, e não a
                    // qualquer outra restrição que happen a estar lá.
                    .hasMessageContaining("fk_exchange_request_status_check");
        }

        @Test
        @DisplayName("as quatro colunas de chave estrangeira existem na tabela")
        void chavesEstrangeiras() {
            long chaves = ((Number) entityManager.createNativeQuery("""
                            select count(*) from information_schema.table_constraints
                            where table_name = 'tb_exchange_request' and constraint_type = 'FOREIGN KEY'
                            """).getSingleResult()).longValue();

            // As três ligações originais mais a do colega pedido, que é a quarta.
            assertThat(chaves).isEqualTo(4);
        }

        @Test
        @DisplayName("apagar um usuário deixa o histórico da troca intacto, com o colega a nulo")
        void apagarUtilizadorNaoApagaHistorico() {
            // O histórico responde "o que ficou combinado". Se desaparecer
            // quando alguém é apagado da conta, a resposta desaparece com ele.
            var terceiro = userRepository.saveAndFlush(
                    TestFixtures.user("Terceiro", "terceiro@teste.com",
                            domain.model.enums.UserProfile.ANALIST));
            ExchangeRequest request = novaPendente();
            request.setRequestedUser(terceiro);
            exchangeRequestRepository.saveAndFlush(request);
            entityManager.clear();

            userRepository.deleteById(terceiro.getId());
            userRepository.flush();
            entityManager.clear();

            ExchangeRequest recarregado = exchangeRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(recarregado).isNotNull();
            assertThat(recarregado.getRequestedUser()).isNull();
        }

        @Test
        @DisplayName("apagar a alocação origem com solicitações associada viola a chave estrangeira")
        void apagarAlocacaoComSolicitacaoViolaForeignKey() {
            exchangeRequestRepository.saveAndFlush(novaPendente());

            assertThatThrownBy(() -> {
                shiftSchedulingRepository.deleteById(alocacaoAdmin.getId());
                shiftSchedulingRepository.flush();
            }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("as referências podem ser nulas, pois não há restrição NOT NULL")
        void referenciasPodemSerNulas() {
            ExchangeRequest request = new ExchangeRequest();
            request.setStatus(ExchangeStatus.PENDING);

            assertThat(exchangeRequestRepository.saveAndFlush(request)).isNotNull();
            ExchangeRequest recarregada = exchangeRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(recarregada.getSourceAllocation()).isNull();
            assertThat(recarregada.getDestinationAllocation()).isNull();
            assertThat(recarregada.getRequestingUser()).isNull();
        }
    }

    @Nested
    @DisplayName("findByIdComDetalhes")
    class FindComDetalhes {

        @Test
        @DisplayName("carrega o pedido com o colega e as alocações")
        void carregaDetalhes() {
            var pedido = novaPendente();
            pedido.setRequestedUser(admin);
            exchangeRequestRepository.saveAndFlush(pedido);
            entityManager.clear();

            var encontrado = exchangeRequestRepository.findByIdComDetalhes(pedido.getId()).orElseThrow();

            assertThat(encontrado.getRequestedUser().getId()).isEqualTo(admin.getId());
            assertThat(encontrado.getSourceAllocation().getId()).isEqualTo(alocacaoAdmin.getId());
            assertThat(encontrado.getDestinationAllocation().getId()).isEqualTo(alocacaoJoao.getId());
        }

        @Test
        @DisplayName("um id inexistente devolve vazio, e não uma exceção")
        void idInexistente() {
            assertThat(exchangeRequestRepository.findByIdComDetalhes(9999L)).isEmpty();
        }
    }
}
