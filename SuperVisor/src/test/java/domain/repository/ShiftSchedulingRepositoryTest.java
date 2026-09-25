package domain.repository;

import domain.model.entities.EditionScale;
import domain.model.entities.Shift;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tests.support.AbstractJpaIntegrationTest;
import tests.support.TestFixtures;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ShiftSchedulingRepository (persistência)")
class ShiftSchedulingRepositoryTest extends AbstractJpaIntegrationTest {

    private User admin;
    private User joao;
    private EditionScale escala;

    @BeforeEach
    void prepararBase() {
        admin = userRepository.saveAndFlush(
                TestFixtures.user("Administrador", "admin@teste.com", domain.model.enums.UserProfile.SUPERVISOR));
        joao = userRepository.saveAndFlush(
                TestFixtures.user("João", "joao@teste.com", domain.model.enums.UserProfile.ANALIST));
        escala = editionScaleRepository.saveAndFlush(TestFixtures.draftScale(admin));
    }

    @Nested
    @DisplayName("Operações herdadas de JpaRepository")
    class JpaRepository {

        @Test
        @DisplayName("save persiste a ligação com a escala e com o utilizador")
        void guardaEscalaEUtilizador() {
            ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(TestFixtures.allocation(escala, joao));
            entityManager.clear();

            ShiftScheduling recarregada = shiftSchedulingRepository.findById(alocacao.getId()).orElseThrow();
            assertThat(recarregada.getEditionScale().getId()).isEqualTo(escala.getId());
            assertThat(recarregada.getUser().getId()).isEqualTo(joao.getId());
        }

        @Test
        @DisplayName("findById devolve vazio para id inexistente")
        void findByIdInexistente() {
            assertThat(shiftSchedulingRepository.findById(9999L)).isEmpty();
        }

        @Test
        @DisplayName("findAll devolve todas as alocações gravadas")
        void findAll() {
            shiftSchedulingRepository.saveAndFlush(TestFixtures.allocation(escala, admin));
            shiftSchedulingRepository.saveAndFlush(TestFixtures.allocation(escala, joao));

            assertThat(shiftSchedulingRepository.findAll())
                    .extracting(a -> a.getUser().getEmail())
                    .containsExactlyInAnyOrder("admin@teste.com", "joao@teste.com");
        }

        @Test
        @DisplayName("count reflete o número de alocações")
        void count() {
            assertThat(shiftSchedulingRepository.count()).isZero();

            shiftSchedulingRepository.saveAndFlush(TestFixtures.allocation(escala, admin));

            assertThat(shiftSchedulingRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("trocar o utilizador da alocação é persistido (base da troca de turno)")
        void trocaUtilizadorPersistida() {
            ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(TestFixtures.allocation(escala, admin));

            alocacao.setUser(joao);
            shiftSchedulingRepository.saveAndFlush(alocacao);
            entityManager.clear();

            assertThat(shiftSchedulingRepository.findById(alocacao.getId()).orElseThrow().getUser().getEmail())
                    .isEqualTo("joao@teste.com");
        }

        @Test
        @DisplayName("deleteById remove a alocação")
        void deleteById() {
            ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(TestFixtures.allocation(escala, admin));

            shiftSchedulingRepository.deleteById(alocacao.getId());
            entityManager.flush();

            assertThat(shiftSchedulingRepository.findById(alocacao.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Campos opcionais")
    class CamposOpcionais {

        @Test
        @DisplayName("turno e data específica podem ficar nulos, como no seed da aplicação")
        void turnoEDataPodemSerNulos() {
            ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(TestFixtures.allocation(escala, admin));
            entityManager.clear();

            ShiftScheduling recarregada = shiftSchedulingRepository.findById(alocacao.getId()).orElseThrow();
            assertThat(recarregada.getShift()).isNull();
            assertThat(recarregada.getSpecificDate()).isNull();
        }

        @Test
        @DisplayName("turno e data específica são persistidos quando informados")
        void turnoEDataPersistidos() {
            Shift turno = shiftRepository.saveAndFlush(
                    TestFixtures.shift("M1", LocalDate.of(2025, 10, 1).atStartOfDay().toLocalTime(),
                            LocalDate.of(2025, 10, 1).atTime(17, 0).toLocalTime(), "SEGUNDA"));
            LocalDate dia = LocalDate.of(2025, 10, 15);

            ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(
                    TestFixtures.allocation(escala, joao, turno, dia));
            entityManager.clear();

            ShiftScheduling recarregada = shiftSchedulingRepository.findById(alocacao.getId()).orElseThrow();
            assertThat(recarregada.getShift().getAcronym()).isEqualTo("M1");
            assertThat(recarregada.getSpecificDate()).isEqualTo(dia);
        }
    }

    @Nested
    @DisplayName("Mapeamento da tabela")
    class Mapeamento {

        @Test
        @DisplayName("o estado de aceitação nasce gravado como PENDING")
        void estadoDeAceitacaoPadraoNoBanco() {
            ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(TestFixtures.allocation(escala, admin));

            Object statusBruto = entityManager
                    .createNativeQuery("select analyst_acceptance_status from tb_shift_scheduling where id = :id")
                    .setParameter("id", alocacao.getId())
                    .getSingleResult();

            assertThat(alocacao.getAnalystAcceptanceStatus())
                    .isEqualTo(domain.model.enums.AllocationStatus.PENDING);
            assertThat(statusBruto).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("o estado de aceitação é gravado como texto e recarregado como enum")
        void estadoGravadoComoTexto() {
            ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(TestFixtures.allocation(escala, admin));
            alocacao.setAnalystAcceptanceStatus(domain.model.enums.AllocationStatus.ACCEPTED);
            shiftSchedulingRepository.saveAndFlush(alocacao);
            entityManager.clear();

            Object statusBruto = entityManager
                    .createNativeQuery("select analyst_acceptance_status from tb_shift_scheduling where id = :id")
                    .setParameter("id", alocacao.getId())
                    .getSingleResult();

            assertThat(statusBruto).isEqualTo("ACCEPTED");
            assertThat(shiftSchedulingRepository.findById(alocacao.getId()).orElseThrow()
                    .getAnalystAcceptanceStatus())
                    .isEqualTo(domain.model.enums.AllocationStatus.ACCEPTED);
        }

        @Test
        @DisplayName("as colunas de data e turno recebem os valores esperados")
        void colunasDeData() {
            Object tipoDaColuna = entityManager
                    .createNativeQuery("""
                            select data_type from information_schema.columns
                            where table_name = 'tb_shift_scheduling' and column_name = 'specific_date'
                            """)
                    .getSingleResult();

            assertThat(tipoDaColuna).isEqualTo("date");
        }

        @Test
        @DisplayName("apagar a escala com alocações associadas viola a chave estrangeira")
        void apagarEscalaComAlocacoesViolaForeignKey() {
            shiftSchedulingRepository.saveAndFlush(TestFixtures.allocation(escala, admin));

            assertThatThrownBy(() -> {
                editionScaleRepository.deleteById(escala.getId());
                editionScaleRepository.flush();
            }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }
}
