-- Índices de Alta Performance para Escalabilidade (QuantAdvisor HFT / AI)

CREATE INDEX IF NOT EXISTS idx_ordens_user_data ON ordens_executadas (usuario_id, data_hora DESC);
CREATE INDEX IF NOT EXISTS idx_ordens_ticker ON ordens_executadas (ticker);

CREATE INDEX IF NOT EXISTS idx_lotes_user_ticker ON lotes_fiscais (usuario_id, ticker, data_entrada);

CREATE INDEX IF NOT EXISTS idx_rec_ticker_data ON historico_recomendacoes (ticker_ativo, data_hora DESC);

CREATE INDEX IF NOT EXISTS idx_lancamentos_user_data ON lancamentos_contabeis (usuario_id, data_liquidacao);

CREATE INDEX IF NOT EXISTS idx_posicoes_user_ticker ON posicoes_carteira (usuario_id, ticker);

CREATE INDEX IF NOT EXISTS idx_patrimonial_user_data ON historico_patrimonial (usuario_id, data_fechamento DESC);
