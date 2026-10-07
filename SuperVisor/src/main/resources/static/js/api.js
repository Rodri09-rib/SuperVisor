/**
 * Camada de acesso à API do SuperVisor.
 *
 * Concentra num único sitio o que é comum a todas as chamadas: o header
 * Authorization a partir do JWT guardado em localStorage, a serialização JSON
 * e o tratamento de 401, que limpa a sessão e volta ao login.
 *
 * O 403 é separado do 401 de propósito: significa que a sessão é válida e o
 * que falta é permissão para esta operação, pelo que a sessão não é
 * destruída. Confundir os dois expulsava o usuário do painel por tentar
 * uma ação que simplesmente não lhe compete.
 */
const SuperVisorApi = (() => {

    const TOKEN_KEY = 'jwt_token';

    let aRedirecionar = false;

    function token() {
        return localStorage.getItem(TOKEN_KEY);
    }

    function limparSessao() {
        localStorage.removeItem(TOKEN_KEY);
    }

    function redirecionarParaLogin() {
        if (aRedirecionar) {
            return;
        }
        aRedirecionar = true;
        window.location.href = '/login';
    }

    function semSessao(status) {
        return status === 401;
    }

    async function mensagemDeErro(response) {
        try {
            const corpo = await response.json();
            return corpo && corpo.message ? corpo.message : null;
        } catch (erro) {
            return null;
        }
    }

    /**
     * Devolve o corpo JSON da resposta, ou null em respostas sem conteúdo
     * (204) ou com corpo vazio. Lança um Error com a mensagem do servidor em
     * qualquer resposta de erro.
     */
    async function request(caminho, opcoes = {}) {
        const metodo = opcoes.metodo || 'GET';
        const corpo = opcoes.corpo === undefined ? null : opcoes.corpo;

        const jwt = token();
        if (!jwt) {
            redirecionarParaLogin();
            throw new Error('Sessão expirada. Inicie sessão novamente.');
        }

        const headers = { 'Authorization': 'Bearer ' + jwt };
        if (corpo !== null) {
            headers['Content-Type'] = 'application/json';
        }

        let response;
        try {
            response = await fetch(caminho, {
                method: metodo,
                headers: headers,
                body: corpo === null ? undefined : JSON.stringify(corpo)
            });
        } catch (erro) {
            console.error('Falha de comunicação com o servidor:', erro);
            throw new Error('Erro de comunicação com o servidor.');
        }

        if (semSessao(response.status)) {
            limparSessao();
            redirecionarParaLogin();
            throw new Error('Sessão expirada. Inicie sessão novamente.');
        }

        if (!response.ok) {
            const mensagem = await mensagemDeErro(response);
            throw new Error(mensagem || 'Ocorreu um erro inesperado (' + response.status + ').');
        }

        if (response.status === 204) {
            return null;
        }

        const texto = await response.text();
        return texto ? JSON.parse(texto) : null;
    }

    return {
        token: token,
        limparSessao: limparSessao,
        redirecionarParaLogin: redirecionarParaLogin,

        utilizadorAtual: () => request('/api/v1/users/me'),
        listarUtilizadores: () => request('/api/v1/users'),
        criarUtilizador: (utilizador) => request('/api/v1/users',
            { metodo: 'POST', corpo: utilizador }),

        /**
         * Lista de usuários com contas inativas incluídas.
         *
         * <p>Separada de {@link listarUtilizadores} porque as duas respondem a
         * perguntas diferentes: a primeira alimenta o <select> do editor de
         * alocações, onde uma conta desativada não pode aparecer; esta alimenta a
         * gerenciamento de contas, onde a conta desativada é precisamente o que se
         * procura para a reativar.
         */
        listarTodosUtilizadores: () => request('/api/v1/users/todos'),

        /**
         * Alteração de cadastro. Não leva e-mail: o servidor não o aceita, porque
         * é a identidade da conta e o que está dentro do token.
         */
        atualizarUtilizador: (id, utilizador) => request(
            '/api/v1/users/' + encodeURIComponent(id), { metodo: 'PUT', corpo: utilizador }),

        /**
         * Ativa ou desativa uma conta.
         *
         * <p>Um PATCH e não um PUT porque só o estado muda; o resto do cadastro
         * fica como está.
         */
        alterarEstadoUtilizador: (id, active) => request(
            '/api/v1/users/' + encodeURIComponent(id) + '/estado',
            { metodo: 'PATCH', corpo: { active: active } }),

        redefinirSenhaUtilizador: (id, password) => request(
            '/api/v1/users/' + encodeURIComponent(id) + '/senha',
            { metodo: 'POST', corpo: { password: password } }),

        /**
         * Exclusão definitiva da conta, ao contrário de `alterarEstadoUtilizador`
         * que é reversível.
         *
         * <p>O servidor recusa a conta que ainda tenha turnos marcados, com uma
         * mensagem que diz quantos são. A decisão de cascadear folgas e presencialidade
         * e de recusar turnos é do servidor e não é negociável pelo cliente, por
         * isso aqui não há parâmetro nenhum.
         */
        apagarUtilizador: (id) => request('/api/v1/users/' + encodeURIComponent(id),
            { metodo: 'DELETE' }),

        listarTurnos: () => request('/api/v1/shifts'),
        listarAtribuicoes: () => request('/api/v1/assignments'),

        listarEscalas: () => request('/api/v1/scales'),
        obterEscala: (id) => request('/api/v1/scales/' + encodeURIComponent(id)),
        criarEscala: (escala) => request('/api/v1/scales', { metodo: 'POST', corpo: escala }),
        publicarEscala: (id) => request('/api/v1/scales/' + encodeURIComponent(id) + '/publish', { metodo: 'POST' }),

        /**
         * Conclui a escala e credita os dias de folga que o trabalho dela
         * rendeu (domingo = 0.5, Celular da Marinas = 1.0).
         *
         * <p>O servidor torna a operação idempotente: repetir não credita duas
         * vezes, por isso um duplo clique ou uma repetição por falha de rede
         * não mexe nos saldos.
         */
        concluirEscala: (id) => request('/api/v1/scales/' + encodeURIComponent(id) + '/complete', { metodo: 'POST' }),

        /**
         * Relatório de cobertura e conflitos de uma escala, numa só resposta.
         *
         * <p>É leitura, como o resto das escalas: qualquer usuário
         * autenticado o pode pedir. O relatório não traz o motivo das folgas
         * precisamente por isso.
         */
        relatorioCoberturaEscala: (id) => request(
            '/api/v1/scales/' + encodeURIComponent(id) + '/coverage'),

        /**
         * Quantos turnos e pedidos de troca a exclusão de uma escala leva.
         *
         * <p>Lida antes de perguntar, para que a confirmação diga um número em vez
         * de «tem a certeza?». Uma escala com doze turnos em jogo e uma escala
         * recém-criada e vazia pedem decisões diferentes.
         */
        resumoExclusaoEscala: (id) => request(
            '/api/v1/scales/' + encodeURIComponent(id) + '/exclusao'),

        /**
         * Exclui a escala com os turnos dela e os pedidos de troca que os
         * referenciam. Devolve nada: quem apaga é a supervisão e a resposta é o
         * estado de um recurso que a tabela volta a ler de seguida.
         */
        apagarEscala: (id) => request(
            '/api/v1/scales/' + encodeURIComponent(id), { metodo: 'DELETE' }),

        listarAlocacoes: (escalaId) => request('/api/v1/allocations'
            + (escalaId ? '?editionScaleId=' + encodeURIComponent(escalaId) : '')),
        criarAlocacao: (alocacao) => request('/api/v1/allocations', { metodo: 'POST', corpo: alocacao }),
        atualizarAlocacao: (id, alocacao) => request('/api/v1/allocations/' + encodeURIComponent(id),
            { metodo: 'PUT', corpo: alocacao }),

        /**
         * Resposta do analista ao turno escalado: aceite ou recusa.
         *
         * <p>Quem pode responder não é decidido aqui: o dono do turno e a
         * supervisão podem, e o servidor devolve 403 a quem não pode. Um PATCH
         * porque é uma transição de estado, não uma substituição.
         */
        responderAlocacao: (id, status) => request(
            '/api/v1/allocations/' + encodeURIComponent(id) + '/acceptance',
            { metodo: 'PATCH', corpo: { status: status } }),

        pedirTroca: (pedido) => request('/api/v1/exchanges', { metodo: 'POST', corpo: pedido }),

        /**
         * Histórico de trocas, com filtros opcionais.
         *
         * <p>Os filtros são montados aqui para que a página fique só com o
         * desenho da tabela, e um filtro sem valor não chega a ser enviado:
         * mandá-lo na mesma faria o servidor ter de distinguir "não filtrado" de
         * "filtrado por nada".
         */
        listarTrocas: (filtros = {}) => {
            const parametros = new URLSearchParams();
            if (filtros.status) {
                parametros.append('status', filtros.status);
            }
            if (filtros.userId) {
                parametros.append('userId', filtros.userId);
            }
            if (filtros.dataInicial) {
                parametros.append('dataInicial', filtros.dataInicial);
            }
            if (filtros.dataFim) {
                parametros.append('dataFim', filtros.dataFim);
            }
            const query = parametros.toString();
            return request('/api/v1/exchanges' + (query ? '?' + query : ''));
        },

        /**
         * Responde a um pedido de troca: aceitar ou recusar.
         *
         * <p>É um PATCH e não um PUT porque a operação é uma transição de
         * estado e não uma substituição do recurso — o resto do pedido fica
         * como está.
         */
        responderTroca: (id, aceitar) => request(
            '/api/v1/exchanges/' + encodeURIComponent(id) + '/respond',
            { metodo: 'PATCH', corpo: { isAccepted: aceitar } }),

        listarEscalaWorkModality: (inicio, fim) => request(
            '/api/v1/work-modality-schedules'
            + '?inicio=' + encodeURIComponent(inicio)
            + '&fim=' + encodeURIComponent(fim)),

        gerarEscalaWorkModality: (dataReferencia) => request(
            '/api/v1/work-modality-schedules/generate',
            { metodo: 'POST', corpo: { dataReferencia: dataReferencia } }),

        /**
         * Apaga a escala de presencialidade de um intervalo, que é a semana que a
         * grelha está a mostrar.
         *
         * <p>O intervalo vai na query e não no corpo porque não há corpo numa
         * operação de apagamento, e é também o que a página envia ao listar: as
         * duas chamadas partilham as mesmas datas, e mandá-las de outra forma
         * abriria espaço para a grelha estar a mostrar uma semana e o botão a
         * apagar outra.
         */
        apagarEscalaWorkModality: (inicio, fim) => request(
            '/api/v1/work-modality-schedules'
            + '?inicio=' + encodeURIComponent(inicio)
            + '&fim=' + encodeURIComponent(fim),
            { metodo: 'DELETE' }),

        /**
         * Regista uma falta ou observação numa célula da presencialidade.
         *
         * <p>O servidor devolve a célula atualizada e não só o OK: a grelha
         * repinta a linha com o que veio de lá — incluindo o saldo que a falta
         * movimentou — em vez de recarregar a semana toda e perder o sítio
         * onde o utilizador estava a trabalhar.
         */
        atualizarPresenca: (scheduleId, dados) => request(
            '/api/v1/work-modality-schedules/' + encodeURIComponent(scheduleId) + '/attendance',
            { metodo: 'PATCH', corpo: dados }),

        /**
         * Ajuste manual do saldo de compensação, feito pela supervisão.
         *
         * <p>O valor é um delta assinado: negativo quando se abate uma dívida,
         * positivo quando se acrescenta. Devolve o colaborador atualizado para
         * a lista do topo se atualizar sem uma nova leitura.
         */
        ajustarCompensacao: (userId, deltaDays) => request(
            '/api/v1/users/' + encodeURIComponent(userId) + '/compensation',
            { metodo: 'PATCH', corpo: { deltaDays: deltaDays } }),

        /**
         * Extrato de um colaborador: faltas, folgas e recompensas, do mais
         * recente para o mais antigo.
         *
         * <p>É a origem do botão de histórico da página de folgas. O servidor
         * restringe a leitura à supervisão — o extrato reconstrói o saldo de
         * qualquer pessoa —, por isso um analista não chega a fazer este
         * pedido: recebe 403 e a sessão continua, que é o que um 403 significa.
         */
        extratoUtilizador: (id) => request(
            '/api/v1/users/' + encodeURIComponent(id) + '/extrato'),

        listarFolgas: (userId) => request('/api/v1/leaves'
            + (userId ? '?userId=' + encodeURIComponent(userId) : '')),
        criarFolga: (folga) => request('/api/v1/leaves', { metodo: 'POST', corpo: folga }),
        atualizarFolga: (id, folga) => request('/api/v1/leaves/' + encodeURIComponent(id),
            { metodo: 'PUT', corpo: folga }),
        apagarFolga: (id) => request('/api/v1/leaves/' + encodeURIComponent(id),
            { metodo: 'DELETE' })
    };
})();
