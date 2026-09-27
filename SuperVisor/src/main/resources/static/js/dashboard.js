/**
 * Dashboard de escalas: listagem, detalhes, pedido de troca e, para o perfil
 * de administrador, criação e publicação de escalas.
 *
 * O estado vive aqui e é repintado por completo em cada renderização; a lista
 * tem sempre poucos elementos, pelo que não justifica paginação nem
 * reconciliação manual do DOM.
 */
document.addEventListener('DOMContentLoaded', () => {

    const PERFIS_ADMIN = ['SUPERVISOR', 'ADMIN'];
    const MOTIVO_MINIMO = 5;
    const SENHA_MINIMA = 6;

    const estado = {
        utilizador: null,
        escalas: [],
        alocacoes: [],
        escalaA_Publicar: null,
        escalaEmEdicao: null,
        utilizadores: [],
        turnos: [],
        atribuicoes: []
    };

    const el = {};

    function guardarElementos() {
        // A saudação e o botão de sair são do `layout.js`.
        el.btnNovoUsuario = document.getElementById('btnNovoUsuario');
        el.btnNovaEscala = document.getElementById('btnNovaEscala');
        el.btnPedirTroca = document.getElementById('btnPedirTroca');
        el.tabelaCorpo = document.getElementById('escalasTableBody');
        el.toasts = document.getElementById('toastContainer');

        el.detalhesTitulo = document.getElementById('detalhesTitulo');
        el.detalhesCorpo = document.getElementById('detalhesCorpo');
        el.detalhesAlocacoes = document.getElementById('detalhesAlocacoesCorpo');
        el.btnTrocarDetalhes = document.getElementById('btnTrocarDaEscala');
        el.btnEditarAlocacoes = document.getElementById('btnEditarAlocacoes');

        el.alocacaoFormulario = document.getElementById('alocacaoForm');
        el.alocacaoId = document.getElementById('alocacaoId');
        el.alocacaoEscalaNome = document.getElementById('alocacaoEscalaNome');
        el.alocacaoUtilizador = document.getElementById('alocacaoUtilizador');
        el.alocacaoTurno = document.getElementById('alocacaoTurno');
        el.alocacaoTurnoAjuda = document.getElementById('alocacaoTurnoAjuda');
        el.alocacaoAtribuicoes = document.getElementById('alocacaoAtribuicoes');
        el.alocacaoHoraInicio = document.getElementById('alocacaoHoraInicio');
        el.alocacaoHoraFim = document.getElementById('alocacaoHoraFim');
        el.alocacaoData = document.getElementById('alocacaoData');
        el.alocacaoAviso = document.getElementById('alocacaoAviso');
        el.alocacaoLista = document.getElementById('alocacaoLista');
        el.alocacaoSubmeter = document.getElementById('btnSubmeterAlocacao');
        el.btnLimparEdicaoAlocacao = document.getElementById('btnCancelarEdicaoAlocacao');

        el.trocaFormulario = document.getElementById('trocaForm');
        el.trocaEscala = document.getElementById('trocaEscala');
        el.trocaOrigem = document.getElementById('trocaOrigem');
        el.trocaDestino = document.getElementById('trocaDestino');
        el.trocaAviso = document.getElementById('trocaAviso');
        el.trocaMotivo = document.getElementById('trocaMotivo');
        el.trocaSubmeter = document.getElementById('btnSubmeterTroca');

        el.escalaFormulario = document.getElementById('novaEscalaForm');
        el.escalaNome = document.getElementById('novaEscalaNome');
        el.escalaInicio = document.getElementById('novaEscalaInicio');
        el.escalaFim = document.getElementById('novaEscalaFim');
        el.escalaAviso = document.getElementById('novaEscalaAviso');
        el.escalaSubmeter = document.getElementById('btnSubmeterEscala');

        el.publicacaoNome = document.getElementById('publicacaoEscalaNome');
        el.btnConfirmarPublicacao = document.getElementById('btnConfirmarPublicacao');

        el.utilizadorFormulario = document.getElementById('novoUsuarioForm');
        el.utilizadorNome = document.getElementById('novoUsuarioNome');
        el.utilizadorEmail = document.getElementById('novoUsuarioEmail');
        el.utilizadorPassword = document.getElementById('novoUsuarioPassword');
        el.utilizadorPerfil = document.getElementById('novoUsuarioPerfil');
        el.utilizadorAviso = document.getElementById('novoUsuarioAviso');
        el.utilizadorSubmeter = document.getElementById('btnSubmeterUsuario');
    }

    /* ------------------------------------------------------------------ */
    /* Modais                                                              */
    /* ------------------------------------------------------------------ */

    /**
     * A instância é procurada ou criada no momento da utilização, e não
     * guardada no arranque: assim um modal criado depois do carregamento
     * continua a funcionar e nenhuma referência fica a apontar para `undefined`.
     */
    function instanciaModal(idElemento) {
        const alvo = document.getElementById(idElemento);
        if (!alvo) {
            console.error('Modal com id #' + idElemento + ' não foi encontrado no DOM.');
            return null;
        }
        return bootstrap.Modal.getOrCreateInstance(alvo);
    }

    function abrirModal(idElemento) {
        const modal = instanciaModal(idElemento);
        if (modal) {
            modal.show();
        }
    }

    function fecharModal(idElemento) {
        const modal = instanciaModal(idElemento);
        if (modal) {
            modal.hide();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Feedback                                                            */
    /* ------------------------------------------------------------------ */

    function notificar(mensagem, tipo) {
        const classe = tipo === 'erro' ? 'text-bg-danger' : 'text-bg-success';
        const icone = tipo === 'erro' ? 'bi-exclamation-triangle' : 'bi-check-circle';

        const toast = document.createElement('div');
        toast.className = 'toast align-items-center border-0 ' + classe;
        toast.setAttribute('role', 'alert');
        toast.setAttribute('aria-live', 'assertive');
        toast.setAttribute('aria-atomic', 'true');
        toast.innerHTML =
            '<div class="d-flex">'
            + '<div class="toast-body">'
            + '<i class="bi ' + icone + ' me-2"></i>'
            + '<span class="js-mensagem">' + SuperVisorFormat.escapar(mensagem) + '</span>'
            + '</div>'
            + '<button type="button" class="btn-close btn-close-white me-2 m-auto" '
            + 'data-bs-dismiss="toast" aria-label="Fechar"></button>'
            + '</div>';

        el.toasts.appendChild(toast);

        const instance = new bootstrap.Toast(toast, { delay: 4500 });
        toast.addEventListener('hidden.bs.toast', () => toast.remove());
        instance.show();
    }

    function ocupado(botao, ativo, rotulo) {
        if (!botao) {
            return;
        }
        if (ativo) {
            botao.dataset.rotuloOriginal = botao.innerHTML;
            botao.disabled = true;
            botao.innerHTML =
                '<span class="spinner-border spinner-border-sm me-2" role="status" '
                + 'aria-hidden="true"></span>' + SuperVisorFormat.escapar(rotulo);
            return;
        }
        botao.disabled = false;
        if (botao.dataset.rotuloOriginal) {
            botao.innerHTML = botao.dataset.rotuloOriginal;
            delete botao.dataset.rotuloOriginal;
        }
    }

    function mostrarAviso(elemento, mensagem, tipo) {
        if (!elemento) {
            return;
        }
        if (!mensagem) {
            elemento.classList.add('d-none');
            elemento.textContent = '';
            return;
        }
        elemento.className = 'alert alert-' + (tipo || 'danger');
        elemento.textContent = mensagem;
    }

    /* ------------------------------------------------------------------ */
    /* Sessão                                                              */
    /* ------------------------------------------------------------------ */

    function ehAdministrador() {
        return !!estado.utilizador
            && PERFIS_ADMIN.indexOf(estado.utilizador.profile) !== -1;
    }

    async function carregarSessao() {
        // A saudação e o botão de sair são do `layout.js`, que os desenha para
        // todas as páginas. Aqui só interessa o perfil, que é o que decide o
        // que este utilizador pode ver.
        try {
            estado.utilizador = await SuperVisorApi.utilizadorAtual();
        } catch (erro) {
            console.warn('Perfil do utilizador indisponível:', erro);
            return;
        }

        el.btnNovaEscala.classList.toggle('d-none', !ehAdministrador());
        // O botão fica escondido por omissão no HTML e só aparece aqui, para
        // que um perfil sem permissão não chegue a ver a ação. O servidor volta
        // a recusar o POST com 403 na mesma: isto é só para não oferecer um
        // botão que não pode fazer nada.
        el.btnNovoUsuario.classList.toggle('d-none', !ehAdministrador());
    }

    /* ------------------------------------------------------------------ */
    /* Tabela de escalas                                                   */
    /* ------------------------------------------------------------------ */

    function linhaCarregando() {
        return '<tr>'
            + '<td colspan="4" class="text-center py-5 text-muted">'
            + '<div class="spinner-border text-primary" role="status">'
            + '<span class="visually-hidden">A carregar escalas...</span>'
            + '</div>'
            + '<div class="mt-2">A carregar escalas...</div>'
            + '</td>'
            + '</tr>';
    }

    function linhaMensagem(colspan, html, classe) {
        return '<tr><td colspan="' + colspan + '" class="text-center py-4 ' + classe + '">'
            + html + '</td></tr>';
    }

    function linhaEscala(escala) {
        const nome = SuperVisorFormat.escapar(escala.name || 'Sem nome');
        const publicavel = escala.status === 'DRAFT' || escala.status === 'RASCUNHO';
        const id = encodeURIComponent(escala.id);

        const botaoPublicar = ehAdministrador() && publicavel
            ? '<button class="btn btn-sm btn-outline-success js-publicar" data-id="' + id + '">'
              + '<i class="bi bi-send"></i> Publicar</button> '
            : '';

        return '<tr>'
            + '<td class="align-middle fw-semibold">' + nome + '</td>'
            + '<td class="align-middle">'
            + SuperVisorFormat.periodo(escala.initialDate, escala.endDate)
            + '</td>'
            + '<td class="align-middle">'
            + SuperVisorFormat.badgeEscala(escala.status)
            + '</td>'
            + '<td class="text-end align-middle text-nowrap">'
            + '<button class="btn btn-sm btn-outline-primary js-detalhes" data-id="' + id + '">'
            + '<i class="bi bi-eye"></i> Detalhes</button> '
            + '<button class="btn btn-sm btn-outline-secondary js-trocar" data-id="' + id + '">'
            + '<i class="bi bi-arrow-left-right"></i> Trocar</button> '
            + botaoPublicar
            + '</td>'
            + '</tr>';
    }

    function renderizarTabela() {
        if (!estado.escalas.length) {
            el.tabelaCorpo.innerHTML = linhaMensagem(4,
                'Não tem escalas registadas neste momento.', 'text-muted');
            return;
        }
        el.tabelaCorpo.innerHTML = estado.escalas.map(linhaEscala).join('');
    }

    async function carregarEscalas() {
        el.tabelaCorpo.innerHTML = linhaCarregando();
        try {
            estado.escalas = await SuperVisorApi.listarEscalas() || [];
            renderizarTabela();
        } catch (erro) {
            if (erro.message.indexOf('Sessão') === 0) {
                return;
            }
            el.tabelaCorpo.innerHTML = linhaMensagem(4,
                '<i class="bi bi-exclamation-triangle me-2"></i>'
                + SuperVisorFormat.escapar(erro.message), 'text-danger fw-semibold');
            notificar(erro.message, 'erro');
        }
    }

    function escalaPorId(id) {
        const alvo = String(id);
        return estado.escalas.find((escala) => String(escala.id) === alvo) || null;
    }

    /* ------------------------------------------------------------------ */
    /* Detalhes da escala                                                  */
    /* ------------------------------------------------------------------ */

    async function abrirDetalhes(id) {
        el.btnTrocarDetalhes.dataset.id = encodeURIComponent(id);
        el.btnEditarAlocacoes.dataset.id = encodeURIComponent(id);
        el.btnEditarAlocacoes.classList.toggle('d-none', !ehAdministrador());
        el.detalhesTitulo.textContent = 'Detalhes da escala';
        el.detalhesCorpo.innerHTML = '<div class="text-center py-4">'
            + '<div class="spinner-border text-primary" role="status">'
            + '<span class="visually-hidden">A carregar...</span></div></div>';
        el.detalhesAlocacoes.innerHTML = '';
        abrirModal('detalhesModal');

        try {
            const [escala, alocacoes] = await Promise.all([
                SuperVisorApi.obterEscala(id),
                SuperVisorApi.listarAlocacoes(id)
            ]);

            el.detalhesTitulo.textContent = escala.name || 'Escala #' + escala.id;
            el.detalhesCorpo.innerHTML = [
                linhaDetalhe('Identificador', escala.id),
                linhaDetalhe('Período', SuperVisorFormat.periodo(escala.initialDate, escala.endDate)),
                linhaDetalhe('Estado', SuperVisorFormat.badgeEscala(escala.status))
            ].join('');

            renderizarAlocacoes(alocacoes || []);
        } catch (erro) {
            el.detalhesCorpo.innerHTML = linhaMensagem(1,
                '<i class="bi bi-exclamation-triangle me-2"></i>'
                + SuperVisorFormat.escapar(erro.message), 'text-danger');
        }
    }

    function linhaDetalhe(rotulo, valorHtml) {
        return '<div class="row mb-2">'
            + '<div class="col-5 text-muted">' + rotulo + '</div>'
            + '<div class="col-7 fw-semibold">' + valorHtml + '</div>'
            + '</div>';
    }

    function renderizarAlocacoes(alocacoes) {
        if (!alocacoes.length) {
            el.detalhesAlocacoes.innerHTML = linhaMensagem(4,
                'Esta escala ainda não tem turnos atribuídos.', 'text-muted');
            return;
        }

        el.detalhesAlocacoes.innerHTML = alocacoes.map((alocacao) => {
            const turno = SuperVisorFormat.rotuloTurno(alocacao);

            return '<tr>'
                + '<td>' + SuperVisorFormat.data(alocacao.specificDate) + '</td>'
                + '<td>' + SuperVisorFormat.escapar(turno) + '</td>'
                + '<td class="fw-semibold">'
                + SuperVisorFormat.utilizadorComDetalhes(alocacao) + '</td>'
                + '<td>' + SuperVisorFormat.badgeAlocacao(alocacao.analystAcceptanceStatus) + '</td>'
                + '</tr>';
        }).join('');
    }

    /* ------------------------------------------------------------------ */
    /* Gestão de alocações (administrador)                                  */
    /* ------------------------------------------------------------------ */

    /**
     * Carrega o vocabulário do editor. Os turnos e as atribuições vêm do
     * servidor, por serem dados do domínio: duplicá-los em JavaScript
     * permitiria que os dois lados divergissem.
     */
    async function carregarVocabularioAlocacoes() {
        try {
            const [utilizadores, turnos, atribuicoes] = await Promise.all([
                SuperVisorApi.listarUtilizadores(),
                SuperVisorApi.listarTurnos(),
                SuperVisorApi.listarAtribuicoes()
            ]);
            estado.utilizadores = utilizadores || [];
            estado.turnos = turnos || [];
            estado.atribuicoes = atribuicoes || [];
        } catch (erro) {
            if (erro.message.indexOf('Sessão') === 0) {
                return;
            }
            console.warn('Vocabulário de alocações indisponível:', erro);
            estado.utilizadores = [];
            estado.turnos = [];
            estado.atribuicoes = [];
        }
    }

    function preencherUtilizadores() {
        const itens = estado.utilizadores.map((u) => ({
            valor: u.id,
            rotulo: rotuloUtilizador(u)
        }));
        preencherOpcoes(el.alocacaoUtilizador, itens, 'Escolha o utilizador');
        el.alocacaoUtilizador.disabled = !itens.length;
    }

    /** "João (joao@teste.com)", ou só o e-mail quando não há nome. */
    function rotuloUtilizador(u) {
        if (u.name && u.email) {
            return u.name + ' (' + u.email + ')';
        }
        return u.name || u.email || 'Utilizador ' + u.id;
    }

    function preencherTurnos() {
        const itens = estado.turnos.map((t) => ({ valor: t.value, rotulo: t.rotulo }));
        preencherOpcoes(el.alocacaoTurno, itens, 'Escolha o turno');
        el.alocacaoTurno.disabled = !itens.length;
        atualizarAjudaTurno();
    }

    function turnoPorValor(valor) {
        return estado.turnos.find((t) => t.value === valor) || null;
    }

    /** Recorda o intervalo do turno escolhido, para o horário especial. */
    function atualizarAjudaTurno() {
        const turno = turnoPorValor(el.alocacaoTurno.value);
        el.alocacaoTurnoAjuda.textContent = turno
            ? 'Sábado e domingo: ' + turno.dayOfWeek + ', ' + turno.startTime + ' às ' + turno.endTime + '.'
            : '';
    }

    function preencherAtribuicoes() {
        el.alocacaoAtribuicoes.innerHTML = estado.atribuicoes.map((a) =>
            '<div class="form-check">'
            + '<input class="form-check-input js-atribuicao" type="checkbox" '
            + 'value="' + SuperVisorFormat.escapar(a.value) + '" '
            + 'id="jsAtribuicao_' + SuperVisorFormat.escapar(a.value) + '">'
            + '<label class="form-check-label" for="jsAtribuicao_'
            + SuperVisorFormat.escapar(a.value) + '">'
            + SuperVisorFormat.escapar(a.rotulo) + '</label>'
            + '</div>'
        ).join('');
    }

    function atribuicoesMarcadas() {
        return Array.from(
            el.alocacaoAtribuicoes.querySelectorAll('.js-atribuicao:checked')
        ).map((caixa) => caixa.value);
    }

    function marcarAtribuicoes(valores) {
        const pretendidas = valores || [];
        el.alocacaoAtribuicoes.querySelectorAll('.js-atribuicao').forEach((caixa) => {
            caixa.checked = pretendidas.indexOf(caixa.value) !== -1;
        });
    }

    function limparFormularioAlocacao() {
        el.alocacaoFormulario.reset();
        el.alocacaoId.value = '';
        marcarAtribuicoes([]);
        mostrarAviso(el.alocacaoAviso, null);
        el.alocacaoSubmeter.innerHTML = '<i class="bi bi-plus-lg"></i> Adicionar alocação';
        el.btnLimparEdicaoAlocacao.classList.add('d-none');
    }

    function preencherFormularioAlocacao(alocacao) {
        el.alocacaoId.value = alocacao.id;
        el.alocacaoUtilizador.value = String(alocacao.userId);
        el.alocacaoTurno.value = alocacao.shift;
        marcarAtribuicoes(alocacao.assignments);
        el.alocacaoHoraInicio.value = horaParaInput(alocacao.customStartTime);
        el.alocacaoHoraFim.value = horaParaInput(alocacao.customEndTime);
        el.alocacaoData.value = alocacao.specificDate
            ? String(alocacao.specificDate).slice(0, 10)
            : '';
        atualizarAjudaTurno();
        el.alocacaoSubmeter.innerHTML = '<i class="bi bi-check-lg"></i> Guardar alterações';
        el.btnLimparEdicaoAlocacao.classList.remove('d-none');
    }

    /** "10:30:00" (LocalTime) passa a "10:30", o formato de <input type=time>. */
    function horaParaInput(valor) {
        if (!valor) {
            return '';
        }
        const partes = String(valor).split(':');
        if (partes.length < 2) {
            return '';
        }
        return partes[0] + ':' + partes[1];
    }

    async function abrirGestaoAlocacoes(escalaId) {
        estado.escalaEmEdicao = escalaId;
        const escala = escalaPorId(escalaId);
        el.alocacaoEscalaNome.textContent = escala
            ? (escala.name || 'Escala #' + escala.id)
            : 'Escala #' + escalaId;

        limparFormularioAlocacao();
        await recarregarAlocacoesDaEscala(escalaId);
        abrirModal('alocacoesModal');
    }

    async function recarregarAlocacoesDaEscala(escalaId) {
        try {
            estado.alocacoes = await SuperVisorApi.listarAlocacoes(escalaId) || [];
        } catch (erro) {
            estado.alocacoes = [];
            mostrarAviso(el.alocacaoAviso, erro.message, 'danger');
        }
        renderizarListaAlocacoes();
    }

    function renderizarListaAlocacoes() {
        if (!estado.alocacoes.length) {
            el.alocacaoLista.innerHTML = '<p class="text-muted mb-0">'
                + 'Ainda não há turnos atribuídos nesta escala.</p>';
            return;
        }

        el.alocacaoLista.innerHTML = '<ul class="list-group list-group-flush">'
            + estado.alocacoes.map((alocacao) =>
                '<li class="list-group-item d-flex justify-content-between align-items-center px-0">'
                + '<span>'
                + '<span class="badge bg-light text-dark border me-2">'
                + SuperVisorFormat.escapar(alocacao.shiftAcronym || '—') + '</span>'
                + SuperVisorFormat.utilizadorComDetalhes(alocacao)
                + '</span>'
                + '<button type="button" class="btn btn-sm btn-outline-primary js-editar-alocacao" '
                + 'data-id="' + SuperVisorFormat.escapar(alocacao.id) + '">'
                + '<i class="bi bi-pencil"></i> Editar</button>'
                + '</li>'
            ).join('')
            + '</ul>';
    }

    async function submeterAlocacao(evento) {
        evento.preventDefault();

        const utilizadorId = el.alocacaoUtilizador.value;
        const turno = el.alocacaoTurno.value;
        const inicio = el.alocacaoHoraInicio.value;
        const fim = el.alocacaoHoraFim.value;

        if (!utilizadorId) {
            mostrarAviso(el.alocacaoAviso, 'Escolha o utilizador que cobre o turno.', 'warning');
            return;
        }
        if (!turno) {
            mostrarAviso(el.alocacaoAviso, 'Escolha o turno de fim de semana.', 'warning');
            return;
        }
        if ((inicio && !fim) || (!inicio && fim)) {
            mostrarAviso(el.alocacaoAviso,
                'O horário especial precisa do início e do fim, ou de nenhum dos dois.', 'warning');
            return;
        }
        if (estado.escalaEmEdicao === null) {
            mostrarAviso(el.alocacaoAviso, 'Escolha primeiro a escala a editar.', 'warning');
            return;
        }

        mostrarAviso(el.alocacaoAviso, null);
        ocupado(el.alocacaoSubmeter, true, 'A guardar...');

        const corpo = {
            editionScaleId: Number(estado.escalaEmEdicao),
            userId: Number(utilizadorId),
            shift: turno,
            assignments: atribuicoesMarcadas(),
            customStartTime: inicio || null,
            customEndTime: fim || null,
            specificDate: el.alocacaoData.value || null
        };

        const emEdicao = el.alocacaoId.value;
        let guardado = false;

        try {
            if (emEdicao) {
                await SuperVisorApi.atualizarAlocacao(emEdicao, corpo);
                notificar('Alocação atualizada.', 'sucesso');
            } else {
                await SuperVisorApi.criarAlocacao(corpo);
                notificar('Alocação criada.', 'sucesso');
            }
            guardado = true;
        } catch (erro) {
            mostrarAviso(el.alocacaoAviso, erro.message, 'danger');
        } finally {
            ocupado(el.alocacaoSubmeter, false);
        }

        // O reposicionamento do botão tem de acontecer depois de ocupado(...,
        // false): esse helper restaura o rótulo que existia no momento do
        // envio, que no modo de edição é o de "Guardar alterações".
        if (guardado) {
            limparFormularioAlocacao();
            await recarregarAlocacoesDaEscala(estado.escalaEmEdicao);
            await carregarEscalas();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Pedido de troca                                                     */
    /* ------------------------------------------------------------------ */

    function preencherOpcoes(select, itens, valorVazio) {
        select.innerHTML = '<option value="">' + valorVazio + '</option>'
            + itens.map((item) => '<option value="' + SuperVisorFormat.escapar(item.valor) + '">'
                + SuperVisorFormat.escapar(item.rotulo) + '</option>').join('');
        select.disabled = !itens.length;
    }

    function validarTroca() {
        if (!estado.utilizador) {
            mostrarAviso(el.trocaAviso,
                'Não foi possível identificar o utilizador. Inicie sessão novamente.', 'danger');
            el.trocaSubmeter.disabled = true;
            return;
        }

        const temColegas = estado.alocacoes.some((a) => a.userId !== estado.utilizador.id);
        if (!temColegas) {
            mostrarAviso(el.trocaAviso,
                'Ainda não existe nenhum colega com turno atribuído nesta escala, '
                + 'por isso não há troca possível.', 'warning');
        } else {
            mostrarAviso(el.trocaAviso, null);
        }
        el.trocaSubmeter.disabled = !temColegas;
    }

    async function atualizarOpcoesTroca() {
        const escalaId = el.trocaEscala.value;

        if (!escalaId) {
            estado.alocacoes = [];
            preencherOpcoes(el.trocaOrigem, [], 'Escolha primeiro a escala');
            preencherOpcoes(el.trocaDestino, [], 'Escolha primeiro a escala');
            el.trocaSubmeter.disabled = true;
            return;
        }

        el.trocaOrigem.disabled = true;
        el.trocaDestino.disabled = true;
        el.trocaSubmeter.disabled = true;

        try {
            estado.alocacoes = await SuperVisorApi.listarAlocacoes(escalaId) || [];
            const meuId = estado.utilizador ? estado.utilizador.id : null;

            const minhas = estado.alocacoes
                .filter((a) => meuId !== null && a.userId === meuId)
                .map((a) => ({ valor: a.id, rotulo: SuperVisorFormat.rotuloAlocacao(a) }));
            const colegas = estado.alocacoes
                .filter((a) => meuId === null || a.userId !== meuId)
                .map((a) => ({
                    valor: a.id,
                    rotulo: SuperVisorFormat.rotuloAlocacao(a) + ' — ' + (a.userName || 'Colega')
                }));

            preencherOpcoes(el.trocaOrigem, minhas, 'Sem turnos seus nesta escala');
            preencherOpcoes(el.trocaDestino, colegas, 'Sem colegas disponíveis');

            if (minhas.length) {
                el.trocaOrigem.value = String(minhas[0].valor);
            }
            if (colegas.length) {
                el.trocaDestino.value = String(colegas[0].valor);
            }
            validarTroca();
        } catch (erro) {
            estado.alocacoes = [];
            preencherOpcoes(el.trocaOrigem, [], 'Erro ao carregar');
            preencherOpcoes(el.trocaDestino, [], 'Erro ao carregar');
            mostrarAviso(el.trocaAviso, erro.message, 'danger');
        }
    }

    function abrirPedidoTroca(escalaId) {
        el.trocaFormulario.reset();
        mostrarAviso(el.trocaAviso, null);

        preencherOpcoes(
            el.trocaEscala,
            estado.escalas.map((escala) => ({
                valor: escala.id,
                rotulo: (escala.name || 'Escala #' + escala.id)
                    + ' (' + SuperVisorFormat.periodo(escala.initialDate, escala.endDate) + ')'
            })),
            'Escolha a escala');

        if (escalaId && escalaPorId(escalaId)) {
            el.trocaEscala.value = String(escalaId);
        }

        abrirModal('trocaModal');
        atualizarOpcoesTroca();
    }

    async function submeterTroca(evento) {
        evento.preventDefault();

        const origem = el.trocaOrigem.value;
        const destino = el.trocaDestino.value;
        const motivo = el.trocaMotivo.value.trim();

        if (!origem || !destino) {
            mostrarAviso(el.trocaAviso, 'Escolha o turno de origem e o turno de destino.', 'warning');
            return;
        }
        if (origem === destino) {
            mostrarAviso(el.trocaAviso, 'O turno de origem e o de destino têm de ser diferentes.', 'warning');
            return;
        }
        if (motivo.length < MOTIVO_MINIMO) {
            mostrarAviso(el.trocaAviso,
                'Descreva o motivo do pedido (mínimo ' + MOTIVO_MINIMO + ' caracteres).', 'warning');
            return;
        }

        mostrarAviso(el.trocaAviso, null);
        ocupado(el.trocaSubmeter, true, 'A enviar...');

        try {
            await SuperVisorApi.pedirTroca({
                originAllocationId: Number(origem),
                destinationAllocationId: Number(destino),
                reason: motivo
            });
            fecharModal('trocaModal');
            notificar('Pedido de troca registado. Aguarda resposta do colega.', 'sucesso');
        } catch (erro) {
            mostrarAviso(el.trocaAviso, erro.message, 'danger');
            notificar(erro.message, 'erro');
        } finally {
            ocupado(el.trocaSubmeter, false);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Administração: criar e publicar                                     */
    /* ------------------------------------------------------------------ */

    function abrirNovaEscala() {
        el.escalaFormulario.reset();
        mostrarAviso(el.escalaAviso, null);
        abrirModal('novaEscalaModal');
    }

    async function submeterNovaEscala(evento) {
        evento.preventDefault();

        const nome = el.escalaNome.value.trim();
        const inicio = el.escalaInicio.value;
        const fim = el.escalaFim.value;

        if (!nome || !inicio || !fim) {
            mostrarAviso(el.escalaAviso, 'Preencha o nome e o período da escala.', 'warning');
            return;
        }
        if (fim < inicio) {
            mostrarAviso(el.escalaAviso, 'A data final tem de ser igual ou posterior à inicial.', 'warning');
            return;
        }
        if (!estado.utilizador) {
            mostrarAviso(el.escalaAviso, 'Sessão expirada. Inicie sessão novamente.', 'danger');
            return;
        }

        mostrarAviso(el.escalaAviso, null);
        ocupado(el.escalaSubmeter, true, 'A criar...');

        try {
            await SuperVisorApi.criarEscala({
                name: nome,
                initialDate: inicio,
                endDate: fim,
                createdById: estado.utilizador.id
            });
            fecharModal('novaEscalaModal');
            notificar('Escala "' + nome + '" criada em rascunho.', 'sucesso');
            await carregarEscalas();
        } catch (erro) {
            mostrarAviso(el.escalaAviso, erro.message, 'danger');
        } finally {
            ocupado(el.escalaSubmeter, false);
        }
    }

    function confirmarPublicacao(id) {
        const escala = escalaPorId(id);
        estado.escalaA_Publicar = escala;
        el.publicacaoNome.textContent = escala && escala.name ? escala.name : 'Escala #' + id;
        abrirModal('publicacaoModal');
    }

    async function publicarEscala() {
        if (!estado.escalaA_Publicar) {
            return;
        }
        const nome = estado.escalaA_Publicar.name;
        ocupado(el.btnConfirmarPublicacao, true, 'A publicar...');

        try {
            await SuperVisorApi.publicarEscala(estado.escalaA_Publicar.id);
            fecharModal('publicacaoModal');
            notificar('Escala "' + nome + '" publicada.', 'sucesso');
            await carregarEscalas();
        } catch (erro) {
            fecharModal('publicacaoModal');
            notificar(erro.message, 'erro');
        } finally {
            ocupado(el.btnConfirmarPublicacao, false);
            estado.escalaA_Publicar = null;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Administração: gestão de utilizadores                                */
    /* ------------------------------------------------------------------ */

    /**
     * Recarrega a lista de utilizadores ativos e repinta o selector de
     * pessoas. Chamar depois de cadastrar alguém evita que a pessoa nova só
     * apareça depois de recarregar a página.
     */
    async function carregarUtilizadores() {
        try {
            estado.utilizadores = await SuperVisorApi.listarUtilizadores() || [];
        } catch (erro) {
            if (erro.message.indexOf('Sessão') === 0) {
                return;
            }
            console.warn('Utilizadores indisponíveis:', erro);
            return;
        }
        preencherUtilizadores();
    }

    function abrirNovoUsuario() {
        el.utilizadorFormulario.reset();
        mostrarAviso(el.utilizadorAviso, null);
        abrirModal('modalNovoUsuario');
    }

    async function submeterNovoUsuario(evento) {
        evento.preventDefault();

        if (!ehAdministrador()) {
            mostrarAviso(el.utilizadorAviso,
                'Apenas o perfil SUPERVISOR pode cadastar utilizadores.', 'danger');
            return;
        }

        const nome = el.utilizadorNome.value.trim();
        const email = el.utilizadorEmail.value.trim();
        const password = el.utilizadorPassword.value;
        const perfil = el.utilizadorPerfil.value;

        if (!nome || !email || !password) {
            mostrarAviso(el.utilizadorAviso,
                'Preencha o nome, o e-mail e a password.', 'warning');
            return;
        }
        if (password.length < SENHA_MINIMA) {
            mostrarAviso(el.utilizadorAviso,
                'A password tem de ter pelo menos ' + SENHA_MINIMA + ' caracteres.', 'warning');
            return;
        }

        mostrarAviso(el.utilizadorAviso, null);
        ocupado(el.utilizadorSubmeter, true, 'A cadastrar...');

        try {
            await SuperVisorApi.criarUtilizador({
                name: nome,
                email: email,
                password: password,
                profile: perfil,
                active: true
            });
            fecharModal('modalNovoUsuario');
            notificar('Utilizador cadastrado com sucesso!', 'sucesso');
            await carregarUtilizadores();
        } catch (erro) {
            // O 400 do e-mail duplicado chega com a mensagem do servidor, que é
            // o que se quer mostrar; o resto é mostrado tal como vier.
            mostrarAviso(el.utilizadorAviso, erro.message, 'danger');
        } finally {
            ocupado(el.utilizadorSubmeter, false);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Eventos                                                             */
    /* ------------------------------------------------------------------ */

    function registarEventos() {
        el.btnPedirTroca.addEventListener('click', () => abrirPedidoTroca(null));
        el.btnNovaEscala.addEventListener('click', abrirNovaEscala);
        el.btnNovoUsuario.addEventListener('click', abrirNovoUsuario);
        el.utilizadorFormulario.addEventListener('submit', submeterNovoUsuario);

        el.tabelaCorpo.addEventListener('click', (evento) => {
            const detalhes = evento.target.closest('.js-detalhes');
            const trocar = evento.target.closest('.js-trocar');
            const publicar = evento.target.closest('.js-publicar');

            if (detalhes) {
                abrirDetalhes(detalhes.dataset.id);
            } else if (trocar) {
                abrirPedidoTroca(trocar.dataset.id);
            } else if (publicar) {
                confirmarPublicacao(publicar.dataset.id);
            }
        });

        el.btnTrocarDetalhes.addEventListener('click', () => {
            const id = el.btnTrocarDetalhes.dataset.id;
            fecharModal('detalhesModal');
            abrirPedidoTroca(id);
        });

        el.btnEditarAlocacoes.addEventListener('click', () => {
            const id = el.btnTrocarDetalhes.dataset.id;
            fecharModal('detalhesModal');
            abrirGestaoAlocacoes(id);
        });

        el.alocacaoTurno.addEventListener('change', atualizarAjudaTurno);
        el.alocacaoFormulario.addEventListener('submit', submeterAlocacao);
        el.btnLimparEdicaoAlocacao.addEventListener('click', limparFormularioAlocacao);

        el.alocacaoLista.addEventListener('click', (evento) => {
            const editar = evento.target.closest('.js-editar-alocacao');
            if (!editar) {
                return;
            }
            const alocacao = estado.alocacoes.find(
                (a) => String(a.id) === String(editar.dataset.id));
            if (alocacao) {
                mostrarAviso(el.alocacaoAviso, null);
                preencherFormularioAlocacao(alocacao);
            }
        });

        el.trocaEscala.addEventListener('change', atualizarOpcoesTroca);
        el.trocaFormulario.addEventListener('submit', submeterTroca);
        el.escalaFormulario.addEventListener('submit', submeterNovaEscala);
        el.btnConfirmarPublicacao.addEventListener('click', publicarEscala);
    }

    async function iniciar() {
        guardarElementos();

        if (!SuperVisorApi.token()) {
            SuperVisorApi.redirecionarParaLogin();
            return;
        }

        registarEventos();
        await carregarSessao();
        await carregarEscalas();

        if (ehAdministrador()) {
            await carregarVocabularioAlocacoes();
            preencherUtilizadores();
            preencherTurnos();
            preencherAtribuicoes();
        }
    }

    iniciar();
});
