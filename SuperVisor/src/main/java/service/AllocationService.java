package service;

import domain.dto.AllocationDTO;
import domain.dto.AllocationRequestDTO;
import domain.model.entities.EditionScale;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.AssignmentType;
import domain.repository.EditionScaleRepository;
import domain.repository.ShiftSchedulingRepository;
import domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AllocationService {

    @Autowired
    private ShiftSchedulingRepository shiftSchedulingRepository;

    @Autowired
    private EditionScaleRepository editionScaleRepository;

    @Autowired
    private UserRepository userRepository;

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

    @Transactional
    public AllocationDTO create(AllocationRequestDTO dto) {
        ShiftScheduling alocacao = new ShiftScheduling();
        aplicar(alocacao, dto);

        return AllocationDTO.from(shiftSchedulingRepository.save(alocacao));
    }

    @Transactional
    public AllocationDTO update(Long id, AllocationRequestDTO dto) {
        ShiftScheduling alocacao = shiftSchedulingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alocação não encontrada."));

        aplicar(alocacao, dto);

        return AllocationDTO.from(shiftSchedulingRepository.save(alocacao));
    }

    /**
     * Remove uma alocação. A autorização não é aqui verificada: o perfil do
     * utilizador vive na camada web, e o serviço mantém-se responsável apenas
     * pela regra de negócio, para que também possa ser chamado de testes e de
     * outras entradas.
     */
    @Transactional
    public void delete(Long id) {
        ShiftScheduling alocacao = shiftSchedulingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alocação não encontrada."));

        shiftSchedulingRepository.delete(alocacao);
    }

    /**
     * Valida e copia o pedido para a entidade. Todas as regras de negócio do
     * editor de alocações estão aqui, para que criar e editar não possam
     * divergir.
     */
    private void aplicar(ShiftScheduling alocacao, AllocationRequestDTO dto) {
        if (dto == null) {
            throw new RuntimeException("Corpo do pedido inválido.");
        }
        if (dto.shift() == null) {
            throw new RuntimeException("Escolha o turno do fim de semana.");
        }
        if (dto.userId() == null) {
            throw new RuntimeException("Escolha o utilizador que cobre o turno.");
        }
        if (dto.editionScaleId() == null) {
            throw new RuntimeException("Escolha a escala a que a alocação pertence.");
        }

        EditionScale escala = editionScaleRepository.findById(dto.editionScaleId())
                .orElseThrow(() -> new RuntimeException("Edição de Escala não encontrada."));

        User utilizador = userRepository.findById(dto.userId())
                .orElseThrow(() -> new RuntimeException("Utilizador não encontrado."));

        if (!utilizador.isActive()) {
            throw new RuntimeException("O utilizador selecionado está inativo e não pode "
                    + "receber turnos.");
        }

        validarHorarioCustomizado(dto);

        alocacao.setEditionScale(escala);
        alocacao.setUser(utilizador);
        alocacao.setShift(dto.shift());
        alocacao.setAssignments(atribuicoes(dto.assignments()));
        alocacao.setCustomStartTime(dto.customStartTime());
        alocacao.setCustomEndTime(dto.customEndTime());
        alocacao.setSpecificDate(dto.specificDate());
    }

    /**
     * O horário especial é opcional, mas tem de ser informado por completo e
     * caber dentro do turno escolhido.
     */
    private void validarHorarioCustomizado(AllocationRequestDTO dto) {
        boolean temInicio = dto.customStartTime() != null;
        boolean temFim = dto.customEndTime() != null;

        if (temInicio != temFim) {
            throw new RuntimeException("O horário especial precisa do início e do fim, "
                    + "ou de nenhum dos dois.");
        }

        if (!temInicio) {
            return;
        }

        if (!dto.shift().contem(dto.customStartTime(), dto.customEndTime())) {
            throw new RuntimeException("O horário especial tem de estar dentro do turno "
                    + dto.shift().getRotulo() + ".");
        }
    }

    private Set<AssignmentType> atribuicoes(List<AssignmentType> pedidas) {
        return pedidas == null ? new LinkedHashSet<>() : new LinkedHashSet<>(pedidas);
    }
}
