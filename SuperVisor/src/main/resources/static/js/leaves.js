/**
 * Registo e consulta de folgas.
 *
 * <p>A escrita é de supervisor, e o `canEdit` que vem em cada linha é o que a
 * interface usa para decidir se mostra os botões. Não é uma restrição: o
 * servidor volta a verificar o perfil em cada pedido, e um `canEdit` verdadeiro
 * num token adulterado só levaria a um 403.
 *
 * <p>Quem pode ser registado vem de `/api/v1/users`, e a lista traz um campo
 * para o utilizador estar ativo. Um utilizador desativado aparece na lista mas
 * não pode receber folgas — o servidor recusa — por isso a opção é marcada em
 * vez de escondida: esconder significa que a pessoa some da lista e o
 * utilizador que a procurava pensava que nunca existiu.
 */
(() => {

    const el = {};
    let folhas = [];
    let utilizadores = [];
    let aApagar = null;
    let modal = null;
    let modalApagar = null;

    /* ------------------------------------------------------------------ */
    /* Desenho                                                             */
    /* ------------------------------------------------------------------ */

    function linhaVazia(mensagem) {
        return '<tr><td colspan="6" class="text-center py-5 text-muted">'
            + SuperVisorFormat.escapar(mensagem) + '</td></tr>';
    }

    function botao(icone, rotulo, classe, accao, id) {
        return '<button class="btn btn-sm ' + classe + ' ' + accao + '" data-id="' + id
            + '" title="' + SuperVisorFormat.escapar(rotulo) + '">'
            + '<i class="bi bi-' + icone + '"></i></button>';
    }

    function desenhar() {
        if (!folhas.length) {
            el.corpo.innerHTML = linhaVazia('Não há folgas registadas.');
            return;
        }

        el.corpo.innerHTML = folhas.map((folga) => {
            const acoes = folga.canEdit
                ? botao('pencil', 'Editar', 'btn-outline-primary me-1', 'js-editar', folga.id)
                    + botao('trash', 'Remover', 'btn-outline-danger', 'js-apagar', folga.id)
                : '<span class="text-muted small">—</span>';

            // A duração vem pronta do servidor, com as extremidades
            // incluídas. Voltar a contá-la aqui dava um número diferente do que
            // a API devolve sempre que alguém acertasse a regra dos dois
            // lados — e o cartão da pessoa passava a dizer 4 dias quando o
            // registo são 5.
            const duracao = folga.durationDays === 1
                ? '1 dia'
                : folga.durationDays + ' dias';

            return '<tr>'
                + '<td class="fw-semibold">'
                + SuperVisorFormat.escapar(folga.userName || '—') + '</td>'
                + '<td>' + SuperVisorFormat.escapar(SuperVisorFormat.data(folga.startDate)) + '</td>'
                + '<td>' + SuperVisorFormat.escapar(SuperVisorFormat.data(folga.endDate)) + '</td>'
                + '<td><span class="badge bg-light text-dark border">'
                + SuperVisorFormat.escapar(duracao) + '</span></td>'
                + '<td>' + SuperVisorFormat.escapar(folga.reason || '—') + '</td>'
                + '<td class="text-end">' + acoes + '</td>'
                + '</tr>';
        }).join('');
    }

    /* ------------------------------------------------------------------ */
    /* Dados                                                               */
    /* ------------------------------------------------------------------ */

    async function carregar() {
        try {
            folhas = (await SuperVisorApi.listarFolgas(el.filtroUtilizador.value || null)) || [];
            desenhar();
        } catch (erro) {
            el.corpo.innerHTML = linhaVazia(erro.message);
        }
    }

    function opcoesDeUtilizadores(destino, seleccionado) {
        [destino].forEach((select) => {
            select.innerHTML = '';
        });

        utilizadores.forEach((utilizador) => {
            const opcao = document.createElement('option');
            opcao.value = utilizador.id;
            opcao.textContent = utilizador.name || utilizador.email;

            if (utilizador.active === false) {
                opcao.disabled = true;
                opcao.textContent += ' (inativo)';
            }

            if (String(utilizador.id) === String(seleccionado)) {
                opcao.selected = true;
            }

            el.utilizadorFormulario.appendChild(opcao);
        });
    }

    async function carregarUtilizadores() {
        try {
            utilizadores = (await SuperVisorApi.listarUtilizadores()) || [];
        } catch (erro) {
            console.warn('Lista de utilizadores indisponível:', erro);
            utilizadores = [];
        }

        el.filtroUtilizador.innerHTML = '<option value="">Toda a equipa</option>';
        utilizadores.forEach((utilizador) => {
            const opcao = document.createElement('option');
            opcao.value = utilizador.id;
            opcao.textContent = utilizador.name || utilizador.email;
            el.filtroUtilizador.appendChild(opcao);
        });
    }

    async function carregarPerfil() {
        try {
            const utilizador = await SuperVisorApi.utilizadorAtual();
            el.btnNova.classList.toggle('d-none', !utilizador || utilizador.profile !== 'SUPERVISOR');
        } catch (erro) {
            console.warn('Perfil indisponível:', erro);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Formulário                                                          */
    /* ------------------------------------------------------------------ */

    function abrirNova() {
        el.titulo.textContent = 'Registar folga';
        el.id.value = '';
        el.inicio.value = '';
        el.fim.value = '';
        el.motivo.value = '';
        opcoesDeUtilizadores(el.utilizadorFormulario, null);
        el.usuarioAjuda.textContent = 'Apenas utilizadores ativos podem receber folga.';
        el.aviso.classList.add('d-none');
        modal.show();
    }

    function abrirEdicao(folga) {
        el.titulo.textContent = 'Editar folga';
        el.id.value = folga.id;
        el.inicio.value = folga.startDate;
        el.fim.value = folga.endDate;
        el.motivo.value = folga.reason || '';
        opcoesDeUtilizadores(el.utilizadorFormulario, folga.userId);

        // A pessoa não muda numa edição: o servidor ignora o userId do corpo, e
        // uma caixa que deixasse escolher outra pessoa faria o formulário
        // prometer uma coisa que o pedido não faz.
        el.utilizadorFormulario.disabled = true;
        el.usuarioAjuda.textContent = 'A pessoa de uma folga não se altera: só as datas e o motivo.';
        el.aviso.classList.add('d-none');
        modal.show();
    }

    function abrirApagar(folga) {
        aApagar = folga;
        el.apagarTexto.textContent = 'Remover a folga de ' + (folga.userName || '—')
            + ' entre ' + SuperVisorFormat.data(folga.startDate)
            + ' e ' + SuperVisorFormat.data(folga.endDate) + '?';
        modalApagar.show();
    }

    async function submeter(evento) {
        evento.preventDefault();

        // Validação no browser só para dar resposta imediata; a regra que
        // decide é a do servidor, e é a que aparece na mensagem de erro se as
        // duas discordarem.
        if (!el.inicio.value || !el.fim.value) {
            el.aviso.textContent = 'Preencha a data de início e a data de fim.';
            el.aviso.className = 'alert alert-danger';
            return;
        }

        if (el.fim.value < el.inicio.value) {
            el.aviso.textContent = 'A data de fim não pode ser anterior à data de início.';
            el.aviso.className = 'alert alert-danger';
            return;
        }

        const corpo = {
            userId: el.utilizadorFormulario.value,
            startDate: el.inicio.value,
            endDate: el.fim.value,
            reason: el.motivo.value
        };

        el.btnSubmeter.disabled = true;
        try {
            if (el.id.value) {
                await SuperVisorApi.atualizarFolga(el.id.value, corpo);
            } else {
                await SuperVisorApi.criarFolga(corpo);
            }
            modal.hide();
            await carregar();
        } catch (erro) {
            el.aviso.textContent = erro.message;
            el.aviso.className = 'alert alert-danger';
        } finally {
            el.btnSubmeter.disabled = false;
        }
    }

    async function confirmarApagar() {
        if (!aApagar) {
            return;
        }

        el.btnConfirmarApagar.disabled = true;
        try {
            await SuperVisorApi.apagarFolga(aApagar.id);
            modalApagar.hide();
            aApagar = null;
            await carregar();
        } catch (erro) {
            modalApagar.hide();
            el.corpo.innerHTML = linhaVazia(erro.message);
        } finally {
            el.btnConfirmarApagar.disabled = false;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Arranque                                                            */
    /* ------------------------------------------------------------------ */

    function guardarElementos() {
        el.corpo = document.getElementById('folgasCorpo');
        el.filtroUtilizador = document.getElementById('filtroUtilizador');
        el.btnNova = document.getElementById('btnNovaFolga');
        el.formulario = document.getElementById('folgaForm');
        el.titulo = document.getElementById('folgaTitulo');
        el.id = document.getElementById('folgaId');
        el.utilizadorFormulario = document.getElementById('folgaUtilizador');
        el.usuarioAjuda = document.getElementById('folgaUtilizadorAjuda');
        el.inicio = document.getElementById('folgaInicio');
        el.fim = document.getElementById('folgaFim');
        el.motivo = document.getElementById('folgaMotivo');
        el.aviso = document.getElementById('folgaAviso');
        el.btnSubmeter = document.getElementById('btnSubmeterFolga');
        el.apagarTexto = document.getElementById('apagarTexto');
        el.btnConfirmarApagar = document.getElementById('btnConfirmarApagar');
    }

    function registarEventos() {
        el.btnNova.addEventListener('click', abrirNova);
        el.formulario.addEventListener('submit', submeter);
        el.btnConfirmarApagar.addEventListener('click', confirmarApagar);

        el.filtroUtilizador.addEventListener('change', carregar);

        el.corpo.addEventListener('click', (evento) => {
            const editar = evento.target.closest('.js-editar');
            const apagar = evento.target.closest('.js-apagar');

            if (editar) {
                const folga = folhas.find((item) => String(item.id) === editar.dataset.id);
                if (folga) {
                    abrirEdicao(folga);
                }
            } else if (apagar) {
                const folga = folhas.find((item) => String(item.id) === apagar.dataset.id);
                if (folga) {
                    abrirApagar(folga);
                }
            }
        });

        // O select da pessoa fica desactivado numa edição e é preciso rearmá-lo
        // na seguinte, senão a segunda folha a abrir vinha sem poder escolher
        // quem a tem.
        document.getElementById('folgaModal').addEventListener('hidden.bs.modal', () => {
            el.utilizadorFormulario.disabled = false;
            el.id.value = '';
        });
    }

    function iniciar() {
        guardarElementos();
        registarEventos();
        modal = new bootstrap.Modal(document.getElementById('folgaModal'));
        modalApagar = new bootstrap.Modal(document.getElementById('apagarModal'));
        carregarUtilizadores();
        carregarPerfil();
        carregar();
    }

    document.addEventListener('DOMContentLoaded', iniciar);
})();
