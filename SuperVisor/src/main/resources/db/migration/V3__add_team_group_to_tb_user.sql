-- Migração 3: equipe do colaborador em `tb_user`.
--
-- Contexto: a escala de presencialidade precisa de saber a que equipe pertence
-- cada pessoa para poder intercalar as duas. A equipe é uma propriedade
-- estável da pessoa, pelo que vive na tabela do usuário e não na escala
-- semanal.
--
-- A coluna é anulável de propósito: as contas existentes não pertencem a
-- nenhuma equipe, e torná-la obrigatória obrigaria a um backfill de equipe que
-- não pode ser inventado a partir dos dados. Um usuário sem equipe simplesmente
-- não entra na escala gerada, e a lista mostra-o como "Sem equipe".
--
-- `ADD COLUMN IF NOT EXISTS` + `DROP CONSTRAINT IF EXISTS` tornam o arquivo
-- repetível, que é o que permite a ele correr em todos os arranques (ver
-- `spring.sql.init.mode` em application.yml).

ALTER TABLE tb_user ADD COLUMN IF NOT EXISTS team_group VARCHAR(20);

ALTER TABLE tb_user DROP CONSTRAINT IF EXISTS fk_user_team_group_check;

-- A restrição repete a lista de valores do enum `TeamGroup` para que a base
-- rejeite um valor que a aplicação não conhece, em vez de o deixar passar e
-- falhar mais tarde ao tentar ler. A aplicação é a fonte da verdade: esta
-- CHECK é a rede que pega um bug de mapeamento.
ALTER TABLE tb_user
    ADD CONSTRAINT fk_user_team_group_check
    CHECK (team_group IS NULL OR team_group IN ('EQUIPE_A', 'EQUIPE_B'));
