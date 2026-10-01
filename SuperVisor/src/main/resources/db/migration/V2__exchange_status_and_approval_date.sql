-- Migração 2: pedidos de troca com estado tipado, data de resposta e o colega
-- a quem a troca foi pedida.
--
-- Contexto: `tb_exchange_request.status` era uma coluna de texto em que os
-- valores apareciam em três dialects diferentes conforme o caminho de código
-- que os escrevia:
--
--   * o inicializador do campo na entidade escrevia 'PENDENTE'
--   * o serviço, ao criar o pedido, escrevia 'PENDING'
--   * o serviço, ao responder, escrevia 'ACCEPTED' ou 'REJECTED'
--
-- Passam agora a ser as constantes do enum `ExchangeStatus`: PENDING, APPROVED
-- e REJECTED. As instruções UPDATE abaixo levam os valores já gravados para o
-- dialecto novo; sem elas, o `@Enumerated(EnumType.STRING)` do Hibernate
-- deixaria de conseguir ler as linhas antigas e a lista de histórico falharia a
-- carregar.
--
-- O 'ACCEPTED' passa a 'APPROVED' porque a troca aprovada já trocou as
-- alocações: o nome antigo descrevia a resposta do colega, o novo descreve o
-- estado do pedido.
--
-- `requested_user_id` é preenchido a partir do dono da alocação de destino, que
-- era o colega pedido na altura em que o pedido foi criado. É a melhor
-- reconstrução possível em pedidos já respondidos, porque a aceitação troca os
-- donos das alocações e o valor original já não existe na base. A ressalva só
-- afeta pedidos anteriores a esta migração; os novos gravam o valor certo.
--
-- Como na migração 1, só há DDL/DML simples, sem blocos `DO $$ ... $$`: o
-- leitor de scripts do Spring parte o arquivo em `;` e não percebe
-- dollar-quoting do PostgreSQL.

ALTER TABLE tb_exchange_request ADD COLUMN IF NOT EXISTS approval_date TIMESTAMP WITH TIME ZONE;

ALTER TABLE tb_exchange_request ADD COLUMN IF NOT EXISTS requested_user_id BIGINT;

-- O `status` passa a `NOT NULL` só depois de normalizado: se a coluna ainda
-- tiver nulos, a alteração seria recusada e a migração parava a meio.
UPDATE tb_exchange_request SET status = 'PENDING' WHERE status IS NULL OR status = 'PENDENTE';
UPDATE tb_exchange_request SET status = 'APPROVED' WHERE status = 'ACCEPTED';
UPDATE tb_exchange_request SET status = 'REJECTED' WHERE status = 'REJECTED';

ALTER TABLE tb_exchange_request ALTER COLUMN status SET NOT NULL;

-- A data de resposta só existe a partir de agora. Os pedidos já resolvidos
-- ficam sem ela, que é honesto: não há registro de quando foram respondidos.
ALTER TABLE tb_exchange_request ALTER COLUMN status SET DEFAULT 'PENDING';

-- O enum protege a aplicação; esta CHECK protege a base. Sem ela, um valor
-- fora do conjunto — de uma importação, de um `psql` a afogar uma correção, ou
-- de uma versão futura da aplicação que escreva um estado novo — era gravado sem
-- nenhum erro, e a linha deixava de ser legível: o `@Enumerated(EnumType.STRING)`
-- falha a ler o pedido e a página de histórico inteira deixava de carregar.
ALTER TABLE tb_exchange_request DROP CONSTRAINT IF EXISTS fk_exchange_request_status_check;

ALTER TABLE tb_exchange_request
    ADD CONSTRAINT fk_exchange_request_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));

UPDATE tb_exchange_request tro
SET requested_user_id = destino.user_id
FROM tb_shift_scheduling destino
WHERE tro.requested_user_id IS NULL
  AND destino.id = tro.destination_allocation_id;

-- A chave estrangeira de `requested_user_id` NÃO é criada aqui. A regra
-- `ON DELETE SET NULL` está declarada em `@OnDelete` na entidade, e é o
-- Hibernate que a materializa: se a migração adicionasse a sua, as duas
-- coexistiriam e o PostgreSQL acabaria por respeitar a do Hibernate, que sem a
-- anotação é `NO ACTION` — e apagar um colaborador rebentava em vez de deixar
-- o histórico da troca vivo.
