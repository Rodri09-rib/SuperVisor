package service;

import domain.dto.LeaveConflictDTO;
import domain.dto.OverlapConflictDTO;
import domain.dto.ScaleCoverageDTO;
import domain.dto.SlotCoverageDTO;
import domain.model.IntervaloHorario;
import domain.model.entities.EditionScale;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.UserLeave;
import domain.model.enums.ShiftType;
import domain.repository.EditionScaleRepository;
import domain.repository.ShiftSchedulingRepository;
import domain.repository.UserLeaveRepository;
import exception.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Cobertura e conflitos de uma escala.
 *
 * <p>Existe para pegar o que as regras de escrita não pegam. O
 * double-booking está bloqueado na criação e atualização de alocações, e isso
 * chega para o caminho normal; mas a aplicação tem alocações anteriores a essa
 * regra, o bloqueio é feito por aplicação e não por restrição de base de dados,
 * e a folga é registrada numa tabela à parte que ninguém liga à escala. Tudo o
 * que é inconsistência entre registos já existentes em vez de entre uma
 * operação nova é o que este serviço lê.
 *
 * <p>Nada aqui escreve. É uma leitura, o que significa que pode correr sobre
 * uma escala que já tenha sido publicada sem alterar nada.
 */
@Service
public class ScaleCoverageService {

    /**
     * Teto de dias listados por conflito.
     *
     * <p>Uma alocação sem data específica cruza todas as datas do dia da
     * semana no período, e uma escala de um ano daria uma lista com mais de
     * cinquenta datas por conflito. O número serve para o texto do relatório
     * continuar legível e não para esconder Conflicts: o total vem à parte.
     */
    private static final int MAX_DIAS_POR_CONFLITO = 12;

    @Autowired
    private EditionScaleRepository editionScaleRepository;

    @Autowired
    private ShiftSchedulingRepository shiftSchedulingRepository;

    @Autowired
    private UserLeaveRepository userLeaveRepository;

    @Transactional(readOnly = true)
    public ScaleCoverageDTO relatorio(Long escalaId) {
        EditionScale escala = editionScaleRepository.findById(escalaId)
                .orElseThrow(() -> new RegraDeNegocioException("Edição de Escala não encontrada."));

        List<ShiftScheduling> alocacoes =
                shiftSchedulingRepository.findByEditionScaleIdOrderByIdAsc(escalaId);

        List<LocalDate> datas = datasDoPeriodo(escala);
        List<UserLeave> folgas = datas.isEmpty()
                ? List.of()
                : userLeaveRepository.listarQueSeCruzamCom(datas.get(0), datas.get(datas.size() - 1));

        List<SlotCoverageDTO> slots = calcularSlots(datas, alocacoes);
        int cobertos = (int) slots.stream().filter(SlotCoverageDTO::covered).count();

        return new ScaleCoverageDTO(
                escala.getId(),
                escala.getName(),
                escala.getInitialDate(),
                escala.getEndDate(),
                slots.size(),
                cobertos,
                slots.size() - cobertos,
                percentagem(cobertos, slots.size()),
                alocacoes.size(),
                contarDesativadosEscalados(alocacoes),
                slots,
                sobreposicoes(alocacoes, datas),
                alocacoesEmFolga(alocacoes, folgas, datas));
    }

    /**
     * Todas as datas do período, em ordem.
     *
     * <p>Devolve lista vazia quando a escala tem o fim antes do início, que é
     * aceito pela criação. Não é corrigido aqui — mudar o que foi gravado seria
     * uma decisão da aplicação, não de um relatório — mas também não rebenta:
     * um relatório que dá exception por causa de um dado estranho é pior do que
     * um relatório que diz que não há dias para cobrir.
     */
    private List<LocalDate> datasDoPeriodo(EditionScale escala) {
        LocalDate inicio = escala.getInitialDate();
        LocalDate fim = escala.getEndDate();

        if (inicio == null || fim == null || fim.isBefore(inicio)) {
            return List.of();
        }

        List<LocalDate> datas = new ArrayList<>();
        for (LocalDate data = inicio; !data.isAfter(fim); data = data.plusDays(1)) {
            datas.add(data);
        }
        return datas;
    }

    /**
     * Um slot por turno e dia do período.
     *
     * <p>Só existem slots nos dias em que o turno pode acontecer: T1 a sábado
     * não cria slot à segunda-feira. Contar dias sem turno adicionado daria
     * denominadores maiores e uma percentagem de cobertura artificialmente
     * baixa, sem que nenhum turno estivesse por cobrir.
     */
    private List<SlotCoverageDTO> calcularSlots(List<LocalDate> datas, List<ShiftScheduling> alocacoes) {
        List<SlotCoverageDTO> slots = new ArrayList<>();

        for (LocalDate data : datas) {
            DayOfWeek dia = data.getDayOfWeek();

            for (ShiftType turno : ShiftType.values()) {
                if (turno.getDayOfWeek() != dia) {
                    continue;
                }

                List<String> pessoas = alocacoes.stream()
                        .filter(alocacao -> alocacao.cobreDia(data))
                        .filter(alocacao -> alocacao.getShift() == turno)
                        .map(alocacao -> alocacao.getUser().getName())
                        .distinct()
                        .sorted()
                        .toList();

                slots.add(new SlotCoverageDTO(
                        data,
                        dia,
                        dia == DayOfWeek.SATURDAY ? "Sábado" : "Domingo",
                        turno.getAcronym(),
                        turno.getRotuloCurto(),
                        turno.getIntervalo(),
                        pessoas.size(),
                        !pessoas.isEmpty(),
                        pessoas));
            }
        }

        return slots;
    }

    /**
     * Pares de alocações da mesma pessoa que se cruzam.
     *
     * <p>Reaproveita {@link ShiftScheduling#conflitaCom}, que é a mesma regra que
     * impede a criação: se as duas disagreeem aqui, a regra de escrita tem um
     * buraco, e se o relatório repetir a aritmética dos intervalos em vez de a
     * chamar, os dois lados podem divergir sem que nenhum teste o note.
     *
     * <p>O agrupamento é por pessoa porque é a condição que a sobreposição
     * exige; dentro de cada pessoa bastam os pares, e um par só é contado uma
     * vez porque o ciclo externo só passa pelo índice maior.
     */
    private List<OverlapConflictDTO> sobreposicoes(List<ShiftScheduling> alocacoes, List<LocalDate> datas) {
        Map<Long, List<ShiftScheduling>> porPessoa = new LinkedHashMap<>();

        for (ShiftScheduling alocacao : alocacoes) {
            porPessoa.computeIfAbsent(alocacao.getUser().getId(), chave -> new ArrayList<>())
                    .add(alocacao);
        }

        List<OverlapConflictDTO> conflitos = new ArrayList<>();

        for (List<ShiftScheduling> doUtilizador : porPessoa.values()) {
            for (int i = 0; i < doUtilizador.size(); i++) {
                for (int j = i + 1; j < doUtilizador.size(); j++) {
                    ShiftScheduling primeira = doUtilizador.get(i);
                    ShiftScheduling segunda = doUtilizador.get(j);

                    if (!primeira.conflitaCom(segunda)) {
                        continue;
                    }

                    List<LocalDate> afetadas = datasConflitantes(primeira, segunda, datas);

                    conflitos.add(new OverlapConflictDTO(
                            primeira.getUser().getId(),
                            primeira.getUser().getName(),
                            primeira.getId(),
                            rotulo(primeira),
                            intervalo(primeira),
                            segunda.getId(),
                            rotulo(segunda),
                            intervalo(segunda),
                            afetadas));
                }
            }
        }

        conflitos.sort(Comparator.comparing(OverlapConflictDTO::userName)
                .thenComparingLong(OverlapConflictDTO::firstAllocationId));

        return conflitos;
    }

    /** As datas do período em que as duas alocações estão as duas ativas. */
    private List<LocalDate> datasConflitantes(ShiftScheduling primeira, ShiftScheduling segunda,
                                              List<LocalDate> datas) {
        return datas.stream()
                .filter(data -> primeira.cobreDia(data) && segunda.cobreDia(data))
                .limit(MAX_DIAS_POR_CONFLITO)
                .toList();
    }

    /**
     * Alocações que caem em dias de folga.
     *
     * <p>Só conta os dias em que a alocação está de pé e a folga cobre. Uma
     * folga registada antes do início da escala não gera conflito nenhum, e uma
     * alocação de um sábado não colide com uma folga só de domingo.
     */
    private List<LeaveConflictDTO> alocacoesEmFolga(List<ShiftScheduling> alocacoes,
                                                    List<UserLeave> folgas,
                                                    List<LocalDate> datas) {
        Map<Long, List<UserLeave>> folgasPorPessoa = new TreeMap<>();

        for (UserLeave folga : folgas) {
            folgasPorPessoa.computeIfAbsent(folga.getUser().getId(), chave -> new ArrayList<>())
                    .add(folga);
        }

        List<LeaveConflictDTO> conflitos = new ArrayList<>();

        for (ShiftScheduling alocacao : alocacoes) {
            List<UserLeave> doUtilizador = folgasPorPessoa.get(alocacao.getUser().getId());
            if (doUtilizador == null) {
                continue;
            }

            for (LocalDate data : datas) {
                if (!alocacao.cobreDia(data)) {
                    continue;
                }

                boolean emFolga = doUtilizador.stream()
                        .anyMatch(folga -> !folga.getStartDate().isAfter(data)
                                && !folga.getEndDate().isBefore(data));

                if (emFolga) {
                    conflitos.add(new LeaveConflictDTO(
                            alocacao.getUser().getId(),
                            alocacao.getUser().getName(),
                            alocacao.getId(),
                            rotulo(alocacao),
                            intervalo(alocacao),
                            data));
                }
            }
        }

        conflitos.sort(Comparator.comparing(LeaveConflictDTO::date)
                .thenComparing(LeaveConflictDTO::userName)
                .thenComparingLong(LeaveConflictDTO::allocationId));

        return conflitos;
    }

    /** Quantas alocações pertencem a contas que já não conseguem entrar. */
    private int contarDesativadosEscalados(List<ShiftScheduling> alocacoes) {
        long total = alocacoes.stream()
                .filter(alocacao -> !alocacao.getUser().isActive())
                .count();
        return (int) Math.min(total, (long) Integer.MAX_VALUE);
    }

    /**
     * A percentagem arredondada, e não um {@code double} cru.
     *
     * <p>O valor vai para o dashboard e é lido por gente, não por código. Sem
     * arredondamento aparecem coisas como 66.66666666666666 numa etiqueta, e
     * quando uma escala tem zero slots a divisão por zero daria {@code NaN}, que
     * nem é um número.
     */
    private int percentagem(int parte, int total) {
        if (total == 0) {
            return 0;
        }
        return (int) Math.round((parte * 100.0) / total);
    }

    private String rotulo(ShiftScheduling alocacao) {
        ShiftType turno = alocacao.getShift();
        return turno == null ? "Turno desconhecido" : turno.getAcronym();
    }

    private String intervalo(ShiftScheduling alocacao) {
        IntervaloHorario efetivo = alocacao.intervaloEfetivo();
        return efetivo == null ? "-" : efetivo.formatado();
    }
}
