package service;

import domain.dto.LeaveConflictDTO;
import domain.dto.OverlapConflictDTO;
import domain.dto.ScaleCoverageDTO;
import domain.dto.SlotCoverageDTO;
import domain.model.entities.EditionScale;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.entities.UserLeave;
import domain.model.enums.EditionStatus;
import domain.model.enums.ShiftType;
import domain.model.enums.TeamGroup;
import domain.model.enums.UserProfile;
import domain.repository.EditionScaleRepository;
import domain.repository.ShiftSchedulingRepository;
import domain.repository.UserLeaveRepository;
import exception.RegraDeNegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tests.support.TestFixtures;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScaleCoverageService")
class ScaleCoverageServiceTest {

    /**
     * A escala do fixture cobre outubro de 2025, que tem quatro sábados (4, 11,
     * 18 e 25) e quatro domingos (5, 12, 19 e 26). Com cinco turnos de sábado e
     * um de domingo, isso dá vinte slots de sábado e quatro de domingo.
     *
     * <p>Estão escritos à mão em vez de calculados para que o teste falhe se o
     * número mudar: um relatório que passa a contar dias úteis como se fossem
     * turnos é exatamente o erro que estes valores apanham.
     */
    private static final int SLOTS_OUTUBRO_2025 = 24;
    private static final int SABADOS_OUTUBRO_2025 = 4;

    private static final LocalDate PRIMEIRO_SABADO = LocalDate.of(2025, 10, 4);
    private static final LocalDate PRIMEIRO_DOMINGO = LocalDate.of(2025, 10, 5);

    @Mock
    private EditionScaleRepository editionScaleRepository;

    @Mock
    private ShiftSchedulingRepository shiftSchedulingRepository;

    @Mock
    private UserLeaveRepository userLeaveRepository;

    private ScaleCoverageService service;

    private User ana;

    @BeforeEach
    void setUp() {
        service = new ScaleCoverageService();
        ReflectionTestUtils.setField(service, "editionScaleRepository", editionScaleRepository);
        ReflectionTestUtils.setField(service, "shiftSchedulingRepository", shiftSchedulingRepository);
        ReflectionTestUtils.setField(service, "userLeaveRepository", userLeaveRepository);

        ana = TestFixtures.userInTeam(
                "Ana", "ana@teste.com", UserProfile.ANALIST, TeamGroup.EQUIPE_A);
        ana.setId(1L);
    }

    private EditionScale escala() {
        return TestFixtures.editionScale("Escala Outubro", ana, EditionStatus.DRAFT);
    }

    /** Deixa a escala e as alocações mockadas, sem folgas. */
    private void comAlocacoes(ShiftScheduling... alocacoes) {
        when(editionScaleRepository.findById(1L)).thenReturn(Optional.of(escala()));
        when(shiftSchedulingRepository.findByEditionScaleIdOrderByIdAsc(1L))
                .thenReturn(List.of(alocacoes));
        when(userLeaveRepository.listarQueSeCruzamCom(any(), any())).thenReturn(List.of());
    }

    private ShiftScheduling alocacao(ShiftType turno) {
        ShiftScheduling a = TestFixtures.allocation(escala(), ana, turno, null);
        a.setId(1L);
        return a;
    }

    private ShiftScheduling alocacaoDe(User utilizador, ShiftType turno) {
        ShiftScheduling a = TestFixtures.allocation(escala(), utilizador, turno, null);
        a.setId((long) (utilizador.getId() + turno.ordinal() + 1));
        return a;
    }

    private SlotCoverageDTO slot(ScaleCoverageDTO relatorio, LocalDate data, String turno) {
        return relatorio.slots().stream()
                .filter(s -> s.date().equals(data) && s.shift().equals(turno))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("Cobertura")
    class Cobertura {

        @Test
        @DisplayName("conta um slot por turno e dia de fim de semana, e nada nos dias úteis")
        void contaSlotsDeFimDeSemana() {
            comAlocacoes();

            ScaleCoverageDTO relatorio = service.relatorio(1L);

            assertThat(relatorio.totalSlots()).isEqualTo(SLOTS_OUTUBRO_2025);
            assertThat(relatorio.slots())
                    .allMatch(s -> s.date().getDayOfWeek().getValue() >= 6);
        }

        @Test
        @DisplayName("uma escala vazia tem todos os slots por cobrir e zero por cento")
        void escalaVazia() {
            comAlocacoes();

            ScaleCoverageDTO relatorio = service.relatorio(1L);

            assertThat(relatorio.coveredSlots()).isZero();
            assertThat(relatorio.uncoveredSlots()).isEqualTo(SLOTS_OUTUBRO_2025);
            assertThat(relatorio.coveragePercent()).isZero();
        }

        @Test
        @DisplayName("uma alocação sem data cobre o turno em todos os dias do período")
        void alocacaoCobreTodosOsDiasDaSemana() {
            comAlocacoes(alocacao(ShiftType.T1_SAB));

            ScaleCoverageDTO relatorio = service.relatorio(1L);

            assertThat(relatorio.coveredSlots()).isEqualTo(SABADOS_OUTUBRO_2025);
            for (int i = 0; i < SABADOS_OUTUBRO_2025; i++) {
                assertThat(slot(relatorio, PRIMEIRO_SABADO.plusWeeks(i), "T1").covered()).isTrue();
            }
        }

        @Test
        @DisplayName("uma alocação com data específica só cobre esse dia")
        void alocacaoComDataCobreUmDia() {
            ShiftScheduling a = TestFixtures.allocation(
                    escala(), ana, ShiftType.T1_SAB, LocalDate.of(2025, 10, 11));
            a.setId(1L);
            comAlocacoes(a);

            ScaleCoverageDTO relatorio = service.relatorio(1L);

            assertThat(relatorio.coveredSlots()).isEqualTo(1);
            assertThat(slot(relatorio, LocalDate.of(2025, 10, 11), "T1").covered()).isTrue();
            assertThat(slot(relatorio, PRIMEIRO_SABADO, "T1").covered()).isFalse();
        }

        @Test
        @DisplayName("o slot diz quem está escalado, e não só que há alguém")
        void slotMostraQuem() {
            comAlocacoes(alocacao(ShiftType.T1_SAB));

            SlotCoverageDTO t1 = slot(service.relatorio(1L), PRIMEIRO_SABADO, "T1");

            assertThat(t1.people()).containsExactly("Ana");
            assertThat(t1.peopleCount()).isEqualTo(1);
            assertThat(t1.interval()).isEqualTo("08h00-12h00");
            assertThat(t1.dayLabel()).isEqualTo("Sábado");
        }

        @Test
        @DisplayName("a percentagem é arredondada e não crua, para o número ler-se numa etiqueta")
        void percentagemArredondada() {
            // 1 slot coberto em 24 dá 4,17% — que tem de sair 4 e não
            // 4.166666666666667.
            ShiftScheduling a = TestFixtures.allocation(
                    escala(), ana, ShiftType.T1_SAB, PRIMEIRO_SABADO);
            a.setId(1L);
            comAlocacoes(a);

            assertThat(service.relatorio(1L).coveragePercent()).isEqualTo(4);
        }

        @Test
        @DisplayName("um domingo só tem o turno T6, e não cinco turnos de sábado")
        void domingoSoTemT6() {
            comAlocacoes();

            ScaleCoverageDTO relatorio = service.relatorio(1L);

            assertThat(relatorio.slots().stream()
                    .filter(s -> s.date().equals(PRIMEIRO_DOMINGO)).count()).isEqualTo(1);
            assertThat(relatorio.slots().stream()
                    .filter(s -> s.date().equals(PRIMEIRO_DOMINGO))
                    .findFirst().orElseThrow().shift()).isEqualTo("T6");
        }

        @Test
        @DisplayName("conta as alocações de contas desativadas, que são turnos que ninguém vai cobrir")
        void contaDesativadosEscalados() {
            User inativo = TestFixtures.user("Fantasma", "fantasma@teste.com", UserProfile.ANALIST);
            inativo.setId(9L);
            inativo.setActive(false);
            ShiftScheduling a = TestFixtures.allocation(
                    escala(), inativo, ShiftType.T6_DOM, PRIMEIRO_DOMINGO);
            a.setId(5L);
            comAlocacoes(a);

            assertThat(service.relatorio(1L).inactivePeopleScheduled()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Escala com datas invertidas")
    class PeriodoInvertido {

        private void comPeriodoInvertido() {
            EditionScale invertida = TestFixtures.editionScale(
                    "Escala Inv", ana, EditionStatus.DRAFT);
            invertida.setInitialDate(LocalDate.of(2025, 10, 31));
            invertida.setEndDate(LocalDate.of(2025, 10, 1));

            when(editionScaleRepository.findById(1L)).thenReturn(Optional.of(invertida));
            when(shiftSchedulingRepository.findByEditionScaleIdOrderByIdAsc(1L))
                    .thenReturn(List.of());
        }

        @Test
        @DisplayName("dá zeros em vez de rebentar: um relatório que dá exception é pior")
        void naoRebenta() {
            comPeriodoInvertido();

            ScaleCoverageDTO relatorio = service.relatorio(1L);

            assertThat(relatorio.totalSlots()).isZero();
            assertThat(relatorio.coveragePercent()).isZero();
            assertThat(relatorio.slots()).isEmpty();
            assertThat(relatorio.temConflitos()).isFalse();
        }

        @Test
        @DisplayName("nem sequer pergunta as folgas, porque não há um único dia para cruzar")
        void naoConsultaFolgas() {
            comPeriodoInvertido();

            service.relatorio(1L);

            verify(userLeaveRepository, never()).listarQueSeCruzamCom(any(), any());
        }
    }

    @Nested
    @DisplayName("Sobreposições")
    class Sobreposicoes {

        @Test
        @DisplayName("T1 em cima de T2 é uma sobreposição, com todos os dias afetados")
        void apanhaSobreposicaoReal() {
            comAlocacoes(alocacao(ShiftType.T1_SAB), alocacao(ShiftType.T2_SAB));

            ScaleCoverageDTO relatorio = service.relatorio(1L);

            assertThat(relatorio.overlaps()).hasSize(1);
            OverlapConflictDTO conflito = relatorio.overlaps().get(0);
            assertThat(conflito.userName()).isEqualTo("Ana");
            assertThat(conflito.firstShift()).isEqualTo("T1");
            assertThat(conflito.secondShift()).isEqualTo("T2");
            assertThat(conflito.affectedDates()).hasSize(SABADOS_OUTUBRO_2025);
        }

        @Test
        @DisplayName("T1 seguido de T3 não é sobreposição: acaba quando o outro começa")
        void turnosSeguidosNaoSaoSobreposicao() {
            comAlocacoes(alocacao(ShiftType.T1_SAB), alocacao(ShiftType.T3_SAB));

            assertThat(service.relatorio(1L).overlaps()).isEmpty();
        }

        @Test
        @DisplayName("T1 e T6 não se cruzam: o mesmo horário em dias diferentes não é conflito")
        void diasDiferentesNaoSaoSobreposicao() {
            comAlocacoes(alocacao(ShiftType.T1_SAB), alocacao(ShiftType.T6_DOM));

            assertThat(service.relatorio(1L).overlaps()).isEmpty();
        }

        @Test
        @DisplayName("pessoas diferentes podem estar nos turnos que se cruzam ao mesmo tempo")
        void outrasPessoasNaoSaoSobreposicao() {
            User bruno = TestFixtures.user("Bruno", "bruno@teste.com", UserProfile.ANALIST);
            bruno.setId(2L);

            comAlocacoes(alocacaoDe(ana, ShiftType.T1_SAB),
                    alocacaoDe(bruno, ShiftType.T2_SAB));

            assertThat(service.relatorio(1L).overlaps()).isEmpty();
        }

        @Test
        @DisplayName("o relatório mostra os horários especiais, que podem mudar o conflito")
        void mostraHorariosCustomizados() {
            ShiftScheduling customizada = TestFixtures.allocationCompleta(
                    escala(), ana, ShiftType.T1_SAB, null, List.of(), LocalTime.of(9, 0),
                    LocalTime.of(13, 0));
            customizada.setId(1L);
            comAlocacoes(customizada, alocacao(ShiftType.T3_SAB));

            OverlapConflictDTO conflito = service.relatorio(1L).overlaps().get(0);

            // T1 customizado é 09h00-13h00 e cruza T3 (12h00-16h00); o T1 de
            // base (08h00-12h00) só se tocaria nele.
            assertThat(conflito.firstInterval()).isEqualTo("09h00-13h00");
            assertThat(conflito.descricao()).contains("09h00-13h00");
        }

        @Test
        @DisplayName("o mesmo par só aparece uma vez, não em duplicado pelos dois sentidos")
        void naoContaOMesmoParDuasVezes() {
            comAlocacoes(alocacao(ShiftType.T1_SAB), alocacao(ShiftType.T2_SAB));

            assertThat(service.relatorio(1L).overlaps()).hasSize(1);
        }

        @Test
        @DisplayName("cada pessoa tem no máximo um conflito por par, mesmo com muitas alocações")
        void naoMixaPessoas() {
            User bruno = TestFixtures.user("Bruno", "bruno@teste.com", UserProfile.ANALIST);
            bruno.setId(2L);

            comAlocacoes(
                    alocacaoDe(ana, ShiftType.T1_SAB),
                    alocacaoDe(ana, ShiftType.T2_SAB),
                    alocacaoDe(bruno, ShiftType.T1_SAB),
                    alocacaoDe(bruno, ShiftType.T3_SAB));

            ScaleCoverageDTO relatorio = service.relatorio(1L);

            assertThat(relatorio.overlaps()).hasSize(1);
            assertThat(relatorio.overlaps().get(0).userName()).isEqualTo("Ana");
        }
    }

    @Nested
    @DisplayName("Alocações em dias de folga")
    class Folgas {

        private void comFolgas(UserLeave... folgas) {
            when(editionScaleRepository.findById(1L)).thenReturn(Optional.of(escala()));
            when(shiftSchedulingRepository.findByEditionScaleIdOrderByIdAsc(1L))
                    .thenReturn(List.of(alocacao(ShiftType.T1_SAB)));
            when(userLeaveRepository.listarQueSeCruzamCom(any(), any())).thenReturn(List.of(folgas));
        }

        @Test
        @DisplayName("uma alocação que calha a um dia de folga é conflicto, dia a dia")
        void apanhaAlocacaoEmFolga() {
            comFolgas(TestFixtures.leave(ana, LocalDate.of(2025, 10, 11)));

            ScaleCoverageDTO relatorio = service.relatorio(1L);

            assertThat(relatorio.leaveConflicts()).hasSize(1);
            LeaveConflictDTO conflito = relatorio.leaveConflicts().get(0);
            assertThat(conflito.date()).isEqualTo(LocalDate.of(2025, 10, 11));
            assertThat(conflito.userName()).isEqualTo("Ana");
            assertThat(conflito.descricao()).contains("folga");
        }

        @Test
        @DisplayName("uma folga que começa antes da escala e acaba dentro dela também conta")
        void folgaQueCruzaOPeriodo() {
            comFolgas(TestFixtures.leave(
                    ana, LocalDate.of(2025, 9, 1), LocalDate.of(2025, 10, 18), "Baixa"));

            assertThat(service.relatorio(1L).leaveConflicts())
                    .hasSize(3);
        }

        @Test
        @DisplayName("uma folga noutro dia não é conflito")
        void folgaNoutroDiaNaoConta() {
            comFolgas(TestFixtures.leave(ana, PRIMEIRO_DOMINGO));

            assertThat(service.relatorio(1L).leaveConflicts()).isEmpty();
        }

        @Test
        @DisplayName("uma folga de outra pessoa não é conflito")
        void folgaDeOutraPessoaNaoConta() {
            User bruno = TestFixtures.user("Bruno", "bruno@teste.com", UserProfile.ANALIST);
            bruno.setId(2L);

            comFolgas(TestFixtures.leave(bruno, LocalDate.of(2025, 10, 11)));

            assertThat(service.relatorio(1L).leaveConflicts()).isEmpty();
        }

        @Test
        @DisplayName("uma alocação com data específica fora do dia de folga não é conflito")
        void dataEspecificaForaDaFolga() {
            ShiftScheduling a = TestFixtures.allocation(
                    escala(), ana, ShiftType.T1_SAB, LocalDate.of(2025, 10, 18));
            a.setId(1L);
            when(editionScaleRepository.findById(1L)).thenReturn(Optional.of(escala()));
            when(shiftSchedulingRepository.findByEditionScaleIdOrderByIdAsc(1L))
                    .thenReturn(List.of(a));
            when(userLeaveRepository.listarQueSeCruzamCom(any(), any()))
                    .thenReturn(List.of(TestFixtures.leave(ana, LocalDate.of(2025, 10, 11))));

            assertThat(service.relatorio(1L).leaveConflicts()).isEmpty();
        }

        @Test
        @DisplayName("o relatório não traz o motivo da folga, porque é visível a qualquer autenticação")
        void naoExpoeOMotivoDaFolga() {
            comFolgas(TestFixtures.leave(
                    ana, LocalDate.of(2025, 10, 11), LocalDate.of(2025, 10, 11),
                    "Motivo reservado"));

            String json = String.valueOf(service.relatorio(1L).leaveConflicts());

            assertThat(json).doesNotContain("Motivo reservado");
        }
    }

    @Nested
    @DisplayName("Escala inexistente")
    class EscalaInexistente {

        @Test
        @DisplayName("dá mensagem de negócio, e não 500")
        void daMensagem() {
            when(editionScaleRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.relatorio(99L))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessage("Edição de Escala não encontrada.");
        }

        @Test
        @DisplayName("não procura alocações de uma escala que não existe")
        void naoProcuraAlocacoes() {
            when(editionScaleRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.relatorio(99L))
                    .isInstanceOf(RegraDeNegocioException.class);

            verify(shiftSchedulingRepository, never())
                    .findByEditionScaleIdOrderByIdAsc(anyLong());
        }
    }
}
