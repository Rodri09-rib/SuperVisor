/**
 * Registro e consulta de folgas.
 *
 * <p>A escrita é de supervisor, e o `canEdit` que vem em cada linha é o que a
 * interface usa para decidir se mostra os botões. Não é uma restrição: o
 * servidor volta a verificar o perfil em cada pedido, e um `canEdit` verdadeiro
 * num token adulterado só levaria a um 403.
 *
 * <p>Quem pode ser cadastrado vem de `/api/v1/users`, e a lista traz um campo
 * para o usuário estar ativo. Um usuário desativado aparece na lista mas
 * não pode receber folgas — o servidor recusa — por isso a opção é marcada em
 * vez de escondida: esconder significa que a pessoa some da lista e o
 * usuário que a procurava pensava que nunca existiu.
 *
 * <p>A página tem duas leituras do mesmo cartão de topo. Quem consulta vê o
 * próprio saldo; quem supervisiona vê os saldos de toda a equipa, com quem
 * tem dias para usar — e é esse o centro da visão administrativa: o saldo de
 * cada um ao lado do nome, com o histórico (faltas, folgas e recompensas) a
 * um clique.
 */
(() => {

    const el = {};
    let folhas = [];
    let utilizadores = [];
    let aApagar = null;
    let modal = null;
    let modalApagar = null;
    let modalCompensacao = null;
    let modalHistorico = null;
    let ehSupervisor = false;

    /** A lista já foi lida alguma vez — para redesenhar quando os saldos
     *  chegam depois das folgas, sem pintar «não há folgas» a meio do
     *  primeiro carregamento. */
    let folgasLidas = false;

    /**
     * Saldos de folgas e dívidas de cada pessoa, pela lista de utilizadores.
     *
     * <p>Guarda a lista inteira e não só um mapa: o modal de ajuste precisa
     * das pessoas com o saldo delas ao lado, e ir buscá-las duas vezes para
     * dois diálogos da mesma página seria ler a mesma lista por causa de um
     * <select>.
     */
    let saldos = [];

    /* ------------------------------------------------------------------ */
    /* Desenho                                                             */
    /* ------------------------------------------------------------------ */

    function linhaVazia(mensagem) {
        return '<tr><td colspan="7" class="text-center py-5 text-muted">'
            + SuperVisorFormat.escapar(mensagem) + '</td></tr>';
    }

    function botao(icone, rotulo, classe, accao, id) {
        return '<button class="btn btn-sm ' + classe + ' ' + accao + '" data-id="' + id
            + '" title="' + SuperVisorFormat.escapar(rotulo) + '">'
            + '<i class="bi bi-' + icone + '"></i></button>';
    }

    /** "5 dias" ou "1 dia", a partir do valor do servidor. */
    function rotuloDias(valor) {
        const dias = Number(valor || 0);
        return SuperVisorFormat.numero(dias) + (Math.abs(dias) === 1 ? ' dia' : ' dias');
    }

    function saldoDe(userId) {
        const registo = saldos.find((item) => String(item.id) === String(userId));
        return registo ? Number(registo.accumulatedLeaves || 0) : null;
    }

    function desenhar() {
        if (!folhas.length) {
            el.corpo.innerHTML = linhaVazia('Não há folgas cadastradas.');
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
            // registro são 5.
            const duracao = folga.durationDays === 1
                ? '1 dia'
                : folga.durationDays + ' dias';

            // O custo é outra pergunta e por isso outra coluna: um período de
            // cinco dias de dia inteiro consome cinco, e o mesmo período só de
            // manhã consome dois e meio. Junto, os dois números dizem o que o
            // único deles não consegue.
            const custo = '<span class="badge bg-light text-dark border">'
                + SuperVisorFormat.escapar(rotuloDias(folga.costDays)) + '</span>'
                + (folga.leaveDuration && folga.leaveDuration !== 'FULL_DAY'
                    ? '<div class="small text-muted">' + SuperVisorFormat.escapar(folga.leaveDurationLabel)
                        + '</div>'
                    : '');

            const saldo = saldoDe(folga.userId);

            return '<tr>'
                + '<td class="fw-semibold">'
                + SuperVisorFormat.escapar(folga.userName || '—')
                + (saldo !== null && ehSupervisor
                    ? ' ' + SuperVisorFormat.badgeSaldoFolgas(saldo)
                    : '')
                + '</td>'
                + '<td>' + SuperVisorFormat.escapar(SuperVisorFormat.data(folga.startDate)) + '</td>'
                + '<td>' + SuperVisorFormat.escapar(SuperVisorFormat.data(folga.endDate)) + '</td>'
                + '<td><span class="badge bg-light text-dark border">'
                + SuperVisorFormat.escapar(duracao) + '</span></td>'
                + '<td>' + custo + '</td>'
                + '<td>' + SuperVisorFormat.escapar(folga.reason || '—') + '</td>'
                + '<td class="text-end">' + acoes + '</td>'
                + '</tr>';
        }).join('');
    }

    /* ------------------------------------------------------------------ */
    /* Cartões de saldo                                                    */
    /* ------------------------------------------------------------------ */

    function desenharCartoes() {
        desenharSaldosEquipa();

        const dividas = saldos.filter((item) => Number(item.pendingCompensationDays || 0) > 0);

        el.dividasResumo.textContent = dividas.length
            ? dividas.length + (dividas.length === 1 ? ' pessoa deve' : ' pessoas devem')
                + ' compensação.'
            : 'Ninguém deve compensação neste momento.';

        el.listaDividas.innerHTML = dividas.length
            ? dividas.map((item) => '<div class="d-flex justify-content-between align-items-center gap-2 border-top pt-2">'
                    + '<span class="small">'
                    + SuperVisorFormat.escapar(item.name || item.email)
                    + ' ' + SuperVisorFormat.badgeCompensacao(item.pendingCompensationDays)
                    + '</span>'
                    + '<div class="d-flex gap-1">'
                    + botaoHistorico(item.id)
                    + '<button type="button" class="btn btn-outline-secondary btn-sm js-ajustar-divida"'
                    + ' data-id="' + item.id + '">Ajustar</button></div></div>').join('')
            : '<div class="small text-muted">A dívida é gerada pelas faltas registadas na'
                + ' página de presencialidade e abatida por aqui.</div>';
    }

    /** Ícone de histórico, usado nas duas listas do cartão de topo. */
    function botaoHistorico(userId) {
        return '<button type="button" class="btn btn-outline-secondary btn-sm js-historico"'
            + ' data-id="' + userId + '"'
            + ' title="Histórico de faltas, folgas e recompensas">'
            + '<i class="bi bi-eye"></i></button>';
    }

    /**
     * A lista de quem tem saldo positivo — o corpo do cartão quando quem olha
     * é um supervisor.
     *
     * <p>Só quem tem dias ganhos entra: uma lista com toda a equipa à mesma
     * altura transformava um destaque num segundo diretório de nomes, e é
     * «quem pode tirar folga já» a pergunta que este cartão responde. Quem
     * deve compensação continua no cartão ao lado, que é a outra metade da
     * mesma pergunta.
     */
    function desenharSaldosEquipa() {
        if (!ehSupervisor) {
            return;
        }

        const comSaldo = saldos.filter((item) => Number(item.accumulatedLeaves || 0) > 0);

        el.saldoEquipaResumo.textContent = comSaldo.length
            ? comSaldo.length
                + (comSaldo.length === 1 ? ' colaborador com saldo positivo.'
                    : ' colaboradores com saldo positivo.')
            : 'Ninguém tem saldo positivo acumulado.';

        el.listaSaldosEquipa.innerHTML = comSaldo.length
            ? comSaldo.map((item) => '<div class="d-flex justify-content-between align-items-center gap-2 border-top pt-2">'
                    + '<span class="small">'
                    + SuperVisorFormat.escapar(item.name || item.email)
                    + ' ' + SuperVisorFormat.badgeSaldoEquipa(item.accumulatedLeaves)
                    + '</span>'
                    + botaoHistorico(item.id) + '</div>').join('')
            : '<div class="small text-muted">Os dias ganhos com turnos de fim de semana'
                + ' aparecem aqui; as folgas registadas consomem-nos.</div>';
    }

    /* ------------------------------------------------------------------ */
    /* Histórico (extrato) de um colaborador                               */
    /* ------------------------------------------------------------------ */

    /**
     * Abre o histórico de uma pessoa: de onde vêm os dias que os cartões
     * mostram.
     *
     * <p>O nome vem da lista de saldos já carregada e não de outro pedido: o
     * botão só existe ao lado do nome, e buscá-lo outra vez seria uma ida à
     * rede para um texto que está no ecrã.
     */
    async function abrirHistorico(userId) {
        const registo = saldos.find((item) => String(item.id) === String(userId));
        el.historicoTitulo.textContent = 'Histórico — '
            + (registo ? (registo.name || registo.email) : 'colaborador');

        el.historicoCorpo.innerHTML = '<div class="text-center py-4 text-muted">'
            + '<div class="spinner-border text-primary" role="status">'
            + '<span class="visually-hidden">A carregar o histórico...</span></div>'
            + '<div class="mt-2">A carregar o histórico...</div></div>';
        modalHistorico.show();

        try {
            const eventos = (await SuperVisorApi.extratoUtilizador(userId)) || [];
            el.historicoCorpo.innerHTML = eventos.length
                ? eventos.map(linhaDeEvento).join('')
                : '<div class="small text-muted">Ainda não há faltas, folgas nem recompensas'
                    + ' registadas nesta conta.</div>';
        } catch (erro) {
            el.historicoCorpo.innerHTML = '<div class="alert alert-danger mb-0">'
                + SuperVisorFormat.escapar(erro.message) + '</div>';
        }
    }

    /** Uma linha do extrato: data e tipo, o que aconteceu, e os dias. */
    function linhaDeEvento(evento) {
        return '<div class="d-flex justify-content-between align-items-start gap-2 border-top pt-2 py-2">'
            + '<div class="small">'
            + '<div class="fw-semibold">'
            + SuperVisorFormat.escapar(SuperVisorFormat.data(evento.date))
            + ' ' + SuperVisorFormat.badgeTipoEvento(evento.tipo, evento.tipoRotulo)
            + '</div>'
            + '<div class="text-muted">' + SuperVisorFormat.escapar(evento.descricao) + '</div>'
            + '</div>'
            + SuperVisorFormat.badgeEventoExtrato(evento.tipo, evento.dias)
            + '</div>';
    }

    async function carregarSaldos() {
        if (!ehSupervisor) {
            saldos = [];
            return;
        }

        try {
            saldos = (await SuperVisorApi.listarUtilizadores()) || [];
        } catch (erro) {
            console.warn('Saldos indisponíveis:', erro);
            saldos = [];
        }
        desenharCartoes();

        // Os saldos chegam à parte das folgas e as duas respostas não estão
        // sincronizadas: sem este redesenho, a tabela fica sem os badges de
        // saldo até ao próximo filtro.
        if (folgasLidas) {
            desenhar();
        }
        // O cartão de equipa também precisa de ser redesenhado quando a lista
        // de utilizadores muda, porque uma pessoa ganhou dias no fecho de
        // uma escala noutro separador ou porque foi ajustada a compensação.
        desenharSaldosEquipa();
    }

    /**
     * O saldo da própria pessoa, no cartão do topo — que é de qualquer um,
     * não só da supervisão.
     *
     * <p>Vem do perfil em vez da lista de utilizadores: é uma chamada que já
     * se faz para o botão de nova folga, e usar o mesmo pedido evita depender
     * de um endpoint de lista para o único número que é sempre igual, seja
     * quem for a olhar.
     */
    async function carregarMeuSaldo() {
        try {
            const utilizador = await SuperVisorApi.utilizadorAtual();
            if (!utilizador) {
                return;
            }
            ehSupervisor = utilizador.profile === 'SUPERVISOR';
            el.cartaoCompensacao.classList.toggle('d-none', !ehSupervisor);
            el.btnNova.classList.toggle('d-none', !ehSupervisor);

            // O cartão de topo é o mesmo para os dois perfis e só o corpo
            // muda: um supervisor passa a ver os saldos da equipa — é a sua
            // visão administrativa —, e o saldo próprio dele continua lá,
            // na lista, como o de qualquer outra pessoa.
            el.saldoTitulo.innerHTML = ehSupervisor
                ? '<i class="bi bi-people me-1"></i> Saldos de Folgas (Equipa)'
                : '<i class="bi bi-wallet2 me-1"></i> O seu saldo de folgas';
            el.saldoIndividual.classList.toggle('d-none', ehSupervisor);
            el.saldoEquipa.classList.toggle('d-none', !ehSupervisor);

            const saldo = Number(utilizador.accumulatedLeaves || 0);
            el.saldoFolgasValor.textContent = saldo === 0
                ? 'Sem saldo'
                : SuperVisorFormat.numero(saldo) + (Math.abs(saldo) === 1 ? ' dia' : ' dias');
            el.saldoFolgasDetalhe.textContent = saldo < 0
                ? 'Saldo negativo: folgas antecipadas ainda não compensadas.'
                : 'Cada folga de dia inteiro consome um dia deste saldo.';
        } catch (erro) {
            console.warn('Perfil indisponível:', erro);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Dados                                                               */
    /* ------------------------------------------------------------------ */

    async function carregar() {
        try {
            folhas = (await SuperVisorApi.listarFolgas(el.filtroUtilizador.value || null)) || [];
            folgasLidas = true;
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
            console.warn('Lista de usuários indisponível:', erro);
            utilizadores = [];
        }

        el.filtroUtilizador.innerHTML = '<option value="">Toda a equipe</option>';
        utilizadores.forEach((utilizador) => {
            const opcao = document.createElement('option');
            opcao.value = utilizador.id;
            opcao.textContent = utilizador.name || utilizador.email;
            el.filtroUtilizador.appendChild(opcao);
        });
    }

    /* ------------------------------------------------------------------ */
    /* Formulário                                                          */
    /* ------------------------------------------------------------------ */

    function abrirNova() {
        el.titulo.textContent = 'Registrar folga';
        el.id.value = '';
        el.inicio.value = '';
        el.fim.value = '';
        el.motivo.value = '';
        el.duracao.value = 'FULL_DAY';
        opcoesDeUtilizadores(el.utilizadorFormulario, null);
        el.usuarioAjuda.textContent = 'Apenas usuários ativos podem receber folga.';
        el.aviso.classList.add('d-none');
        modal.show();
    }

    function abrirEdicao(folga) {
        el.titulo.textContent = 'Editar folga';
        el.id.value = folga.id;
        el.inicio.value = folga.startDate;
        el.fim.value = folga.endDate;
        el.motivo.value = folga.reason || '';
        el.duracao.value = folga.leaveDuration || 'FULL_DAY';
        opcoesDeUtilizadores(el.utilizadorFormulario, folga.userId);

        // A pessoa não muda numa edição: o servidor ignora o userId do corpo, e
        // uma caixa que deixasse escolher outra pessoa faria o formulário
        // prometer uma coisa que o pedido não faz.
        el.utilizadorFormulario.disabled = true;
        el.usuarioAjuda.textContent = 'A pessoa de uma folga não se altera: só as datas, a duração e o motivo.';
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

        // Meia jornada só num dia. A regra é do servidor e é ele que a
        // aplica, mas dizê-la aqui evita um pedido que se sabe que vai ser
        // recusado — e a mensagem é a mesma das duas partes.
        if (el.duracao.value !== 'FULL_DAY' && el.inicio.value !== el.fim.value) {
            el.aviso.textContent = 'Uma folga de meia jornada tem de ser num único dia: '
                + 'escolha o mesmo dia de início e de fim.';
            el.aviso.className = 'alert alert-danger';
            return;
        }

        const corpo = {
            userId: el.utilizadorFormulario.value,
            startDate: el.inicio.value,
            endDate: el.fim.value,
            reason: el.motivo.value,
            leaveDuration: el.duracao.value
        };

        el.btnSubmeter.disabled = true;
        try {
            if (el.id.value) {
                await SuperVisorApi.atualizarFolga(el.id.value, corpo);
            } else {
                await SuperVisorApi.criarFolga(corpo);
            }
            modal.hide();
            await Promise.all([carregar(), carregarMeuSaldo(), carregarSaldos()]);
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
            // Apagar devolve os dias ao saldo de quem a tinha, por isso os
            // cartões são relidos junto com a lista.
            await Promise.all([carregar(), carregarMeuSaldo(), carregarSaldos()]);
        } catch (erro) {
            modalApagar.hide();
            el.corpo.innerHTML = linhaVazia(erro.message);
        } finally {
            el.btnConfirmarApagar.disabled = false;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Ajuste de compensação                                               */
    /* ------------------------------------------------------------------ */

    /** Saldo atual da pessoa escolhida, escrito em baixo do <select>. */
    function escreverSaldoEscolhido() {
        const registo = saldos.find((item) => String(item.id) === String(el.compensacaoUtilizador.value));
        if (!registo) {
            el.compensacaoSaldo.textContent = '';
            return;
        }
        el.compensacaoSaldo.innerHTML = 'Dívida atual: '
            + SuperVisorFormat.badgeCompensacao(registo.pendingCompensationDays);
    }

    function abrirCompensacao(userId) {
        el.compensacaoAviso.classList.add('d-none');
        el.compensacaoValor.value = '';
        el.compensacaoUtilizador.innerHTML = '';

        saldos.forEach((registo) => {
            const opcao = document.createElement('option');
            opcao.value = registo.id;
            opcao.textContent = registo.name || registo.email;
            el.compensacaoUtilizador.appendChild(opcao);
        });

        if (userId) {
            el.compensacaoUtilizador.value = userId;
        }

        escreverSaldoEscolhido();
        modalCompensacao.show();
    }

    async function submeterCompensacao(evento) {
        evento.preventDefault();

        const delta = Number(el.compensacaoValor.value);
        if (!el.compensacaoUtilizador.value || !el.compensacaoValor.value || Number.isNaN(delta)) {
            el.compensacaoAviso.textContent = 'Escolha a pessoa e indique os dias do ajuste.';
            el.compensacaoAviso.className = 'alert alert-danger';
            return;
        }

        el.btnConfirmarCompensacao.disabled = true;
        try {
            await SuperVisorApi.ajustarCompensacao(el.compensacaoUtilizador.value, delta);
            modalCompensacao.hide();
            await Promise.all([carregarSaldos(), carregar()]);
        } catch (erro) {
            el.compensacaoAviso.textContent = erro.message;
            el.compensacaoAviso.className = 'alert alert-danger';
        } finally {
            el.btnConfirmarCompensacao.disabled = false;
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
        el.titulo = document.getElementById('folgaModalTitulo');
        el.id = document.getElementById('folgaId');
        el.utilizadorFormulario = document.getElementById('folgaUtilizador');
        el.usuarioAjuda = document.getElementById('folgaUtilizadorAjuda');
        el.inicio = document.getElementById('folgaInicio');
        el.fim = document.getElementById('folgaFim');
        el.duracao = document.getElementById('folgaDuracao');
        el.motivo = document.getElementById('folgaMotivo');
        el.aviso = document.getElementById('folgaAviso');
        el.btnSubmeter = document.getElementById('btnSubmeterFolga');
        el.apagarTexto = document.getElementById('apagarTexto');
        el.btnConfirmarApagar = document.getElementById('btnConfirmarApagar');
        el.saldoFolgasValor = document.getElementById('saldoFolgasValor');
        el.saldoFolgasDetalhe = document.getElementById('saldoFolgasDetalhe');
        el.cartaoCompensacao = document.getElementById('cartaoCompensacao');
        el.dividasResumo = document.getElementById('dividasResumo');
        el.listaDividas = document.getElementById('listaDividas');
        el.btnAjustarCompensacao = document.getElementById('btnAjustarCompensacao');
        el.compensacaoForm = document.getElementById('compensacaoForm');
        el.compensacaoUtilizador = document.getElementById('compensacaoUtilizador');
        el.compensacaoSaldo = document.getElementById('compensacaoSaldo');
        el.compensacaoValor = document.getElementById('compensacaoValor');
        el.compensacaoAviso = document.getElementById('compensacaoAviso');
        el.btnConfirmarCompensacao = document.getElementById('btnConfirmarCompensacao');
        el.historicoTitulo = document.getElementById('historicoTitulo');
        el.historicoCorpo = document.getElementById('historicoCorpo');
        el.saldoTitulo = document.getElementById('saldoTitulo');
        el.saldoIndividual = document.getElementById('saldoIndividual');
        el.saldoEquipa = document.getElementById('saldoEquipa');
        el.saldoEquipaResumo = document.getElementById('saldoEquipaResumo');
        el.listaSaldosEquipa = document.getElementById('listaSaldosEquipa');
    }

    function registarEventos() {
        el.btnNova.addEventListener('click', abrirNova);
        el.formulario.addEventListener('submit', submeter);
        el.btnConfirmarApagar.addEventListener('click', confirmarApagar);
        el.btnAjustarCompensacao.addEventListener('click', () => abrirCompensacao(null));
        el.compensacaoForm.addEventListener('submit', submeterCompensacao);
        el.compensacaoUtilizador.addEventListener('change', escreverSaldoEscolhido);

        el.filtroUtilizador.addEventListener('change', carregar);

        // As dívidas listadas no cartão abrem o mesmo diálogo já com a pessoa
        // escolhida: o botão existe ao lado do nome dela, e escolher de novo
        // seria pedir duas vezes a mesma informação.
        el.listaDividas.addEventListener('click', (evento) => {
            const alvo = evento.target.closest('.js-ajustar-divida');
            if (alvo) {
                abrirCompensacao(alvo.dataset.id);
            }
        });

        // Os botões de histórico estão no cartão de topo (dividas e saldos da
        // equipa) e também podem chegar à tabela — o ouvinte acima cobre a
        // tabela; este cobre o cartão de dívidas e a lista da equipa, que é
        // uma div, não a tbody.
        document.getElementById('saldosCartoes').addEventListener('click', (evento) => {
            const alvo = evento.target.closest('.js-historico');
            if (alvo) {
                abrirHistorico(alvo.dataset.id);
            }
        });

        el.corpo.addEventListener('click', (evento) => {
            const editar = evento.target.closest('.js-editar');
            const apagar = evento.target.closest('.js-apagar');
            const historico = evento.target.closest('.js-historico');

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
            } else if (historico) {
                abrirHistorico(historico.dataset.id);
            }
        });

        // O select da pessoa fica desativado numa edição e é preciso rearmá-lo
        // na seguinte, senão a segunda folha a abrir vinha sem poder escolher
        // quem a tem.
        document.getElementById('folgaModal').addEventListener('hidden.bs.modal', () => {
            el.utilizadorFormulario.disabled = false;
            el.id.value = '';
        });

        // Voltar ao separador refaz a leitura do saldo. Concluir uma escala
        // credita dias do outro lado — noutro separador, noutro dispositivo —
        // e esta página, que já estava aberta, não recebe aviso nenhum: sem
        // esta releitura, o cartão continuaria a mostrar o saldo de quando a
        // página abriu, que é exatamente o valor errado a seguir a um fecho
        // de escala.
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState !== 'visible') {
                return;
            }
            carregarMeuSaldo().then(() => carregarSaldos());
        });
    }

    function iniciar() {
        guardarElementos();
        registarEventos();
        modal = new bootstrap.Modal(document.getElementById('folgaModal'));
        modalApagar = new bootstrap.Modal(document.getElementById('apagarModal'));
        modalCompensacao = new bootstrap.Modal(document.getElementById('compensacaoModal'));
        modalHistorico = new bootstrap.Modal(document.getElementById('historicoModal'));
        carregarUtilizadores();

        // O perfil é lido primeiro porque decide o que existe na página: a
        // lista de saldos e o cartão de dívidas são da supervisão, e pedi-los
        // a quem não os pode ver seria um 403 na consola à abertura.
        carregarMeuSaldo().then(() => Promise.all([carregarSaldos(), carregar()]));
    }

    document.addEventListener('DOMContentLoaded', iniciar);
})();
