package controller;

import domain.dto.ExchangeRequestDTO;
import domain.dto.RespondExchangeDTO;
import domain.dto.ShiftExchangeDTO;
import domain.model.entities.User;
import domain.model.enums.ExchangeStatus;
import service.ChangeTimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/exchanges")
public class ExchangeController {

    @Autowired
    private ChangeTimeService changeTimeService;

    /**
     * Histórico de trocas.
     *
     * <p>Não há {@code @PreAuthorize} a propósito: quem vê o quê depende de quem
     * pergunta e não da rota — um supervisor vê tudo e um analista vê as suas.
     * Uma regra fixa na anotação teria de ser a mais restritiva das duas e
     * tiraria o histórico ao supervisor, ou então deixaria passar o pedido de
     * filtro de um analista a ver o dos outros. A distinção está em
     * {@link ChangeTimeService#listarHistorico}, com a rota a limitar-se a
     * traduzir os parâmetros.
     *
     * <p>Os filtros são todos opcionais e independentes: sem nenhum deles vem
     * o histórico completo de quem tem permissão. As datas são dias completos,
     * incluídos: {@code dataFim=2026-03-20} devolve também o que foi pedido às
     * 14h de dia 20.
     */
    @GetMapping
    public ResponseEntity<List<ShiftExchangeDTO>> listarHistorico(
            @RequestParam(required = false) ExchangeStatus status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicial,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim) {

        User loggedUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        List<ShiftExchangeDTO> historico = changeTimeService.listarHistorico(
                status, userId, dataInicial, dataFim, loggedUser);

        return ResponseEntity.ok(historico);
    }

    @PostMapping
    public ResponseEntity<Void> requestExchange(@RequestBody ExchangeRequestDTO dto) {

        User loggedUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (dto.reason() == null || dto.reason().isBlank()) {
            changeTimeService.requestExchange(dto.originAllocationId(), dto.destinationAllocationId(), loggedUser.getId());
        } else {
            changeTimeService.requestExchange(dto.originAllocationId(), dto.destinationAllocationId(),
                    loggedUser.getId(), dto.reason());
        }

        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/respond")
    public ResponseEntity<Void> respondExchange(@PathVariable Long id, @RequestBody RespondExchangeDTO dto) {

        User loggedUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        changeTimeService.respondExchange(id, dto.isAccepted(), loggedUser.getId());
        return ResponseEntity.ok().build();
    }
}
