package service;

import domain.dto.UserLeaveDTO;
import domain.dto.UserLeaveRequestDTO;
import domain.model.entities.User;
import domain.model.entities.UserLeave;
import domain.model.enums.LeaveDuration;
import domain.model.enums.UserProfile;
import domain.repository.UserLeaveRepository;
import domain.repository.UserRepository;
import exception.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class LeaveService {

    @Autowired
    private UserLeaveRepository userLeaveRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Todas as folgas, ou as de uma pessoa.
     *
     * <p>Ao contrário do histórico de trocas, aqui a leitura não é filtrada por
     * perfil. A falta de uma pessoa é informação de toda a equipe — o resto
     * precisa de saber quem não está — e o que a aplicação protege não é o
     * facto de estar de folga, que já é visível no calendário, mas o texto
     * opcional do motivo. A lista devolve o que a equipe precisa ver.
     */
    @Transactional(readOnly = true)
    public List<UserLeaveDTO> listar(Long userId, UserProfile perfilDeQuemPede) {
        return userLeaveRepository.listar(userId).stream()
                .map(folga -> UserLeaveDTO.from(folga, perfilDeQuemPede))
                .toList();
    }

    @Transactional
    public UserLeaveDTO criar(UserLeaveRequestDTO dto, UserProfile perfilDeQuemPede) {
        User utilizador = utilizadorAtivo(dto.userId());

        UserLeave folga = new UserLeave();
        aplicar(folga, dto, utilizador);
        debitar(utilizador, folga.custoEmDias());

        return UserLeaveDTO.from(userLeaveRepository.save(folga), perfilDeQuemPede);
    }

    /**
     * Atualiza datas e motivo, mantendo o dono da folga.
     *
     * <p>{@code userId} do corpo é ignorado de propósito, como diz o DTO: uma
     * correção de calendário não deve conseguir mover a ausência de uma pessoa
     * para outra.
     *
     * <p>O saldo é devolvido pelo custo antigo e debitado pelo novo, e não
     * recalculado do zero: o que se está a corrigir é uma folga que já tinha
     * sido cobrada, e qualquer outro caminho cobrava-a duas vezes ou a mais.
     */
    @Transactional
    public UserLeaveDTO atualizar(Long id, UserLeaveRequestDTO dto, UserProfile perfilDeQuemPede) {
        UserLeave folga = userLeaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Folga não encontrada."));

        User dono = folga.getUser();
        BigDecimal custoAntigo = folga.custoEmDias();

        aplicar(folga, dto, dono);

        if (dono != null) {
            creditar(dono, custoAntigo);
            debitar(dono, folga.custoEmDias());
        }

        return UserLeaveDTO.from(userLeaveRepository.save(folga), perfilDeQuemPede);
    }

    /**
     * Apaga a folga e devolve os dias ao saldo.
     *
     * <p>A devolução é o inverso exato do que a criação cobrou: uma folga de
     * meio dia repõe meio dia, e uma de cinco dias repõe cinco. Sem isto,
     * apagar uma folga por engano deixava o saldo de quem a tinha sem os dias
     * que nunca chegou a usar.
     */
    @Transactional
    public void apagar(Long id) {
        UserLeave folga = userLeaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Folga não encontrada."));

        if (folga.getUser() != null) {
            creditar(folga.getUser(), folga.custoEmDias());
        }

        userLeaveRepository.delete(folga);
    }

    private void aplicar(UserLeave folga, UserLeaveRequestDTO dto, User utilizador) {
        if (dto.endDate().isBefore(dto.startDate())) {
            throw new IllegalArgumentException(
                    "A data de fim da folga não pode ser anterior à data de início.");
        }

        LeaveDuration duracao = dto.leaveDuration() == null ? LeaveDuration.FULL_DAY : dto.leaveDuration();

        if (duracao.isMeiaJornada() && !dto.startDate().equals(dto.endDate())) {
            throw new RegraDeNegocioException(
                    "Uma folga de meia jornada tem de ser num único dia: "
                    + "escolha o mesmo dia de início e de fim.");
        }

        folga.setUser(utilizador);
        folga.setStartDate(dto.startDate());
        folga.setEndDate(dto.endDate());
        folga.setLeaveDuration(duracao);
        folga.setReason(motivoNormalizado(dto.reason()));
    }

    /**
     * Deduz dias do saldo, podendo deixá-lo negativo.
     *
     * <p>Não há corte em zero aqui, ao contrário do saldo de compensação: o
     * saldo de folgas é um crédito que se gasta, e um supervisor a marcar uma
     * folga a quem ainda não acumulou nada está a decidir um adiantamento —
     * recusar seria transformar a falta de histórico em impossibilidade. O
     * saldo negativo aparece como tal na página, que é o aviso.
     */
    private void debitar(User user, BigDecimal custo) {
        BigDecimal saldo = user.getAccumulatedLeaves() == null ? BigDecimal.ZERO : user.getAccumulatedLeaves();
        BigDecimal valor = custo == null ? BigDecimal.ZERO : custo;
        user.setAccumulatedLeaves(saldo.subtract(valor));
    }

    private void creditar(User user, BigDecimal custo) {
        BigDecimal saldo = user.getAccumulatedLeaves() == null ? BigDecimal.ZERO : user.getAccumulatedLeaves();
        BigDecimal valor = custo == null ? BigDecimal.ZERO : custo;
        user.setAccumulatedLeaves(saldo.add(valor));
    }

    private User utilizadorAtivo(Long userId) {
        User utilizador = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado."));

        if (!utilizador.isActive()) {
            throw new IllegalArgumentException("Não é possível registrar folga a um usuário inativo.");
        }

        return utilizador;
    }

    private String motivoNormalizado(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }
}
