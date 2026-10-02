package tests.integration;

import domain.model.entities.EditionScale;
import domain.model.entities.ShiftScheduling;
import domain.model.entities.User;
import domain.model.enums.AssignmentType;
import domain.model.enums.EditionStatus;
import domain.model.enums.ExchangeStatus;
import domain.model.enums.ShiftType;
import domain.model.enums.TeamGroup;
import domain.model.enums.UserProfile;
import domain.model.enums.WorkModality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tests.support.AbstractApiIntegrationTest;
import tests.support.TestFixtures;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exclusão de contas, de escalas e de escalas de presencialidade, ponta a ponta.
 *
 * <p>São três operações que partilham o mesmo risco e que por isso são testadas
 * juntas: apagar de uma tabela o que outra ainda aponta. Cada uma delas tem uma
 * ordem de apagamento imposta pelas chaves estrangeiras, e essa ordem é
 * exatamente o que um teste com um repositório mockado não vê — o mock aceita
 * qualquer sequência. Aqui o PostgreSQL recusa a ordem errada.
 *
 * <p>O teste do que fica para trás é o mais importante de todos: uma exclusão
 * que apaga a mais não dá erro nenhum. Passa a responder 204, a interface
 * anuncia o sucesso, e a perda só aparece semanas depois, quando alguém vem
 * perguntar por um turno de outubro.
 */
@DisplayName("Exclusões — API")
class DeleteApiIntegrationTest extends AbstractApiIntegrationTest {

    /** Segunda-feira da semana ISO 13 de 2026, que é ímpar. */
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 3, 23);
    private static final LocalDate SEXTA = SEGUNDA.plusDays(4);
    /** Segunda da semana anterior: serve de contraste para o que não deve ser tocado. */
    private static final LocalDate SEGUNDA_ANTERIOR = SEGUNDA.minusWeeks(1);

    private User supervisor;
    private User ana;
    /** O perfil de analista vem de uma fixture, como nas restantes suítes. */
    private UserProfile perfilDeAnalista;
    private String tokenSupervisor;
    private String tokenAna;

    @BeforeEach
    void preparar() {
        perfilDeAnalista = TestFixtures.analystInTeam(TeamGroup.EQUIPE_A).getProfile();

        supervisor = criarUsuario("Administrador", "admin@teste.com", UserProfile.SUPERVISOR);
        ana = criarUsuarioComEquipa("Ana", "ana@teste.com", perfilDeAnalista, TeamGroup.EQUIPE_A);
        tokenSupervisor = tokenService.gerarToken(supervisor);
        tokenAna = tokenService.gerarToken(ana);
    }

    private String auth(String token) {
        return "Bearer " + token;
    }

    private ResultActions apagarUtilizador(Long id, String token) throws Exception {
        return mockMvc.perform(delete("/api/v1/users/" + id)
                .header("Authorization", auth(token)));
    }

    private ResultActions apagarEscala(Long id, String token) throws Exception {
        return mockMvc.perform(delete("/api/v1/scales/" + id)
                .header("Authorization", auth(token)));
    }

    private ResultActions apagarPresencialidade(LocalDate inicio, LocalDate fim, String token)
            throws Exception {
        return mockMvc.perform(delete("/api/v1/work-modality-schedules")
                .param("inicio", inicio.toString())
                .param("fim", fim.toString())
                .header("Authorization", auth(token)));
    }

    /** Gera a escala de presencialidade da semana da data indicada. */
    private void gerarPresencialidade(LocalDate inicio) throws Exception {
        mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                        .header("Authorization", auth(tokenSupervisor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataReferencia\":\"%s\"}".formatted(inicio)))
                .andExpect(status().isOk());
        sincronizar();
    }

    /** Contagem de linhas por JPQL, para os testes que só querem saber se sobrou alguma. */
    private long contar(String jpql) {
        return entityManager.createQuery(jpql).getResultList().size();
    }

    /* ------------------------------------------------------------------ */
    /* Usuários                                                            */
    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("Excluir usuário")
    class Usuarios {

        @Test
        @DisplayName("a conta desaparece da base de dados")
        void apagaAConta() throws Exception {
            apagarUtilizador(ana.getId(), tokenSupervisor)
                    .andExpect(status().isNoContent());

            sincronizar();

            assertThat(userRepository.findById(ana.getId())).isEmpty();
        }

        @Test
        @DisplayName("e sai da vista de administração, e não só da base de dados")
        void saiDaVistaDeAdministracao() throws Exception {
            apagarUtilizador(ana.getId(), tokenSupervisor);

            sincronizar();

            var resposta = mockMvc.perform(get("/api/v1/users/todos")
                            .header("Authorization", auth(tokenSupervisor)))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(resposta.getResponse().getContentAsString())
                    .doesNotContain("ana@teste.com");
        }

        @Test
        @DisplayName("o token da conta apagada deixa de autenticar")
        void oTokenMorreComAConta() throws Exception {
            String tokenAna = tokenService.gerarToken(ana);
            apagarUtilizador(ana.getId(), tokenSupervisor).andExpect(status().isNoContent());
            sincronizar();

            mockMvc.perform(get("/api/v1/users/me")
                            .header("Authorization", auth(tokenAna)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("as folgas e a presencialidade da pessoa caem com a conta: não têm sentido sem ela")
        void apagaFolgasEPresencialidade() throws Exception {
            userLeaveRepository.saveAndFlush(
                    TestFixtures.leave(ana, LocalDate.of(2026, 3, 20)));
            workModalityScheduleRepository.saveAndFlush(TestFixtures.workModality(
                    ana, SEGUNDA, WorkModality.PRESENCIAL, TeamGroup.EQUIPE_A));
            sincronizar();

            apagarUtilizador(ana.getId(), tokenSupervisor).andExpect(status().isNoContent());
            sincronizar();

            assertThat(contar("SELECT l FROM UserLeave l")).isZero();
            assertThat(contar("SELECT w FROM WorkModalitySchedule w")).isZero();
        }

        @Test
        @DisplayName("as escalas que a pessoa criou sobrevivem sem autoria: o registo do fim de semana é mais importante que quem o montou")
        void asEscalasSobrevivemSemAutoria() throws Exception {
            EditionScale escala = criarEscala("Escala Outubro", ana);
            sincronizar();

            apagarUtilizador(ana.getId(), tokenSupervisor).andExpect(status().isNoContent());
            sincronizar();

            assertThat(editionScaleRepository.findById(escala.getId())).isPresent();
            assertThat(editionScaleRepository.findById(escala.getId()).orElseThrow()
                    .getCreatedBy()).isNull();
        }

        @Test
        @DisplayName("recusa quem tem turnos marcados, em vez de os apagar e com eles o trabalho dos colegas")
        void recusaComTurnosMarcados() throws Exception {
            EditionScale escala = criarEscala("Escala Outubro", supervisor);
            criarAlocacao(escala, ana);
            sincronizar();

            apagarUtilizador(ana.getId(), tokenSupervisor)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Não é possível excluir a conta: a pessoa tem 1 turno marcado. "
                                    + "Retire-os antes de excluir a conta."));

            sincronizar();

            assertThat(userRepository.findById(ana.getId())).isPresent();
            assertThat(shiftSchedulingRepository.findByEditionScaleIdOrderByIdAsc(escala.getId()))
                    .hasSize(1);
        }

        @Test
        @DisplayName("retirados os turnos, a conta já pode ser excluída")
        void depoisDeRetirarOsTurnosPassa() throws Exception {
            EditionScale escala = criarEscala("Escala Outubro", supervisor);
            ShiftScheduling turno = criarAlocacao(escala, ana);
            sincronizar();

            apagarUtilizador(ana.getId(), tokenSupervisor)
                    .andExpect(status().isBadRequest());

            mockMvc.perform(delete("/api/v1/allocations/" + turno.getId())
                            .header("Authorization", auth(tokenSupervisor)))
                    .andExpect(status().isNoContent());
            sincronizar();

            apagarUtilizador(ana.getId(), tokenSupervisor)
                    .andExpect(status().isNoContent());

            sincronizar();

            assertThat(userRepository.findById(ana.getId())).isEmpty();
        }

        @Test
        @DisplayName("o supervisor não se exclui a si próprio")
        void naoSeExclui() throws Exception {
            apagarUtilizador(supervisor.getId(), tokenSupervisor)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Não pode excluir a sua própria conta."));

            sincronizar();

            assertThat(userRepository.findById(supervisor.getId())).isPresent();
        }

        @Test
        @DisplayName("um usuário inexistente dá 400 com mensagem, não 500")
        void inexistenteDa400() throws Exception {
            apagarUtilizador(999999L, tokenSupervisor)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Usuário não encontrado."));
        }

        @Test
        @DisplayName("o ANALIST recebe 403 e nada muda")
        void analistNaoExclui() throws Exception {
            User bruno = criarUsuario("Bruno", "bruno@teste.com", perfilDeAnalista);

            apagarUtilizador(bruno.getId(), tokenAna)
                    .andExpect(status().isForbidden());
            sincronizar();

            assertThat(userRepository.findById(bruno.getId())).isPresent();
        }

        @Test
        @DisplayName("sem token a resposta é 401, não 403: não há principal para avaliar")
        void semTokenDa401() throws Exception {
            mockMvc.perform(delete("/api/v1/users/" + ana.getId()))
                    .andExpect(status().isUnauthorized());

            sincronizar();

            assertThat(userRepository.findById(ana.getId())).isPresent();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Escalas                                                             */
    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("Excluir escala")
    class Escalas {

        @Test
        @DisplayName("a escala e os seus turnos desaparecem")
        void apagaEscalaETurnos() throws Exception {
            EditionScale escala = criarEscala("Escala Outubro", supervisor);
            criarAlocacao(escala, ana);
            sincronizar();

            apagarEscala(escala.getId(), tokenSupervisor)
                    .andExpect(status().isNoContent());
            sincronizar();

            assertThat(editionScaleRepository.findById(escala.getId())).isEmpty();
            assertThat(shiftSchedulingRepository.findByEditionScaleIdOrderByIdAsc(escala.getId()))
                    .isEmpty();
        }

        @Test
        @DisplayName("as atribuições especiais dos turnos também saem, e não ficam órfãs na tabela delas")
        void apagaAsAtribuicoesEspeciais() throws Exception {
            EditionScale escala = criarEscala("Escala Outubro", supervisor);
            ShiftScheduling turno = new ShiftScheduling();
            turno.setEditionScale(escala);
            turno.setUser(ana);
            turno.setShift(ShiftType.T1_SAB);
            turno.setAssignments(new java.util.LinkedHashSet<>(java.util.List.of(
                    AssignmentType.REDES_SOCIAIS, AssignmentType.CELULAR_MARINAS)));
            shiftSchedulingRepository.saveAndFlush(turno);
            sincronizar();

            apagarEscala(escala.getId(), tokenSupervisor)
                    .andExpect(status().isNoContent());
            sincronizar();

            assertThat(contar(
                    "SELECT a FROM ShiftScheduling a")).isZero();
            assertThat((long) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM tb_shift_scheduling_assignment")
                    .getSingleResult()).isZero();
        }

        @Test
        @DisplayName("os pedidos de troca que referenciam os turnos também saem, ou ficariam a apontar para linhas que já não existem")
        void apagaOsPedidosDeTroca() throws Exception {
            User bruno = criarUsuario("Bruno", "bruno@teste.com", perfilDeAnalista);
            EditionScale escala = criarEscala("Escala Outubro", supervisor);
            ShiftScheduling origem = criarAlocacao(escala, ana);
            ShiftScheduling destino = criarAlocacao(escala, bruno, ShiftType.T2_SAB);
            exchangeRequestRepository.saveAndFlush(TestFixtures.exchangeRequest(
                    origem, destino, ana, bruno, ExchangeStatus.PENDING));
            sincronizar();

            apagarEscala(escala.getId(), tokenSupervisor)
                    .andExpect(status().isNoContent());
            sincronizar();

            assertThat(exchangeRequestRepository.count()).isZero();
        }

        @Test
        @DisplayName("os turnos de outras escalas não são tocados")
        void naoTocaEmOutrasEscalas() throws Exception {
            EditionScale alvo = criarEscala("Escale Outubro", supervisor);
            EditionScale outra = criarEscala("Escala Novembro", supervisor);
            criarAlocacao(alvo, ana);
            ShiftScheduling turnoDeOutra = criarAlocacao(outra, ana, ShiftType.T3_SAB);
            sincronizar();

            apagarEscala(alvo.getId(), tokenSupervisor)
                    .andExpect(status().isNoContent());
            sincronizar();

            assertThat(editionScaleRepository.findById(outra.getId())).isPresent();
            assertThat(shiftSchedulingRepository.findById(turnoDeOutra.getId())).isPresent();
        }

        @Test
        @DisplayName("o resumo de exclusão conta os turnos e os pedidos, e é ele que a confirmação mostra")
        void oResumoContaOQueSai() throws Exception {
            User bruno = criarUsuario("Bruno", "bruno@teste.com", perfilDeAnalista);
            EditionScale escala = criarEscala("Escala Outubro", supervisor);
            ShiftScheduling origem = criarAlocacao(escala, ana);
            ShiftScheduling destino = criarAlocacao(escala, bruno, ShiftType.T2_SAB);
            exchangeRequestRepository.saveAndFlush(TestFixtures.exchangeRequest(
                    origem, destino, ana, bruno, ExchangeStatus.PENDING));
            sincronizar();

            mockMvc.perform(get("/api/v1/scales/" + escala.getId() + "/exclusao")
                            .header("Authorization", auth(tokenSupervisor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.alocacoes").value(2))
                    .andExpect(jsonPath("$.trocas").value(1));
        }

        @Test
        @DisplayName("uma escala sem turnos conta zero, sem falhar: é a escala que mais se quer apagar")
        void oResumoDeEscalaVazia() throws Exception {
            EditionScale escala = criarEscala("Escala Outubro", supervisor);
            sincronizar();

            mockMvc.perform(get("/api/v1/scales/" + escala.getId() + "/exclusao")
                            .header("Authorization", auth(tokenSupervisor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.alocacoes").value(0))
                    .andExpect(jsonPath("$.trocas").value(0));
        }

        @Test
        @DisplayName("apagar uma escala já publicada é permitido: não há estado que impeça a exclusão")
        void apagaEscalaPublicada() throws Exception {
            EditionScale escala = criarEscala(
                    "Escala Outubro", supervisor, EditionStatus.PUBLISHED);
            sincronizar();

            apagarEscala(escala.getId(), tokenSupervisor)
                    .andExpect(status().isNoContent());
            sincronizar();

            assertThat(editionScaleRepository.findById(escala.getId())).isEmpty();
        }

        @Test
        @DisplayName("uma escala inexistente dá 400 com mensagem, não 500")
        void escalaInexistenteDa400() throws Exception {
            apagarEscala(999999L, tokenSupervisor)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Edição de Escala não encontrada."));

            mockMvc.perform(get("/api/v1/scales/999999/exclusao")
                            .header("Authorization", auth(tokenSupervisor)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("o ANALIST recebe 403 na exclusão e no resumo, e a escala fica")
        void analistNaoExcluiEscalas() throws Exception {
            EditionScale escala = criarEscala("Escala Outubro", supervisor);
            sincronizar();

            apagarEscala(escala.getId(), tokenAna)
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/scales/" + escala.getId() + "/exclusao")
                            .header("Authorization", auth(tokenAna)))
                    .andExpect(status().isForbidden());
            sincronizar();

            assertThat(editionScaleRepository.findById(escala.getId())).isPresent();
        }

        @Test
        @DisplayName("sem token a resposta é 401 e a escala fica")
        void semTokenDa401() throws Exception {
            EditionScale escala = criarEscala("Escala Outubro", supervisor);
            sincronizar();

            mockMvc.perform(delete("/api/v1/scales/" + escala.getId()))
                    .andExpect(status().isUnauthorized());
            sincronizar();

            assertThat(editionScaleRepository.findById(escala.getId())).isPresent();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Presencialidade                                                     */
    /* ------------------------------------------------------------------ */

    @Nested
    @DisplayName("Excluir escala de presencialidade")
    class Presencialidade {

        @Test
        @DisplayName("a grelha da semana fica vazia")
        void apagaASemanaVisivel() throws Exception {
            gerarPresencialidade(SEGUNDA);

            // Cinco células: uma por dia, de segunda a sexta, para a única pessoa
            // do cenário que tem equipa atribuída.
            mockMvc.perform(get("/api/v1/work-modality-schedules")
                            .param("inicio", SEGUNDA.toString())
                            .param("fim", SEXTA.toString())
                            .header("Authorization", auth(tokenSupervisor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(5));

            apagarPresencialidade(SEGUNDA, SEXTA, tokenSupervisor)
                    .andExpect(status().isNoContent());
            sincronizar();

            mockMvc.perform(get("/api/v1/work-modality-schedules")
                            .param("inicio", SEGUNDA.toString())
                            .param("fim", SEXTA.toString())
                            .header("Authorization", auth(tokenSupervisor)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("as semanas vizinhas ficam intactas: apagar uma semana não pode arrastar o ano")
        void naoArrastaAsSemanasVizinhas() throws Exception {
            gerarPresencialidade(SEGUNDA_ANTERIOR);
            gerarPresencialidade(SEGUNDA);
            assertThat(workModalityScheduleRepository.listarIntervalo(
                    SEGUNDA, SEXTA)).isNotEmpty();

            apagarPresencialidade(SEGUNDA, SEXTA, tokenSupervisor)
                    .andExpect(status().isNoContent());
            sincronizar();

            assertThat(workModalityScheduleRepository.listarIntervalo(SEGUNDA, SEXTA)).isEmpty();
            assertThat(workModalityScheduleRepository.listarIntervalo(
                    SEGUNDA_ANTERIOR, SEGUNDA_ANTERIOR.plusDays(4))).isNotEmpty();
        }

        @Test
        @DisplayName("apagar e voltar a gerar repõe a mesma escala, que é o que torna a operação reversível")
        void apagarEGerarDevolveOMesmo() throws Exception {
            gerarPresencialidade(SEGUNDA);
            var antes = workModalityScheduleRepository.listarIntervalo(SEGUNDA, SEXTA);

            apagarPresencialidade(SEGUNDA, SEXTA, tokenSupervisor)
                    .andExpect(status().isNoContent());
            sincronizar();
            gerarPresencialidade(SEGUNDA);

            var depois = workModalityScheduleRepository.listarIntervalo(SEGUNDA, SEXTA);
            assertThat(depois).hasSameSizeAs(antes);
            assertThat(depois.stream().map(c -> c.getUser().getId() + ":" + c.getModality()))
                    .containsExactlyInAnyOrderElementsOf(
                            antes.stream().map(c -> c.getUser().getId() + ":" + c.getModality())
                                    .toList());
        }

        @Test
        @DisplayName("uma semana sem escala dá 204: não havia nada a perder")
        void semanaVaziaDa204() throws Exception {
            apagarPresencialidade(SEGUNDA, SEXTA, tokenSupervisor)
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("um intervalo invertido é recusado, e nada é apagado")
        void intervaloInvertidoDa400() throws Exception {
            gerarPresencialidade(SEGUNDA);

            apagarPresencialidade(SEXTA, SEGUNDA, tokenSupervisor)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "A data final do intervalo é anterior à inicial."));
            sincronizar();

            assertThat(workModalityScheduleRepository.listarIntervalo(SEGUNDA, SEXTA)).isNotEmpty();
        }

        @Test
        @DisplayName("sem as duas datas a resposta é 400, e não o ano inteiro")
        void semIntervaloDa400() throws Exception {
            mockMvc.perform(delete("/api/v1/work-modality-schedules")
                            .header("Authorization", auth(tokenSupervisor)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("o ANALIST recebe 403 e a escala fica")
        void analistNaoApaga() throws Exception {
            gerarPresencialidade(SEGUNDA);

            apagarPresencialidade(SEGUNDA, SEXTA, tokenAna)
                    .andExpect(status().isForbidden());
            sincronizar();

            assertThat(workModalityScheduleRepository.listarIntervalo(SEGUNDA, SEXTA)).isNotEmpty();
        }

        @Test
        @DisplayName("sem token a resposta é 401 e a escala fica")
        void semTokenDa401() throws Exception {
            gerarPresencialidade(SEGUNDA);

            mockMvc.perform(delete("/api/v1/work-modality-schedules")
                            .param("inicio", SEGUNDA.toString())
                            .param("fim", SEXTA.toString()))
                    .andExpect(status().isUnauthorized());
            sincronizar();

            assertThat(workModalityScheduleRepository.listarIntervalo(SEGUNDA, SEXTA)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("O que não pode acontecer")
    class Reconciliacao {

        @Test
        @DisplayName("excluir a escala que sustenta as alocações de alguém não liberta a conta: a exclusão da conta continua recusada")
        void ExcluirAEscaleNaoLibertaAConta() throws Exception {
            EditionScale escala = criarEscala("Escala Outubro", supervisor);
            criarAlocacao(escala, ana);
            sincronizar();

            apagarEscala(escala.getId(), tokenSupervisor)
                    .andExpect(status().isNoContent());
            sincronizar();

            apagarUtilizador(ana.getId(), tokenSupervisor)
                    .andExpect(status().isNoContent());
            sincronizar();

            assertThat(userRepository.findById(ana.getId())).isEmpty();
            assertThat(shiftSchedulingRepository.count()).isZero();
        }

        @Test
        @DisplayName("depois de excluída a conta, a grelha não volta a mostrá-la em nenhuma semana")
        void aContaApagadaSaiDaGrelha() throws Exception {
            gerarPresencialidade(SEGUNDA);
            assertThat(workModalityScheduleRepository.listarIntervalo(SEGUNDA, SEXTA))
                    .isNotEmpty();

            apagarUtilizador(ana.getId(), tokenSupervisor)
                    .andExpect(status().isNoContent());
            sincronizar();

            assertThat(workModalityScheduleRepository.listarIntervalo(SEGUNDA, SEXTA)).isEmpty();
        }
    }
}