CREATE TABLE IF NOT EXISTS public.contas_virtuais (
    usuario_id SERIAL PRIMARY KEY,
    nome_cliente VARCHAR(255) NOT NULL,
    perfil_risco VARCHAR(50),
    saldo_brl NUMERIC(15,4) DEFAULT 0.0,
    saldo_usd NUMERIC(15,4) DEFAULT 0.0,
    email VARCHAR(255),
    whatsapp VARCHAR(50),
    login VARCHAR(100) NOT NULL UNIQUE,
    senha VARCHAR(255) NOT NULL,
    piloto_automatico BOOLEAN DEFAULT false,
    lucro_acumulado NUMERIC(15,4) DEFAULT 0.0,
    data_cadastro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    role VARCHAR(20) DEFAULT 'CLIENTE',
    gestor_id INTEGER DEFAULT 1
);
