-- Migração 4: registo de presença, duração de folga e saldos.
--
-- Contexto: a presencialidade passa a distinguir quem faltou o dia todo de
-- quem faltou metade, as folgas passam a ter duração (dia inteiro, manhã ou
-- tarde), e cada uma das duas move um saldo no perfil do colaborador — a
-- dívida de compensação e o crédito de folgas.
--
-- A ordem das instruções é a que faz o arquivo funcionar nos dois cenários:
--  1. `ADD COLUMN IF NOT EXISTS` cobre o caso em que o Hibernate ainda não
--     conseguiu criar a coluna (numa base povoada o `update` falha porque não
--     escreve DEFAULT e a tabela tem linhas);
--  2. o `UPDATE ... WHERE ... IS NULL` cobre o caso em que a coluna já existe
--     e veio com NULLs, antes de a NOT NULL poder ser imposta;
--  3. `SET DEFAULT` + `SET NOT NULL` fecham a coluna, ficando idempotentes.
--
-- Sem o passo 2, o `SET NOT NULL` rebentava numa base com folgas antigas e a
-- aplicação não arrancava — que é exatamente o problema que motivou a V1.

ALTER TABLE tb_work_modality_schedule
    ADD COLUMN IF NOT EXISTS attendance_status VARCHAR(20);

ALTER TABLE tb_work_modality_schedule
    ADD COLUMN IF NOT EXISTS notes VARCHAR(500);

ALTER TABLE tb_user_leave
    ADD COLUMN IF NOT EXISTS leave_duration VARCHAR(20);

ALTER TABLE tb_user
    ADD COLUMN IF NOT EXISTS pending_compensation_days NUMERIC(10, 1);

ALTER TABLE tb_user
    ADD COLUMN IF NOT EXISTS accumulated_leaves NUMERIC(10, 1);

UPDATE tb_work_modality_schedule SET attendance_status = 'PRESENT'
WHERE attendance_status IS NULL;

UPDATE tb_user_leave SET leave_duration = 'FULL_DAY'
WHERE leave_duration IS NULL;

UPDATE tb_user SET pending_compensation_days = 0
WHERE pending_compensation_days IS NULL;

UPDATE tb_user SET accumulated_leaves = 0
WHERE accumulated_leaves IS NULL;

ALTER TABLE tb_work_modality_schedule
    ALTER COLUMN attendance_status SET DEFAULT 'PRESENT';

ALTER TABLE tb_work_modality_schedule
    ALTER COLUMN attendance_status SET NOT NULL;

ALTER TABLE tb_user_leave
    ALTER COLUMN leave_duration SET DEFAULT 'FULL_DAY';

ALTER TABLE tb_user_leave
    ALTER COLUMN leave_duration SET NOT NULL;

ALTER TABLE tb_user
    ALTER COLUMN pending_compensation_days SET DEFAULT 0;

ALTER TABLE tb_user
    ALTER COLUMN pending_compensation_days SET NOT NULL;

ALTER TABLE tb_user
    ALTER COLUMN accumulated_leaves SET DEFAULT 0;

ALTER TABLE tb_user
    ALTER COLUMN accumulated_leaves SET NOT NULL;
