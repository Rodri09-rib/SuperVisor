package domain.repository;

import domain.model.entities.EditionScale;
import domain.model.entities.User;
import domain.model.enums.EditionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tests.support.AbstractJpaIntegrationTest;
import tests.support.TestFixtures;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EditionScaleRepository (persistência)")
class EditionScaleRepositoryTest extends AbstractJpaIntegrationTest {

    private User criarUtilizador() {
        return userRepository.saveAndFlush(
                TestFixtures.user("Administrador", "admin@teste.com", domain.model.enums.UserProfile.SUPERVISOR));
    }

    @Nested
    @DisplayName("Operações herdadas de JpaRepository")
    class JpaRepository {

        @Test
        @DisplayName("save persiste todos os campos da escala")
        void savePersisteTodosOsCampos() {
            User creator = criarUtilizador();
            EditionScale escala = editionScaleRepository.saveAndFlush(
                    TestFixtures.editionScale("Escala Outubro", creator, EditionStatus.DRAFT));
            entityManager.clear();

            EditionScale recarregada = editionScaleRepository.findById(escala.getId()).orElseThrow();
            assertThat(recarregada.getName()).isEqualTo("Escala Outubro");
            assertThat(recarregada.getInitialDate()).isEqualTo(LocalDate.of(2025, 10, 1));
            assertThat(recarregada.getEndDate()).isEqualTo(LocalDate.of(2025, 10, 31));
            assertThat(recarregada.getStatus()).isEqualTo(EditionStatus.DRAFT);
        }

        @Test
        @DisplayName("findById devolve vazio para id inexistente")
        void findByIdInexistente() {
            assertThat(editionScaleRepository.findById(9999L)).isEmpty();
        }

        @Test
        @DisplayName("findAll devolve todas as escalas gravadas")
        void findAll() {
            User creator = criarUtilizador();
            editionScaleRepository.saveAllAndFlush(java.util.List.of(
                    TestFixtures.draftScale(creator),
                    TestFixtures.editionScale("Escala Novembro", creator, EditionStatus.PUBLISHED)));

            assertThat(editionScaleRepository.findAll())
                    .extracting(EditionScale::getName)
                    .containsExactlyInAnyOrder("Escala Outubro", "Escala Novembro");
        }

        @Test
        @DisplayName("count reflete o número de escalas")
        void count() {
            assertThat(editionScaleRepository.count()).isZero();

            editionScaleRepository.saveAndFlush(TestFixtures.draftScale(criarUtilizador()));

            assertThat(editionScaleRepository.count()).isEqualTo(1);
        }

        @Test
        @DisplayName("a transição de rascunho para publicada é persistida")
        void atualizaStatus() {
            EditionScale escala = editionScaleRepository.saveAndFlush(TestFixtures.draftScale(criarUtilizador()));

            escala.setStatus(EditionStatus.PUBLISHED);
            editionScaleRepository.saveAndFlush(escala);
            entityManager.clear();

            assertThat(editionScaleRepository.findById(escala.getId()).orElseThrow().getStatus())
                    .isEqualTo(EditionStatus.PUBLISHED);
        }

        @Test
        @DisplayName("deleteById remove a escala")
        void deleteById() {
            EditionScale escala = editionScaleRepository.saveAndFlush(
                    TestFixtures.draftScale(criarUtilizador()));

            editionScaleRepository.deleteById(escala.getId());
            entityManager.flush();

            assertThat(editionScaleRepository.findById(escala.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("Relação com o usuário criador")
    class RelacaoComCriador {

        @Test
        @DisplayName("a escala salva a referência ao usuário que a criou")
        void referenciaAoCriador() {
            User creator = criarUtilizador();
            EditionScale escala = editionScaleRepository.saveAndFlush(TestFixtures.draftScale(creator));
            entityManager.clear();

            EditionScale recarregada = editionScaleRepository.findById(escala.getId()).orElseThrow();
            assertThat(recarregada.getCreatedBy()).isNotNull();
            assertThat(recarregada.getCreatedBy().getId()).isEqualTo(creator.getId());
        }

        @Test
        @DisplayName("a coluna created_by_id é preenchida na tabela tb_edition_scale")
        void colunaPreenchida() {
            User creator = criarUtilizador();
            EditionScale escala = editionScaleRepository.saveAndFlush(TestFixtures.draftScale(creator));

            Object idBruto = entityManager
                    .createNativeQuery("select created_by_id from tb_edition_scale where id = :id")
                    .setParameter("id", escala.getId())
                    .getSingleResult();

            assertThat(((Number) idBruto).longValue()).isEqualTo(creator.getId());
        }

        @Test
        @DisplayName("apagar o usuário com escalas associada viola a chave estrangeira")
        void apagarCriadorComEscalaViolaForeignKey() {
            User creator = criarUtilizador();
            editionScaleRepository.saveAndFlush(TestFixtures.draftScale(creator));

            assertThatThrownBy(() -> {
                userRepository.deleteById(creator.getId());
                userRepository.flush();
            }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("Mapeamento da tabela")
    class Mapeamento {

        @Test
        @DisplayName("o estado é gravado como texto, não como ordinal")
        void estadoGravadoComoTexto() {
            EditionScale escala = editionScaleRepository.saveAndFlush(
                    TestFixtures.editionScale("Escala Outubro", criarUtilizador(), EditionStatus.DRAFT));

            Object statusBruto = entityManager
                    .createNativeQuery("select status from tb_edition_scale where id = :id")
                    .setParameter("id", escala.getId())
                    .getSingleResult();

            assertThat(statusBruto).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("as datas são gravadas em colunas de data, sem componente de hora")
        void datasGravadasComoData() {
            EditionScale escala = editionScaleRepository.saveAndFlush(
                    TestFixtures.editionScale("Escala Outubro", criarUtilizador(), EditionStatus.DRAFT));

            Object tipoDaColuna = entityManager
                    .createNativeQuery("""
                            select data_type from information_schema.columns
                            where table_name = 'tb_edition_scale' and column_name = 'initial_date'
                            """)
                    .getSingleResult();

            assertThat(tipoDaColuna).isEqualTo("date");
            assertThat(escala.getId()).isNotNull();
        }

        @Test
        @DisplayName("a escala aceita criado nulo, pois a coluna não é NOT NULL")
        void criadoNuloAceito() {
            EditionScale escala = new EditionScale();
            escala.setName("Escala Sem Criador");
            escala.setInitialDate(LocalDate.of(2025, 1, 1));
            escala.setEndDate(LocalDate.of(2025, 1, 31));
            escala.setStatus(EditionStatus.DRAFT);

            assertThat(editionScaleRepository.saveAndFlush(escala)).isNotNull();
            assertThat(editionScaleRepository.findById(escala.getId()).orElseThrow().getCreatedBy()).isNull();
        }

        @Test
        @DisplayName("nomes repetidos são permitidos, pois não há restrição de unicidade")
        void nomesRepetidosPermitidos() {
            User creator = criarUtilizador();
            editionScaleRepository.saveAndFlush(TestFixtures.draftScale(creator));
            editionScaleRepository.saveAndFlush(TestFixtures.draftScale(creator));

            assertThat(editionScaleRepository.findAll())
                    .filteredOn(e -> "Escala Outubro".equals(e.getName()))
                    .hasSize(2);
        }
    }
}
