package controller;

import domain.dto.ExchangeRequestDTO;
import domain.dto.RespondExchangeDTO;
import service.ChangeTimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import domain.model.entities.User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/exchanges")
public class ExchangeController {

    @Autowired
    private ChangeTimeService changeTimeService;

    @PostMapping
    public ResponseEntity<Void> requestExchange(@RequestBody ExchangeRequestDTO dto) {

        User loggedUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();


        changeTimeService.requestExchange(dto.originAllocationId(), dto.destinationAllocationId(), loggedUser.getId());

        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/respond")
    public ResponseEntity<Void> respondExchange(@PathVariable Long id, @RequestBody RespondExchangeDTO dto) {

        changeTimeService.respondExchange(id, dto.isAccepted());
        return ResponseEntity.ok().build();
    }
}

