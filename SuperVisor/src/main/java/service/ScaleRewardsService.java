package service;

import domain.model.entities.EditionScale;
import domain.model.entities.Holiday;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.AssignmentType;
import domain.model.enums.ShiftType;
import domain.repository.HolidayRepository;
import domain.repository.ShiftSchedulingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Crédito de folgas ganhas pelo trabalho numa escala de fim de semana.
 *
 * <p>Três regras, cumulativas:
 * <ul>
 *   <li><strong>Domingo</strong> — quem trabalha o turno {@code T6_DOM} ganha
 *       meio dia de folga (meio expediente);</li>
 *   <li><strong>Celular da Marinas</strong> — quem leva a atribuição especial
 *       {@code CELULAR_MARINAS} num turno ganha um dia inteiro;</li>
 *   <li><strong>Feriado</strong> — quem trabalha num dia registado como feriado
 *       ganha um dia inteiro, independentemente do subturno que faça.</li>
 * </ul>
 * Quem cumpre as três num único turno — domingo com celular, num feriado — leva
 * 2.5 dias, e é por isso que o crédito é calculado por alocação e só depois
 * somado por utilizador: as regras aplicam-se ao mesmo trabalho, não a turnos
 * diferentes.
 *
 * <p>A regra do feriado é a única que não vive em {@link #creditoDe}: as outras
 * duas dependem da alocação sozinha, mas o feriado é uma propriedade do dia e
 * rende <strong>uma vez por dia trabalhado</strong> — dois subturnos no mesmo
 * feriado valem 1.0, não 2.0. O desconto desse «uma vez» é uma decisão sobre o
 * conjunto das alocações de uma pessoa, e por isso acontece em
 * {@link #aplicar}, onde os dias de cada pessoa já estão a ser reunidos.
 *
 * <p>Este serviço só é chamado no fecho da escala (ver
 * {@link ScaleService#completeSchedule}). Enquanto a escala está em rascunho
 * ou publicada, os turnos ainda mudam — alocações são editadas, trocadas e
 * apagadas — e creditar a cada alteração inflacionava o saldo com trabalho que
 * ainda não aconteceu. Fecho é o momento em que o trabalho é um facto.
 *
 * <p>A leitura das alocações e dos feriados vem da mesma transação que marca a
 * escala como concluída, e as alterações aos {@code User} são persistidas por
 * dirty checking: ou tudo fica gravado, ou nada.
 */
@Service
public class ScaleRewardsService {

    /**
     * Dias ganhos por trabalhar o turno de domingo. Meio expediente, como a
     * falta parcial do módulo de presencialidade: o T6 cobre as oito às doze.
     */
    public static final BigDecimal DIAS_DOMINGO = BigDecimal.valueOf(0.5);

    /** Dias ganhos por levar o Celular da Marinas: um dia de folga completa. */
    public static final BigDecimal DIAS_CELULAR = BigDecimal.ONE;

    /**
     * Dias ganhos por trabalhar num feriado. Um dia completo, seja qual for o
     * subturno — manhã, tarde, ou os dois.
     */
    public static final BigDecimal DIAS_FERIADO = BigDecimal.ONE;

    @Autowired
    private ShiftSchedulingRepository shiftSchedulingRepository;

    @Autowired
    private HolidayRepository holidayRepository;

    /**
     * Creditar os dias ganhos pelas alocações desta escala.
     *
     * @return quantos utilizadores receberam crédito (0 numa escala sem
     *         trabalho de fim de semana nem feriados)
     */
    @Transactional
    public int aplicar(EditionScale escala) {
        List<ShiftScheduling> alocacoes =
                shiftSchedulingRepository.findByEditionScaleIdOrderByIdAsc(escala.getId());
        Set<LocalDate> feriados = datasDeFeriado();

        // A soma é por utilizador e não por alocação: os créditos desta escala
        // têm de entrar no saldo numa única escrita, com um único saldo
        // inicial, para dois domingos valerem 1.0 e não meio a meio. A ordem
        // de inserção é a das alocações, mas o resultado é o mesmo em qualquer
        // ordem — a soma de BigDecimals é comutativa.
        Map<User, BigDecimal> porUtilizador = new LinkedHashMap<>();

        // Dias de feriado já creditados a cada pessoa. Um Set e não um
        // contador: o que se dedupe é o par (pessoa, dia), e dois subturnos do
        // mesmo feriado são duas alocações com a mesma data — a segunda não
        // volta a somar. Feriados diferentes continuam a somar, um a um.
        Map<User, Set<LocalDate>> feriadosCreditados = new LinkedHashMap<>();

        for (ShiftScheduling alocacao : alocacoes) {
            BigDecimal credito = creditoDe(alocacao);

            if (ehFeriado(alocacao, feriados)
                    && feriadosCreditados
                            .computeIfAbsent(alocacao.getUser(), utilizador -> new LinkedHashSet<>())
                            .add(alocacao.getSpecificDate())) {
                credito = credito.add(DIAS_FERIADO);
            }

            if (credito.signum() == 0) {
                continue;
            }
            porUtilizador.merge(alocacao.getUser(), credito, BigDecimal::add);
        }

        porUtilizador.forEach((utilizador, total) ->
                utilizador.setAccumulatedLeaves(soma(utilizador.getAccumulatedLeaves(), total)));

        return porUtilizador.size();
    }

    /**
     * Quantos dias vale uma alocação segundo as regras que não dependem da
     * data: domingo e celular.
     *
     * <p>A regra do feriado não entra aqui — ver a documentação da classe — e é
     * por isso que este método continua a devolver 0.0 para um sábado sem
     * atribuições, mesmo que esse sábado seja feriado.
     *
     * <p>Sem validação de turno nulo: o modelo torna {@code shift} obrigatório,
     * e uma alocação sem turno não existe na base. Nulo aqui seria um bug noutro
     * sítio, e devolver meio dia por omissão esconder-lo-ia.
     */
    public BigDecimal creditoDe(ShiftScheduling alocacao) {
        BigDecimal credito = BigDecimal.ZERO;

        if (alocacao.getShift() == ShiftType.T6_DOM) {
            credito = credito.add(DIAS_DOMINGO);
        }

        if (alocacao.getAssignments() != null
                && alocacao.getAssignments().contains(AssignmentType.CELULAR_MARINAS)) {
            credito = credito.add(DIAS_CELULAR);
        }

        return credito;
    }

    /**
     * Datas de feriado registadas, uma vez por fecho.
     *
     * <p>Uma leitura do conjunto inteiro e não uma consulta por alocação: a
     * alternativa faria uma ida ao banco por turno, quando o calendário de
     * feriados cabe inteiro em memória — e é um calendário que quase nunca
     * cresce.
     */
    private Set<LocalDate> datasDeFeriado() {
        return holidayRepository.findAll().stream()
                .map(Holiday::getDate)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * A alocação cai num feriado?
     *
     * <p>Alocação sem data específica não conta: sem data não há com que
     * comparar, e atribuir o feriado ao turno todo seria inventar uma data que
     * a escala não declarou.
     */
    private boolean ehFeriado(ShiftScheduling alocacao, Set<LocalDate> feriados) {
        return alocacao.getSpecificDate() != null
                && feriados.contains(alocacao.getSpecificDate());
    }

    /**
     * Soma ao saldo, tratando um registo antigo sem saldo como zero.
     *
     * <p>A coluna é {@code NOT NULL} desde a migração V4, mas uma base
     * povoada antes dela pode guardar nulos, e um nulo aqui virava um
     * {@code NullPointerException} a meio da conclusão da escala.
     */
    private BigDecimal soma(BigDecimal saldoAtual, BigDecimal credito) {
        BigDecimal saldo = saldoAtual == null ? BigDecimal.ZERO : saldoAtual;
        return saldo.add(credito);
    }
}
