package domain.repository;

import domain.model.entities.ExchangeRequest;
import domain.model.entities.Shift;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tests.support.AbstractJpaIntegrationTest;
import tests.support.TestFixtures;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ShiftRepository (persistência)")
class ShiftRepositoryTest extends AbstractJpaIntegrationTest {

    @Nested
    @DisplayName("Operações herdadas de JpaRepository")
    class JpaRepository {

        @Test
        @DisplayName("save persiste sigla, horários e dia da semana")
        void savePersisteTodosOsCampos() {
            Shift shift = shiftRepository.saveAndFlush(
                    TestFixtures.shift("M1", LocalTime.of(8, 0), LocalTime.of(12, 0), "SEGUNDA"));
            entityManager.clear();

            Shift recarregado = shiftRepository.findById(shift.getId()).orElseThrow();
            assertThat(recarregado.getAcronym()).isEqualTo("M1");
            assertThat(recarregado.getStartTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(recarregado.getEndTime()).isEqualTo(LocalTime.of(12, 0));
            assertThat(recarregado.getDayiftheWeek()).isEqualTo("SEGUNDA");
        }

        @Test
        @DisplayName("findById devolve vazio para id inexistente")
        void findByIdInexistente() {
            assertThat(shiftRepository.findById(9999L)).isEmpty();
        }

        @Test
        @DisplayName("findAll devolve todos os turnos gravados")
        void findAll() {
            shiftRepository.saveAllAndFlush(java.util.List.of(
                    TestFixtures.shift("M1", LocalTime.of(8, 0), LocalTime.of(12, 0), "SEGUNDA"),
                    TestFixtures.shift("T1", LocalTime.of(13, 0), LocalTime.of(18, 0), "TERCA")));

            assertThat(shiftRepository.findAll())
                    .extracting(Shift::getAcronym)
                    .containsExactlyInAnyOrder("M1", "T1");
        }

        @Test
        @DisplayName("count reflete o número de turnos")
        void count() {
            assertThat(shiftRepository.count()).isZero();

            shiftRepository.saveAndFlush(
                    TestFixtures.shift("M1", LocalTime.of(8, 0), LocalTime.of(12, 0), "SEGUNDA"));

            assertThat(shiftRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("siglas repetidas são permitidas, pois não há restrição de unicidade")
        void siglasRepetidasPermitidas() {
            shiftRepository.saveAndFlush(
                    TestFixtures.shift("M1", LocalTime.of(8, 0), LocalTime.of(12, 0), "SEGUNDA"));
            shiftRepository.saveAndFlush(
                    TestFixtures.shift("M1", LocalTime.of(8, 0), LocalTime.of(12, 0), "QUARTA"));

            assertThat(shiftRepository.count()).isEqualTo(2);
        }

        @Test
        @DisplayName("os horários podem ser nulos")
        void horariosPodemSerNulos() {
            Shift shift = new Shift();
            shift.setAcronym("N1");

            assertThat(shiftRepository.saveAndFlush(shift)).isNotNull();
            Shift recarregado = shiftRepository.findById(shift.getId()).orElseThrow();
            assertThat(recarregado.getStartTime()).isNull();
            assertThat(recarregado.getEndTime()).isNull();
        }

        @Test
        @DisplayName("update altera o turno já gravado")
        void update() {
            Shift shift = shiftRepository.saveAndFlush(
                    TestFixtures.shift("M1", LocalTime.of(8, 0), LocalTime.of(12, 0), "SEGUNDA"));

            shift.setAcronym("M1-A");
            shiftRepository.saveAndFlush(shift);
            entityManager.clear();

            assertThat(shiftRepository.findById(shift.getId()).orElseThrow().getAcronym()).isEqualTo("M1-A");
            assertThat(shiftRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("deleteById remove o turno")
        void deleteById() {
            Shift shift = shiftRepository.saveAndFlush(
                    TestFixtures.shift("M1", LocalTime.of(8, 0), LocalTime.of(12, 0), "SEGUNDA"));

            shiftRepository.deleteById(shift.getId());
            entityManager.flush();

            assertThat(shiftRepository.findById(shift.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Mapeamento da tabela")
    class Mapeamento {

        @Test
        @DisplayName("os horários são gravados em colunas de hora sem fuso")
        void colunasDeHora() {
            java.util.List<?> tipos = entityManager.createNativeQuery("""
                            select column_name, data_type from information_schema.columns
                            where table_name = 'tb_shift'
                              and column_name in ('start_time', 'end_time', 'dayifthe_week')
                            order by column_name
                            """).getResultList();

            assertThat(tipos).isNotEmpty();
        }

        @Test
        @DisplayName("o turno não tem colunas de chave estrangeira na tabela")
        void semChavesEstrangeiras() {
            long chaves = ((Number) entityManager.createNativeQuery("""
                            select count(*) from information_schema.table_constraints
                            where table_name = 'tb_shift' and constraint_type = 'FOREIGN KEY'
                            """).getSingleResult()).longValue();

            assertThat(chaves).isZero();
        }
    }
}
