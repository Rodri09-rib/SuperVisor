-- Migração 1: coluna `active` em `tb_user`.
--
-- Contexto: a entidade `User` passou a ter a propriedade `active`, mas o
-- `ddl-auto: update` do Hibernate não a consegue criar numa base já povoada.
-- O Hibernate emite `alter table tb_user add column active boolean not null`,
-- e o PostgreSQL recusa esse comando quando a tabela já tem linhas:
--
--   ERROR: column "active" of relation "tb_user" contains null values
--
-- O Hibernate registra a falha do DDL como aviso e a aplicação arranca na mesma,
-- só para rebentar mais tarde com `column u1_0.active does not exist` na
-- primeira consulta. O `DEFAULT true` é o que torna a alteração aplicável a uma
-- tabela com dados: as linhas existentes são preenchidas com `true` e as contas
-- antigas continuam a funcionar sem backfill manual.
--
-- Este arquivo corre no arranque, depois do Hibernate (ver
-- `spring.jpa.defer-datasource-initialization` em application.yml), por isso a
-- tabela já existe quando estas instruções correm.
--
-- Só há DDL simples, sem blocos `DO $$ ... $$`: o leitor de scripts do Spring
-- parte o arquivo em `;` e não percebe dollar-quoting do PostgreSQL, pelo que
-- um bloco procedural era truncado e falhava com "unterminated dollar quote".

ALTER TABLE tb_user ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true;

-- Numa base nova, o Hibernate cria a coluna sem default; sem esta instrução, um
-- registro inserido sem a coluna ficava com `false` em vez de ativo.
ALTER TABLE tb_user ALTER COLUMN active SET DEFAULT true;
