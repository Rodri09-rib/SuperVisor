package service;

import domain.dto.UserLeaveDTO;
import domain.dto.UserLeaveRequestDTO;
import domain.model.entities.User;
import domain.model.entities.UserLeave;
import domain.model.enums.UserProfile;
import domain.repository.UserLeaveRepository;
import domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        return UserLeaveDTO.from(userLeaveRepository.save(folga), perfilDeQuemPede);
    }

    /**
     * Atualiza datas e motivo, mantendo o dono da folga.
     *
     * <p>{@code userId} do corpo é ignorado de propósito, como diz o DTO: uma
     * correção de calendário não deve conseguir mover a ausência de uma pessoa
     * para outra.
     */
    @Transactional
    public UserLeaveDTO atualizar(Long id, UserLeaveRequestDTO dto, UserProfile perfilDeQuemPede) {
        UserLeave folga = userLeaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Folga não encontrada."));

        aplicar(folga, dto, folga.getUser());

        return UserLeaveDTO.from(userLeaveRepository.save(folga), perfilDeQuemPede);
    }

    @Transactional
    public void apagar(Long id) {
        UserLeave folga = userLeaveRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Folga não encontrada."));

        userLeaveRepository.delete(folga);
    }

    private void aplicar(UserLeave folga, UserLeaveRequestDTO dto, User utilizador) {
        if (dto.endDate().isBefore(dto.startDate())) {
            throw new IllegalArgumentException(
                    "A data de fim da folga não pode ser anterior à data de início.");
        }

        folga.setUser(utilizador);
        folga.setStartDate(dto.startDate());
        folga.setEndDate(dto.endDate());
        folga.setReason(motivoNormalizado(dto.reason()));
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
