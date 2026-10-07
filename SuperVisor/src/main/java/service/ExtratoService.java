package service;

import domain.dto.ExtratoEventoDTO;
import domain.model.entities.User;
import domain.model.enums.AttendanceStatus;
import domain.repository.ShiftSchedulingRepository;
import domain.repository.UserLeaveRepository;
import domain.repository.UserRepository;
import domain.repository.WorkModalityScheduleRepository;
import exception.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Extrato de folgas de um colaborador: faltas, folgas e recompensas, por ordem
 * decrescente.
 *
 * <p>As três fontes chegam de sítios diferentes — folgas da tabela de
 * folgas, faltas da escala de presencialidade, recompensas das alocações de
 * escalas já fechadas — e é aqui que se juntam numa única linha do tempo. O
 * serviço não calcula nada: cada acontecimento é montado pela fábrica do
 * {@link ExtratoEventoDTO} com o mesmo número que a operação original
 * gravou, e o crédito nem é refeito, vem do {@link ScaleRewardsService} — a
 * leitura tem de contar exatamente o que a escrita aplicou.
 *
 * <p>A leitura é da supervisão (verificado no controlador): o extrato diz de
 * onde vem o saldo de qualquer pessoa, o que é informação de gestão e não do
 * colega de equipa.
 */
@Service
public class ExtratoService {

    /**
     * Limite de acontecimentos devolvidos.
     *
     * <p>O modal de histórico mostra os recentes, não o arquivo da vida de
     * alguém: sem teto, uma pessoa com dez anos de faltas e turnos mandava
     * uma resposta que ninguém vai ler até ao fim. Cem é folga para o ecrã
     * inteiro e para vários meses de histórico.
     */
    private static final int LIMITE = 100;

    /** Mais recente primeiro; sem data, no fim — nunca a omitir. */
    private static final Comparator<ExtratoEventoDTO> MAIS_RECENTE_PRIMEIRO =
            Comparator.comparing(ExtratoEventoDTO::date,
                    Comparator.nullsLast(Comparator.reverseOrder()));

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserLeaveRepository userLeaveRepository;

    @Autowired
    private WorkModalityScheduleRepository workModalityScheduleRepository;

    @Autowired
    private ShiftSchedulingRepository shiftSchedulingRepository;

    @Autowired
    private ScaleRewardsService scaleRewardsService;

    @Transactional(readOnly = true)
    public List<ExtratoEventoDTO> listar(Long userId) {
        User utilizador = userRepository.findById(userId)
                .orElseThrow(() -> new RegraDeNegocioException("Usuário não encontrado."));

        List<ExtratoEventoDTO> eventos = new ArrayList<>();

        for (var folga : userLeaveRepository.listar(utilizador.getId())) {
            eventos.add(ExtratoEventoDTO.deFolga(folga));
        }

        for (var celula : workModalityScheduleRepository
                .listarAusenciasDoUtilizador(utilizador.getId(), AttendanceStatus.PRESENT)) {
            eventos.add(ExtratoEventoDTO.deAusencia(celula));
        }

        // O crédito só é evento quando é maior que zero: as alocações de
        // sábado sem celular também vêm na consulta porque a escala foi
        // fechada, mas não geraram nada e listá-las faria o extrato mostrar
        // acontecimentos que não mexeram no saldo.
        for (var alocacao : shiftSchedulingRepository
                .listarRecompensadasDoUtilizador(utilizador.getId())) {
            BigDecimal credito = scaleRewardsService.creditoDe(alocacao);
            if (credito.signum() > 0) {
                eventos.add(ExtratoEventoDTO.deRecompensa(alocacao, credito));
            }
        }

        eventos.sort(MAIS_RECENTE_PRIMEIRO);

        return eventos.size() > LIMITE
                ? List.copyOf(eventos.subList(0, LIMITE))
                : eventos;
    }
}
