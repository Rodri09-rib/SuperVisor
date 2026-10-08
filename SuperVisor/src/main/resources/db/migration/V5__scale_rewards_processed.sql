-- Conclusão de escala com crédito de folgas.
--
-- `rewards_processed` é a trava de idempotência do crédito: quando uma escala
-- passa de PUBLISHED para COMPLETED, os dias de folga ganhos por trabalho ao
-- domingo e pelo Celular da Marinas entram no saldo dos utilizadores, e esta
-- coluna regista que isso já aconteceu. Sem ela, um repetição (duplo clique,
-- falha de rede) creditava o mesmo trabalho duas vezes.
--
-- Idempotente, como as anteriores: o `IF NOT EXISTS` torna repetir inócuo, o
-- `UPDATE ... WHERE rewards_processed IS NULL` só toma nos registos antigos e
-- o `DEFAULT FALSE` garante que escalas já publicadas nascem por creditar.
ALTER TABLE tb_edition_scale
    ADD COLUMN IF NOT EXISTS rewards_processed BOOLEAN DEFAULT FALSE;

UPDATE tb_edition_scale
   SET rewards_processed = FALSE
 WHERE rewards_processed IS NULL;

ALTER TABLE tb_edition_scale
    ALTER COLUMN rewards_processed SET DEFAULT FALSE;

ALTER TABLE tb_edition_scale
    ALTER COLUMN rewards_processed SET NOT NULL;
