package service;

import domain.dto.ShiftExchangeDTO;
import domain.model.entities.ExchangeRequest;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.ExchangeStatus;
import domain.model.enums.UserProfile;
import domain.repository.ExchangeRequestRepository;
import domain.repository.ShiftSchedulingRepository;
import domain.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChangeTimeService {

    private static final String MENSAGEM_RESPONDER =
            "Apenas a pessoa a quem a troca foi pedida pode responder ao pedido.";

    @Autowired
    private ExchangeRequestRepository exchangeRequestRepository;

    @Autowired
    private ShiftSchedulingRepository shiftSchedulingRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public void requestExchange(Long originAllocationId, Long destinationAllocationId, Long loggedUserId) {
        requestExchange(originAllocationId, destinationAllocationId, loggedUserId, null);
    }

    @Transactional
    public void requestExchange(Long originAllocationId, Long destinationAllocationId,
                                Long loggedUserId, String reason) {

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

        // O turno de origem tem de ser de quem pede. Sem esta guarda, um
        // utilizador apontava para o turno de um colega como "origem" e para o
        // seu próprio como "destino": ficava a ser o colega pedido — ou seja, o
        // próprio — eRespondia ao próprio pedido, ficando com os dois turnos e
        // deixando o dono do primeiro sem ele.
        if (!pertenceA(origin, requester)) {
            throw new AccessDeniedException("Só é possível pedir a troca de um turno que lhe pertence.");
        }

        if (pertenceA(destination, requester)) {
            throw new IllegalArgumentException("O turno de destino já é seu, por isso não há nada a trocar.");
        }

        ExchangeRequest request = new ExchangeRequest();
        request.setSourceAllocation(origin);
        request.setDestinationAllocation(destination);
        request.setRequestingUser(requester);
        // O colega pedido fica copiado para o pedido. Ao responder, as
        // alocações trocam de dono, e ler a alocação de destino nessa altura
        // devolveria o próprio requerente: o histórico deixaria de dizer com
        // quem foi a troca.
        request.setRequestedUser(destination.getUser());
        request.setStatus(ExchangeStatus.PENDING);
        request.setCreationDate(OffsetDateTime.now());
        request.setReason(motivoNormalizado(reason));

        exchangeRequestRepository.save(request);
    }

    private String motivoNormalizado(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }

    /**
     * Se a alocação pertence ao utilizador.
     *
     * <p>Compara pelo id, e não pelo objeto: um {@code User} lido de outra
     * alocação é outra instância com o mesmo id, e a comparação por identidade
     * daria {@code false} a uma alocação que é mesmo do utilizador.
     */
    private boolean pertenceA(ShiftScheduling alocacao, User user) {
        return alocacao.getUser() != null
                && alocacao.getUser().getId() != null
                && alocacao.getUser().getId().equals(user.getId());
    }

    @Transactional
    public void respondExchange(Long requestId, boolean isAccepted, Long loggedUserId){

        ExchangeRequest request = exchangeRequestRepository.findByIdComDetalhes(requestId).
                orElseThrow(() -> new RuntimeException("Solicitação não encontrada."));

        if(request.getStatus() != ExchangeStatus.PENDING) {
            throw new RuntimeException("Esta solicitação já foi respondida.");
        }

        exigirQuemPodeResponder(request, loggedUserId);

        if(isAccepted) {
            ShiftScheduling origin = request.getSourceAllocation();
            ShiftScheduling destination = request.getDestinationAllocation();

            User originalDestinationUser = destination.getUser();

            destination.setUser(origin.getUser());
            origin.setUser(originalDestinationUser);

            shiftSchedulingRepository.save(origin);
            shiftSchedulingRepository.save(destination);

            request.setStatus(ExchangeStatus.APPROVED);
        }
        else {
              request.setStatus(ExchangeStatus.REJECTED);
        }

        request.setApprovalDate(OffsetDateTime.now());
        exchangeRequestRepository.save(request);
    }

    /**
     * Só responde quem tem algo a ceder: o colega a quem a troca foi pedida.
     *
     * <p>Um pedido de troca é um pedido para o outro aceitar. Sem esta guarda,
     * qualquer pessoa autenticada podia responder ao pedido de outra e ficar com
     * o turno dela — aceitando o pedido de um colega, trocando os dois turnos e
     * recambiando-os, num par de pedidos em que cada um aprova o pedido do
     * outro. A supervisão também pode responder, para desbloquear um pedido de
     * alguém que não está disponível, e é por isso que o perfil entra na regra.
     *
     * <p>Quando o pedido é anterior à migração de {@code requestedUser}, o
     * colega não está guardado e não há como saber a quem pertence: nesse caso a
     * supervisão é a única que passa. Um pedido antigo sem resposta é
     * exatamente o caso em que a supervisão precisa de poder intervir.
     */
    private void exigirQuemPodeResponder(ExchangeRequest request, Long loggedUserId) {
        User logado = userRepository.findById(loggedUserId).orElse(null);

        if (logado == null) {
            throw new AccessDeniedException(MENSAGEM_RESPONDER);
        }

        if (logado.getProfile() == UserProfile.SUPERVISOR) {
            return;
        }

        User solicitado = request.getRequestedUser();

        if (solicitado == null || solicitado.getId() == null
                || !solicitado.getId().equals(logado.getId())) {
            throw new AccessDeniedException(MENSAGEM_RESPONDER);
        }
    }

    /**
     * Histórico de trocas visível para quem pergunta.
     *
     * <p>A regra de visibilidade mora aqui e não na rota: um supervisor vê tudo,
     * um analista vê só as trocas em que é parte — as que iniciou ou as que lhe
     * foram pedidas. Um pedido de filtro {@code userId} de um analista é
     * ignorado em vez de recusado, e o filtro é-forçado ao próprio id: se a
     * aplicação devolvesse 403 por um parâmetro que o frontend preenche com o
     * próprio utilizador, a página pareceria avariada.
     */
    @Transactional(readOnly = true)
    public List<ShiftExchangeDTO> listarHistorico(ExchangeStatus status, Long userId,
                                                  LocalDate dataInicial, LocalDate dataFim,
                                                  User logado) {

        List<ExchangeRequest> pedidos = exchangeRequestRepository.findAll(
                historicoVisivelA(status, utilizadorDoFiltro(logado, userId), dataInicial, dataFim));

        return pedidos.stream().map(ShiftExchangeDTO::from).toList();
    }

    /**
     * A quem o filtro de pessoa se aplica.
     *
     * <p>Um supervisor filtra por quem quiser, ou não filtra. Um analista é
     * sempre filtrado pelo seu próprio id, e um {@code userId} que venha no
     * pedido é deitado fora.
     *
     * <p>Descartar o parâmetro em vez de recusar o pedido é deliberado: o
     * selector da página é preenchido com o utilizador corrente, e devolver 403
     * por um parâmetro que a própria interface enviou faria a página parecer
     * avariada. O efeito é o mesmo nos dois casos — o analista não vê o histórico
     * dos outros.
     *
     * <p>Visível para o pacote para poder ser testado sem montar a consulta: a
     * regra de visibilidade é o que protege o histórico, e testar o que ela
     * devolve é mais claro do que inferir isso do JPQL gerado.
     */
    Long utilizadorDoFiltro(User logado, Long userId) {
        return logado.getProfile() == UserProfile.SUPERVISOR ? userId : logado.getId();
    }

    /**
     * Monta o filtro do histórico como {@link Specification}, e não como JPQL
     * com {@code :parametro is null}.
     *
     * <p>A forma com parâmetros opcionais no JPQL funciona para tipos simples,
     * mas rebenta com um {@code OffsetDateTime} nulo: o Hibernate escreve um
     * parâmetro sem tipo e o PostgreSQL responde "could not determine data type
     * of parameter". A {@code Specification} resolve-o de raiz, porque cada
     * predicado opcional é simplesmente omitido em vez de enviado como nulo.
     *
     * <p>As quatro ligações vêm em {@code fetch} porque o histórico desenha
     * nomes e turnos. Sem elas, a página dispararia uma consulta por linha — e é
     * paginada pelo dia, que é o pior caso de N+1 da aplicação. O {@code fetch}
     * é guardado por causa de uma particularidade da API: numa
     * {@code Specification} pode ser pedida uma consulta de contagem, e essa não
     * aceita {@code fetch}.
     */
    private Specification<ExchangeRequest> historicoVisivelA(ExchangeStatus status, Long userId,
                                                             LocalDate dataInicial, LocalDate dataFim) {
        return (root, query, cb) -> {
            if (query != null) {
                // `var` em vez do tipo nomeado: `FetchParent` ganhou um segundo
                // parâmetro de tipo no JPA 3.1, e nomeá-lo aqui só criaria
                // trabalho a cada versão da API.
                var pedido = root;
                pedido.fetch("requestingUser", JoinType.LEFT);
                pedido.fetch("requestedUser", JoinType.LEFT);
                pedido.fetch("sourceAllocation", JoinType.LEFT);
                pedido.fetch("destinationAllocation", JoinType.LEFT);
                query.orderBy(
                        cb.desc(root.get("creationDate")),
                        cb.desc(root.get("id")));
            }

            List<Predicate> condicoes = new ArrayList<>();

            if (status != null) {
                condicoes.add(cb.equal(root.get("status"), status));
            }

            if (userId != null) {
                // "As minhas trocas" inclui as duas pontas: as que a pessoa
                // pediu e as que lhe foram pedidas. Um `or` entre os dois lados
                // é o que faz a lista mostrar ao analista o histórico em que
                // está envolvido, e não só o que ele iniciou.
                condicoes.add(cb.or(
                        cb.equal(root.get("requestingUser").get("id"), userId),
                        cb.equal(root.get("requestedUser").get("id"), userId)));
            }

            if (dataInicial != null) {
                condicoes.add(cb.greaterThanOrEqualTo(root.get("creationDate"),
                        inicioDoDia(dataInicial)));
            }

            if (dataFim != null) {
                condicoes.add(cb.lessThan(root.get("creationDate"), inicioDoDiaSeguinte(dataFim)));
            }

            return condicoes.isEmpty()
                    ? cb.conjunction()
                    : cb.and(condicoes.toArray(new Predicate[0]));
        };
    }

    /**
     * Início do dia pedido, no fuso da aplicação. A data que vem do filtro é um
     * {@code LocalDate} sem hora, e a coluna é um {@code OffsetDateTime}: sem
     * esta conversão, o limite inferior seria o ano 1 e o filtro não filtraria.
     */
    private OffsetDateTime inicioDoDia(LocalDate data) {
        return data == null ? null : data.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    }

    /**
     * Limite superior exclusivo do intervalo: a meia-noite do dia seguinte. Os
     * pedidos são guardados com hora, e comparar a coluna com a data final
     * deixaria de fora tudo o que foi pedido depois das 00h00 desse dia.
     */
    private OffsetDateTime inicioDoDiaSeguinte(LocalDate data) {
        return data == null ? null : data.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
