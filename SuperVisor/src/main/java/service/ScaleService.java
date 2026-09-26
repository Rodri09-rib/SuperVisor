package service;

import domain.dto.CreateScaleDTO;
import domain.model.entities.EditionScale;
import domain.model.entities.User;
import domain.model.enums.EditionStatus;
import domain.repository.EditionScaleRepository;
import domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.annotation.Target;
import java.util.List;

@Service
public class ScaleService {

    @Autowired
    private EditionScaleRepository editionScaleRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public EditionScale createScale(CreateScaleDTO dto) {

        User creator = userRepository.findById(dto.createdById()).
                orElseThrow(() -> new RuntimeException("Utilizador não encontrado"));

        EditionScale newScale = new EditionScale();
        newScale.setName(dto.name());
        newScale.setInitialDate(dto.initialDate());
        newScale.setEndDate(dto.endDate());
        newScale.setStatus(EditionStatus.DRAFT);
        newScale.setCreatedBy(creator);

        return editionScaleRepository.save(newScale);
    }

    @Transactional
    public void publishSchedule(Long idEdition) {

        EditionScale scale = editionScaleRepository.findById(idEdition).
                orElseThrow(() -> new RuntimeException("Edição de Escala não encontrada."));

        if (scale.getStatus() != EditionStatus.DRAFT) {
            throw new RuntimeException("Apenas escalas em rascunho podem ser publicadas");
        }

        scale.setStatus(EditionStatus.PUBLISHED);
        editionScaleRepository.save(scale);
    }

    @Transactional(readOnly = true)
    public List<EditionScale> listAll() {
        return editionScaleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public EditionScale findById(Long id) {
        return editionScaleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Edição de Escala não encontrada."));
    }
}