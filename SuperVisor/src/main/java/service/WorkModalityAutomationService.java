package service;

import domain.dto.WorkModalityScheduleDTO;
import domain.model.entities.User;
import domain.model.entities.WorkModalitySchedule;
import domain.model.enums.TeamGroup;
import domain.model.enums.WorkModality;
import domain.repository.UserRepository;
import domain.repository.WorkModalityScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkModalityAutomationService {

    /**
     * Dias úteis da escala. Sábado e domingo ficam de fora de propósito: uma
     * grelha de home office com dois dias sem sentido só distorce a leitura do
     * que a escala diz.
     */
    private static final List<DayOfWeek> DIAS_UTEIS =
            List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    /**
     * Dias ímpares da semana (segunda, quarta, sexta) a que o padrão se refere.
     * A semana par é a imagem espelhada, por isso o mesmo conjunto serve para as
     * duas metades.
     */
    private static final List<DayOfWeek> DIAS_ESGADOS = List.of(DayOfWeek.MONDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

    @Autowired
    private WorkModalityScheduleRepository workModalityScheduleRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Gera a escala de presencialidade da semana da data de referência.
     *
     * <p>A semana é a ISO — segunda a domingo — e a paridade vem do número da
     * semana, não do calendário: o padrão alterna de semana para semana, e uma
     * referência a "dias desde o início do ano" mudaria de par no dia 1 de
     * janeiro e reiniciaria o ciclo. A partir do número ISO, o padrão é
     * reprodutível a partir de qualquer data e não depende de quando correu a
     * última geração.
     *
     * <p>A operação é repetível: as células que já existam são reescritas com o
     * valor do padrão em vez de duplicadas. A restrição de unicidade
     * {@code (usuário, dia)} garante que a segunda passagem não pode criar
     * linhas iguais, mesmo que duas gerações concorram.
     *
     * <p>Um colaborador sem equipe não gera linha. Atribuir-lhe um padrão
     * arbitrário produziria uma escala plausível e errada, e a grelha não
     * distinguiria os dois casos.
     */
    @Transactional
    public List<WorkModalityScheduleDTO> gerar(LocalDate dataReferencia) {

        LocalDate referencia = dataReferencia == null ? LocalDate.now() : dataReferencia;
        LocalDate inicioSemana = inicioDaSemanaIso(referencia);
        LocalDate fimSemana = inicioSemana.plusDays(4);
        boolean semanaImpar = semanaIsoDe(referencia) % 2 == 1;

        List<User> colaboradores = userRepository.findByActiveTrueAndTeamGroupIsNotNullOrderByNameAsc();

        if (colaboradores.isEmpty()) {
            throw new IllegalStateException(
                    "Nenhum usuário ativo tem equipe atribuída, por isso não há escala para gerar.");
        }

        Map<ChaveCelula, WorkModalitySchedule> existentes = new HashMap<>();
        for (WorkModalitySchedule registo : workModalityScheduleRepository
                .listarSemUtilizadorDoIntervalo(inicioSemana, fimSemana)) {
            existentes.put(new ChaveCelula(registo.getUser().getId(), registo.getDate()), registo);
        }

        List<WorkModalitySchedule> aGravar = new ArrayList<>();

        for (User colaborador : colaboradores) {
            TeamGroup equipa = colaborador.getTeamGroup();
            for (LocalDate dia : diasUteisDe(inicioSemana)) {
                WorkModality modalidade = modalidadePara(equipa, dia, semanaImpar);

                ChaveCelula chave = new ChaveCelula(colaborador.getId(), dia);
                WorkModalitySchedule registo = existentes.get(chave);

                if (registo == null) {
                    registo = new WorkModalitySchedule();
                    registo.setUser(colaborador);
                    registo.setDate(dia);
                }

                registo.setModality(modalidade);
                registo.setTeamGroup(equipa);
                aGravar.add(registo);
            }
        }

        return workModalityScheduleRepository.saveAll(aGravar).stream()
                .map(WorkModalityScheduleDTO::from)
                .toList();
    }

    /**
     * Escala já existente num intervalo, para a grelha.
     *
     * <p>Devolve só as linhas que existem. Um usuário sem equipe não aparece
     * aqui, o que é coerente com a geração: a grelha mostra quem tem escala, e
     * a lista de equipes mostra quem está sem equipe atribuída.
     */
    @Transactional(readOnly = true)
    public List<WorkModalityScheduleDTO> listar(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) {
            throw new IllegalArgumentException("O intervalo da escala é obrigatório.");
        }
        if (fim.isBefore(inicio)) {
            throw new IllegalArgumentException("A data final do intervalo é anterior à inicial.");
        }
        return workModalityScheduleRepository.listarIntervalo(inicio, fim).stream()
                .map(WorkModalityScheduleDTO::from)
                .toList();
    }

    /**
     * Segunda-feira da semana ISO da data.
     *
     * <p>{@code with(DayOfWeek.MONDAY)} ajusta para a segunda anterior ou igual,
     * que é exatamente o início da semana ISO mesmo quando a data é um domingo:
     * nesse caso devolve a segunda do domingo, e não a seguinte, que pertence à
     * semana ISO seguinte.
     */
    private LocalDate inicioDaSemanaIso(LocalDate data) {
        return data.with(DayOfWeek.MONDAY);
    }

    private List<LocalDate> diasUteisDe(LocalDate inicioSemana) {
        List<LocalDate> dias = new ArrayList<>(DIAS_UTEIS.size());
        for (DayOfWeek dia : DIAS_UTEIS) {
            dias.add(inicioSemana.with(dia));
        }
        return dias;
    }

    private int semanaIsoDe(LocalDate data) {
        return data.get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    /**
     * Regra de alternância.
     *
     * <p>Comprimida ao essencial: a equipe que está presencial à segunda-feira
     * numa semana ímpar é a equipe A, e a semana par é a sua imagem espelhada.
     * Tudo o que o enunciado descreve — A ímpar presencial seg/qua/sex, B ímpar
     * home office nos mesmos dias, e a inversão nas semanas pares — sai daqui
     * sem uma tabela de 4 linhas nem uma condição por dia.
     */
    private WorkModality modalidadePara(TeamGroup equipa, LocalDate dia, boolean semanaImpar) {
        boolean diaEsgado = DIAS_ESGADOS.contains(dia.getDayOfWeek());
        boolean presencialNosEsgados = semanaImpar
                ? equipa == TeamGroup.EQUIPE_A
                : equipa == TeamGroup.EQUIPE_B;

        return diaEsgado == presencialNosEsgados
                ? WorkModality.PRESENCIAL
                : WorkModality.HOME_OFFICE;
    }

    /**
     * Identidade de uma célula da grelha. Existe para não repetir o
     * {@code userId + "|" + date} espalhado pelo serviço, onde um erro de
     * formato colidiria duas células e faria a reescrita falhar em silêncio.
     */
    private record ChaveCelula(Long userId, LocalDate date) {
    }
}
