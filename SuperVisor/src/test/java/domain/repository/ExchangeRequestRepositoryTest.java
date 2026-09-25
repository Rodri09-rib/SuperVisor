package domain.repository;

import domain.model.entities.EditionScale;
import domain.model.entities.ExchangeRequest;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
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
        ExchangeRequest request = new ExchangeRequest();
        request.setSourceAllocation(alocacaoAdmin);
        request.setDestinationAllocation(alocacaoJoao);
        request.setRequestingUser(joao);
        request.setStatus("PENDING");
        request.setCreationDate(OffsetDateTime.now());
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
        @DisplayName("a atualização de PENDING para ACCEPTED é persistida")
        void atualizaStatus() {
            ExchangeRequest request = exchangeRequestRepository.saveAndFlush(novaPendente());

            request.setStatus("ACCEPTED");
            exchangeRequestRepository.saveAndFlush(request);
            entityManager.clear();

            assertThat(exchangeRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                    .isEqualTo("ACCEPTED");
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
        @DisplayName("o estado é gravado como texto livre, sem validação de domínio")
        void estadoEhTextoLivre() {
            ExchangeRequest request = novaPendente();
            request.setStatus("QUALQUER_COISA_ACEITA");

            exchangeRequestRepository.saveAndFlush(request);
            entityManager.clear();

            assertThat(exchangeRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                    .isEqualTo("QUALQUER_COISA_ACEITA");
        }

        @Test
        @DisplayName("o valor PENDENTE do campo da entidade também é aceito pelo banco")
        void valorPadraoDaEntidadeAceito() {
            ExchangeRequest request = new ExchangeRequest();
            request.setSourceAllocation(alocacaoAdmin);
            request.setDestinationAllocation(alocacaoJoao);
            request.setRequestingUser(joao);
            request.setCreationDate(OffsetDateTime.now());
            assertThat(request.getStatus()).isEqualTo("PENDENTE");

            exchangeRequestRepository.saveAndFlush(request);
            entityManager.clear();

            assertThat(exchangeRequestRepository.findById(request.getId()).orElseThrow().getStatus())
                    .isEqualTo("PENDENTE");
        }

        @Test
        @DisplayName("as três colunas de chave estrangeira existem na tabela")
        void chavesEstrangeiras() {
            long chaves = ((Number) entityManager.createNativeQuery("""
                            select count(*) from information_schema.table_constraints
                            where table_name = 'tb_exchange_request' and constraint_type = 'FOREIGN KEY'
                            """).getSingleResult()).longValue();

            assertThat(chaves).isEqualTo(3);
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
            request.setStatus("PENDING");

            assertThat(exchangeRequestRepository.saveAndFlush(request)).isNotNull();
            ExchangeRequest recarregada = exchangeRequestRepository.findById(request.getId()).orElseThrow();
            assertThat(recarregada.getSourceAllocation()).isNull();
            assertThat(recarregada.getDestinationAllocation()).isNull();
            assertThat(recarregada.getRequestingUser()).isNull();
        }
    }
}
