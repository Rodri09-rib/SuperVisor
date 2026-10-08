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
        EQUIPE_A: 'Equipe A',
        EQUIPE_B: 'Equipe B'
    };

    /**
     * Estados de presença de uma célula. As faltas parciais são amarelas e não
     * vermelhas de propósito: a diferença entre falta de meio expediente e
     * falta de dia inteiro é a diferença entre meio ponto e um ponto, e as
     * duas do mesmo tom fazia a grelha parecer que o estado mais grave era o
     * habitual.
     */
    const PRESENCAS = {
        PRESENT: { rotulo: 'Presente', classe: 'bg-success' },
        ABSENT_FULL: { rotulo: 'Falta', classe: 'bg-danger' },
        ABSENT_MORNING: { rotulo: 'Falta (manhã)', classe: 'bg-warning text-dark' },
        ABSENT_AFTERNOON: { rotulo: 'Falta (tarde)', classe: 'bg-warning text-dark' }
    };

    const PRESENCIA_SEM_VALOR = { rotulo: '—', classe: 'bg-light text-dark border' };

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

    function presenca(valor) {
        return PRESENCAS[valor] || PRESENCIA_SEM_VALOR;
    }

    /**
     * Badge de presença de uma célula. O rótulo vem do servidor quando existe
     * — o mapa local é a rede de segurança para uma constante nova, e não a
     * fonte da verdade.
     */
    function badgePresenca(valor, rotuloServidor) {
        const estado = presenca(valor);
        const rotulo = rotuloServidor || estado.rotulo;
        return '<span class="badge ' + estado.classe + '">' + escapar(rotulo) + '</span>';
    }

    /** "1,5" — números do servidor em texto com vírgula, como a página escreve. */
    function numero(valor) {
        if (valor === null || valor === undefined || valor === '') {
            return '—';
        }
        return Number(valor).toLocaleString('pt-BR');
    }

    /**
     * Dívida de compensação de um colaborador.
     *
     * <p>Zero é um estado e não uma ausência: escrever "—" faria quem não deve
     * nada parecer não ter informação, e o objetivo da coluna é precisamente
     * distinguir os dois.
     */
    function badgeCompensacao(valor) {
        const dias = Number(valor || 0);
        if (dias > 0) {
            return '<span class="badge bg-danger">Deve '
                + escapar(numero(dias)) + (dias === 1 ? ' dia' : ' dias') + '</span>';
        }
        return '<span class="badge bg-secondary">Sem dívida</span>';
    }

    /**
     * Saldo de folgas acumuladas.
     *
     * <p>Um saldo negativo é possível e aparece como tal — é o que acontece
     * quando um supervisor antecipa folgas a quem ainda não acumulou nada — e
     * não é escondido com um corte em zero, que faria a página dizer "sem
     * saldo" a quem está devedor.
     */
    function badgeSaldoFolgas(valor) {
        const dias = Number(valor || 0);
        if (dias > 0) {
            return '<span class="badge bg-info text-dark">' + escapar(numero(dias))
                + (dias === 1 ? ' dia' : ' dias') + '</span>';
        }
        if (dias < 0) {
            return '<span class="badge bg-warning text-dark">−'
                + escapar(numero(Math.abs(dias))) + ' dias</span>';
        }
        return '<span class="badge bg-light text-dark border">Sem saldo</span>';
    }

    /**
     * Saldo positivo de um colaborador, no cartão «Saldos de Folgas (Equipa)».
     *
     * <p>Verde e com o verbo «Tem» porque a lista é de quem tem dias ganhos:
     * é um destaque de gestão, e não uma segunda leitura neutra do saldo —
     * essa já existe no cartão individual de quem está a ver.
     */
    function badgeSaldoEquipa(valor) {
        const dias = Number(valor || 0);
        if (dias > 0) {
            return '<span class="badge bg-success">Tem '
                + escapar(numero(dias)) + (dias === 1 ? ' dia' : ' dias') + '</span>';
        }
        return '<span class="badge bg-light text-dark border">Sem saldo</span>';
    }

    /**
     * Chip do tipo de acontecimento no extrato.
     *
     * <p>Falta é amarela e não vermelha, como na grelha de presencialidade:
     * a mesma cor para o mesmo estado em toda a aplicação é o que deixa a
     * página ser lida sem legenda.
     */
    function badgeTipoEvento(tipo, rotulo) {
        const classes = {
            FOLGA: 'bg-secondary',
            FALTA: 'bg-warning text-dark',
            RECOMPENSA: 'bg-success'
        };
        return '<span class="badge ' + (classes[tipo] || 'bg-light text-dark border') + '">'
            + escapar(rotulo || tipo || '—') + '</span>';
    }

    /**
     * Dias de um acontecimento do extrato, com o sinal do efeito dele.
     *
     * <p>O sinal vem do tipo e não do valor, porque o servidor manda a
     * magnitude: folga consome, recompensa acrescenta, falta é dívida de
     * compensação e por isso aparece sem sinal — quem lê já sabe que dívida
     * é para trás, e «−1 dia» ao lado de «Falta» pareceria que a falta tirava
     * dias do saldo de folgas.
     */
    function badgeEventoExtrato(tipo, dias) {
        const valor = Number(dias || 0);
        const sinal = tipo === 'FOLGA' ? '−' : (tipo === 'RECOMPENSA' ? '+' : '');
        const classes = {
            FOLGA: 'bg-light text-dark border',
            FALTA: 'bg-warning text-dark',
            RECOMPENSA: 'bg-success'
        };
        return '<span class="badge ' + (classes[tipo] || 'bg-light text-dark border') + '">'
            + sinal + escapar(numero(valor))
            + (Math.abs(valor) === 1 ? ' dia' : ' dias') + '</span>';
    }

    /** "Equipe A", a partir da constante que o servidor envia. */
    function rotuloEquipa(valor) {
        if (!valor) {
            return 'Sem equipe';
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

    /**
     * Badge "[Feriado]" que se cola a uma data na escala, com a descrição no
     * atributo title — a tooltip mostra o nome do dia sem encher a linha.
     */
    function badgeFeriado(descricao) {
        const titulo = descricao ? ' title="' + escapar(descricao) + '"' : '';
        return '<span class="badge bg-warning text-dark"' + titulo + '>Feriado</span>';
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

    /** Nome do usuário, com recurso ao e-mail quando não há nome. */
    function nomeUtilizador(alocacao) {
        return (alocacao && (alocacao.userName || alocacao.userEmail)) || 'Sem usuário';
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
     * Coluna "Usuário" da tabela de alocações: o nome do usuário
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
        presenca: presenca,
        badgePresenca: badgePresenca,
        numero: numero,
        badgeCompensacao: badgeCompensacao,
        badgeSaldoFolgas: badgeSaldoFolgas,
        badgeSaldoEquipa: badgeSaldoEquipa,
        badgeTipoEvento: badgeTipoEvento,
        badgeEventoExtrato: badgeEventoExtrato,
        rotuloEquipa: rotuloEquipa,
        dataHora: dataHora,
        rotuloAlocacao: rotuloAlocacao,
        nomeUtilizador: nomeUtilizador,
        rotuloAtribuicoes: rotuloAtribuicoes,
        rotuloAtribuicao: rotuloAtribuicao,
        horarioCustom: horarioCustom,
        utilizadorComDetalhes: utilizadorComDetalhes,
        rotuloTurno: rotuloTurno,
        badgeFeriado: badgeFeriado
    };
})();
