<div align="center">

# SuperVisor

**Gerenciamento, alocação e publicação de escalas, folgas e usuários**

[![Licença MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://github.com/Rodri09-rib/SuperVisor/blob/main/LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791.svg)](https://www.postgresql.org/)

</div>

## Sobre

O SuperVisor é uma plataforma web que nasceu da nescessidade de uma equipe de SAC
que trabalho, onde utilizavam-se apenas planilhas de excel. Comecei a desenvolve-lo pela diversão
e também por ser um projeto acadêmico, mas com o tempo foi tomando uma forma mais complexa, e estou deixando publico 
para equipes de trabalho operacional que precisam de um modelo mais atual e moderno de
montar escalas de fim de semana ou gerais, gerir ausências...


O nome vem do **`SuperVisor`**, o perfil de supervisão: é quem cria escalas, gere
alocações e responde pelo estado das contas.

Basicamente o programa soluciona a necessidade de planilhas e centraliza a função
que o supervisor da equipe tem. Como a gestão dos horários, folgas, controles da presencialidade e etc. 

## Funcionalidades

**Escalas e alocações**

- Criação, listagem e publicação de escalas, com os estados `DRAFT`, `PUBLISHED` e
  `COMPLETED`. Só um rascunho pode ser publicado.
- Alocação de pessoas a turnos, com atribuições especiais e horário personalizado por
  alocação.
- Bloqueio de duplo agendamento na criação e na edição: a mesma pessoa não fica com dois
  turnos sobrepostos. A comparação de intervalos é meio-aberta, pelo que T2 (11h–15h)
  bloqueia T1 (08h–12h) e T3 (12h–16h), mas T1 e T3 não se cruzam entre si.
- Períodos com data de início posterior à de fim são aceitos e simplesmente não geram
  slots de fim de semana — a escala existe, está vazia.

**Relatório de cobertura e conflitos**

Um único endpoint devolve as três respostas, porque mudam juntas e obrigariam o frontend
a fazer três pedidos para pintar três blocos que se atualizam em conjunto.

- Cobertura por turno e dia, percentagem de turnos com alguém e listagem dos turnos vazios.
- Sobreposições detetadas no estado atual da base, incluindo as que vieram de inserções
  anteriores à regra.
- Alocações que caem num dia de folga da mesma pessoa.
- Aviso (não conflito) para alocações de contas desativadas: são turnos que ninguém vai
  cobrir.

**Gerenciamento de usuários**

- Listagem de contas ativas e, separadamente, de todas as contas, incluindo as inativas 
  uma conta desativada continua a existir e tem de poder ser encontrada para ser reativada.
- Edição de nome, perfil e equipe. **O e-mail não é editável**: é a identidade da conta e
  o que vai dentro do token JWT; alterá-lo deixaria no ar todos os tokens já emitidos.
- Ativação e desativação com duas proteções: ninguém desativa a própria conta, e o último
  supervisor ativo não pode ser desativado a aplicação ficaria sem ninguém capaz de
  gerenciar usuários.
- Redefinição de senha, com mínimo de 6 caracteres.

**Aceitação de turnos**

Quem fica escalado tem de responder ao turno, e o estado é reposto sempre que o turno
muda de forma.

- O dono do turno e a supervisão podem aceitar ou recusar; os restantes recebem 403.
- Recusar é bloqueado enquanto existir uma troca pendente sobre esse turno, para não
  deixar uma troca órfã. Aceitar não é bloqueado: aceitar um turno que se vai trocar não
  é contraditório.
- Editar a alocação ou aprovar uma troca repõe o aceite para pendente, porque quem tinha
  aceite estava a aceitar outra coisa.

**Trocas de turno**

- Pedido de troca com destino e motivo opcional.
- Resposta apenas por quem a troca foi pedida ou pela supervisão.
- Bloqueio de pedidos duplicados no mesmo sentido e em estado pendente. O sentido
  contrário e os pedidos já respondidos são permitidos.
- Histórico com filtros por estado, pessoa e intervalo de datas. Um analista vê apenas o
  que lhe diz respeito; a supervisão vê tudo.

**Folgas e modalidades de trabalho**

- CRUD de folgas por pessoa e período.
- Geração automática da grelha de presencialidade (presencial / home office) a partir do
  número de semana ISO e da equipe de cada pessoa. Só dias úteis: sábado e domingo ficam de
  fora porque a grelha de home office se aplica à semana de trabalho.

## Stack

| Camada | Tecnologias |
| --- | --- |
| Backend | Java 21, Spring Boot 3.3.5, Spring Web, Spring Data JPA, Spring Security, Bean Validation |
| Autenticação | JWT stateless (java-jwt 4.4.0), `BCryptPasswordEncoder` |
| Base de dados | PostgreSQL 16, migrações SQL idempotentes executadas no arranque |
| Frontend | Thymeleaf, Bootstrap 5.3, Vanilla JS, `fetch` com token em `localStorage` |
| Testes | JUnit 5, Mockito, MockMvc, Spring Security Test, Testcontainers (PostgreSQL real) |


## Estrutura

```
src/main/java/
├── controller/   # Rotas REST e páginas
├── domain/
│   ├── dto/      # Contratos de entrada e saída (nunca entidades)
│   ├── model/
│   │   ├── entities/
│   │   └── enums/    # Turnos, estados, perfis, modalidades — invariantes no enum
│   └── repository/
├── security/     # JWT, filtro, regras por perfil, handlers de 401/403
├── service/      # Regras de negócio
└── exception/    # Tradução de exceções em respostas JSON
```

## API

Todas as rotas exigem token, exceto o login e o carregamento do *shell* HTML das páginas.

| Método | Rota | Acesso |
| --- | --- | --- |
| `POST` | `/api/auth/login` | público |
| `GET` | `/api/v1/users` | autenticado |
| `GET` | `/api/v1/users/me` | autenticado |
| `POST` | `/api/v1/users` | `SUPERVISOR` |
| `GET` | `/api/v1/users/todos` | `SUPERVISOR` |
| `PUT` | `/api/v1/users/{id}` | `SUPERVISOR` |
| `PATCH` | `/api/v1/users/{id}/estado` | `SUPERVISOR` |
| `POST` | `/api/v1/users/{id}/senha` | `SUPERVISOR` |
| `GET` | `/api/v1/scales` | autenticado |
| `GET` | `/api/v1/scales/{id}` | autenticado |
| `GET` | `/api/v1/scales/{id}/coverage` | autenticado |
| `POST` | `/api/v1/scales` | autenticado |
| `POST` | `/api/v1/scales/{id}/publish` | autenticado |
| `GET` | `/api/v1/allocations` | autenticado |
| `POST` | `/api/v1/allocations` | `SUPERVISOR` |
| `PUT` | `/api/v1/allocations/{id}` | `SUPERVISOR` |
| `DELETE` | `/api/v1/allocations/{id}` | `SUPERVISOR` |
| `PATCH` | `/api/v1/allocations/{id}/acceptance` | dono do turno ou `SUPERVISOR` |
| `GET` | `/api/v1/exchanges` | autenticado (visibilidade por perfil) |
| `POST` | `/api/v1/exchanges` | autenticado |
| `PATCH` | `/api/v1/exchanges/{id}/respond` | pedido ou `SUPERVISOR` |
| `GET` | `/api/v1/leaves` | autenticado |
| `POST` `PUT` `DELETE` | `/api/v1/leaves[/{id}]` | `SUPERVISOR` |
| `GET` | `/api/v1/work-modality-schedules` | autenticado |
| `POST` | `/api/v1/work-modality-schedules/generate` | `SUPERVISOR` |
| `GET` | `/api/v1/shifts`, `/api/v1/assignments` | autenticado |

Criar e publicar uma escala é acessível a qualquer perfil autenticado, por decisão: a
supervisão tem de poder montar as escalas e outras funcionalidades com o resto da equipe. O que a aplicação restringe à
supervisão é a escrita que afeta os outros alocações, contas e modalidades de trabalho.

Erros de regra de negócio devolvem `400` com mensagem em português; falta de sessão devolve
`401` e falta de permissão `403` — este último **não** encerra a sessão, porque o token é
válido e o que falta é permissão para aquela operação.

## Modelo conceitual

<img width="907" height="593" alt="Modelo conceitual do SuperVisor" src="https://github.com/user-attachments/assets/84a7e9f4-770c-4c5c-a70d-30c9aade618d" />

## Autor

**Rodrigo Ribeiro Ferreira** — [LinkedIn](https://www.linkedin.com/in/rodrigo-ribeiro-abbb713aa?utm_source=share_via&utm_content=profile&utm_medium=member_ios)

## Licença

[MIT](LICENSE)
