package service;

import domain.model.entities.User;
import domain.model.entities.WorkModalitySchedule;
import domain.model.enums.TeamGroup;
import domain.model.enums.WorkModality;
import domain.repository.UserRepository;
import domain.repository.WorkModalityScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Escala de presencialidade — geração automática")
class WorkModalityAutomationServiceTest {

    /**
     * Datas escolhidas pelo seu número ISO, e não pelo calendário: a regra
     * alterna por semana, e um teste que escolhesse datas ao acaso falharia
     * metade das vezes sem que nada tivesse mudado.
     *
     * <p>2026-03-16 é a semana ISO 12, par. 2026-03-23 é a semana 13, ímpar.
     * Ambas começam à segunda-feira, para a conversão do início da semana não
     * ser ela própria o que se está sendo testado.
     */
    private static final LocalDate SEMANA_PAR = LocalDate.of(2026, 3, 16);
    private static final LocalDate SEMANA_IMPAR = LocalDate.of(2026, 3, 23);

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkModalityScheduleRepository workModalityScheduleRepository;

    @InjectMocks
    private WorkModalityAutomationService workModalityAutomationService;

    private User ana;
    private User bruno;
    private User semEquipa;

    @BeforeEach
    void setUp() {
        ana = utilizador(1L, "Ana", TeamGroup.EQUIPE_A);
        bruno = utilizador(2L, "Bruno", TeamGroup.EQUIPE_B);
        semEquipa = utilizador(3L, "Carla", null);

        // Lenient porque nem todos os testes geram escala: os da listagem não
        // passam por aqui, e um stubbing estrito que fica por usar faria o
        // teste falhar por uma razão que nada tem com o que se está sendo testado.
        lenient().when(workModalityScheduleRepository.saveAll(any()))
                .thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    private User utilizador(Long id, String nome, TeamGroup equipa) {
        // Passa pelas fixtures do projeto em vez de construir o usuário à
        // mão: é onde a equipe é atribuída, e é o mesmo caminho que os testes
        // de integração usam, para não haver duas formas de montar a mesma
        // coisa.
        User user = equipa == null
                ? tests.support.TestFixtures.analyst()
                : tests.support.TestFixtures.analystInTeam(equipa);
        user.setId(id);
        user.setName(nome);
        user.setEmail(nome.toLowerCase() + "@teste.com");
        return user;
    }

    private void colaboradores(User... users) {
        when(userRepository.findByActiveTrueAndTeamGroupIsNotNullOrderByNameAsc())
                .thenReturn(List.of(users));
    }

    private void semRegistosExistentes() {
        lenient().when(workModalityScheduleRepository.listarSemUtilizadorDoIntervalo(any(), any()))
                .thenReturn(List.of());
    }

    /**
     * Gera e devolve o que foi entregue ao {@code saveAll}.
     */
    @SuppressWarnings("unchecked")
    private List<WorkModalitySchedule> gerar(LocalDate referencia) {
        workModalityAutomationService.gerar(referencia);
        ArgumentCaptor<List<WorkModalitySchedule>> captor = ArgumentCaptor.forClass(List.class);
        verify(workModalityScheduleRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    /**
     * Todas as gerações, por ordem, quando a mesma data é gerada mais do que
     * uma vez.
     */
    @SuppressWarnings("unchecked")
    private List<List<WorkModalitySchedule>> gerarVarias(LocalDate... referencias) {
        for (LocalDate referencia : referencias) {
            workModalityAutomationService.gerar(referencia);
        }
        ArgumentCaptor<List<WorkModalitySchedule>> captor = ArgumentCaptor.forClass(List.class);
        verify(workModalityScheduleRepository, times(referencias.length)).saveAll(captor.capture());
        return captor.getAllValues();
    }

    /**
     * As modalidades de uma geração, por dia, para a Ana.
     */
    private Map<LocalDate, WorkModality> porDia(List<WorkModalitySchedule> registos) {
        Map<LocalDate, WorkModality> porDia = new java.util.TreeMap<>();
        for (WorkModalitySchedule registo : registos) {
            if (registo.getUser().getId().equals(ana.getId())) {
                porDia.put(registo.getDate(), registo.getModality());
            }
        }
        return porDia;
    }

    /**
     * Gera a partir de {@code segunda} e devolve as modalidades por dia.
     */
    private Map<LocalDate, WorkModality> escalaDe(LocalDate segunda) {
        return porDia(gerar(segunda));
    }

    @Nested
    @DisplayName("A alternância do padrão")
    class Alternancia {

        @Test
        @DisplayName("numa semana ímpar, a equipe A está presencial à segunda, quarta e sexta")
        void imparEquipeAPresencial() {
            colaboradores(ana);
            semRegistosExistentes();

            var porDia = escalaDe(SEMANA_IMPAR);

            assertThat(porDia.get(SEMANA_IMPAR)).isEqualTo(WorkModality.PRESENCIAL);
            assertThat(porDia.get(SEMANA_IMPAR.plusDays(2))).isEqualTo(WorkModality.PRESENCIAL);
            assertThat(porDia.get(SEMANA_IMPAR.plusDays(4))).isEqualTo(WorkModality.PRESENCIAL);
        }

        @Test
        @DisplayName("numa semana ímpar, a equipe A está em home office à terça e quinta")
        void imparEquipeAHomeOffice() {
            colaboradores(ana);
            semRegistosExistentes();

            var porDia = escalaDe(SEMANA_IMPAR);

            assertThat(porDia.get(SEMANA_IMPAR.plusDays(1))).isEqualTo(WorkModality.HOME_OFFICE);
            assertThat(porDia.get(SEMANA_IMPAR.plusDays(3))).isEqualTo(WorkModality.HOME_OFFICE);
        }

        @Test
        @DisplayName("as duas equipes nunca coincidem no mesmo dia")
        void asDequipasNuncaCoincidem() {
            colaboradores(ana, bruno);
            semRegistosExistentes();

            var registos = gerar(SEMANA_IMPAR);
            var porDiaEEquipa = new java.util.TreeMap<LocalDate, Map<Long, WorkModality>>();
            for (WorkModalitySchedule registo : registos) {
                porDiaEEquipa
                        .computeIfAbsent(registo.getDate(), d -> new HashMap<>())
                        .put(registo.getUser().getId(), registo.getModality());
            }

            // Um dia em que as duas equipes tivessem a mesma modalidade seria
            // o sinal de que o padrão deixou de alternar: a semana inteira
            // ficaria igual, e a escala de um escritório não é isso.
            assertThat(porDiaEEquipa).hasSize(5);
            porDiaEEquipa.forEach((dia, porEquipa) ->
                    assertThat(porEquipa).as("dia %s", dia).hasSize(2));
            assertThat(porDiaEEquipa.get(SEMANA_IMPAR))
                    .containsEntry(ana.getId(), WorkModality.PRESENCIAL)
                    .containsEntry(bruno.getId(), WorkModality.HOME_OFFICE);
        }

        @Test
        @DisplayName("numa semana par, o padrão inverte: quem estava presencial passa a home office")
        void parInverte() {
            colaboradores(ana);
            semRegistosExistentes();

            // O que muda entre as duas é a paridade, e é isso que a comparação
            // tem de isolar.
            var geracoes = gerarVarias(SEMANA_IMPAR, SEMANA_PAR);

            var impar = porDia(geracoes.get(0));
            var par = porDia(geracoes.get(1));

            assertThat(par).hasSameSizeAs(impar);
            par.forEach((dia, modalidade) ->
                    assertThat(modalidade).as("dia %s", dia).isNotEqualTo(impar.get(dia)));
        }

        @Test
        @DisplayName("o padrão não depende do dia da semana em que se pede a geração")
        void qualquerDiaDaSemanaDaOMesmaEscala() {
            colaboradores(ana);
            semRegistosExistentes();

            // Quarta-feira e domingo da semana ímpar. O domingo é o caso que
            // separa as duas maneiras de calcular o início da semana: com
            // `with(MONDAY)`, um domingo devolve a segunda anterior, e não a
            // seguinte — que já pertence a outra semana.
            var geracoes = gerarVarias(SEMANA_IMPAR, SEMANA_IMPAR.plusDays(2),
                    SEMANA_IMPAR.plusDays(6));

            List<LocalDate> primeira = new ArrayList<>();
            geracoes.get(0).forEach(r -> primeira.add(r.getDate()));
            List<LocalDate> ultima = new ArrayList<>();
            geracoes.get(2).forEach(r -> ultima.add(r.getDate()));

            assertThat(ultima).isEqualTo(primeira);
        }

        @Test
        @DisplayName("só os dias úteis entram na escala")
        void apenasDiasUteis() {
            colaboradores(ana);
            semRegistosExistentes();

            var datas = gerar(SEMANA_IMPAR).stream().map(WorkModalitySchedule::getDate).toList();

            assertThat(datas).hasSize(5);
            assertThat(datas).allSatisfy(d ->
                    assertThat(d.getDayOfWeek().getValue())
                            .as("%s não é um dia útil", d)
                            .isBetween(1, 5));
        }
    }

    @Nested
    @DisplayName("Quem entra na escala")
    class QuemEntra {

        @Test
        @DisplayName("quem não tem equipe é excluído pela consulta, e o serviço não volta a perguntar")
        void semEquipaExcluidoPelaConsulta() {
            // Não há teste possível para "devolve a escala de alguém sem equipe"
            // a este nível: o serviço pede à base só quem tem equipe, e um
            // repositório mocked que devolvesse alguém sem equipe estaria a
            // mentir sobre a consulta que o serviço fez. O filtro é da
            // consulta, e o que o serviço garante é não fazer outra.
            colaboradores(ana);
            semRegistosExistentes();

            workModalityAutomationService.gerar(SEMANA_IMPAR);

            verify(userRepository).findByActiveTrueAndTeamGroupIsNotNullOrderByNameAsc();
            verifyNoMoreInteractions(userRepository);
            assertThat(semEquipa.getTeamGroup()).isNull();
        }

        @Test
        @DisplayName("a consulta à base é a que filtra os ativos com equipe, pela ordem do nome")
        void apenasAtivosComEquipa() {
            colaboradores(ana, bruno);
            semRegistosExistentes();

            workModalityAutomationService.gerar(SEMANA_IMPAR);

            // Fixar o método chamado é o que impede uma troca por "todos os
            // usuários" que trouxesse quem não tem equipe, ou uma ordenação
            // diferente que fizesse a grelha aparecer por ordem de entrada.
            verify(userRepository).findByActiveTrueAndTeamGroupIsNotNullOrderByNameAsc();
        }

        @Test
        @DisplayName("sem ninguém com equipe, a geração falha com uma mensagem que diz porquê")
        void semNinguemComEquipa() {
            colaboradores();
            semRegistosExistentes();

            assertThatThrownBy(() -> workModalityAutomationService.gerar(SEMANA_IMPAR))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Nenhum usuário ativo tem equipe");

            verify(workModalityScheduleRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("Repetibilidade")
    class Repetibilidade {

        @Test
        @DisplayName("a segunda geração reescreve as células em vez de as duplicar")
        void reescreveEmVezDeDuplicar() {
            colaboradores(ana);

            // Já existe uma célula de segunda, com a modalidade errada.
            WorkModalitySchedule previa = new WorkModalitySchedule();
            previa.setId(500L);
            previa.setUser(ana);
            previa.setDate(SEMANA_IMPAR);
            previa.setModality(WorkModality.HOME_OFFICE);

            when(workModalityScheduleRepository.listarSemUtilizadorDoIntervalo(any(), any()))
                    .thenReturn(List.of(previa));

            var registos = gerar(SEMANA_IMPAR);

            assertThat(registos).hasSize(5);
            var reescrita = registos.stream()
                    .filter(r -> r.getDate().equals(SEMANA_IMPAR))
                    .findFirst()
                    .orElseThrow();

            // A mesma linha, com o id que já tinha, e com o valor do padrão: uma
            // linha nova em vez desta deixaria a grelha com duas modalidades
            // para a mesma pessoa no mesmo dia, e o padrão perderia sempre.
            assertThat(reescrita.getId()).isEqualTo(500L);
            assertThat(reescrita.getModality()).isEqualTo(WorkModality.PRESENCIAL);
        }

        @Test
        @DisplayName("as linhas novas ficam sem id, para a base as atribuir")
        void linhasNovasSemId() {
            colaboradores(ana);
            semRegistosExistentes();

            assertThat(gerar(SEMANA_IMPAR)).allSatisfy(r -> assertThat(r.getId()).isNull());
        }

        @Test
        @DisplayName("cada célula é gerada uma vez por pessoa e dia")
        void umaCelulaPorPessoaEDia() {
            colaboradores(ana, bruno);
            semRegistosExistentes();

            var registos = gerar(SEMANA_IMPAR);

            assertThat(registos).hasSize(10);
            assertThat(registos.stream()
                    .map(r -> r.getUser().getId() + "|" + r.getDate())
                    .distinct())
                    .hasSize(10);
        }
    }

    @Nested
    @DisplayName("A referência")
    class Referencia {

        @Test
        @DisplayName("sem data de referência, usa a de hoje")
        void semDataUsaHoje() {
            colaboradores(ana);
            semRegistosExistentes();

            workModalityAutomationService.gerar(null);

            var segunda = LocalDate.now().with(DayOfWeek.MONDAY);
            verify(workModalityScheduleRepository)
                    .listarSemUtilizadorDoIntervalo(eq(segunda), eq(segunda.plusDays(4)));
        }

        @Test
        @DisplayName("o pedido de linhas existentes é limitado à semana ISO da referência")
        void consultaSemanaInteira() {
            colaboradores(ana);
            semRegistosExistentes();

            workModalityAutomationService.gerar(SEMANA_IMPAR.plusDays(3));

            verify(workModalityScheduleRepository)
                    .listarSemUtilizadorDoIntervalo(eq(SEMANA_IMPAR), eq(SEMANA_IMPAR.plusDays(4)));
        }
    }

    @Nested
    @DisplayName("Listagem")
    class Listagem {

        @Test
        @DisplayName("sem intervalo, recusa com uma mensagem de validação")
        void semIntervalo() {
            assertThatThrownBy(() -> workModalityAutomationService.listar(null, LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("O intervalo da escala é obrigatório.");

            assertThatThrownBy(() -> workModalityAutomationService.listar(LocalDate.now(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("O intervalo da escala é obrigatório.");
        }

        @Test
        @DisplayName("um intervalo invertido é recusado, em vez de devolver uma lista vazia")
        void intervaloInvertido() {
            assertThatThrownBy(() -> workModalityAutomationService
                            .listar(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 10)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("A data final do intervalo é anterior à inicial.");
        }

        @Test
        @DisplayName("devolve só as linhas que existem, para a grelha não inventar células")
        void devolveOQueExiste() {
            var registo = new WorkModalitySchedule();
            registo.setUser(ana);
            registo.setDate(SEMANA_IMPAR);
            registo.setTeamGroup(TeamGroup.EQUIPE_A);
            registo.setModality(WorkModality.PRESENCIAL);

            when(workModalityScheduleRepository.listarIntervalo(any(), any()))
                    .thenReturn(List.of(registo));

            var resultado = workModalityAutomationService.listar(SEMANA_IMPAR,
                    SEMANA_IMPAR.plusDays(4));

            assertThat(resultado).hasSize(1);
            assertThat(resultado.get(0).userName()).isEqualTo("Ana");
            assertThat(resultado.get(0).weekNumber()).isEqualTo(13);
            assertThat(resultado.get(0).weekOdd()).isTrue();
        }
    }

    @Nested
    @DisplayName("Exclusão")
    class Exclusao {

        @Test
        @DisplayName("apaga o intervalo pedido e diz quantas linhas caíram")
        void apagaOIntervalo() {
            when(workModalityScheduleRepository
                    .apagarDoIntervalo(SEMANA_IMPAR, SEMANA_IMPAR.plusDays(4)))
                    .thenReturn(10);

            int apagadas = workModalityAutomationService
                    .apagarIntervalo(SEMANA_IMPAR, SEMANA_IMPAR.plusDays(4));

            assertThat(apagadas).isEqualTo(10);
        }

        @Test
        @DisplayName("sem intervalo, recusa sem tocar na base de dados")
        void semIntervalo() {
            assertThatThrownBy(() -> workModalityAutomationService.apagarIntervalo(null, LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("O intervalo da escala é obrigatório.");

            verify(workModalityScheduleRepository, never()).apagarDoIntervalo(any(), any());
        }

        @Test
        @DisplayName("um intervalo invertido é recusado: apagar o período errado é o pior resultado possível")
        void intervaloInvertido() {
            assertThatThrownBy(() -> workModalityAutomationService
                            .apagarIntervalo(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 10)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("A data final do intervalo é anterior à inicial.");

            verify(workModalityScheduleRepository, never()).apagarDoIntervalo(any(), any());
        }

        @Test
        @DisplayName("uma semana sem escala devolve zero e não dá erro: já não havia nada para perder")
        void semanaVaziaDevolveZero() {
            when(workModalityScheduleRepository
                    .apagarDoIntervalo(SEMANA_IMPAR, SEMANA_IMPAR.plusDays(4)))
                    .thenReturn(0);

            assertThat(workModalityAutomationService
                    .apagarIntervalo(SEMANA_IMPAR, SEMANA_IMPAR.plusDays(4))).isZero();
        }

        @Test
        @DisplayName("o intervalo apagado é o mesmo que a grelha está a mostrar, e não a semana corrente")
        void apagaOIntervaloVisivel() {
            when(workModalityScheduleRepository
                    .apagarDoIntervalo(any(), any()))
                    .thenReturn(3);

            workModalityAutomationService.apagarIntervalo(LocalDate.of(2025, 10, 6),
                    LocalDate.of(2025, 10, 10));

            verify(workModalityScheduleRepository).apagarDoIntervalo(
                    eq(LocalDate.of(2025, 10, 6)), eq(LocalDate.of(2025, 10, 10)));
        }
    }
}
