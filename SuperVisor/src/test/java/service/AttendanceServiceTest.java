package service;

import domain.dto.AttendanceRequestDTO;
import domain.dto.WorkModalityScheduleDTO;
import domain.model.entities.User;
import domain.model.entities.WorkModalitySchedule;
import domain.model.enums.AttendanceStatus;
import domain.model.enums.TeamGroup;
import domain.model.enums.UserProfile;
import domain.model.enums.WorkModality;
import domain.repository.WorkModalityScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Presencialidade — AttendanceService")
class AttendanceServiceTest {

    private static final LocalDate DIA = LocalDate.of(2026, 3, 23);

    @Mock
    private WorkModalityScheduleRepository workModalityScheduleRepository;

    @Mock
    private CompensationService compensationService;

    @InjectMocks
    private AttendanceService attendanceService;

    private User joao;
    private WorkModalitySchedule celula;

    @BeforeEach
    void setUp() {
        joao = tests.support.TestFixtures.analyst();
        joao.setId(2L);

        celula = tests.support.TestFixtures.workModality(joao, DIA,
                WorkModality.PRESENCIAL, TeamGroup.EQUIPE_A);
        celula.setId(50L);

        // Leniente porque o caso «célula inexistente» substitui o findById e nunca
        // grava: os stubs comuns servem todos os outros testes da classe, e
        // exigir o uso de cada um deles aqui seria acoplar os casos entre si.
        lenient().when(workModalityScheduleRepository.findById(50L)).thenReturn(Optional.of(celula));
        lenient().when(workModalityScheduleRepository.save(any()))
                .thenAnswer(resposta -> resposta.getArgument(0));
    }

    private AttendanceRequestDTO pedido(AttendanceStatus estado, String observacao) {
        return new AttendanceRequestDTO(estado, observacao);
    }

    @Nested
    @DisplayName("A dívida que cada estado gera")
    class Divida {

        @Test
        @DisplayName("registar uma falta inteira soma um dia ao saldo do colaborador")
        void faltaInteiraSomaUmDia() {
            WorkModalityScheduleDTO dto = attendanceService.atualizar(
                    50L, pedido(AttendanceStatus.ABSENT_FULL, null));

            verify(compensationService).aplicar(eq(joao), eq(BigDecimal.ONE));
            assertThat(dto.attendanceStatus()).isEqualTo(AttendanceStatus.ABSENT_FULL);
        }

        @Test
        @DisplayName("falta de meio expediente soma meio dia")
        void faltaParcialSomaMeioDia() {
            attendanceService.atualizar(50L, pedido(AttendanceStatus.ABSENT_MORNING, null));

            verify(compensationService).aplicar(eq(joao),
                    eq(new BigDecimal("0.5")));
        }

        @Test
        @DisplayName("voltar a marcar presente devolve o dia: o delta é negativo")
        void reversaoDevolveODia() {
            celula.setAttendanceStatus(AttendanceStatus.ABSENT_FULL);

            attendanceService.atualizar(50L, pedido(AttendanceStatus.PRESENT, null));

            // A reversão não é um caso especial do serviço: é o mesmo delta de
            // sempre, com o sinal trocado. É isto que impede que uma falta
            // apagada continue a contar.
            verify(compensationService).aplicar(eq(joao), eq(new BigDecimal("-1")));
        }

        @Test
        @DisplayName("o estado que não muda não move o saldo")
        void estadoInalteradoNaoMoveSaldo() {
            attendanceService.atualizar(50L, pedido(AttendanceStatus.PRESENT, null));

            verify(compensationService).aplicar(eq(joao), eq(BigDecimal.ZERO));
        }

        @Test
        @DisplayName("uma célula inexistente dá erro, e o saldo não se mexe")
        void celulaInexistente() {
            when(workModalityScheduleRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> attendanceService.atualizar(
                    404L, pedido(AttendanceStatus.ABSENT_FULL, null)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Registo de presencialidade não encontrado.");

            verify(compensationService, never()).aplicar(any(), any());
        }
    }

    @Nested
    @DisplayName("A observação")
    class Observacao {

        @Test
        @DisplayName("uma observação com espaços é aparada")
        void observacaoAparada() {
            WorkModalityScheduleDTO dto = attendanceService.atualizar(
                    50L, pedido(AttendanceStatus.ABSENT_FULL, "  Atestado entregue  "));

            assertThat(dto.notes()).isEqualTo("Atestado entregue");
            assertThat(celula.getNotes()).isEqualTo("Atestado entregue");
        }

        @Test
        @DisplayName("uma observação vazia fica nula, para a página testar um só valor")
        void observacaoVaziaViraNula() {
            WorkModalityScheduleDTO dto = attendanceService.atualizar(
                    50L, pedido(AttendanceStatus.ABSENT_FULL, "   "));

            assertThat(dto.notes()).isNull();
            assertThat(celula.getNotes()).isNull();
        }

        @Test
        @DisplayName("a observação omitida no pedido mantém a que já está gravada")
        void observacaoAusenteMantemAExistente() {
            celula.setNotes("Observação antiga");

            WorkModalityScheduleDTO dto = attendanceService.atualizar(
                    50L, pedido(AttendanceStatus.PRESENT, null));

            assertThat(dto.notes()).isEqualTo("Observação antiga");
        }
    }
}
