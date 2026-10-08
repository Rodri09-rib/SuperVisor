package service;

import domain.dto.ActiveUserDTO;
import domain.model.entities.User;
import domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Saldo de compensação (dívida de horas/dias) dos colaboradores.
 *
 * <p>Concentra num sítio só a aritmética do saldo porque há três maneiras de
 * o mover — registar uma falta na presencialidade, revertê-la, e o ajuste
 * manual do supervisor — e cada uma delas podia acabar por arredondar ou
 * cortar de maneira diferente. Aqui há duas regras e só duas: o saldo é
 * somado com o delta assinado, e nunca fica negativo.
 *
 * <p>O corte em zero é o que distingue dívida de saldo: um colaborador não
 * pode ter "dívida negativa" a seu favor, porque isso seria um registo de
 * falta que alguém apagou depois de já ter sido abatido — e a correção disso
 * é o próprio corte, não um saldo fantasma.
 */
@Service
public class CompensationService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Soma {@code delta} (positivo para acrescentar dívida, negativo para
     * abater) ao saldo do usuário, cortando em zero.
     *
     * <p>Modifica a entidade e não a grava: quem a chama já está dentro de
     * uma transação, e a entidade veio de um repositório dentro dessa mesma
     * transação, pelo que a escrita acontece por verificação de alterações.
     * Isto mantém o método utilizável a partir do serviço de presencialidade
     * sem duplicar transações.
     */
    @Transactional
    public void aplicar(User user, BigDecimal delta) {
        if (user == null || delta == null || delta.signum() == 0) {
            return;
        }

        BigDecimal saldo = user.getPendingCompensationDays() == null
                ? BigDecimal.ZERO
                : user.getPendingCompensationDays();

        BigDecimal novo = saldo.add(delta);
        if (novo.signum() < 0) {
            novo = BigDecimal.ZERO;
        }

        user.setPendingCompensationDays(novo);
    }

    /**
     * Ajuste manual do saldo, pedido pelo supervisor.
     *
     * <p>É o caminho para as situações que a presencialidade não sabe sozinha:
     * abater a dívida de um colaborador que compensou, ou acrescentar um dia
     * de uma troca não autorizada de home office para presencial.
     */
    @Transactional
    public ActiveUserDTO ajustar(Long userId, BigDecimal deltaDays) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado."));

        aplicar(user, deltaDays);
        userRepository.save(user);

        return ActiveUserDTO.from(user);
    }
}
