package service;

import domain.dto.CreateScaleDTO;
import domain.dto.ScaleDeletionSummaryDTO;
import domain.model.entities.EditionScale;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.EditionStatus;
import domain.model.enums.ShiftType;
import domain.model.enums.UserProfile;
import domain.repository.EditionScaleRepository;
import domain.repository.ExchangeRequestRepository;
import domain.repository.ShiftSchedulingRepository;
import domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScaleService")
class ScaleServiceTest {

    @Mock
    private EditionScaleRepository editionScaleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ShiftSchedulingRepository shiftSchedulingRepository;

    @Mock
    private ExchangeRequestRepository exchangeRequestRepository;

    @InjectMocks
    private ScaleService scaleService;

    private User criador() {
        return new User(1L, "Administrador", "admin@teste.com", "123456", UserProfile.SUPERVISOR);
    }

    private CreateScaleDTO dto(Long criadoPorId) {
        return new CreateScaleDTO("Escala Outubro",
                LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 31), criadoPorId);
    }

    @Nested
    @DisplayName("createScale")
    class Create {

        @Test
        @DisplayName("cria a escala em rascunho e devolve a entidade persistida")
        void criaEmRascunho() {
            User creator = criador();
            when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
            when(editionScaleRepository.save(any(EditionScale.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            EditionScale resultado = scaleService.createScale(dto(1L));

            assertThat(resultado.getName()).isEqualTo("Escala Outubro");
            assertThat(resultado.getInitialDate()).isEqualTo(LocalDate.of(2025, 10, 1));
            assertThat(resultado.getEndDate()).isEqualTo(LocalDate.of(2025, 10, 31));
            assertThat(resultado.getStatus()).isEqualTo(EditionStatus.DRAFT);
            assertThat(resultado.getCreatedBy()).isSameAs(creator);
        }

        @Test
        @DisplayName("encaminha ao repositório exatamente a escala montada")
        void dadosEncaminhadosAoRepositorio() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(criador()));
            when(editionScaleRepository.save(any(EditionScale.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            scaleService.createScale(dto(1L));

            ArgumentCaptor<EditionScale> captor = ArgumentCaptor.forClass(EditionScale.class);
            verify(editionScaleRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(EditionStatus.DRAFT);
            assertThat(captor.getValue().getCreatedBy().getEmail()).isEqualTo("admin@teste.com");
        }

        @Test
        @DisplayName("devolve o que o repositório devolveu, já com o id gerado")
        void devolveEntidadeDoRepositorio() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(criador()));
            EditionScale persistida = new EditionScale(99L, "Escala Outubro",
                    LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 31),
                    EditionStatus.DRAFT, criador());
            when(editionScaleRepository.save(any(EditionScale.class))).thenReturn(persistida);

            assertThat(scaleService.createScale(dto(1L))).isSameAs(persistida);
        }

        @Test
        @DisplayName("criador inexistente resulta em erro e nada é salvo")
        void criadorInexistente() {
            when(userRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scaleService.createScale(dto(404L)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Usuário não encontrado");

            verify(editionScaleRepository, never()).save(any());
        }

        @Test
        @DisplayName("não valida que a data final é posterior à inicial")
        void naoValidaOrdemDasDatas() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(criador()));
            when(editionScaleRepository.save(any(EditionScale.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            EditionScale escala = scaleService.createScale(new CreateScaleDTO(
                    "Escala Inválida", LocalDate.of(2025, 10, 31), LocalDate.of(2025, 10, 1), 1L));

            assertThat(escala.getInitialDate()).isAfter(escala.getEndDate());
        }
    }

    @Nested
    @DisplayName("publishSchedule")
    class Publish {

        @Test
        @DisplayName("promove uma escala em rascunho para publicada")
        void rascunhoViraPublicada() {
            EditionScale escala = new EditionScale(1L, "Escala Outubro",
                    LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 31),
                    EditionStatus.DRAFT, criador());
            when(editionScaleRepository.findById(1L)).thenReturn(Optional.of(escala));

            scaleService.publishSchedule(1L);

            assertThat(escala.getStatus()).isEqualTo(EditionStatus.PUBLISHED);
            verify(editionScaleRepository).save(escala);
        }

        @Test
        @DisplayName("escala inexistente resulta em erro")
        void escalaInexistente() {
            when(editionScaleRepository.findById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scaleService.publishSchedule(42L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Edição de Escala não encontrada.");

            verify(editionScaleRepository, never()).save(any());
        }

        @Test
        @DisplayName("escala já publicada não pode ser publicada de novo")
        void jaPublicada() {
            EditionScale escala = new EditionScale(1L, "Escala Outubro", null, null,
                    EditionStatus.PUBLISHED, criador());
            when(editionScaleRepository.findById(1L)).thenReturn(Optional.of(escala));

            assertThatThrownBy(() -> scaleService.publishSchedule(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Apenas escalas em rascunho podem ser publicadas");

            verify(editionScaleRepository, never()).save(any());
        }

        @Test
        @DisplayName("escala concluída também não pode ser publicada")
        void jaConcluida() {
            EditionScale escala = new EditionScale(1L, "Escala Outubro", null, null,
                    EditionStatus.COMPLETED, criador());
            when(editionScaleRepository.findById(1L)).thenReturn(Optional.of(escala));

            assertThatThrownBy(() -> scaleService.publishSchedule(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Apenas escalas em rascunho podem ser publicadas");
        }
    }

    @Nested
    @DisplayName("listAll")
    class ListAll {

        @Test
        @DisplayName("devolve a lista do repositório")
        void devolveLista() {
            EditionScale a = new EditionScale();
            EditionScale b = new EditionScale();
            when(editionScaleRepository.findAll()).thenReturn(List.of(a, b));

            assertThat(scaleService.listAll()).containsExactly(a, b);
        }

        @Test
        @DisplayName("devolve lista vazia quando não há escalas")
        void listaVazia() {
            when(editionScaleRepository.findAll()).thenReturn(List.of());

            assertThat(scaleService.listAll()).isEmpty();
        }

        @Test
        @DisplayName("não escreve no banco")
        void somenteLeitura() {
            when(editionScaleRepository.findAll()).thenReturn(List.of());

            scaleService.listAll();

            verify(editionScaleRepository).findAll();
            verifyNoInteractions(userRepository);
        }
    }

    @Nested
    @DisplayName("Fronteiras transacionais")
    class Transacoes {

        @Test
        @DisplayName("createScale e publishSchedule são transacionais; listAll é somente leitura")
        void anotacoesTransacionais() throws NoSuchMethodException {
            Method create = ScaleService.class.getMethod("createScale", CreateScaleDTO.class);
            Method publish = ScaleService.class.getMethod("publishSchedule", Long.class);
            Method list = ScaleService.class.getMethod("listAll");

            assertThat(create.getAnnotation(Transactional.class)).isNotNull();
            assertThat(create.getAnnotation(Transactional.class).readOnly()).isFalse();
            assertThat(publish.getAnnotation(Transactional.class)).isNotNull();
            assertThat(list.getAnnotation(Transactional.class).readOnly()).isTrue();
        }
    }

    @Nested
    @DisplayName("Resumo da exclusão")
    class ResumoDaExclusao {

        private EditionScale escala(long id) {
            return new EditionScale(id, "Escala Outubro", null, null, EditionStatus.DRAFT, criador());
        }

        @Test
        @DisplayName("conta os turnos e os pedidos de troca que a exclusão leva")
        void contaOQueSai() {
            when(editionScaleRepository.findById(1L)).thenReturn(Optional.of(escala(1L)));
            when(shiftSchedulingRepository.idsPorEscala(1L)).thenReturn(List.of(10L, 11L, 12L));
            when(exchangeRequestRepository.contarDasAlocacoes(List.of(10L, 11L, 12L)))
                    .thenReturn(2L);

            ScaleDeletionSummaryDTO resumo = scaleService.resumoExclusao(1L);

            assertThat(resumo.alocacoes()).isEqualTo(3);
            assertThat(resumo.trocas()).isEqualTo(2);
        }

        @Test
        @DisplayName("uma escala sem turnos conta zero e não pergunta pelos pedidos: um IN vazio não é JPQL válido")
        void escalaSemTurnosNaoConsultaTrocas() {
            when(editionScaleRepository.findById(1L)).thenReturn(Optional.of(escala(1L)));
            when(shiftSchedulingRepository.idsPorEscala(1L)).thenReturn(List.of());

            ScaleDeletionSummaryDTO resumo = scaleService.resumoExclusao(1L);

            assertThat(resumo.alocacoes()).isZero();
            assertThat(resumo.trocas()).isZero();
            verify(exchangeRequestRepository, never()).contarDasAlocacoes(any());
        }

        @Test
        @DisplayName("uma escala inexistente dá erro, e não um relatório de zero que se leria como «pode apagar»")
        void escalaInexistenteDaErro() {
            when(editionScaleRepository.findById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scaleService.resumoExclusao(42L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Edição de Escala não encontrada.");
        }

        @Test
        @DisplayName("não escreve nada: o resumo é só leitura")
        void somenteLeitura() {
            when(editionScaleRepository.findById(1L)).thenReturn(Optional.of(escala(1L)));
            when(shiftSchedulingRepository.idsPorEscala(1L)).thenReturn(List.of());

            scaleService.resumoExclusao(1L);

            verify(editionScaleRepository, never()).delete(any());
            verify(shiftSchedulingRepository, never()).deleteAll(any());
        }
    }

    @Nested
    @DisplayName("Exclusão")
    class Exclusao {

        private EditionScale escala(long id) {
            return new EditionScale(id, "Escala Outubro", null, null, EditionStatus.DRAFT, criador());
        }

        private ShiftScheduling turno(long id, EditionScale escala) {
            ShiftScheduling alocacao = new ShiftScheduling();
            alocacao.setId(id);
            alocacao.setEditionScale(escala);
            alocacao.setShift(ShiftType.T1_SAB);
            return alocacao;
        }

        @Test
        @DisplayName("apaga os pedidos de troca antes dos turnos que eles referenciam")
        void pedidosCaemAntesDosTurnos() {
            EditionScale escala = escala(1L);
            ShiftScheduling turno = turno(10L, escala);
            when(editionScaleRepository.findById(1L)).thenReturn(Optional.of(escala));
            when(shiftSchedulingRepository.findByEditionScaleIdOrderByIdAsc(1L))
                    .thenReturn(List.of(turno));

            scaleService.apagar(1L);

            verify(exchangeRequestRepository).apagarDasAlocacoes(List.of(10L));
            InOrder ordem = inOrder(exchangeRequestRepository, shiftSchedulingRepository,
                    editionScaleRepository);
            ordem.verify(exchangeRequestRepository).apagarDasAlocacoes(List.of(10L));
            ordem.verify(shiftSchedulingRepository).deleteAll(List.of(turno));
            ordem.verify(editionScaleRepository).delete(escala);
        }

        @Test
        @DisplayName("uma escala vazia apaga-se sem tocar nos pedidos de troca")
        void escalaVaziaNaoTocaEmPedidos() {
            EditionScale escala = escala(1L);
            when(editionScaleRepository.findById(1L)).thenReturn(Optional.of(escala));
            when(shiftSchedulingRepository.findByEditionScaleIdOrderByIdAsc(1L)).thenReturn(List.of());

            scaleService.apagar(1L);

            verify(exchangeRequestRepository, never()).apagarDasAlocacoes(any());
            verify(shiftSchedulingRepository).deleteAll(List.of());
            verify(editionScaleRepository).delete(escala);
        }

        @Test
        @DisplayName("apaga a escala mesmo que já esteja publicada: não há estado que impeça a exclusão")
        void apagaEscalaPublicada() {
            EditionScale escala = new EditionScale(1L, "Escala Outubro", null, null,
                    EditionStatus.PUBLISHED, criador());
            when(editionScaleRepository.findById(1L)).thenReturn(Optional.of(escala));
            when(shiftSchedulingRepository.findByEditionScaleIdOrderByIdAsc(1L)).thenReturn(List.of());

            scaleService.apagar(1L);

            verify(editionScaleRepository).delete(escala);
        }

        @Test
        @DisplayName("escala inexistente dá erro e nada é apagado")
        void escalaInexistenteDaErro() {
            when(editionScaleRepository.findById(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> scaleService.apagar(42L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Edição de Escala não encontrada.");

            verify(editionScaleRepository, never()).delete(any());
            verify(shiftSchedulingRepository, never()).deleteAll(any());
        }
    }
}
