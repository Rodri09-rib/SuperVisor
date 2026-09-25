package service;

import domain.model.entities.ExchangeRequest;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.repository.ExchangeRequestRepository;
import domain.repository.ShiftSchedulingRepository;
import domain.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class ChangeTimeService {

    @Autowired
    private ExchangeRequestRepository exchangeRequestRepository;

    @Autowired
    private ShiftSchedulingRepository shiftSchedulingRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public void requestExchange(Long originAllocationId, Long destinationAllocationId, Long loggedUserId) {

        if (originAllocationId.equals(destinationAllocationId)) {
            throw new IllegalArgumentException("Alocação de origem e destino não podem ser iguais.");
        }

        ShiftScheduling origin = shiftSchedulingRepository.findById(originAllocationId)
                .orElseThrow(() -> new RuntimeException("Alocação de origem não encontrada."));

        ShiftScheduling destination = shiftSchedulingRepository.findById(destinationAllocationId)
                .orElseThrow(() -> new RuntimeException("Alocação de destino não encontrada."));

        if (!origin.getEditionScale().getId().equals(destination.getEditionScale().getId())) {
            throw new IllegalArgumentException("As alocações pertencem a escalas diferentes.");
        }

        User requester = userRepository.findById(loggedUserId)
                .orElseThrow(() -> new RuntimeException("Utilizador não encontrado"));

        ExchangeRequest request = new ExchangeRequest();
        request.setSourceAllocation(origin);
        request.setDestinationAllocation(destination);
        request.setRequestingUser(requester);
        request.setStatus("PENDING");
        request.setCreationDate(OffsetDateTime.now());

        exchangeRequestRepository.save(request);
    }

    @Transactional
    public void respondExchange(Long requestId, boolean isAccepted){

        ExchangeRequest request = exchangeRequestRepository.findById(requestId).
                orElseThrow(() -> new RuntimeException("Solicitação não encontrada."));

        if(!request.getStatus().equals("PENDING")) {
            throw new RuntimeException("Esta solicitação já foi respondida.");
        }

        if(isAccepted) {
            ShiftScheduling origin = request.getSourceAllocation();
            ShiftScheduling destination = request.getDestinationAllocation();


            User originalDestinationUser = destination.getUser();

            destination.setUser(origin.getUser());
            origin.setUser(originalDestinationUser);

            shiftSchedulingRepository.save(origin);
            shiftSchedulingRepository.save(destination);

            request.setStatus("ACCEPTED");
        }
        else {
              request.setStatus("REJECTED");
        }

           exchangeRequestRepository.save(request);


    }

}
