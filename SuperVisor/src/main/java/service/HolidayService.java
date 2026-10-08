package service;

import domain.dto.HolidayDTO;
import domain.dto.HolidayRequestDTO;
import domain.model.entities.Holiday;
import domain.repository.HolidayRepository;
import exception.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Feriados do calendário.
 *
 * <p>CRUD pequeno e deliberadamente simples: a informação é um dia e uma frase,
 * e tudo o que a regra de recompensas precisa é do conjunto de datas. A
 * escrita é da supervisão — decidir o que é feriado é decisão de quem
 * supervisiona — e a leitura é de qualquer perfil autenticado, porque o badge
 * de feriado na escala tem de aparecer para quem vê a escala.
 */
@Service
public class HolidayService {

    @Autowired
    private HolidayRepository holidayRepository;

    @Transactional(readOnly = true)
    public List<HolidayDTO> listar() {
        return holidayRepository.findAllByOrderByDateAsc().stream()
                .map(HolidayDTO::from)
                .toList();
    }

    @Transactional
    public HolidayDTO criar(HolidayRequestDTO dto) {
        exigirDataUnica(dto.date(), null);

        Holiday feriado = new Holiday(dto.date(), normalizar(dto.description()));

        return HolidayDTO.from(holidayRepository.save(feriado));
    }

    /**
     * Atualiza data e descrição de um feriado existente.
     *
     * <p>A unicidade é verificada ignorando o próprio registo: uma correção de
     * descrição que não mexe na data não pode ser recusada como duplicado da
     * própria data.
     */
    @Transactional
    public HolidayDTO atualizar(Long id, HolidayRequestDTO dto) {
        Holiday feriado = holidayRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feriado não encontrado."));

        exigirDataUnica(dto.date(), id);

        feriado.setDate(dto.date());
        feriado.setDescription(normalizar(dto.description()));

        return HolidayDTO.from(holidayRepository.save(feriado));
    }

    /**
     * Apaga o feriado.
     *
     * <p>Não há saldo a devolver: os dias já creditados por trabalho em feriado
     * creditaram-se no fecho da escala, como um facto consumado — o mesmo
     * tratamento do crédito de domingo e de celular. Apagar um feriado tira-o
     * dos fechos futuros, e não dos que já aconteceram.
     */
    @Transactional
    public void apagar(Long id) {
        Holiday feriado = holidayRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feriado não encontrado."));

        holidayRepository.delete(feriado);
    }

    /**
     * Dois registos para a mesma data seriam dois feriados que o fecho da
     * escala não distingue — e a recompensa, que é por dia, ficaria sem saber
     * qual dos registos descreve o dia. A coluna também é única, mas a recusa
     * tem de vir daqui com uma frase que se possa mostrar: a violação de
     * constraint chegaria como um erro de base de dados.
     */
    private void exigirDataUnica(LocalDate data, Long ignorandoId) {
        boolean duplicado = ignorandoId == null
                ? holidayRepository.existsByDate(data)
                : holidayRepository.existsByDateAndIdNot(data, ignorandoId);

        if (duplicado) {
            throw new RegraDeNegocioException("Já existe um feriado registado para esta data.");
        }
    }

    private String normalizar(String descricao) {
        return descricao == null ? null : descricao.trim();
    }
}
