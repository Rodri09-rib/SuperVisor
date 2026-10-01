package controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import domain.dto.AllocationDTO;
import domain.dto.AllocationRequestDTO;
import domain.model.entities.User;
import domain.model.enums.AllocationStatus;
import domain.model.enums.AssignmentType;
import domain.model.enums.ShiftType;
import domain.model.enums.UserProfile;
import exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import security.ProfileAuthorization;
import service.AllocationService;
import tests.support.TestFixtures;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationController")
class AllocationControllerTest {

    private static final String SABADO = "S\u00e1bado";

    private static final String SEM_PERMISSAO =
            "Apenas o perfil SUPERVISOR pode executar esta opera\u00e7\u00e3o.";

    private static final String APLICACAO_INEXISTENTE =
            "Aloca\u00e7\u00e3o n\u00e3o encontrada.";

    @Mock
    private AllocationService allocationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AllocationController controller = new AllocationController();
        ReflectionTestUtils.setField(controller, "allocationService", allocationService);
        // A ProfileAuthorization real, e não um mock, para o teste cobrir a
        // ligação entre o perfil do principal e o 403 devolvido.
        ReflectionTestUtils.setField(controller, "profileAuthorization", new ProfileAuthorization());
        // Mesmo serializador do Spring Boot: horas e datas em ISO, não em array.
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        SecurityContextHolder.clearContext();
        autenticarComo(TestFixtures.supervisor());
    }

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(User user) {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private AllocationDTO alocacao() {
        return new AllocationDTO(42L, 10L, "Escala Outubro", 5L, "Joao", "joao@teste.com",
                ShiftType.T2_SAB, "T2", LocalTime.of(11, 0), LocalTime.of(15, 0), SABADO,
                List.of(AssignmentType.REDES_SOCIAIS), List.of("Redes Sociais"),
                LocalTime.of(11, 30), LocalTime.of(14, 30), true,
                LocalDate.of(2025, 10, 4), AllocationStatus.PENDING);
    }

    private static final String PEDIDO_JSON = """
            {"editionScaleId":10,"userId":5,"shift":"T1_SAB"}""";

    @Nested
    @DisplayName("GET /api/v1/allocations")
    class Listar {

        @Test
        @DisplayName("devolve 200 com a lista enriquecida")
        void devolveLista() throws Exception {
            when(allocationService.list(null)).thenReturn(List.of(alocacao()));

            mockMvc.perform(get("/api/v1/allocations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(42))
                    .andExpect(jsonPath("$[0].editionScaleName").value("Escala Outubro"))
                    .andExpect(jsonPath("$[0].userEmail").value("joao@teste.com"))
                    .andExpect(jsonPath("$[0].shift").value("T2_SAB"))
                    .andExpect(jsonPath("$[0].shiftAcronym").value("T2"))
                    .andExpect(jsonPath("$[0].shiftDayOfTheWeek").value(SABADO))
                    .andExpect(jsonPath("$[0].assignments[0]").value("REDES_SOCIAIS"))
                    .andExpect(jsonPath("$[0].assignmentLabels[0]").value("Redes Sociais"))
                    .andExpect(jsonPath("$[0].customSchedule").value(true))
                    .andExpect(jsonPath("$[0].specificDate").value("2025-10-04"));
        }

        @Test
        @DisplayName("encaminha o editionScaleId como filtro da escala")
        void encaminhaFiltroDaEscala() throws Exception {
            when(allocationService.list(10L)).thenReturn(List.of(alocacao()));

            mockMvc.perform(get("/api/v1/allocations").param("editionScaleId", "10"))
                    .andExpect(status().isOk());

            verify(allocationService).list(10L);
        }

        @Test
        @DisplayName("a leitura não exige perfil SUPERVISOR")
        void leituraAbertaAAnalistas() throws Exception {
            autenticarComo(TestFixtures.analyst());
            when(allocationService.list(null)).thenReturn(List.of(alocacao()));

            mockMvc.perform(get("/api/v1/allocations"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/allocations")
    class Criar {

        @Test
        @DisplayName("devolve 200 com a alocação gravada")
        void devolveAlocacaoGravada() throws Exception {
            when(allocationService.create(any())).thenReturn(alocacao());

            mockMvc.perform(post("/api/v1/allocations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"editionScaleId":10,"userId":5,"shift":"T2_SAB",
                                     "assignments":["REDES_SOCIAIS"],
                                     "customStartTime":"11:30:00","customEndTime":"14:30:00",
                                     "specificDate":"2025-10-04"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(42))
                    .andExpect(jsonPath("$.shift").value("T2_SAB"))
                    .andExpect(jsonPath("$.customStartTime").value("11:30:00"));
        }

        @Test
        @DisplayName("converte o corpo JSON no DTO do serviço, sem texto livre para turnos")
        void converteCorpoNoDto() throws Exception {
            when(allocationService.create(any())).thenReturn(alocacao());

            mockMvc.perform(post("/api/v1/allocations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"editionScaleId":10,"userId":5,"shift":"T5_SAB",
                                     "assignments":["REDES_SOCIAIS","CELULAR_MARINAS"],
                                     "customStartTime":"21:00","customEndTime":"00:00"}
                                    """))
                    .andExpect(status().isOk());

            ArgumentCaptor<AllocationRequestDTO> captor =
                    ArgumentCaptor.forClass(AllocationRequestDTO.class);
            verify(allocationService).create(captor.capture());

            AllocationRequestDTO dto = captor.getValue();
            assertThat(dto.editionScaleId()).isEqualTo(10L);
            assertThat(dto.userId()).isEqualTo(5L);
            assertThat(dto.shift()).isEqualTo(ShiftType.T5_SAB);
            assertThat(dto.assignments())
                    .containsExactly(AssignmentType.REDES_SOCIAIS, AssignmentType.CELULAR_MARINAS);
            assertThat(dto.customStartTime()).isEqualTo(LocalTime.of(21, 0));
            assertThat(dto.customEndTime()).isEqualTo(LocalTime.MIDNIGHT);
            assertThat(dto.specificDate()).isNull();
        }

        @Test
        @DisplayName("rejeita um turno desconhecido em vez de o aceitar como texto livre")
        void rejeitaTurnoDesconhecido() throws Exception {
            mockMvc.perform(post("/api/v1/allocations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"editionScaleId":10,"userId":5,"shift":"T9_INV"}
                                    """))
                    .andExpect(status().is4xxClientError());

            verify(allocationService, never()).create(any());
        }

        @Test
        @DisplayName("devolve 400 com a mensagem quando o serviço rejeita a alocação")
        void devolve400ComMensagem() throws Exception {
            String mensagem = "O usuário selecionado est\u00e1 inativo e n\u00e3o pode "
                    + "receber turnos.";
            when(allocationService.create(any())).thenThrow(new RuntimeException(mensagem));

            mockMvc.perform(post("/api/v1/allocations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(mensagem));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/allocations/{id}")
    class Atualizar {

        @Test
        @DisplayName("devolve 200 com a alocação atualizada")
        void devolveAlocacaoAtualizada() throws Exception {
            when(allocationService.update(eq(42L), any())).thenReturn(alocacao());

            mockMvc.perform(put("/api/v1/allocations/42")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"editionScaleId":10,"userId":5,"shift":"T4_SAB",
                                     "assignments":[],"specificDate":"2025-10-11"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(42))
                    .andExpect(jsonPath("$.shift").value("T2_SAB"));
        }

        @Test
        @DisplayName("encaminha o id do caminho para o serviço")
        void encaminhaIdDoCaminho() throws Exception {
            when(allocationService.update(anyLong(), any())).thenReturn(alocacao());

            mockMvc.perform(put("/api/v1/allocations/7")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO_JSON))
                    .andExpect(status().isOk());

            verify(allocationService).update(eq(7L), any());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/allocations/{id}")
    class Remover {

        @Test
        @DisplayName("devolve 204 sem conteúdo quando a alocação é removida")
        void devolve204() throws Exception {
            mockMvc.perform(delete("/api/v1/allocations/42"))
                    .andExpect(status().isNoContent());

            verify(allocationService).delete(42L);
        }

        @Test
        @DisplayName("devolve 400 com a mensagem quando a alocação não existe")
        void devolve400SeNaoExistir() throws Exception {
            doThrow(new RuntimeException(APLICACAO_INEXISTENTE))
                    .when(allocationService).delete(404L);

            mockMvc.perform(delete("/api/v1/allocations/404"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(APLICACAO_INEXISTENTE));
        }
    }

    @Nested
    @DisplayName("Autorizacao por perfil")
    class Autorizacao {

        @Test
        @DisplayName("POST com perfil ANALIST devolve 403 e não chega ao serviço")
        void postDeAnalistaNegado() throws Exception {
            autenticarComo(TestFixtures.analyst());

            mockMvc.perform(post("/api/v1/allocations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"))
                    .andExpect(jsonPath("$.message").value(SEM_PERMISSAO));

            verify(allocationService, never()).create(any());
        }

        @Test
        @DisplayName("PUT com perfil ANALIST devolve 403 e não chega ao serviço")
        void putDeAnalistaNegado() throws Exception {
            autenticarComo(TestFixtures.analyst());

            mockMvc.perform(put("/api/v1/allocations/42")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(SEM_PERMISSAO));

            verify(allocationService, never()).update(anyLong(), any());
        }

        @Test
        @DisplayName("DELETE com perfil ANALIST devolve 403 e não chega ao serviço")
        void deleteDeAnalistaNegado() throws Exception {
            autenticarComo(TestFixtures.analyst());

            mockMvc.perform(delete("/api/v1/allocations/42"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(SEM_PERMISSAO));

            verify(allocationService, never()).delete(anyLong());
        }

        @Test
        @DisplayName("um perfil ANALIST desativado também é recusado")
        void perfilInativoNegado() throws Exception {
            autenticarComo(TestFixtures.inactiveAnalyst());

            mockMvc.perform(post("/api/v1/allocations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("sem principal autenticado a escrita e negada, em vez de rebentar")
        void semPrincipalNegado() throws Exception {
            SecurityContextHolder.clearContext();

            mockMvc.perform(post("/api/v1/allocations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(PEDIDO_JSON))
                    .andExpect(status().isForbidden());

            verify(allocationService, never()).create(any());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/allocations/{id}/acceptance")
    class Responder {

        private void responderComo(String token, String estado) throws Exception {
            mockMvc.perform(patch("/api/v1/allocations/42/acceptance")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"" + estado + "\"}"));
        }

        @Test
        @DisplayName("devolve 200 com a alocação já com o novo estado")
        void devolveAlocacaoAtualizada() throws Exception {
            User ana = TestFixtures.analyst();
            ana.setId(5L);
            autenticarComo(ana);
            when(allocationService.responder(42L, AllocationStatus.ACCEPTED, 5L))
                    .thenReturn(alocacao());

            mockMvc.perform(patch("/api/v1/allocations/42/acceptance")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ACCEPTED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(42))
                    .andExpect(jsonPath("$.analystAcceptanceStatus").value("PENDING"));
        }

        @Test
        @DisplayName("encaminha o estado e o id de quem responde, para o serviço decidir")
        void encaminhaEstadoEUtilizador() throws Exception {
            User ana = TestFixtures.analyst();
            ana.setId(5L);
            autenticarComo(ana);
            when(allocationService.responder(anyLong(), any(), anyLong())).thenReturn(alocacao());

            responderComo("irrelevante", "REJECTED");

            verify(allocationService).responder(42L, AllocationStatus.REJECTED, 5L);
        }

        @Test
        @DisplayName("sem estado no corpo dá 400 e o serviço não é chamado")
        void semEstadoDa400() throws Exception {
            User ana = TestFixtures.analyst();
            ana.setId(5L);
            autenticarComo(ana);

            mockMvc.perform(patch("/api/v1/allocations/42/acceptance")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fields.status").exists());

            verify(allocationService, never()).responder(anyLong(), any(), anyLong());
        }

        @Test
        @DisplayName("um estado que não existe dá 400, em vez de falhar a converter")
        void estadoDesconhecidoDa400() throws Exception {
            User ana = TestFixtures.analyst();
            ana.setId(5L);
            autenticarComo(ana);

            mockMvc.perform(patch("/api/v1/allocations/42/acceptance")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"TALVEZ\"}"))
                    .andExpect(status().isBadRequest());

            verify(allocationService, never()).responder(anyLong(), any(), anyLong());
        }

        @Test
        @DisplayName("sem principal autenticado o id chega a null ao serviço, que é quem nega")
        void semPrincipalEncaminhaIdNulo() throws Exception {
            SecurityContextHolder.clearContext();
            when(allocationService.responder(42L, AllocationStatus.ACCEPTED, null))
                    .thenThrow(new AccessDeniedException(
                            "Apenas a pessoa a quem o turno foi escalado, ou a supervisão, pode responder."));

            mockMvc.perform(patch("/api/v1/allocations/42/acceptance")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ACCEPTED\"}"))
                    .andExpect(status().isForbidden());

            verify(allocationService).responder(42L, AllocationStatus.ACCEPTED, null);
        }

        @Test
        @DisplayName("um ANALIST não é barrado pela rota: quem responde é decidido no serviço")
        void analistaDa403QuandoOMServicoRecusa() throws Exception {
            User bruno = TestFixtures.user("Bruno", "bruno@teste.com", UserProfile.ANALIST);
            bruno.setId(6L);
            autenticarComo(bruno);
            when(allocationService.responder(42L, AllocationStatus.ACCEPTED, 6L))
                    .thenThrow(new AccessDeniedException(
                            "Apenas a pessoa a quem o turno foi escalado, ou a supervisão, pode responder."));

            mockMvc.perform(patch("/api/v1/allocations/42/acceptance")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"ACCEPTED\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                            "Apenas a pessoa a quem o turno foi escalado")));
        }
    }
}
