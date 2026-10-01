/**
 * Camada de acesso à API do SuperVisor.
 *
 * Concentra num único sitio o que é comum a todas as chamadas: o header
 * Authorization a partir do JWT guardado em localStorage, a serialização JSON
 * e o tratamento de 401, que limpa a sessão e volta ao login.
 *
 * O 403 é separado do 401 de propósito: significa que a sessão é válida e o
 * que falta é permissão para esta operação, pelo que a sessão não é
 * destruída. Confundir os dois expulsava o utilizador do painel por tentar
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
         * Lista de utilizadores com contas inativas incluídas.
         *
         * <p>Separada de {@link listarUtilizadores} porque as duas respondem a
         * perguntas diferentes: a primeira alimenta o <select> do editor de
         * alocações, onde uma conta desativada não pode aparecer; esta alimenta a
         * gestão de contas, onde a conta desativada é precisamente o que se
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

        listarTurnos: () => request('/api/v1/shifts'),
        listarAtribuicoes: () => request('/api/v1/assignments'),

        listarEscalas: () => request('/api/v1/scales'),
        obterEscala: (id) => request('/api/v1/scales/' + encodeURIComponent(id)),
        criarEscala: (escala) => request('/api/v1/scales', { metodo: 'POST', corpo: escala }),
        publicarEscala: (id) => request('/api/v1/scales/' + encodeURIComponent(id) + '/publish', { metodo: 'POST' }),

        /**
         * Relatório de cobertura e conflitos de uma escala, numa só resposta.
         *
         * <p>É leitura, como o resto das escalas: qualquer utilizador
         * autenticado o pode pedir. O relatório não traz o motivo das folgas
         * precisamente por isso.
         */
        relatorioCoberturaEscala: (id) => request(
            '/api/v1/scales/' + encodeURIComponent(id) + '/coverage'),

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

        listarFolgas: (userId) => request('/api/v1/leaves'
            + (userId ? '?userId=' + encodeURIComponent(userId) : '')),
        criarFolga: (folga) => request('/api/v1/leaves', { metodo: 'POST', corpo: folga }),
        atualizarFolga: (id, folga) => request('/api/v1/leaves/' + encodeURIComponent(id),
            { metodo: 'PUT', corpo: folga }),
        apagarFolga: (id) => request('/api/v1/leaves/' + encodeURIComponent(id),
            { metodo: 'DELETE' })
    };
})();
