package service;

import domain.dto.AllocationDTO;
import domain.model.entities.ShiftScheduling;
import domain.repository.ShiftSchedulingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AllocationService {

    @Autowired
    private ShiftSchedulingRepository shiftSchedulingRepository;

    /**
     * Alocações de turno, opcionalmente restritas a uma escala. É a origem de
     * dados dos seletores do pedido de troca: a alocação de origem é a do
     * utilizador e a de destino é a de um colega dentro da mesma escala.
     */
    @Transactional(readOnly = true)
    public List<AllocationDTO> list(Long editionScaleId) {
        List<ShiftScheduling> alocacoes = editionScaleId == null
                ? shiftSchedulingRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                : shiftSchedulingRepository.findByEditionScaleIdOrderByIdAsc(editionScaleId);

        return alocacoes.stream().map(AllocationDTO::from).toList();
    }
}
