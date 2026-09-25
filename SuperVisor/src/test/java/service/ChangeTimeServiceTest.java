package service;

import domain.model.entities.EditionScale;
import domain.model.entities.ExchangeRequest;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
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

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private EditionScale escala;
    private ShiftScheduling alocacaoAdmin;
    private ShiftScheduling alocacaoJoao;

    @BeforeEach
    void setUp() {
        admin = new User(1L, "Administrador", "admin@teste.com", "123456", UserProfile.SUPERVISOR);
        joao = new User(2L, "João", "joao@teste.com", "123456", UserProfile.ANALIST);

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

            changeTimeService.requestExchange(10L, 11L, 2L);

            ArgumentCaptor<ExchangeRequest> captor = ArgumentCaptor.forClass(ExchangeRequest.class);
            verify(exchangeRequestRepository).save(captor.capture());
            ExchangeRequest request = captor.getValue();

            assertThat(request.getSourceAllocation()).isSameAs(alocacaoAdmin);
            assertThat(request.getDestinationAllocation()).isSameAs(alocacaoJoao);
            assertThat(request.getRequestingUser()).isSameAs(joao);
            assertThat(request.getStatus()).isEqualTo("PENDING");
            assertThat(request.getCreationDate()).isNotNull();
        }

        @Test
        @DisplayName("a data de criação é preenchida com o instante atual")
        void dataDeCriacaoPreenchida() {
            when(shiftSchedulingRepository.findById(10L)).thenReturn(Optional.of(alocacaoAdmin));
            when(shiftSchedulingRepository.findById(11L)).thenReturn(Optional.of(alocacaoJoao));
            when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

            var antes = java.time.OffsetDateTime.now().minusSeconds(1);
            changeTimeService.requestExchange(10L, 11L, 1L);
            var depois = java.time.OffsetDateTime.now().plusSeconds(1);

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

        @Test
        @DisplayName("não exige que o requisitante seja o dono da alocação de origem")
        void naoValidaPosseDaAlocacao() {
            when(shiftSchedulingRepository.findById(10L)).thenReturn(Optional.of(alocacaoAdmin));
            when(shiftSchedulingRepository.findById(11L)).thenReturn(Optional.of(alocacaoJoao));
            when(userRepository.findById(2L)).thenReturn(Optional.of(joao));

            changeTimeService.requestExchange(10L, 11L, 2L);

            verify(exchangeRequestRepository).save(any(ExchangeRequest.class));
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
            request.setStatus("PENDING");
            return request;
        }

        @Test
        @DisplayName("troca os utilizadores entre as duas alocações")
        void trocaUtilizadores() {
            when(exchangeRequestRepository.findById(100L)).thenReturn(Optional.of(requestPendente()));

            changeTimeService.respondExchange(100L, true);

            assertThat(alocacaoAdmin.getUser()).isSameAs(joao);
            assertThat(alocacaoJoao.getUser()).isSameAs(admin);
        }

        @Test
        @DisplayName("persiste as duas alocações e a solicitação como ACCEPTED")
        void persisteTudoComoAceito() {
            ExchangeRequest request = requestPendente();
            when(exchangeRequestRepository.findById(100L)).thenReturn(Optional.of(request));

            changeTimeService.respondExchange(100L, true);

            verify(shiftSchedulingRepository).save(alocacaoAdmin);
            verify(shiftSchedulingRepository).save(alocacaoJoao);
            verify(exchangeRequestRepository).save(request);
            assertThat(request.getStatus()).isEqualTo("ACCEPTED");
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
            request.setStatus("PENDING");
            return request;
        }

        @Test
        @DisplayName("marca como REJECTED sem tocar nas alocações")
        void marcaRejeitada() {
            ExchangeRequest request = requestPendente();
            when(exchangeRequestRepository.findById(100L)).thenReturn(Optional.of(request));

            changeTimeService.respondExchange(100L, false);

            assertThat(request.getStatus()).isEqualTo("REJECTED");
            verify(shiftSchedulingRepository, never()).save(any());
            verify(exchangeRequestRepository).save(request);
        }

        @Test
        @DisplayName("os utilizadores das alocações permanecem os mesmos")
        void alocacoesIntactas() {
            when(exchangeRequestRepository.findById(100L)).thenReturn(Optional.of(requestPendente()));

            changeTimeService.respondExchange(100L, false);

            assertThat(alocacaoAdmin.getUser()).isSameAs(admin);
            assertThat(alocacaoJoao.getUser()).isSameAs(joao);
        }
    }

    @Nested
    @DisplayName("respondExchange — guardas")
    class Guardas {

        @Test
        @DisplayName("solicitação inexistente resulta em erro")
        void solicitacaoInexistente() {
            when(exchangeRequestRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> changeTimeService.respondExchange(404L, true))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Solicitação não encontrada.");
        }

        @Test
        @DisplayName("já respondida não pode ser respondida outra vez")
        void jaRespondida() {
            ExchangeRequest respondida = new ExchangeRequest();
            respondida.setId(100L);
            respondida.setStatus("ACCEPTED");
            when(exchangeRequestRepository.findById(100L)).thenReturn(Optional.of(respondida));

            assertThatThrownBy(() -> changeTimeService.respondExchange(100L, true))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Esta solicitação já foi respondida.");

            verify(shiftSchedulingRepository, never()).save(any());
        }

        @Test
        @DisplayName("recusada também não pode ser respondida outra vez")
        void jaRecusada() {
            ExchangeRequest recusada = new ExchangeRequest();
            recusada.setId(100L);
            recusada.setStatus("REJECTED");
            when(exchangeRequestRepository.findById(100L)).thenReturn(Optional.of(recusada));

            assertThatThrownBy(() -> changeTimeService.respondExchange(100L, false))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Esta solicitação já foi respondida.");
        }

        @Test
        @DisplayName("o estado PENDENTE do valor default da entidade não é reconhecido como pendente")
        void estadoPadraoDaEntidadeNaoEhReconhecido() {
            ExchangeRequest comDefault = new ExchangeRequest();
            comDefault.setId(101L);
            assertThat(comDefault.getStatus()).isEqualTo("PENDENTE");
            when(exchangeRequestRepository.findById(101L)).thenReturn(Optional.of(comDefault));

            assertThatThrownBy(() -> changeTimeService.respondExchange(101L, true))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Esta solicitação já foi respondida.");
        }
    }

    @Test
    @DisplayName("requestExchange e respondExchange são transacionais")
    void anotacoesTransacionais() throws NoSuchMethodException {
        Method request = ChangeTimeService.class.getMethod(
                "requestExchange", Long.class, Long.class, Long.class);
        Method respond = ChangeTimeService.class.getMethod("respondExchange", Long.class, boolean.class);

        assertThat(request.getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                .isNotNull();
        assertThat(respond.getAnnotation(org.springframework.transaction.annotation.Transactional.class))
                .isNotNull();
    }
}
