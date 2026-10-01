package tests.integration;

import domain.model.entities.User;
import domain.model.entities.WorkModalitySchedule;
import domain.model.enums.TeamGroup;
import domain.model.enums.UserProfile;
import domain.model.enums.WorkModality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tests.support.AbstractApiIntegrationTest;
import tests.support.TestFixtures;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Escala de presencialidade contra a base de dados a série.
 *
 * <p>O que estes testes fixam é a alternância tal como a base a devolve, não
 * tal como o serviço a calcula. Um teste de unidade com um repositório mockado
 * passa a não ser que a aritmética do número ISO esteja certa, que é o ponto
 * donde tudo sai; aqui a paridade vem do número de semana que o PostgreSQL e o
 * Hibernate concordam, e a consulta que traz as células para a grelha é a
 * mesma que o frontend vai usar.
 */
@DisplayName("Work modality — API")
class WorkModalityApiIntegrationTest extends AbstractApiIntegrationTest {

    /** Segunda-feira da semana ISO 13 de 2026, que é ímpar. */
    private static final LocalDate SEGUNDA_IMPAR = LocalDate.of(2026, 3, 23);
    /** Segunda-feira da semana ISO 12 de 2026, que é par. */
    private static final LocalDate SEGUNDA_PAR = LocalDate.of(2026, 3, 16);

    private UserProfile perfilDeAnalista;

    private User supervisor;
    private User ana;
    private User bruno;
    private User semEquipa;
    private User inativo;

    private String tokenAdmin;
    private String tokenAna;

    @BeforeEach
    void prepararCenario() {
        // O perfil é lido de uma fixture em vez de escrito à mão: é o mesmo
        // caminho que os restantes testes usam.
        perfilDeAnalista = TestFixtures.analystInTeam(TeamGroup.EQUIPE_A).getProfile();

        ana = criar("Ana", "ana@teste.com", TeamGroup.EQUIPE_A, true);
        bruno = criar("Bruno", "bruno@teste.com", TeamGroup.EQUIPE_B, true);
        semEquipa = criar("Carla", "carla@teste.com", null, true);
        inativo = criar("Hugo", "hugo@teste.com", TeamGroup.EQUIPE_A, false);
        supervisor = criarSupervisor();

        tokenAdmin = tokenService.gerarToken(supervisor);
        tokenAna = tokenService.gerarToken(ana);
    }

    private User criarSupervisor() {
        // O supervisor é aqui só quem pede a escala, e fica sem equipe de
        // propósito: com equipe, entrava na grelha como mais um colaborador e
        // as contagens deste arquivo teriam de incluir uma terceira pessoa que
        // não está sendo testada.
        var supervisor = TestFixtures.supervisor();
        supervisor.setPassword(passwordEncoder.encode(TestFixtures.RAW_PASSWORD));
        return userRepository.saveAndFlush(supervisor);
    }

    private User criar(String nome, String email, TeamGroup equipa, boolean ativo) {
        var user = TestFixtures.userInTeam(nome, email, perfilDeAnalista, equipa);
        user.setPassword(passwordEncoder.encode(TestFixtures.RAW_PASSWORD));
        user.setActive(ativo);
        return userRepository.saveAndFlush(user);
    }

    private String corpo(LocalDate referencia) {
        try {
            return objectMapper.writeValueAsString(Map.of("dataReferencia", referencia.toString()));
        } catch (Exception erro) {
            throw new IllegalStateException(erro);
        }
    }

    private long contarLinhas() {
        return workModalityScheduleRepository.count();
    }

    /** Modalidades de uma pessoa, por dia, na base de dados. */
    private Map<LocalDate, WorkModality> celulasDe(User user, LocalDate segunda) {
        LocalDate fim = segunda.plusDays(4);
        return workModalityScheduleRepository.listarIntervalo(segunda, fim).stream()
                .filter(registo -> registo.getUser().getId().equals(user.getId()))
                .collect(Collectors.toMap(
                        registo -> registo.getDate(),
                        registo -> registo.getModality()));
    }

    @Nested
    @DisplayName("Gerar")
    class Gerar {

        @Test
        @DisplayName("na semana ímpar, a equipe A está presencial à segunda, quarta e sexta")
        void semanaImparEquipaA() throws Exception {
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_IMPAR)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(10));

            var celulas = celulasDe(ana, SEGUNDA_IMPAR);

            // A semana ISO 13 é ímpar, e é daí que vem todo o padrão. Se este
            // teste deixar de passar depois de uma mudança no cálculo da
            // semana, o erro está na paridade e não nas modalidades.
            assertThat(celulas).containsExactlyInAnyOrderEntriesOf(Map.of(
                    SEGUNDA_IMPAR, WorkModality.PRESENCIAL,
                    SEGUNDA_IMPAR.plusDays(1), WorkModality.HOME_OFFICE,
                    SEGUNDA_IMPAR.plusDays(2), WorkModality.PRESENCIAL,
                    SEGUNDA_IMPAR.plusDays(3), WorkModality.HOME_OFFICE,
                    SEGUNDA_IMPAR.plusDays(4), WorkModality.PRESENCIAL));
        }

        @Test
        @DisplayName("na mesma semana, a equipe B faz exatamente o contrário")
        void semanaImparEquipaB() throws Exception {
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_IMPAR)))
                    .andExpect(status().isOk());

            var celulas = celulasDe(bruno, SEGUNDA_IMPAR);

            assertThat(celulas).containsExactlyInAnyOrderEntriesOf(Map.of(
                    SEGUNDA_IMPAR, WorkModality.HOME_OFFICE,
                    SEGUNDA_IMPAR.plusDays(1), WorkModality.PRESENCIAL,
                    SEGUNDA_IMPAR.plusDays(2), WorkModality.HOME_OFFICE,
                    SEGUNDA_IMPAR.plusDays(3), WorkModality.PRESENCIAL,
                    SEGUNDA_IMPAR.plusDays(4), WorkModality.HOME_OFFICE));
        }

        @Test
        @DisplayName("na semana par, o padrão inverte")
        void semanaParInverte() throws Exception {
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_IMPAR)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_PAR)))
                    .andExpect(status().isOk());

            var AnaNaPar = celulasDe(ana, SEGUNDA_PAR);
            var anaNaImpar = celulasDe(ana, SEGUNDA_IMPAR);

            // A mesma pessoa, semanas seguidas: o que mudou foi a paridade, e
            // a grelha tem de mostrar o inverso.
            assertThat(AnaNaPar.get(SEGUNDA_PAR)).isEqualTo(WorkModality.HOME_OFFICE);
            assertThat(AnaNaPar.get(SEGUNDA_PAR.plusDays(2))).isEqualTo(WorkModality.HOME_OFFICE);
            assertThat(AnaNaPar.get(SEGUNDA_PAR.plusDays(4))).isEqualTo(WorkModality.HOME_OFFICE);
            assertThat(AnaNaPar.get(SEGUNDA_PAR.plusDays(1)))
                    .isEqualTo(WorkModality.PRESENCIAL);

            AnaNaPar.forEach((dia, modalidade) ->
                    assertThat(modalidade).as("dia %s", dia)
                            .isNotEqualTo(anaNaImpar.get(dia)));
        }

        @Test
        @DisplayName("sábado e domingo ficam de fora")
        void semFimDeSemana() throws Exception {
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_IMPAR)))
                    .andExpect(status().isOk());

            var celulas = celulasDe(ana, SEGUNDA_IMPAR);

            // Cinco células e não sete: um sábado com modalidade seria uma
            // leitura que a grelha mostra e o calendário não confirma.
            assertThat(celulas).hasSize(5);
            assertThat(celulas.keySet()).allSatisfy(dia ->
                    assertThat(dia.getDayOfWeek().getValue()).isLessThanOrEqualTo(5));
        }

        @Test
        @DisplayName("quem não tem equipe, ou está inativo, não gera linha")
        void soEquipasAtivas() throws Exception {
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_IMPAR)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(10));

            // Duas pessoas escaladas, cinco dias cada. A terceira não tem equipe
            // e a quarta está inativa, e nenhuma das duas pode aparecer: a
            // escala precisa de dizer quem é que vai estar onde.
            assertThat(contarLinhas()).isEqualTo(10);
            assertThat(celulasDe(semEquipa, SEGUNDA_IMPAR)).isEmpty();
            assertThat(celulasDe(inativo, SEGUNDA_IMPAR)).isEmpty();
        }

        @Test
        @DisplayName("gerar duas vezes reescreve as mesmas linhas, e não duplica")
        void repetivel() throws Exception {
            for (int vez = 0; vez < 2; vez++) {
                mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                                .header("Authorization", "Bearer " + tokenAdmin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(corpo(SEGUNDA_IMPAR)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.length()").value(10));
            }

            // A restrição de unicidade (usuário, dia) é o que impede o
            // duplicado, mas o serviço tem de reescrever em vez de inserir
            // para lá chegar: se inserisse, a segunda passagem rebentava em
            // vez de devolver a escala.
            assertThat(contarLinhas()).isEqualTo(10);
        }

        @Test
        @DisplayName("a referência pode ser qualquer dia da semana")
        void referenciaQualquerDia() throws Exception {
            // Quinta-feira da semana 13: pedir a escala de uma quinta é o caso
            // normal, porque é quando se percebe que a semana não foi feita.
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_IMPAR.plusDays(3))))
                    .andExpect(status().isOk());

            assertThat(celulasDe(ana, SEGUNDA_IMPAR)).hasSize(5);
        }

        @Test
        @DisplayName("sem corpo, gera a semana corrente")
        void semCorpoUsaASemanaCorrente() throws Exception {
            LocalDate segundaDaSemanaCorrente = LocalDate.now()
                    .with(java.time.DayOfWeek.MONDAY);

            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(10));

            assertThat(celulasDe(ana, segundaDaSemanaCorrente)).hasSize(5);
        }

        @Test
        @DisplayName("a resposta traz o número ISO da semana e se é ímpar, para a grelha se desenhar sozinha")
        void devolveNumeroDaSemana() throws Exception {
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_IMPAR)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].weekNumber").value(13))
                    .andExpect(jsonPath("$[0].weekOdd").value(true))
                    .andExpect(jsonPath("$[0].teamGroup").value("EQUIPE_A"))
                    .andExpect(jsonPath("$[0].modality").value("PRESENCIAL"))
                    .andExpect(jsonPath("$[0].modalityLabel").exists())
                    .andExpect(jsonPath("$[0].teamLabel").exists())
                    .andExpect(jsonPath("$[0].weekdayLabel").exists());
        }

        @Test
        @DisplayName("a paridade que vem no DTO é a do número ISO da data")
        void paridadeVemDoIso() throws Exception {
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_PAR)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].weekNumber").value(12))
                    .andExpect(jsonPath("$[0].weekOdd").value(false));
        }

        @Test
        @DisplayName("o analista é recusado com 403, e não gera nada")
        void analistaNaoGera() throws Exception {
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAna)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_IMPAR)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message")
                            .value("Apenas o perfil SUPERVISOR pode executar esta operação."));

            // Gerar reescreve a escala de toda a equipe. Um analista que o
            // conseguisse fazer mudaria o calendário de toda a gente, e a
            // partir de uma semana errada ninguém perceberia porque é que a
            // escala mudou.
            assertThat(contarLinhas()).isZero();
        }

        @Test
        @DisplayName("sem token, é 401")
        void semToken() throws Exception {
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate"))
                    .andExpect(status().isUnauthorized());

            assertThat(contarLinhas()).isZero();
        }

        @Test
        @DisplayName("sem nenhum usuário com equipe, é 400 e a escala não fica a meio")
        void semNinguemComEquipa() throws Exception {
            // Desativa-se a equipe toda, em vez de apagar as contas: o token do
            // supervisor tem de continuar a resolver para ele, ou a resposta
            // seria 401 e o teste passaria a medir a autenticação.
            for (User colaborador : List.of(ana, bruno, semEquipa, inativo)) {
                colaborador.setActive(false);
            }
            userRepository.flush();

            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_IMPAR)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Nenhum usuário ativo tem equipe atribuída, "
                                    + "por isso não há escala para gerar."));

            assertThat(contarLinhas()).isZero();
        }
    }

    @Nested
    @DisplayName("Listar")
    class Listar {

        @Test
        @DisplayName("devolve as células do intervalo, por nome e por dia")
        void listaOrdenada() throws Exception {
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_IMPAR)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/work-modality-schedules")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .param("inicio", SEGUNDA_IMPAR.toString())
                            .param("fim", SEGUNDA_IMPAR.plusDays(4).toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(10))
                    .andExpect(jsonPath("$[0].userName").value("Ana"))
                    .andExpect(jsonPath("$[4].userName").value("Ana"))
                    .andExpect(jsonPath("$[5].userName").value("Bruno"));
        }

        @Test
        @DisplayName("uma semana ainda não gerada vem vazia, e não com as presenças assumidas")
        void semanaNaoGeradaVazia() throws Exception {
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_IMPAR)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/work-modality-schedules")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .param("inicio", SEGUNDA_PAR.toString())
                            .param("fim", SEGUNDA_PAR.plusDays(4).toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("o intervalo pega as duas semanas de uma vez")
        void intervaloDeDuasSemanas() throws Exception {
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_PAR)))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_IMPAR)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/work-modality-schedules")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .param("inicio", SEGUNDA_PAR.toString())
                            .param("fim", SEGUNDA_IMPAR.plusDays(4).toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(20));
        }

        @Test
        @DisplayName("o analista também lê a escala, com a mesma grelha")
        void analistaLe() throws Exception {
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_IMPAR)))
                    .andExpect(status().isOk());

            // Ler é consultar onde se vai estar. Se esta chamada devolvesse 403,
            // o calendário de um analista deixava de funcionar por causa de quem
            // o pede, e isso não é uma restrição: é uma falha.
            mockMvc.perform(get("/api/v1/work-modality-schedules")
                            .header("Authorization", "Bearer " + tokenAna)
                            .param("inicio", SEGUNDA_IMPAR.toString())
                            .param("fim", SEGUNDA_IMPAR.plusDays(4).toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(10));
        }

        @Test
        @DisplayName("um intervalo invertido é 400")
        void intervaloInvertido() throws Exception {
            mockMvc.perform(get("/api/v1/work-modality-schedules")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .param("inicio", SEGUNDA_IMPAR.toString())
                            .param("fim", SEGUNDA_PAR.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("A data final do intervalo é anterior à inicial."));
        }

        @Test
        @DisplayName("sem as datas, é 400")
        void semDatas() throws Exception {
            mockMvc.perform(get("/api/v1/work-modality-schedules")
                            .header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("sem token, é 401")
        void semToken() throws Exception {
            mockMvc.perform(get("/api/v1/work-modality-schedules")
                            .param("inicio", SEGUNDA_IMPAR.toString())
                            .param("fim", SEGUNDA_IMPAR.plusDays(4).toString()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("A célula como a base a salva")
    class Celula {

        @Test
        @DisplayName("a equipe fica escrita na célula, e sobrevive a quem a ler")
        void equipaNaCelula() throws Exception {
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_IMPAR)))
                    .andExpect(status().isOk());
            sincronizar();

            // A equipe é desnormalizada para dentro da célula de propósito: a
            // grelha mostra a etiqueta da equipe do dia, e ir buscar a equipe
            // ao usuário a cada célula fazia uma consulta por linha. A
            // contrapartida é que mudar a equipe de uma pessoa não reescreve o
            // passado, e é por isso que isto vale a pena fixar num teste.
            List<WorkModalitySchedule> celulas =
                    workModalityScheduleRepository.listarIntervalo(SEGUNDA_IMPAR, SEGUNDA_IMPAR.plusDays(4));

            assertThat(celulas).allSatisfy(celula -> {
                assertThat(celula.getTeamGroup()).isNotNull();
                assertThat(celula.getUser().getId()).isNotNull();
            });
            assertThat(celulas).extracting(WorkModalitySchedule::getTeamGroup)
                    .contains(TeamGroup.EQUIPE_A, TeamGroup.EQUIPE_B);
        }

        @Test
        @DisplayName("não há duas linhas para o mesmo usuário no mesmo dia")
        void unicidadeUtilizadorDia() throws Exception {
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_IMPAR)))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/v1/work-modality-schedules/generate")
                            .header("Authorization", "Bearer " + tokenAdmin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo(SEGUNDA_IMPAR)))
                    .andExpect(status().isOk());
            sincronizar();

            assertThat(contarLinhas()).isEqualTo(10);
            assertThat(contarLinhas()).isEqualTo(
                    workModalityScheduleRepository.findAll().stream()
                            .map(celula -> celula.getUser().getId() + "|" + celula.getDate())
                            .distinct()
                            .count());
        }
    }
}
