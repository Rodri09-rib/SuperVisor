# Projeto SuperVisor.

# Sobre o projeto

O SuperVisor Ã© uma plataforma web para a gestÃ£o, alocaÃ§Ã£o e publicaÃ§Ã£o de escalas/folgas/usuÃ¡rios de trabalho operacionais,
desenhada para automatizar o planejamento de equipes e garantir o cumprimento de regras operacionais.

## Modelo conceitual
<img width="907" height="593" alt="Captura de tela 2026-09-25 162533" src="https://github.com/user-attachments/assets/84a7e9f4-770c-4c5c-a70d-30c9aade618d" />


# Tecnologias utilizadas
Backend: Desenvolvido em Java 21 e Spring Boot 3,
utilizando Spring Security, Spring Data JPA, validaÃ§Ã£o de esquemas SQL via migraÃ§Ãµes
e base de dados PostgreSQL, apoiado por uma suÃ­te de testes unitÃ¡rios e de integraÃ§Ã£o 
(mais de 350 testes automatizados).

Frontend: Interface reativa construÃ­da com Thymeleaf,
Bootstrap 5 e Vanilla JS, integrada via REST API com manipulaÃ§Ã£o de tokens JWT no localStorage 
e componentes modais interativos para gestÃ£o ao vivo.

# Autor

Rodrigo Ribeiro Ferreira

https://www.linkedin.com/in/rodrigo-ribeiro-abbb713aa?utm_source=share_via&utm_content=profile&utm_medium=member_ios

## Novidades (atualizacao atual)

Esta versao aprofunda a gestao de contas, o controlo de aceites de alocacoes e o relatorio de cobertura de escalas.

- Gestao de utilizadores: SUPERVISOR pode listar ativos e inativos (GET /api/v1/users/todos), editar nome/perfil/equipa sem alterar o e-mail, ativar/desativar contas com protecoes (nao desativar o proprio nem o ultimo supervisor ativo) e redefinir password com minimo de 6 caracteres.
- Equipa nos utilizadores: ActiveUserDTO expõe teamGroup, teamGroupLabel e active; teamGroupLabel mostra 'Sem equipa' quando nao ha equipa.
- Aceite de alocacoes: novos endpoints/DTOs permitem ao dono do turno ou ao SUPERVISOR aceitar/recusar uma alocacao (PATCH /api/v1/allocations/{id}/acceptance). Recusar fica bloqueado enquanto houver uma troca PENDING; aceitar nao e bloqueado. Edicoes de alocacao ou aprovacoes de troca repõem o aceite para PENDING.
- Relatorio de cobertura: GET /api/v1/scales/{id}/coverage devolve cobertura por turno/dia, sobreposicoes (reutilizando a regra de conflito) e alocacoes em folgas, com avisos para contas desativadas escaladas. Periodos invertidos devolvem zero slots sem excecao.
- Frontend: UI de gestao de utilizadores (incluir inativos, editar, ativar/desativar, redefinir password), botoes de aceitar/recusar no modal de detalhes com confirmacao, e bloco de cobertura/conflitos no detalhe da escala.
- Testes: suite completa com 761 testes (0 falhas, 0 erros).
