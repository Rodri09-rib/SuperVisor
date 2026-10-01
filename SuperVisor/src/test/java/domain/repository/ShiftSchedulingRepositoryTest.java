package domain.repository;

import domain.model.entities.EditionScale;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.AllocationStatus;
import domain.model.enums.AssignmentType;
import domain.model.enums.ShiftType;
import domain.model.enums.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tests.support.AbstractJpaIntegrationTest;
import tests.support.TestFixtures;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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
                TestFixtures.user("Administrador", "admin@teste.com", UserProfile.SUPERVISOR));
        joao = userRepository.saveAndFlush(TestFixtures.user("João", "joao@teste.com", UserProfile.ANALIST));
        escala = editionScaleRepository.saveAndFlush(TestFixtures.draftScale(admin));
    }

    @Nested
    @DisplayName("Operações herdadas de JpaRepository")
    class JpaRepository {

        @Test
        @DisplayName("save persiste a ligação com a escala e com o usuário")
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
        @DisplayName("trocar o usuário da alocação é persistido (base da troca de turno)")
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
    @DisplayName("Turno de fim de semana")
    class Turno {

        @Test
        @DisplayName("o turno é gravado como texto e recarregado como enum")
        void turnoGravadoComoTexto() {
            ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(
                    TestFixtures.allocation(escala, joao, ShiftType.T2_SAB, null));
            entityManager.clear();

            Object bruto = entityManager
                    .createNativeQuery("select shift from tb_shift_scheduling where id = :id")
                    .setParameter("id", alocacao.getId())
                    .getSingleResult();

            assertThat(bruto).isEqualTo("T2_SAB");
            assertThat(shiftSchedulingRepository.findById(alocacao.getId()).orElseThrow().getShift())
                    .isEqualTo(ShiftType.T2_SAB);
        }

        @Test
        @DisplayName("todos os turnos do domínio são persistíveis")
        void todosOsTurnos() {
            for (ShiftType turno : ShiftType.values()) {
                ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(
                        TestFixtures.allocation(escala, joao, turno, null));
                entityManager.clear();

                assertThat(shiftSchedulingRepository.findById(alocacao.getId()).orElseThrow().getShift())
                        .isEqualTo(turno);
                entityManager.clear();
            }
        }

        @Test
        @DisplayName("a data específica continua a ser opcional")
        void dataEspecificaOpcional() {
            ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(
                    TestFixtures.allocation(escala, admin, ShiftType.T6_DOM, null));
            entityManager.clear();

            assertThat(shiftSchedulingRepository.findById(alocacao.getId()).orElseThrow().getSpecificDate())
                    .isNull();
        }

        @Test
        @DisplayName("a data específica é persistida quando informada")
        void dataEspecificaPersistida() {
            LocalDate dia = LocalDate.of(2025, 10, 15);

            ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(
                    TestFixtures.allocation(escala, joao, ShiftType.T6_DOM, dia));
            entityManager.clear();

            assertThat(shiftSchedulingRepository.findById(alocacao.getId()).orElseThrow().getSpecificDate())
                    .isEqualTo(dia);
        }
    }

    @Nested
    @DisplayName("Atribuições especiais")
    class Atribuicoes {

        @Test
        @DisplayName("as atribuições são persistidas e recarregadas")
        void atribuicoesPersistidas() {
            ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(
                    TestFixtures.allocationCompleta(escala, joao, ShiftType.T2_SAB, null,
                            List.of(AssignmentType.REDES_SOCIAIS, AssignmentType.CELULAR_MARINAS), null, null));
            entityManager.clear();

            assertThat(shiftSchedulingRepository.findById(alocacao.getId()).orElseThrow().getAssignments())
                    .containsExactlyInAnyOrder(AssignmentType.REDES_SOCIAIS, AssignmentType.CELULAR_MARINAS);
        }

        @Test
        @DisplayName("uma alocação sem atribuições fica sem linhas na tabela da coleção")
        void semAtribuicoes() {
            ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(
                    TestFixtures.allocation(escala, joao));
            entityManager.clear();

            Long linhas = (Long) entityManager
                    .createNativeQuery("""
                            select count(*) from tb_shift_scheduling_assignment
                            where shift_scheduling_id = :id
                            """)
                    .setParameter("id", alocacao.getId())
                    .getSingleResult();

            assertThat(linhas).isZero();
            assertThat(shiftSchedulingRepository.findById(alocacao.getId()).orElseThrow().getAssignments())
                    .isEmpty();
        }

        @Test
        @DisplayName("remover a última atribuição apaga a linha da tabela da coleção")
        void removerAtribuicoes() {
            ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(
                    TestFixtures.allocationCompleta(escala, joao, ShiftType.T2_SAB, null,
                            List.of(AssignmentType.REDES_SOCIAIS), null, null));
            entityManager.clear();

            ShiftScheduling gerida = shiftSchedulingRepository.findById(alocacao.getId()).orElseThrow();
            gerida.clearAssignments();
            shiftSchedulingRepository.saveAndFlush(gerida);
            entityManager.clear();

            assertThat(shiftSchedulingRepository.findById(alocacao.getId()).orElseThrow().getAssignments())
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Horário especial")
    class HorarioCustomizado {

        @Test
        @DisplayName("o horário dentro do turno é persistido")
        void horarioDentroDoTurno() {
            ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(
                    TestFixtures.allocationCompleta(escala, joao, ShiftType.T6_DOM, null, null,
                            LocalTime.of(10, 30), LocalTime.of(14, 30)));
            entityManager.clear();

            ShiftScheduling recarregada = shiftSchedulingRepository.findById(alocacao.getId()).orElseThrow();
            assertThat(recarregada.getCustomStartTime()).isEqualTo(LocalTime.of(10, 30));
            assertThat(recarregada.getCustomEndTime()).isEqualTo(LocalTime.of(14, 30));
            assertThat(recarregada.temHorarioCustomizado()).isTrue();
        }

        @Test
        @DisplayName("o horário que atravessa a meia-noite de T5 é persistido")
        void horarioAposMeiaNoite() {
            ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(
                    TestFixtures.allocationCompleta(escala, joao, ShiftType.T5_SAB, null, null,
                            LocalTime.of(21, 0), LocalTime.MIDNIGHT));
            entityManager.clear();

            ShiftScheduling recarregada = shiftSchedulingRepository.findById(alocacao.getId()).orElseThrow();
            assertThat(recarregada.getCustomEndTime()).isEqualTo(LocalTime.MIDNIGHT);
        }

        @Test
        @DisplayName("sem horário especial as duas colunas ficam nulas")
        void semHorarioCustomizado() {
            ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(
                    TestFixtures.allocation(escala, joao, ShiftType.T1_SAB, null));
            entityManager.clear();

            ShiftScheduling recarregada = shiftSchedulingRepository.findById(alocacao.getId()).orElseThrow();
            assertThat(recarregada.getCustomStartTime()).isNull();
            assertThat(recarregada.getCustomEndTime()).isNull();
            assertThat(recarregada.temHorarioCustomizado()).isFalse();
        }
    }

    @Nested
    @DisplayName("Obrigatoriedade do usuário, da escala e do turno")
    class Obrigatoriedade {

        @Test
        @DisplayName("o turno não pode ficar nulo em base de dados")
        void turnoObrigatorio() {
            ShiftScheduling alocacao = new ShiftScheduling();
            alocacao.setEditionScale(escala);
            alocacao.setUser(joao);

            assertThatThrownBy(() -> shiftSchedulingRepository.saveAndFlush(alocacao))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("o usuário não pode ficar nulo em base de dados")
        void utilizadorObrigatorio() {
            ShiftScheduling alocacao = new ShiftScheduling();
            alocacao.setEditionScale(escala);
            alocacao.setShift(ShiftType.T1_SAB);

            assertThatThrownBy(() -> shiftSchedulingRepository.saveAndFlush(alocacao))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("a escala não pode ficar nula em base de dados")
        void escalaObrigatoria() {
            ShiftScheduling alocacao = new ShiftScheduling();
            alocacao.setUser(joao);
            alocacao.setShift(ShiftType.T1_SAB);

            assertThatThrownBy(() -> shiftSchedulingRepository.saveAndFlush(alocacao))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("as colunas turno, usuário e escala estão definidas como NOT NULL")
        void colunasNotNull() {
            java.util.List<String> colunas = entityManager
                    .createNativeQuery("""
                            select column_name from information_schema.columns
                            where table_name = 'tb_shift_scheduling' and is_nullable = 'NO'
                            """)
                    .getResultList();

            assertThat(colunas).contains("shift", "user_id", "edition_scale_id");
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

            assertThat(alocacao.getAnalystAcceptanceStatus()).isEqualTo(AllocationStatus.PENDING);
            assertThat(statusBruto).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("o estado de aceitação é gravado como texto e recarregado como enum")
        void estadoGravadoComoTexto() {
            ShiftScheduling alocacao = shiftSchedulingRepository.saveAndFlush(TestFixtures.allocation(escala, admin));
            alocacao.setAnalystAcceptanceStatus(AllocationStatus.ACCEPTED);
            shiftSchedulingRepository.saveAndFlush(alocacao);
            entityManager.clear();

            Object statusBruto = entityManager
                    .createNativeQuery("select analyst_acceptance_status from tb_shift_scheduling where id = :id")
                    .setParameter("id", alocacao.getId())
                    .getSingleResult();

            assertThat(statusBruto).isEqualTo("ACCEPTED");
            assertThat(shiftSchedulingRepository.findById(alocacao.getId()).orElseThrow()
                    .getAnalystAcceptanceStatus())
                    .isEqualTo(AllocationStatus.ACCEPTED);
        }

        @Test
        @DisplayName("as colunas de data e horário são do tipo esperado")
        void colunasDeData() {
            java.util.List<Object> tipos = entityManager
                    .createNativeQuery("""
                            select column_name || ':' || data_type from information_schema.columns
                            where table_name = 'tb_shift_scheduling'
                              and column_name in ('specific_date', 'custom_start_time', 'custom_end_time')
                            """)
                    .getResultList();

            assertThat(tipos).contains("specific_date:date",
                    "custom_start_time:time without time zone",
                    "custom_end_time:time without time zone");
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
