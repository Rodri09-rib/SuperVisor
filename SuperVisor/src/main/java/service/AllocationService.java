package service;

import domain.dto.AllocationDTO;
import domain.dto.AllocationRequestDTO;
import domain.model.entities.EditionScale;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.AllocationStatus;
import domain.model.enums.AssignmentType;
import domain.model.enums.ExchangeStatus;
import domain.model.enums.UserProfile;
import domain.repository.EditionScaleRepository;
import domain.repository.ExchangeRequestRepository;
import domain.repository.ShiftSchedulingRepository;
import domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AllocationService {

    private static final String MENSAGEM_RESPOSTA =
            "Apenas a pessoa a quem o turno foi escalado, ou a supervisão, pode responder.";

    @Autowired
    private ShiftSchedulingRepository shiftSchedulingRepository;

    @Autowired
    private EditionScaleRepository editionScaleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExchangeRequestRepository exchangeRequestRepository;

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
     * O analista aceita ou recusa o turno que lhe foi escalado.
     *
     * <p>Quem responde é a pessoa a quem o turno pertence, ou a supervisão — a
     * mesma regra de {@code ChangeTimeService}, e pela mesma razão: um pedido
     * de aceite é um pedido de quem tem algo a cumprir, e responder por outro
     * seria poder ausência. A supervisão entra para desbloquear o turno de
     * alguém que não está disponível, que é o caso em que ficar pendente para
     * sempre é pior do que ficar decidido.
     *
     * <p>A recusa é bloqueada enquanto houver uma troca pendente sobre a mesma
     * alocação. Recusar um turno que já se está a tentar ceder deixa a troca
     * pendente sobre um turno que o dono já não quer, e a troca só se resolveria
     * quando alguém reparasse nisso. Aceitar não é bloqueado, porque aceitar
     * e trocar não se contradizem.
     */
    @Transactional
    public AllocationDTO responder(Long id, AllocationStatus status, Long loggedUserId) {
        if (status == null) {
            throw new RuntimeException("Indique se aceita ou recusa o turno.");
        }

        ShiftScheduling alocacao = shiftSchedulingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alocação não encontrada."));

        exigirQuemPodeResponder(alocacao, loggedUserId);

        if (status == alocacao.getAnalystAcceptanceStatus()) {
            return AllocationDTO.from(alocacao);
        }

        if (status == AllocationStatus.REJECTED) {
            impedirRecusaComTrocaPendente(id);
        }

        alocacao.setAnalystAcceptanceStatus(status);

        return AllocationDTO.from(shiftSchedulingRepository.save(alocacao));
    }

    /**
     * Recusa a resposta de quem não é o dono do turno e não é supervisor.
     *
     * <p>Compara por id e não por objeto, como em {@code ChangeTimeService}: o
     * {@link User} lido através da alocação é outra instância com o mesmo id, e
     * comparar por identidade daria {@code false} ao próprio dono.
     */
    private void exigirQuemPodeResponder(ShiftScheduling alocacao, Long loggedUserId) {
        User logado = loggedUserId == null ? null : userRepository.findById(loggedUserId).orElse(null);

        if (logado == null) {
            throw new AccessDeniedException(MENSAGEM_RESPOSTA);
        }

        if (logado.getProfile() == UserProfile.SUPERVISOR) {
            return;
        }

        User dono = alocacao.getUser();

        if (dono == null || dono.getId() == null || !dono.getId().equals(logado.getId())) {
            throw new AccessDeniedException(MENSAGEM_RESPOSTA);
        }
    }

    private void impedirRecusaComTrocaPendente(Long alocacaoId) {
        boolean emTroca = exchangeRequestRepository.existePendenteParaA(
                ExchangeStatus.PENDING, alocacaoId);

        if (emTroca) {
            throw new RuntimeException("Não pode recusar este turno enquanto houver uma "
                    + "troca pendente sobre ele. Resolva primeiro a troca.");
        }
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

        // A sobreposição só pode ser avaliada com os valores novos em mãos, e
        // os valores novos não podem ser escritos na entidade antes de a
        // avaliação estar feita: numa alteração, a entidade é gerida pelo
        // Hibernate, e escrevê-la antes de recusar deixaria a linha alterada
        // mesmo com o pedido devolvido como erro.
        //
        // Por isso a regra corre sobre uma cópia desligada, e a entidade só é
        // tocada depois de todas as regras passarem.
        ShiftScheduling candidata = copiaDesligada(alocacao);
        aplicarValores(candidata, escala, utilizador, dto);

        impedirSobreposicao(candidata);

        aplicarValores(alocacao, escala, utilizador, dto);
    }

    /**
     * Uma alocação desligada com o mesmo identificador da original.
     *
     * <p>Tem de trazer o id para que a exclusão da própria alocação funcione: sem
     * ele, a candidata era comparada com a linha que está a ser editada como se
     * fosse outra, e nenhuma alteração seria aceite.
     */
    private ShiftScheduling copiaDesligada(ShiftScheduling origem) {
        ShiftScheduling copia = new ShiftScheduling();
        copia.setId(origem.getId());
        return copia;
    }

    /** Escreve os valores do pedido na alocação. */
    private void aplicarValores(ShiftScheduling alocacao, EditionScale escala, User utilizador,
                                AllocationRequestDTO dto) {
        alocacao.setEditionScale(escala);
        alocacao.setUser(utilizador);
        alocacao.setShift(dto.shift());
        alocacao.setAssignments(atribuicoes(dto.assignments()));
        alocacao.setCustomStartTime(dto.customStartTime());
        alocacao.setCustomEndTime(dto.customEndTime());
        alocacao.setSpecificDate(dto.specificDate());
        // Qualquer edição volta o aceite ao estado inicial. Quem tinha aceite
        // tinha aceite outra coisa — outro turno, outra data, ou o turno de outra
        // pessoa — e um aceite seu não pode valer para o que passou a estar
        // escrito. Voltar a pendente é o estado honesto: a alocação mudou e
        // precisa de novo de ser vista por quem a vai cumprir.
        alocacao.setAnalystAcceptanceStatus(AllocationStatus.PENDING);
    }

    /**
     * Recusa a mesma pessoa escalada duas vezes em cima uma da outra.
     *
     * <p>A regra de negócio central que faltava: nada impedia que a mesma pessoa
     * ficasse com T1 (08h00-12h00) e T2 (11h00-15h00) no mesmo dia, e o
     * resultado era uma escala impossível de cumprir.
     *
     * <p>Turnos que só se tocam — T1 e T3, que partilham a fronteira das 12h00
     * — são aceites: quem acaba ao meio-dia entra no turno seguinte. A decisão
     * está em {@link ShiftScheduling#conflitaCom}.
     *
     * <p>Na alteração, a própria alocação é excluída da comparação. Sem isso,
     * editar uma alocação sem mexer no turno nem nas datas entraria em conflito
     * consigo própria e nenhuma edição seria possível.
     */
    private void impedirSobreposicao(ShiftScheduling candidata) {
        List<ShiftScheduling> doMesmoUtilizador =
                shiftSchedulingRepository.findByEditionScaleIdAndUserIdOrderByIdAsc(
                        candidata.getEditionScale().getId(),
                        candidata.getUser().getId());

        ShiftScheduling conflito = doMesmoUtilizador.stream()
                .filter(existente -> !eOMesmoRegisto(existente, candidata))
                .filter(candidata::conflitaCom)
                .findFirst()
                .orElse(null);

        if (conflito == null) {
            return;
        }

        throw new RuntimeException(mensagemDeSobreposicao(conflito));
    }

    /**
     * Verdadeiro quando as duas referências são o mesmo registo.
     *
     * <p>Compara por id, e não por identidade: numa alteração a candidata é a
     * mesma instância que o repositório devolveu, mas o inverso também é
     * verdade depois de um {@code flush}, e a comparação por objeto passaria a
     * depender disso.
     */
    private boolean eOMesmoRegisto(ShiftScheduling existente, ShiftScheduling candidata) {
        return candidata.getId() != null && candidata.getId().equals(existente.getId());
    }

    /**
     * A mensagem nomeia o turno que já lá está, porque "conflito" sozinho não
     * ajuda ninguém a decidir o que fazer: a correção é quase sempre mudar um
     * dos dois turnos para outro que não se cruze.
     */
    private String mensagemDeSobreposicao(ShiftScheduling conflito) {
        return "Este utilizador já tem o turno "
                + conflito.getShift().getRotuloCurto()
                + " ("
                + conflito.intervaloEfetivo().formatado()
                + ") neste dia, e os horários sobrepõem-se.";
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
