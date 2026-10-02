package service;

import domain.dto.CreateScaleDTO;
import domain.dto.ScaleDeletionSummaryDTO;
import domain.model.entities.EditionScale;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.EditionStatus;
import domain.repository.EditionScaleRepository;
import domain.repository.ExchangeRequestRepository;
import domain.repository.ShiftSchedulingRepository;
import domain.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ScaleService {

    @Autowired
    private EditionScaleRepository editionScaleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShiftSchedulingRepository shiftSchedulingRepository;

    @Autowired
    private ExchangeRequestRepository exchangeRequestRepository;

    @Transactional
    public EditionScale createScale(CreateScaleDTO dto) {

        User creator = userRepository.findById(dto.createdById()).
                orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

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

    /**
     * Quanto é que a exclusão de uma escala leva consigo.
     *
     * <p>Existe para a confirmação dizer um número em vez de «tem a certeza?».
     * Uma escala com doze turnos e três trocas em jogo e uma escala recém-criada
     * e vazia pedem decisões diferentes, e Treat-as igual é o que faz com que a
     * confirmação deixe de ser uma decisão.
     *
     * <p>A escala é procurada para que um identificador inválido dê a mesma
     * mensagem de «não encontrada» que o resto das operações de escala, em vez de
     * um relatório de zero que o frontend leria como «pode excluir à vontade».
     */
    @Transactional(readOnly = true)
    public ScaleDeletionSummaryDTO resumoExclusao(Long id) {
        EditionScale escala = findById(id);

        List<Long> alocacoes = shiftSchedulingRepository.idsPorEscala(escala.getId());

        return new ScaleDeletionSummaryDTO(alocacoes.size(), contarTrocas(alocacoes));
    }

    /**
     * Exclui uma escala e o que depende dela.
     *
     * <p>Uma escala não sobrevive aos seus turnos: a alocação aponta para a
     * escala e é obrigatória, portanto apagar a escala sem os turnos rebentaria
     * na restrição. E os turnos, por sua vez, não sobrevivem aos pedidos de troca
     * que os referenciam. Daí a ordem, que é a do grafo e não a da leitura: o
     * pedido de troca cai primeiro, porque é o único dos três que aponta para
     * outro e não é apontado por nenhum.
     *
     * <p>Um pedido de troca pode ter as duas pontas em escalas diferentes, e a
     * consulta do apagamento não distingue: apaga o pedido. Deixar metade de um
     * pedido vivo seria um histórico que já não corresponde a nada, e a troca
     * que ele descrevia deixou de existir com os turnos.
     *
     * <p>Os pedidos de resposta pendente são apagados sem distinção de estado, e
     * é o que a pergunta «o que é que eu vou perder?» exige: há gente à espera
     * de responder a uma troca que deixa de existir, e notificar isso seria mais
     * do que a exclusão de uma escala tem para dizer.
     *
     * <p>Os turnos são removidos entidade a entidade e não com um DELETE em
     * bloco porque têm uma tabela própria de atribuições especiais: apagar a
     * tabela-mãe por consulta deixaria essas linhas para trás.
     */
    @Transactional
    public void apagar(Long id) {
        EditionScale escala = findById(id);

        List<ShiftScheduling> alocacoes =
                shiftSchedulingRepository.findByEditionScaleIdOrderByIdAsc(escala.getId());

        if (!alocacoes.isEmpty()) {
            List<Long> ids = alocacoes.stream().map(ShiftScheduling::getId).toList();
            exchangeRequestRepository.apagarDasAlocacoes(ids);
        }

        shiftSchedulingRepository.deleteAll(alocacoes);
        editionScaleRepository.delete(escala);
    }

    /**
     * Quantos pedidos de troca tocam nestas alocações.
     *
     * <p>Uma lista vazia é devolvida como zero antes de chegar à consulta: um
     * {@code IN ()} sem elementos não é JPQL válido, e o caso é real — uma
     * escala acabada de criar, que é a que mais se quer excluir.
     *
     * <p>A contagem vem como {@code long} do banco e sai como {@code int} porque é
     * o que o relatório transporta. A conversão é exata e não arredonda: um
     * número de pedidos de troca que não caiba num {@code int} é um número que
     * não cabe na escala que o produziu, e truncá-lo daria à confirmação uma
     * contagem errada em silêncio.
     */
    private int contarTrocas(List<Long> alocacoes) {
        if (alocacoes.isEmpty()) {
            return 0;
        }

        return Math.toIntExact(exchangeRequestRepository.contarDasAlocacoes(alocacoes));
    }
}