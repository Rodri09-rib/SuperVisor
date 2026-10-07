package domain.dto;

import domain.model.entities.ShiftScheduling;
import domain.model.entities.UserLeave;
import domain.model.entities.WorkModalitySchedule;
import domain.model.enums.AssignmentType;
import domain.model.enums.AttendanceStatus;
import domain.model.enums.LeaveDuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * Um acontecimento do extrato de folgas de um colaborador.
 *
 * <p>O extrato responde a «de onde vem este número?». O saldo que os cartões
 * mostram é o resultado de três movimentos que chegam por caminhos diferentes
 * — uma folga que o consome, uma falta que gera compensação, um turno de fim
 * de semana que o enche — e sem a lista de acontecimentos o valor é apenas um
 * número que ninguém consegue justificar nem corrigir.
 *
 * <p>{@code dias} é sempre a magnitude, nunca o sinal: o efeito de cada tipo
 * é fixo e conhecido (folga consome, recompensa acrescenta, falta gera
 * dívida) e é o {@code tipo} que o diz. Um sinal embutido no número faria o
 * mesmo valor ser interpretado de duas formas conforme o tipo — e o frontend
 * precisaria de o remover para mostrar «1 dia» numa falta.
 *
 * <p>As fábricas são funções puras sobre as entidades, sem acesso a
 * repositórios nem a estado: é o que as torna testáveis sem base de dados e é
 * também a fronteira que mantém a redação do texto numa só parte — o serviço
 * só junta e ordena.
 */
public record ExtratoEventoDTO(
        LocalDate date,
        String tipo,
        String tipoRotulo,
        String descricao,
        BigDecimal dias
) {

    /** Uma folga registada: consome dias do saldo de folgas. */
    public static final String TIPO_FOLGA = "FOLGA";

    /** Uma falta na presencialidade: gera dívida de compensação. */
    public static final String TIPO_FALTA = "FALTA";

    /** Trabalho de fim de semana creditado no fecho da escala. */
    public static final String TIPO_RECOMPENSA = "RECOMPENSA";

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Folga como acontecimento do extrato.
     *
     * <p>A data é a do início e não a de criação: o extrato é uma linha do
     * tempo das ausências, e uma folga corrigida continua a ter acontecido no
     * dia em que aconteceu — o carimbo de criação diria quando é que alguém
     * preencheu o formulário.
     */
    public static ExtratoEventoDTO deFolga(UserLeave folga) {
        LeaveDuration duracao = folga.getLeaveDuration() == null
                ? LeaveDuration.FULL_DAY
                : folga.getLeaveDuration();
        BigDecimal custo = folga.custoEmDias();

        String periodo = folga.getStartDate().equals(folga.getEndDate())
                ? DATA.format(folga.getStartDate())
                : "de " + DATA.format(folga.getStartDate()) + " a " + DATA.format(folga.getEndDate());
        String motivo = folga.getReason() == null || folga.getReason().isBlank()
                ? ""
                : " — " + folga.getReason();

        String descricao = "Folga " + periodo + " (" + duracao.getRotulo() + ")" + motivo
                + ". Consome " + rotuloDias(custo) + " do saldo.";

        return new ExtratoEventoDTO(folga.getStartDate(), TIPO_FOLGA, "Folga", descricao, custo);
    }

    /**
     * Falta registada na presencialidade.
     *
     * <p>Só uma célula que não está presente chega aqui — o filtro é da
     * consulta, e a fábrica assume o estado não-nulo. Uma observação vazia é
     * omitida em vez de deixar uma ponta solta na frase.
     */
    public static ExtratoEventoDTO deAusencia(WorkModalitySchedule celula) {
        AttendanceStatus estado = celula.getAttendanceStatus();
        BigDecimal custo = estado.custoEmDias();

        String observacao = celula.getNotes() == null || celula.getNotes().isBlank()
                ? ""
                : " — " + celula.getNotes();

        String descricao = "Presencialidade: " + estado.getRotulo() + observacao
                + ". Gera " + rotuloDias(custo) + " de compensação.";

        return new ExtratoEventoDTO(celula.getDate(), TIPO_FALTA, "Falta", descricao, custo);
    }

    /**
     * Crédito de uma alocação no fecho da escala.
     *
     * <p>{@code credito} vem calculado pelo {@code ScaleRewardsService} — a
     * mesma regra que o fecho aplicou — e não é recalculado aqui: o extrato
     * conta o que foi creditado, e duas implementações da mesma regra seriam
     * duas coisas que podiam divergir.
     *
     * <p>A data é a do turno quando existe; sem data específica, cai na data
     * inicial da escala, que é o intervalo a que a alocação pertence. Um
     * histórico sem datas ficaria sem ordenação possível.
     */
    public static ExtratoEventoDTO deRecompensa(ShiftScheduling alocacao, BigDecimal credito) {
        var turno = alocacao.getShift();
        var escala = alocacao.getEditionScale();

        String atribuicoes = alocacao.getAssignments() == null
                ? ""
                : alocacao.getAssignments().stream()
                        .map(AssignmentType::getRotulo)
                        .collect(Collectors.joining(" + "));

        StringBuilder descricao = new StringBuilder("Turno ").append(turno.getRotuloCurto());
        if (!atribuicoes.isEmpty()) {
            descricao.append(" com ").append(atribuicoes);
        }
        if (escala != null && escala.getName() != null) {
            descricao.append(" na escala ").append(escala.getName());
        }
        descricao.append(". Acrescenta ").append(rotuloDias(credito)).append(" ao saldo.");

        LocalDate data = alocacao.getSpecificDate() != null
                ? alocacao.getSpecificDate()
                : (escala == null ? null : escala.getInitialDate());

        return new ExtratoEventoDTO(data, TIPO_RECOMPENSA, "Recompensa",
                descricao.toString(), credito);
    }

    /**
     * «1 dia» ou «0,5 dias», em texto corrido.
     *
     * <p>A vírgula é a decimal do português, como em todo o texto desta
     * aplicação; o singular só cai no dia inteiro, porque «0,5 dia» não é
     * como se fala.
     */
    private static String rotuloDias(BigDecimal valor) {
        String numero = valor.stripTrailingZeros().toPlainString().replace('.', ',');
        boolean singular = valor.abs().compareTo(BigDecimal.ONE) == 0;
        return numero + (singular ? " dia" : " dias");
    }
}
