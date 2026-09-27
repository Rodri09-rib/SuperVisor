/**
 * Formatação e composição de elementos para o dashboard.
 *
 * Todas as funções de texto devolvem HTML já escapado: os nomes das escalas e
 * os motivos são livres e não podem ser interpolados sem isso.
 */
const SuperVisorFormat = (() => {

    const ESTADOS_ESCALA = {
        DRAFT: { rotulo: 'Rascunho', classe: 'bg-warning text-dark' },
        PUBLISHED: { rotulo: 'Publicada', classe: 'bg-success' },
        COMPLETED: { rotulo: 'Concluída', classe: 'bg-secondary' },
        CLOSED: { rotulo: 'Fechada', classe: 'bg-secondary' },

    };

    const ESTADO_SEM_VALOR = { rotulo: 'Sem estado', classe: 'bg-light text-dark border' };

    const ESTADOS_ALOCACAO = {
        PENDING: { rotulo: 'Pendente', classe: 'bg-warning text-dark' },
        ACCEPTED: { rotulo: 'Aceite', classe: 'bg-success' },
        REJECTED: { rotulo: 'Rejeitado', classe: 'bg-danger' }
    };

    const ESTADO_ALOCACAO_SEM_VALOR = { rotulo: 'Pendente', classe: 'bg-warning text-dark' };

    /**
     * Estados de um pedido de troca. São diferentes dos de uma alocação: um
     * pedido de troca está à espera de alguém responder, não à espera de a
     * escala ser publicada, e usar o badge de "Pendente" da alocação por
     * preguiça de fazer um mapa novo fazia os dois estados indistinguíveis na
     * mesma página.
     */
    const ESTADOS_TROCA = {
        PENDING: { rotulo: 'Por responder', classe: 'bg-warning text-dark' },
        APPROVED: { rotulo: 'Aprovada', classe: 'bg-success' },
        REJECTED: { rotulo: 'Recusada', classe: 'bg-danger' }
    };

    const ESTADO_TROCA_SEM_VALOR = { rotulo: 'Desconhecido', classe: 'bg-light text-dark border' };

    /**
     * Modalidade de uma célula da escala. As cores são as do mesmo código em
     * toda a aplicação: verde para quem está no escritório, azul para quem está
     * em casa, para que a grelha se leia sem legenda.
     */
    const MODALIDADES = {
        PRESENCIAL: { rotulo: 'Presencial', classe: 'bg-success' },
        HOME_OFFICE: { rotulo: 'Home Office', classe: 'bg-info text-dark' }
    };

    const EQUIPAS = {
        EQUIPE_A: 'Equipa A',
        EQUIPE_B: 'Equipa B'
    };

    /** Rótulos de atribuições, por constante. O servidor é a fonte da verdade
     *  (GET /api/v1/assignments); este mapa é apenas a rede de segurança para
     *  uma constante nova não aparecer crua na tabela. */
    const ATRIBUICOES = {
        REDES_SOCIAIS: 'Redes Sociais',
        CELULAR_MARINAS: 'Celular da Marinas'
    };

    function escapar(valor) {
        if (valor === null || valor === undefined) {
            return '';
        }
        return String(valor)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    /** Converte "2025-10-01" (ISO, como o Jackson envia) em "01/10/2025". */
    function data(valor) {
        if (!valor) {
            return '—';
        }
        const partes = String(valor).slice(0, 10).split('-');
        if (partes.length !== 3) {
            return escapar(valor);
        }
        return partes[2] + '/' + partes[1] + '/' + partes[0];
    }

    function periodo(inicio, fim) {
        if (!inicio && !fim) {
            return '—';
        }
        return data(inicio) + ' a ' + data(fim);
    }

    /** "08:00:00" (LocalTime) passa a "08h00". */
    function hora(valor) {
        if (!valor) {
            return null;
        }
        const partes = String(valor).split(':');
        if (partes.length < 2) {
            return escapar(valor);
        }
        return partes[0] + 'h' + partes[1];
    }

    function estadoEscala(valor) {
        return ESTADOS_ESCALA[valor] || ESTADO_SEM_VALOR;
    }

    function estadoAlocacao(valor) {
        return ESTADOS_ALOCACAO[valor] || ESTADO_ALOCACAO_SEM_VALOR;
    }

    function estadoTroca(valor) {
        return ESTADOS_TROCA[valor] || ESTADO_TROCA_SEM_VALOR;
    }

    function modalidade(valor) {
        return MODALIDADES[valor] || { rotulo: valor || '—', classe: 'bg-light text-dark border' };
    }

    function badgeTroca(valor) {
        const estado = estadoTroca(valor);
        return '<span class="badge ' + estado.classe + '">' + escapar(estado.rotulo) + '</span>';
    }

    function badgeModalidade(valor) {
        const mod = modalidade(valor);
        return '<span class="badge ' + mod.classe + '">' + escapar(mod.rotulo) + '</span>';
    }

    /** "Equipa A", a partir da constante que o servidor envia. */
    function rotuloEquipa(valor) {
        if (!valor) {
            return 'Sem equipa';
        }
        return EQUIPAS[valor] || valor;
    }

    /**
     * Data e hora de um carimbo ISO, em "16/03/2026 14h00".
     *
     * <p>O histórico de trocas mostra a data com a hora porque dois pedidos do
     * mesmo dia precisam de ordem, e a coluna de data sozinha não a dava. O
     * carimbo chega com desvio horário, por isso o valor é lido como as partes
     * do texto: converter para Date e formatá-lo devolveria a hora no fuso do
     * navegador, que num cliente fora do fuso do servidor deslocava o pedido
     * para o dia anterior.
     */
    function dataHora(valor) {
        if (!valor) {
            return '—';
        }
        const texto = String(valor);
        return data(texto) + ' ' + hora(texto.slice(11, 19));
    }

    function badgeEscala(valor) {
        const estado = estadoEscala(valor);
        return '<span class="badge ' + estado.classe + '">' + escapar(estado.rotulo) + '</span>';
    }

    function badgeAlocacao(valor) {
        const estado = estadoAlocacao(valor);
        return '<span class="badge ' + estado.classe + '">' + escapar(estado.rotulo) + '</span>';
    }

    /** Texto legível de uma alocação, usado nos <option> e nas listas. */
    function rotuloAlocacao(alocacao) {
        const partes = [];
        const turno = alocacao.shiftAcronym
            ? alocacao.shiftAcronym
            : 'Turno por atribuir';

        partes.push(turno);

        if (alocacao.shiftDayOfTheWeek) {
            partes[partes.length - 1] += ' (' + alocacao.shiftDayOfTheWeek + ')';
        }

        const inicio = hora(alocacao.shiftStartTime);
        const fim = hora(alocacao.shiftEndTime);
        if (inicio && fim) {
            partes[partes.length - 1] += ' ' + inicio + '–' + fim;
        }

        if (alocacao.specificDate) {
            partes.unshift(data(alocacao.specificDate));
        }

        partes.push('#' + alocacao.id);
        return partes.join(' · ');
    }

    /** Nome do utilizador, com recurso ao e-mail quando não há nome. */
    function nomeUtilizador(alocacao) {
        return (alocacao && (alocacao.userName || alocacao.userEmail)) || 'Sem utilizador';
    }

    /** "Redes Sociais + Celular da Marinas", ou null se não houver. */
    function rotuloAtribuicoes(alocacao) {
        const rotulos = (alocacao && alocacao.assignmentLabels) || [];
        return rotulos.length ? rotulos.join(' + ') : null;
    }

    /** Rótulo de uma atribuição isolada, a partir da constante. */
    function rotuloAtribuicao(valor) {
        return ATRIBUICOES[valor] || valor;
    }

    /** "10h30 às 14h30", ou null se a alocação não tiver horário especial. */
    function horarioCustom(alocacao) {
        if (!alocacao || !alocacao.customSchedule) {
            return null;
        }
        const inicio = hora(alocacao.customStartTime);
        const fim = hora(alocacao.customEndTime);
        if (!inicio || !fim) {
            return null;
        }
        return inicio + ' às ' + fim;
    }

    /**
     * Coluna "Utilizador" da tabela de alocações: o nome do utilizador
     * cadastrado, seguido das atribuições e do horário especial quando
     * existirem. Ex.: "Rodrigo (Redes Sociais + Celular da Marinas)" ou
     * "Luã (10h30 às 14h30)".
     */
    function utilizadorComDetalhes(alocacao) {
        const nome = escapar(nomeUtilizador(alocacao));
        const atribuicoes = rotuloAtribuicoes(alocacao);
        const custom = horarioCustom(alocacao);

        const extras = [];
        if (atribuicoes) {
            extras.push(escapar(atribuicoes));
        }
        if (custom) {
            extras.push(escapar(custom));
        }

        if (!extras.length) {
            return nome;
        }
        return nome + ' <span class="text-muted">(' + extras.join(' · ') + ')</span>';
    }

    /** "T2 (11h00–15h00) - Sábado", para as listas de troca. */
    function rotuloTurno(alocacao) {
        const turno = (alocacao && alocacao.shiftAcronym) || 'Turno por atribuir';
        const dia = alocacao && alocacao.shiftDayOfTheWeek;
        const inicio = hora(alocacao && alocacao.shiftStartTime);
        const fim = hora(alocacao && alocacao.shiftEndTime);

        let rotulo = dia ? turno + ' (' + dia + ')' : turno;
        if (inicio && fim) {
            rotulo += ' ' + inicio + '–' + fim;
        }
        return rotulo;
    }

    return {
        escapar: escapar,
        data: data,
        periodo: periodo,
        hora: hora,
        estadoEscala: estadoEscala,
        estadoAlocacao: estadoAlocacao,
        estadoTroca: estadoTroca,
        modalidade: modalidade,
        badgeEscala: badgeEscala,
        badgeAlocacao: badgeAlocacao,
        badgeTroca: badgeTroca,
        badgeModalidade: badgeModalidade,
        rotuloEquipa: rotuloEquipa,
        dataHora: dataHora,
        rotuloAlocacao: rotuloAlocacao,
        nomeUtilizador: nomeUtilizador,
        rotuloAtribuicoes: rotuloAtribuicoes,
        rotuloAtribuicao: rotuloAtribuicao,
        horarioCustom: horarioCustom,
        utilizadorComDetalhes: utilizadorComDetalhes,
        rotuloTurno: rotuloTurno
    };
})();
