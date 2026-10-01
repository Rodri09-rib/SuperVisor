package service;

import domain.dto.ShiftExchangeDTO;
import domain.model.entities.EditionScale;
import domain.model.entities.ExchangeRequest;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.ExchangeStatus;
import domain.model.enums.UserProfile;
import domain.repository.ExchangeRequestRepository;
import domain.repository.ShiftSchedulingRepository;
import domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChangeTimeService")
class ChangeTimeServiceTest {

    @Mock
    private ExchangeRequestRepository exchangeRequestRepository;

    @Mock
    private ShiftSchedulingRepository shiftSchedulingRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChangeTimeService changeTimeService;

    private User admin;
    private User joao;
    private User maria;
    private EditionScale escala;
    private ShiftScheduling alocacaoAdmin;
    private ShiftScheduling alocacaoJoao;

    @BeforeEach
    void setUp() {
        admin = new User(1L, "Administrador", "admin@teste.com", "123456", UserProfile.SUPERVISOR);
        joao = new User(2L, "João", "joao@teste.com", "123456", UserProfile.ANALIST);
        maria = new User(3L, "Maria", "maria@teste.com", "123456", UserProfile.ANALIST);

        escala = new EditionScale();
        escala.setId(1L);

        alocacaoAdmin = new ShiftScheduling();
        alocacaoAdmin.setId(10L);
        alocacaoAdmin.setEditionScale(escala);
        alocacaoAdmin.setUser(admin);

        alocacaoJoao = new ShiftScheduling();
        alocacaoJoao.setId(11L);
        alocacaoJoao.setEditionScale(escala);
        alocacaoJoao.setUser(joao);
    }

    @Nested
    @DisplayName("requestExchange")
    class Request {

        @Test
        @DisplayName("regista a solicitação com as duas alocações, o requisitante e o estado PENDING")
        void criaSolicitacaoPendente() {
            when(shiftSchedulingRepository.findById(10L)).thenReturn(Optional.of(alocacaoAdmin));
            when(shiftSchedulingRepository.findById(11L)).thenReturn(Optional.of(alocacaoJoao));
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));

            // João dá o turno 11, que é dele, e pede o 10, que é do admin.
            changeTimeService.requestExchange(11L, 10L, 2L);

            ArgumentCaptor<ExchangeRequest> captor = ArgumentCaptor.forClass(ExchangeRequest.class);
            verify(exchangeRequestRepository).save(captor.capture());
            ExchangeRequest request = captor.getValue();

            assertThat(request.getSourceAllocation()).isSameAs(alocacaoJoao);
            assertThat(request.getDestinationAllocation()).isSameAs(alocacaoAdmin);
            assertThat(request.getRequestingUser()).isSameAs(joao);
            assertThat(request.getStatus()).isEqualTo(ExchangeStatus.PENDING);
            assertThat(request.getCreationDate()).isNotNull();
        }

        @Test
        @DisplayName("guarda quem é o colega a quem a troca foi pedida")
        void guardaColegaPedido() {
            // O pedido é o do dono do turno de origem, que quer o turno de
            // destino. Quem tem de responder é o dono do destino — por isso é
            // esse, e não o requerente, que fica copiado para o pedido.
            when(shiftSchedulingRepository.findById(10L)).thenReturn(Optional.of(turnoDoJoao(10L)));
            when(shiftSchedulingRepository.findById(11L)).thenReturn(Optional.of(turnoDoAdmin(11L)));
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));

            changeTimeService.requestExchange(10L, 11L, 2L);

            ArgumentCaptor<ExchangeRequest> captor = ArgumentCaptor.forClass(ExchangeRequest.class);
            verify(exchangeRequestRepository).save(captor.capture());

            assertThat(captor.getValue().getRequestedUser()).isSameAs(admin);
            assertThat(captor.getValue().getRequestingUser()).isSameAs(joao);
        }

        /**
         * Alocação do requerente, a ser abandonada.
         */
        private ShiftScheduling turnoDoJoao(Long id) {
            ShiftScheduling alocacao = new ShiftScheduling();
            alocacao.setId(id);
            alocacao.setEditionScale(escala);
            alocacao.setUser(joao);
            return alocacao;
        }

        /**
         * Alocação do colega, a ser conquistada — e a única que decide quem
         * aprova.
         */
        private ShiftScheduling turnoDoAdmin(Long id) {
            ShiftScheduling alocacao = new ShiftScheduling();
            alocacao.setId(id);
            alocacao.setEditionScale(escala);
            alocacao.setUser(admin);
            return alocacao;
        }

        @Test
        @DisplayName("recusa pedir a troca de um turno que não é do utilizador")
        void naoTrocaTurnoDeOutro() {
            // Sem esta guarda, o pedido apontava para o turno do admin como
            // origem e para o do próprio João como destino. O colega pedido
            // ficava a ser o próprio João, que respondia ao seu próprio pedido
            // e ficava com os dois turnos — o dono do primeiro perdia-o sem
            // nunca ter dito que sim.
            when(shiftSchedulingRepository.findById(10L)).thenReturn(Optional.of(alocacaoAdmin));
            when(shiftSchedulingRepository.findById(11L)).thenReturn(Optional.of(alocacaoJoao));
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));

            assertThatThrownBy(() -> changeTimeService.requestExchange(10L, 11L, 2L))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                    .hasMessageContaining("lhe pertence");

            verify(exchangeRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("recusa pedir a troca para um turno que já é do utilizador")
        void naoTrocaParaTurnoProprio() {
            when(shiftSchedulingRepository.findById(11L)).thenReturn(Optional.of(alocacaoJoao));
            when(shiftSchedulingRepository.findById(12L)).thenReturn(Optional.of(turnoDoJoao(12L)));
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));

            // O 12 também é de João. Pedir a troca para lá não é uma troca: é um
            // pedido que o próprio João aprovaria, e a escala ficaria igual.
            assertThatThrownBy(() -> changeTimeService.requestExchange(11L, 12L, 2L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("O turno de destino já é seu, por isso não há nada a trocar.");

            verify(exchangeRequestRepository, never()).save(any());
        }


        @Test
        @DisplayName("um supervisor também não pode pedir a troca de um turno de outro")
        void supervisorRespeitaADonoDoTurno() {
            // O perfil não dá direito a turnos alheios: quem responde a um
            // pedido é outra pessoa, e o supervisor responder ao pedido de
            // alguém sobre o turno de um terceiro seria o mesmo buraco.
            when(shiftSchedulingRepository.findById(10L)).thenReturn(Optional.of(alocacaoAdmin));
            when(shiftSchedulingRepository.findById(11L)).thenReturn(Optional.of(alocacaoJoao));
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

            assertThatThrownBy(() -> changeTimeService.requestExchange(11L, 10L, 1L))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

            verify(exchangeRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("um pedido novo não tem data de resposta")
        void semDataDeResposta() {
            when(shiftSchedulingRepository.findById(10L)).thenReturn(Optional.of(alocacaoAdmin));
            when(shiftSchedulingRepository.findById(11L)).thenReturn(Optional.of(alocacaoJoao));
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));

            changeTimeService.requestExchange(11L, 10L, 2L);

            ArgumentCaptor<ExchangeRequest> captor = ArgumentCaptor.forClass(ExchangeRequest.class);
            verify(exchangeRequestRepository).save(captor.capture());

            assertThat(captor.getValue().getApprovalDate()).isNull();
        }

        @Test
        @DisplayName("a data de criação é preenchida com o instante atual")
        void dataDeCriacaoPreenchida() {
            when(shiftSchedulingRepository.findById(10L)).thenReturn(Optional.of(alocacaoAdmin));
            when(shiftSchedulingRepository.findById(11L)).thenReturn(Optional.of(alocacaoJoao));
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

            var antes = OffsetDateTime.now().minusSeconds(1);
            changeTimeService.requestExchange(10L, 11L, 1L);
            var depois = OffsetDateTime.now().plusSeconds(1);

            ArgumentCaptor<ExchangeRequest> captor = ArgumentCaptor.forClass(ExchangeRequest.class);
            verify(exchangeRequestRepository).save(captor.capture());
            assertThat(captor.getValue().getCreationDate()).isBetween(antes, depois);
        }

        @Test
        @DisplayName("alocação de origem inexistente resulta em erro")
        void origemInexistente() {
            when(shiftSchedulingRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> changeTimeService.requestExchange(404L, 11L, 2L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Alocação de origem não encontrada.");

            verifyNoInteractions(exchangeRequestRepository, userRepository);
        }

        @Test
        @DisplayName("alocação de destino inexistente resulta em erro")
        void destinoInexistente() {
            when(shiftSchedulingRepository.findById(10L)).thenReturn(Optional.of(alocacaoAdmin));
            when(shiftSchedulingRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> changeTimeService.requestExchange(10L, 404L, 2L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Alocação de destino não encontrada.");

            verifyNoInteractions(exchangeRequestRepository, userRepository);
        }

        @Test
        @DisplayName("requisitante inexistente resulta em erro")
        void requisitanteInexistente() {
            when(shiftSchedulingRepository.findById(10L)).thenReturn(Optional.of(alocacaoAdmin));
            when(shiftSchedulingRepository.findById(11L)).thenReturn(Optional.of(alocacaoJoao));
            when(userRepository.findById(404L)).thenReturn(Optional.empty());

            // O id 404 vai no terceiro argumento, o do requerente. Com um id
            // inválido no destino, o serviço falhava antes — com a mensagem
            // errada, e a causa someva por baixo da outra.
            assertThatThrownBy(() -> changeTimeService.requestExchange(10L, 11L, 404L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Utilizador não encontrado");

            verify(exchangeRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("recusa origem e destino iguais, porque a alocação não pode ser origem e destino ao mesmo tempo")
        void naoValidaAlocacoesDistintas() {
            assertThatThrownBy(() -> changeTimeService.requestExchange(10L, 10L, 1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Alocação de origem e destino não podem ser iguais.");

            verify(exchangeRequestRepository, never()).save(any());
        }

        @Nested
        @DisplayName("Pedidos duplicados")
        class Duplicados {

            /** O cenário válido: a troca de 11 (do João) por 10 (do admin). */
            private void cenarioDaTrocaDoJoao() {
                when(shiftSchedulingRepository.findById(10L)).thenReturn(Optional.of(alocacaoAdmin));
                when(shiftSchedulingRepository.findById(11L)).thenReturn(Optional.of(alocacaoJoao));
                when(userRepository.findById(2L)).thenReturn(Optional.of(joao));
            }

            @Test
            @DisplayName("recusa repetir um pedido que já está pendente")
            void recusaRepetirOPedidoPendente() {
                // Carregar outra vez no botão, ou recarregar a página e carregar
                // outra vez, criava pedidos idênticos ao infinito e o colega
                // passava a ver o mesmo pedido várias vezes na fila.
                cenarioDaTrocaDoJoao();
                when(exchangeRequestRepository
                        .existsByStatusAndSourceAllocationIdAndDestinationAllocationId(
                                ExchangeStatus.PENDING, 11L, 10L))
                        .thenReturn(true);

                assertThatThrownBy(() -> changeTimeService.requestExchange(11L, 10L, 2L))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("Já existe um pedido de troca pendente para estes dois turnos.");

                verify(exchangeRequestRepository, never()).save(any());
            }

            @Test
            @DisplayName("aceita o pedido quando não há nenhum pendente no mesmo sentido")
            void aceitaQuandoNaoHaPendente() {
                cenarioDaTrocaDoJoao();
                when(exchangeRequestRepository
                        .existsByStatusAndSourceAllocationIdAndDestinationAllocationId(
                                ExchangeStatus.PENDING, 11L, 10L))
                        .thenReturn(false);

                changeTimeService.requestExchange(11L, 10L, 2L);

                verify(exchangeRequestRepository).save(any(ExchangeRequest.class));
            }

            @Test
            @DisplayName("o sentido contrário não é duplicado: cada um cede o seu turno")
            void sentidoContrarioNaoEDuplicado() {
                // Se o João pede o turno do admin e o admin pede o do João, os
                // dois pedidos são legítimos e diferentes: um cede 11, o outro
                // cede 10. Tratar isto como duplicado impediria a única
                // negociação que funciona, em que os dois chegam a acordo.
                when(shiftSchedulingRepository.findById(10L)).thenReturn(Optional.of(alocacaoAdmin));
                when(shiftSchedulingRepository.findById(11L)).thenReturn(Optional.of(alocacaoJoao));
                when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
                when(exchangeRequestRepository
                        .existsByStatusAndSourceAllocationIdAndDestinationAllocationId(
                                ExchangeStatus.PENDING, 10L, 11L))
                        .thenReturn(false);

                changeTimeService.requestExchange(10L, 11L, 1L);

                verify(exchangeRequestRepository).save(any(ExchangeRequest.class));
            }

            @Test
            @DisplayName("só o estado pendente trava; um pedido já respondido deixa pedir de novo")
            void soOPendenteTrava() {
                // A consulta leva o estado, e não pergunta só "existe": um pedido
                // recusado ou aceite já tem resposta e não pode impedir uma nova
                // tentativa pela mesma troca.
                cenarioDaTrocaDoJoao();
                when(exchangeRequestRepository
                        .existsByStatusAndSourceAllocationIdAndDestinationAllocationId(
                                ExchangeStatus.PENDING, 11L, 10L))
                        .thenReturn(false);

                changeTimeService.requestExchange(11L, 10L, 2L);

                verify(exchangeRequestRepository)
                        .existsByStatusAndSourceAllocationIdAndDestinationAllocationId(
                                ExchangeStatus.PENDING, 11L, 10L);
            }

            @Test
            @DisplayName("um pedido que não podia ser feito por outro motivo continua a falhar por esse motivo")
            void aGuardaVemNoFim() {
                // Se a duplicidade fosse verificada primeiro, um pedido de um
                // turno que não é seu deixaria de dizer "não lhe pertence".
                when(shiftSchedulingRepository.findById(10L)).thenReturn(Optional.of(alocacaoAdmin));
                when(shiftSchedulingRepository.findById(11L)).thenReturn(Optional.of(alocacaoJoao));
                when(userRepository.findById(2L)).thenReturn(Optional.of(joao));

                assertThatThrownBy(() -> changeTimeService.requestExchange(10L, 11L, 2L))
                        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                        .hasMessageContaining("lhe pertence");

                verify(exchangeRequestRepository, never()).save(any());
                verify(exchangeRequestRepository, never())
                        .existsByStatusAndSourceAllocationIdAndDestinationAllocationId(
                                any(), any(), any());
            }
        }

        @Test
        @DisplayName("recusa alocações de escalas diferentes, porque a troca tem de ser dentro da mesma escala")
        void alocacoesDeEscalasDiferentes() {
            EditionScale outra = new EditionScale();
            outra.setId(2L);
            alocacaoJoao.setEditionScale(outra);

            when(shiftSchedulingRepository.findById(10L)).thenReturn(Optional.of(alocacaoAdmin));
            when(shiftSchedulingRepository.findById(11L)).thenReturn(Optional.of(alocacaoJoao));

            assertThatThrownBy(() -> changeTimeService.requestExchange(10L, 11L, 2L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("As alocações pertencem a escalas diferentes.");

            verify(exchangeRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("um motivo só com espaços é guardado como nulo")
        void motivoEmBrancoViraNulo() {
            when(shiftSchedulingRepository.findById(10L)).thenReturn(Optional.of(alocacaoAdmin));
            when(shiftSchedulingRepository.findById(11L)).thenReturn(Optional.of(alocacaoJoao));
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));

            changeTimeService.requestExchange(11L, 10L, 2L, "   ");

            ArgumentCaptor<ExchangeRequest> captor = ArgumentCaptor.forClass(ExchangeRequest.class);
            verify(exchangeRequestRepository).save(captor.capture());

            assertThat(captor.getValue().getReason()).isNull();
        }

        @Test
        @DisplayName("um motivo com espaços nas pontas é guardado aparado")
        void motivoAparado() {
            when(shiftSchedulingRepository.findById(10L)).thenReturn(Optional.of(alocacaoAdmin));
            when(shiftSchedulingRepository.findById(11L)).thenReturn(Optional.of(alocacaoJoao));
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));

            changeTimeService.requestExchange(11L, 10L, 2L, "  Consulta médica  ");

            ArgumentCaptor<ExchangeRequest> captor = ArgumentCaptor.forClass(ExchangeRequest.class);
            verify(exchangeRequestRepository).save(captor.capture());

            assertThat(captor.getValue().getReason()).isEqualTo("Consulta médica");
        }
    }

    @Nested
    @DisplayName("respondExchange — aceite")
    class Aceite {

        private ExchangeRequest requestPendente() {
            ExchangeRequest request = new ExchangeRequest();
            request.setId(100L);
            request.setSourceAllocation(alocacaoAdmin);
            request.setDestinationAllocation(alocacaoJoao);
            request.setRequestingUser(joao);
            request.setRequestedUser(admin);
            request.setStatus(ExchangeStatus.PENDING);
            return request;
        }

        @Test
        @DisplayName("troca os utilizadores entre as duas alocações")
        void trocaUtilizadores() {
            when(exchangeRequestRepository.findByIdComDetalhes(100L))
                    .thenReturn(Optional.of(requestPendente()));
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

            changeTimeService.respondExchange(100L, true, 1L);

            assertThat(alocacaoAdmin.getUser()).isSameAs(joao);
            assertThat(alocacaoJoao.getUser()).isSameAs(admin);
        }

        @Test
        @DisplayName("persiste as duas alocações e o pedido como APPROVED")
        void persisteTudoComoAprovado() {
            ExchangeRequest request = requestPendente();
            when(exchangeRequestRepository.findByIdComDetalhes(100L)).thenReturn(Optional.of(request));
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

            changeTimeService.respondExchange(100L, true, 1L);

            verify(shiftSchedulingRepository).save(alocacaoAdmin);
            verify(shiftSchedulingRepository).save(alocacaoJoao);
            verify(exchangeRequestRepository).save(request);
            assertThat(request.getStatus()).isEqualTo(ExchangeStatus.APPROVED);
        }

        @Test
        @DisplayName("regista a data da resposta")
        void registaDataDeResposta() {
            ExchangeRequest request = requestPendente();
            when(exchangeRequestRepository.findByIdComDetalhes(100L)).thenReturn(Optional.of(request));
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

            var antes = OffsetDateTime.now().minusSeconds(1);
            changeTimeService.respondExchange(100L, true, 1L);
            var depois = OffsetDateTime.now().plusSeconds(1);

            assertThat(request.getApprovalDate()).isBetween(antes, depois);
        }

        @Test
        @DisplayName("o colega pedido não muda ao responder, apesar de as alocações trocarem de dono")
        void colegaPedidoNaoMuda() {
            // É a razão de o colega estar copiado no pedido. Depois de aceite,
            // a alocação de destino já é do requerente, e lê-la devolveria o
            // próprio nome no lugar de "troca com".
            ExchangeRequest request = requestPendente();
            when(exchangeRequestRepository.findByIdComDetalhes(100L)).thenReturn(Optional.of(request));
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

            changeTimeService.respondExchange(100L, true, 1L);

            assertThat(request.getRequestedUser()).isSameAs(admin);
        }
    }

    @Nested
    @DisplayName("respondExchange — recusa")
    class Recusa {

        private ExchangeRequest requestPendente() {
            ExchangeRequest request = new ExchangeRequest();
            request.setId(100L);
            request.setSourceAllocation(alocacaoAdmin);
            request.setDestinationAllocation(alocacaoJoao);
            request.setRequestingUser(joao);
            request.setRequestedUser(admin);
            request.setStatus(ExchangeStatus.PENDING);
            return request;
        }

        @Test
        @DisplayName("marca como REJECTED sem tocar nas alocações")
        void marcaRejeitada() {
            ExchangeRequest request = requestPendente();
            when(exchangeRequestRepository.findByIdComDetalhes(100L)).thenReturn(Optional.of(request));
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

            changeTimeService.respondExchange(100L, false, 1L);

            assertThat(request.getStatus()).isEqualTo(ExchangeStatus.REJECTED);
            verify(shiftSchedulingRepository, never()).save(any());
            verify(exchangeRequestRepository).save(request);
        }

        @Test
        @DisplayName("regista a data da resposta mesmo numa recusa")
        void registaDataNaRecusa() {
            ExchangeRequest request = requestPendente();
            when(exchangeRequestRepository.findByIdComDetalhes(100L)).thenReturn(Optional.of(request));
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

            changeTimeService.respondExchange(100L, false, 1L);

            assertThat(request.getApprovalDate()).isNotNull();
        }

        @Test
        @DisplayName("os utilizadores das alocações permanecem os mesmos")
        void alocacoesIntactas() {
            when(exchangeRequestRepository.findByIdComDetalhes(100L)).thenReturn(Optional.of(requestPendente()));
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

            changeTimeService.respondExchange(100L, false, 1L);

            assertThat(alocacaoAdmin.getUser()).isSameAs(admin);
            assertThat(alocacaoJoao.getUser()).isSameAs(joao);
        }
    }

    @Nested
    @DisplayName("respondExchange — quem pode responder")
    class Autorizacao {

        private ExchangeRequest pedidoParaAdmin() {
            ExchangeRequest request = new ExchangeRequest();
            request.setId(100L);
            request.setSourceAllocation(alocacaoAdmin);
            request.setDestinationAllocation(alocacaoJoao);
            request.setRequestingUser(joao);
            request.setRequestedUser(admin);
            request.setStatus(ExchangeStatus.PENDING);
            return request;
        }

        @Test
        @DisplayName("o colega a quem a troca foi pedida pode responder")
        void colegaPodeResponder() {
            // A mesma instância que o serviço recebeu: `pedidoParaAdmin()` cria
            // um objeto novo a cada chamada, e verificar sobre outro dava
            // sempre PENDING.
            ExchangeRequest pedido = pedidoParaAdmin();
            when(exchangeRequestRepository.findByIdComDetalhes(100L)).thenReturn(Optional.of(pedido));
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

            changeTimeService.respondExchange(100L, true, 1L);

            assertThat(pedido.getStatus()).isEqualTo(ExchangeStatus.APPROVED);
        }

        @Test
        @DisplayName("quem não é o colega não pode responder, e as alocações ficam intactas")
        void terceiroNaoPodeResponder() {
            // Sem esta guarda, um par de pedidos em que cada um respondesse ao
            // pedido do outro acabava com os dois turnos trocados e devolvidos,
            // com ambos os pedidos marcados como aceites.
            ExchangeRequest request = pedidoParaAdmin();
            when(exchangeRequestRepository.findByIdComDetalhes(100L)).thenReturn(Optional.of(request));
            when(userRepository.findById(3L)).thenReturn(Optional.of(maria));

            assertThatThrownBy(() -> changeTimeService.respondExchange(100L, true, 3L))
                    .isInstanceOf(AccessDeniedException.class);

            assertThat(alocacaoAdmin.getUser()).isSameAs(admin);
            assertThat(alocacaoJoao.getUser()).isSameAs(joao);
            verify(exchangeRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("o próprio requerente não pode responder ao seu próprio pedido")
        void requerenteNaoPodeResponder() {
            when(exchangeRequestRepository.findByIdComDetalhes(100L))
                    .thenReturn(Optional.of(pedidoParaAdmin()));
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));

            assertThatThrownBy(() -> changeTimeService.respondExchange(100L, true, 2L))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("a supervisão pode responder a um pedido de alguém que está indisponível")
        void supervisaoPodeResponder() {
            ExchangeRequest request = pedidoParaAdmin();
            request.setRequestedUser(joao);
            when(exchangeRequestRepository.findByIdComDetalhes(100L)).thenReturn(Optional.of(request));
            when(userRepository.findById(3L)).thenReturn(Optional.of(
                    new User(3L, "Supervisor", "sup@teste.com", "hash", UserProfile.SUPERVISOR)));

            changeTimeService.respondExchange(100L, true, 3L);

            assertThat(request.getStatus()).isEqualTo(ExchangeStatus.APPROVED);
        }

        @Test
        @DisplayName("num pedido antigo sem colega guardado, só a supervisão passa")
        void pedidoAntigoSemColega() {
            // O caso é real: a migração de `requested_user_id` não consegue
            // reconstruir o colega de um pedido já aceite, porque as alocações
            // já trocaram de dono. É um pedido que a supervisão tem de poder
            // desbloquear.
            ExchangeRequest request = pedidoParaAdmin();
            request.setRequestedUser(null);
            when(exchangeRequestRepository.findByIdComDetalhes(100L)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> changeTimeService.respondExchange(100L, true, 2L))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("um principal que não resolve para um utilizador não passa")
        void utilizadorInexistente() {
            when(exchangeRequestRepository.findByIdComDetalhes(100L))
                    .thenReturn(Optional.of(pedidoParaAdmin()));
            when(userRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> changeTimeService.respondExchange(100L, true, 404L))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("respondExchange — guardas")
    class Guardas {

        @Test
        @DisplayName("solicitação inexistente resulta em erro")
        void solicitacaoInexistente() {
            when(exchangeRequestRepository.findByIdComDetalhes(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> changeTimeService.respondExchange(404L, true, 1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Solicitação não encontrada.");
        }

        @Test
        @DisplayName("já respondida não pode ser respondida outra vez")
        void jaRespondida() {
            ExchangeRequest respondida = new ExchangeRequest();
            respondida.setId(100L);
            respondida.setStatus(ExchangeStatus.APPROVED);
            when(exchangeRequestRepository.findByIdComDetalhes(100L)).thenReturn(Optional.of(respondida));

            assertThatThrownBy(() -> changeTimeService.respondExchange(100L, true, 1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Esta solicitação já foi respondida.");

            verify(shiftSchedulingRepository, never()).save(any());
        }

        @Test
        @DisplayName("recusada também não pode ser respondida outra vez")
        void jaRecusada() {
            ExchangeRequest recusada = new ExchangeRequest();
            recusada.setId(100L);
            recusada.setStatus(ExchangeStatus.REJECTED);
            when(exchangeRequestRepository.findByIdComDetalhes(100L)).thenReturn(Optional.of(recusada));

            assertThatThrownBy(() -> changeTimeService.respondExchange(100L, false, 1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Esta solicitação já foi respondida.");
        }

        @Test
        @DisplayName("um pedido novo, com o estado por omissão, é reconhecido como pendente")
        void estadoPadraoDaEntidadeEhPendente() {
            // Este teste é o inverso do que existia antes. O inicializador do
            // campo escrevia "PENDENTE" e o serviço comparava com "PENDING", pelo
            // que um pedido construído sem estado explícito respondia "já foi
            // respondida" para sempre. Com o enum, o valor por omissão é o
            // mesmo que o serviço reconhece.
            ExchangeRequest comDefault = new ExchangeRequest();
            comDefault.setId(101L);
            comDefault.setSourceAllocation(alocacaoAdmin);
            comDefault.setDestinationAllocation(alocacaoJoao);
            comDefault.setRequestingUser(joao);
            comDefault.setRequestedUser(admin);

            assertThat(comDefault.getStatus()).isEqualTo(ExchangeStatus.PENDING);

            when(exchangeRequestRepository.findByIdComDetalhes(101L)).thenReturn(Optional.of(comDefault));
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

            changeTimeService.respondExchange(101L, true, 1L);

            assertThat(comDefault.getStatus()).isEqualTo(ExchangeStatus.APPROVED);
        }
    }

    @Nested
    @DisplayName("listarHistorico")
    class Historico {

        @Test
        @DisplayName("a consulta é feita por Specification, e não por um JPQL de parâmetros opcionais")
        void consultaPorSpecification() {
            when(exchangeRequestRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                    .thenReturn(List.of());

            changeTimeService.listarHistorico(null, null, null, null, admin);

            // Um `:parametro is null` em JPQL não funciona com OffsetDateTime: o
            // Hibernate envia um parâmetro sem tipo e o PostgreSQL recusa a
            // consulta inteira. A Specification omite o predicado, o que não
            // tem o problema e ainda é legível.
            verify(exchangeRequestRepository)
                    .findAll(any(org.springframework.data.jpa.domain.Specification.class));
        }

        @Test
        @DisplayName("um supervisor sem filtro de pessoa vê todas as trocas")
        void supervisorVeTudo() {
            assertThat(changeTimeService.utilizadorDoFiltro(admin, null)).isNull();
        }

        @Test
        @DisplayName("um supervisor pode filtrar por uma pessoa concreta")
        void supervisorFiltraPorPessoa() {
            assertThat(changeTimeService.utilizadorDoFiltro(admin, 3L)).isEqualTo(3L);
        }

        @Test
        @DisplayName("um analista sem filtro de pessoa vê apenas as trocas em que é parte")
        void analistaVeApenasAsSuas() {
            assertThat(changeTimeService.utilizadorDoFiltro(joao, null)).isEqualTo(2L);
        }

        @Test
        @DisplayName("o pedido de filtro de outro utilizador é ignorado, e não honored")
        void analistaNaoVedeTrocasDeOutros() {
            // O filtro é forçado ao próprio id em vez de recusado: o frontend
            // preenche o selector com o utilizador corrente, e devolver 403 por
            // um parâmetro legítimo faria a página parecer avariada. O resultado
            // é o mesmo em qualquer dos casos — o analista não vê o dos outros.
            assertThat(changeTimeService.utilizadorDoFiltro(joao, maria.getId())).isEqualTo(joao.getId());
        }

        @Test
        @DisplayName("mapeia as entidades para DTOs achatados")
        void mapeiaParaDto() {
            ExchangeRequest pedido = new ExchangeRequest();
            pedido.setId(100L);
            pedido.setSourceAllocation(alocacaoAdmin);
            pedido.setDestinationAllocation(alocacaoJoao);
            pedido.setRequestingUser(joao);
            pedido.setRequestedUser(admin);
            pedido.setStatus(ExchangeStatus.PENDING);
            pedido.setCreationDate(OffsetDateTime.now());

            when(exchangeRequestRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                    .thenReturn(List.of(pedido));

            List<ShiftExchangeDTO> historico =
                    changeTimeService.listarHistorico(null, null, null, null, admin);

            assertThat(historico).hasSize(1);
            ShiftExchangeDTO dto = historico.get(0);
            assertThat(dto.id()).isEqualTo(100L);
            assertThat(dto.requesterName()).isEqualTo("João");
            assertThat(dto.requestedName()).isEqualTo("Administrador");
            assertThat(dto.status()).isEqualTo(ExchangeStatus.PENDING);
            assertThat(dto.approvalDate()).isNull();
        }
    }

    @Test
    @DisplayName("a conversão das datas do filtro usa o fuso da aplicação")
    void limitesDeData() throws ReflectiveOperationException {
        // O limite superior é a meia-noite do dia seguinte, e não a própria data:
        // a coluna tem hora, e um pedido feito às 14h do dia final ficaria de
        // fora se o limite fosse o início desse dia. Com um JPQL de parâmetros
        // opcionais isto não se consegue verificar sem tocar na base, por isso
        // o teste vive ao nível do que a conversão produz.
        Method inicioDoDia = ChangeTimeService.class.getDeclaredMethod("inicioDoDia", LocalDate.class);
        Method inicioDoDiaSeguinte =
                ChangeTimeService.class.getDeclaredMethod("inicioDoDiaSeguinte", LocalDate.class);
        inicioDoDia.setAccessible(true);
        inicioDoDiaSeguinte.setAccessible(true);

        LocalDate dia = LocalDate.of(2026, 3, 20);

        assertThat((OffsetDateTime) inicioDoDia.invoke(changeTimeService, dia))
                .isEqualTo(dia.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime());
        assertThat((OffsetDateTime) inicioDoDiaSeguinte.invoke(changeTimeService, dia))
                .isEqualTo(LocalDate.of(2026, 3, 21).atStartOfDay(ZoneId.systemDefault())
                        .toOffsetDateTime());
    }

    @Test
    @DisplayName("sem datas, os limites são nulos e nenhum filtro de intervalo é aplicado")
    void semDatasNaoFiltraIntervalo() throws ReflectiveOperationException {
        Method inicioDoDia = ChangeTimeService.class.getDeclaredMethod("inicioDoDia", LocalDate.class);
        Method inicioDoDiaSeguinte =
                ChangeTimeService.class.getDeclaredMethod("inicioDoDiaSeguinte", LocalDate.class);
        inicioDoDia.setAccessible(true);
        inicioDoDiaSeguinte.setAccessible(true);

        assertThat(inicioDoDia.invoke(changeTimeService, new Object[]{null})).isNull();
        assertThat(inicioDoDiaSeguinte.invoke(changeTimeService, new Object[]{null})).isNull();
    }

    @Test
    @DisplayName("requestExchange e respondExchange são transacionais")
    void anotacoesTransacionais() throws NoSuchMethodException {
        Method request = ChangeTimeService.class.getMethod(
                "requestExchange", Long.class, Long.class, Long.class);
        Method respond = ChangeTimeService.class.getMethod(
                "respondExchange", Long.class, boolean.class, Long.class);
        Method historico = ChangeTimeService.class.getMethod(
                "listarHistorico", ExchangeStatus.class, Long.class,
                LocalDate.class, LocalDate.class, User.class);

        assertThat(request.getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                .isNotNull();
        assertThat(respond.getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                .isNotNull();
        // A leitura não escreve nada, por isso declara `readOnly`: o Hibernate
        // pode usar um cache de segunda nível e o PostgreSQL não gasta ciclos
        // em fsync à toa numa página que o utilizador vai recarregar.
        assertThat(historico.getAnnotation(org.springframework.transaction.annotation.Transactional.class)
                .readOnly()).isTrue();
    }
}
