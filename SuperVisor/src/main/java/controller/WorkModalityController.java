package controller;

import domain.dto.GenerateWorkModalityDTO;
import domain.dto.WorkModalityScheduleDTO;
import security.ProfileAuthorization;
import service.WorkModalityAutomationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Escala de presencialidade / home office.
 *
 * <p>Gerar a escala é escrever em todas as escalas de toda a equipe de uma
 * vez, e é a operação mais abrangente da aplicação depois do cadastro de
 * usuários, pelo que é restrita à supervisão pela mesma razão de
 * {@link UserController}. Ver a escala é ler, e todos os perfis autenticados
 * leem: é a informação que as pessoas consultam para saber onde vão estar.
 *
 * <p>É a razão de a rota de leitura não ser filtrada por perfil. Um filtro de
 * {@code @PreAuthorize} devolveria 403 e a página deixaria de funcionar para
 * quem não é supervisor, o que seria o calendário de uma pessoa a falhar por
 * causa de quem o pede.
 */
@RestController
@RequestMapping("/api/v1/work-modality-schedules")
public class WorkModalityController {

    @Autowired
    private WorkModalityAutomationService workModalityAutomationService;

    @Autowired
    private ProfileAuthorization profileAuthorization;

    /**
     * Escala de um intervalo, para desenhar a grelha.
     *
     * <p>Devolve só as células existentes, pelo que uma semana ainda não gerada
     * vem vazia em vez de vir com as presenças assumidas.
     */
    @GetMapping
    public ResponseEntity<List<WorkModalityScheduleDTO>> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {

        return ResponseEntity.ok(workModalityAutomationService.listar(inicio, fim));
    }

    /**
     * Gera a escala da semana da data de referência.
     *
     * <p>O corpo é opcional: sem ele usa a semana corrente, que é o caso normal
     * de uso. A data pode ser qualquer dia da semana pretendida.
     *
     * <p>Devolve as células geradas e não um {@code 201}: a operação é
     * repetível e não cria um recurso com identidade própria, e devolver a
     * escala pronta poupa ao frontend uma segunda ida ao servidor.
     */
    @PostMapping("/generate")
    public ResponseEntity<List<WorkModalityScheduleDTO>> gerar(
            @RequestBody(required = false) GenerateWorkModalityDTO dto) {

        profileAuthorization.exigirSupervisor();

        List<WorkModalityScheduleDTO> escala =
                workModalityAutomationService.gerar(dto == null ? null : dto.dataReferencia());

        return ResponseEntity.ok(escala);
    }
}
