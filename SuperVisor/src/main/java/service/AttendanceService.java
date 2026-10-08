package service;

import domain.dto.AttendanceRequestDTO;
import domain.dto.WorkModalityScheduleDTO;
import domain.model.entities.WorkModalitySchedule;
import domain.model.enums.AttendanceStatus;
import domain.repository.WorkModalityScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Registo de presencialidade: falta, observação e a dívida que cada falta gera.
 *
 * <p>Acompanha o {@link WorkModalityAutomationService} de propósito. A
 * geração escreve a escala da semana toda de uma vez; aqui escreve-se uma
 * célula de cada vez, e as duas operações têm regras diferentes — a geração
 * preserva o que já lá está, esta é a única que muda o estado de presença.
 *
 * <p>A compensação é calculada como a diferença entre o estado anterior e o
 * novo, e não a partir do estado novo. É o que faz a reversão funcionar sem
 * um caso especial: voltar a marcar "Presente" aplica um delta negativo e
 * devolve ao colaborador exatamente o que a falta lhe tinha tirado.
 */
@Service
public class AttendanceService {

    @Autowired
    private WorkModalityScheduleRepository workModalityScheduleRepository;

    @Autowired
    private CompensationService compensationService;

    /**
     * Atualiza a presença de uma célula e move o saldo de compensação do
     * colaborador pelo valor correspondente.
     */
    @Transactional
    public WorkModalityScheduleDTO atualizar(Long id, AttendanceRequestDTO dto) {
        WorkModalitySchedule registo = workModalityScheduleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(
                        "Registo de presencialidade não encontrado."));

        AttendanceStatus anterior = registo.getAttendanceStatus() == null
                ? AttendanceStatus.PRESENT
                : registo.getAttendanceStatus();
        AttendanceStatus novo = dto.attendanceStatus();

        BigDecimal delta = novo.custoEmDias().subtract(anterior.custoEmDias());

        registo.setAttendanceStatus(novo);
        if (dto.notes() != null) {
            registo.setNotes(normalizar(dto.notes()));
        }

        compensationService.aplicar(registo.getUser(), delta);

        return WorkModalityScheduleDTO.from(workModalityScheduleRepository.save(registo));
    }

    /**
     * Observação aparada, ou nula quando vem em branco — o mesmo tratamento do
     * motivo das folgas. Um campo de texto opcional guardado como string
     * vazia obriga o frontend a testar dois valores falsos para a mesma
     * ausência de informação.
     *
     * <p>Quem está aqui é só o valor presente no pedido: a ausência de campo
     * (nulo) nem chega, porque mantém o que a célula já diz — ver a nota em
     * {@link AttendanceRequestDTO}.
     */
    private String normalizar(String notes) {
        if (notes.isBlank()) {
            return null;
        }
        return notes.trim();
    }
}
