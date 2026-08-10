import os
import psycopg2
import sys

def restaurar_banco():
    print("🔌 Conectando ao PostgreSQL (Docker)...")
    try:
        conn = psycopg2.connect(
            host=os.getenv("DB_HOST", "db"),
            port=os.getenv("DB_PORT", "5432"),
            dbname=os.getenv("DB_NAME", "devdb"),
            user=os.getenv("DB_USER", "devuser"),
            password=os.getenv("DB_PASSWORD", "devpassword")
        )
        cursor = conn.cursor()
        
        sql_script = """
        -- 1. A Conta Corrente do Cliente
        CREATE TABLE IF NOT EXISTS contas_virtuais (
            usuario_id SERIAL PRIMARY KEY,
            nome_cliente VARCHAR(100) NOT NULL,
            perfil_risco VARCHAR(20) DEFAULT 'SOFISTICADO',
            saldo_brl NUMERIC(15, 4) DEFAULT 1000.00,
            saldo_usd NUMERIC(15, 4) DEFAULT 0.00
        );

        -- 2. O Histórico de Transações (Livro-Razão)
        CREATE TABLE IF NOT EXISTS ordens_executadas (
            id SERIAL PRIMARY KEY,
            usuario_id INTEGER REFERENCES contas_virtuais(usuario_id),
            ticker VARCHAR(12) NOT NULL,
            tipo_ordem VARCHAR(10) NOT NULL,
            quantidade INTEGER NOT NULL,
            preco_execucao NUMERIC(15, 4) NOT NULL,
            data_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );

        -- 3. A Fotografia Atual da Carteira (Posições Abertas)
        CREATE TABLE IF NOT EXISTS posicoes_carteira (
            id SERIAL PRIMARY KEY,
            usuario_id INTEGER REFERENCES contas_virtuais(usuario_id),
            ticker VARCHAR(12) NOT NULL,
            quantidade_total INTEGER NOT NULL,
            preco_medio NUMERIC(15, 4) NOT NULL,
            CONSTRAINT unique_posicao UNIQUE (usuario_id, ticker)
        );

        -- 4. Tabela de Perfis de Investidor
        CREATE TABLE IF NOT EXISTS perfil_investidor (
            id SERIAL PRIMARY KEY,
            nome_usuario VARCHAR(100) NOT NULL,
            tolerancia_risco_var NUMERIC(8,4),
            horizonte_investimento_meses INTEGER,
            perfil_comportamental VARCHAR(50),
            data_criacao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );

        -- 5. Tabela de Log do Motor de Inteligência Artificial
        CREATE TABLE IF NOT EXISTS historico_recomendacoes (
            id SERIAL PRIMARY KEY,
            ticker_ativo VARCHAR(12) NOT NULL,
            preco_analisado NUMERIC(15, 4) NOT NULL,
            z_score_calculado NUMERIC(15, 4),
            var_diario_calculado NUMERIC(15, 4),
            decisao_ia TEXT,
            taxa_selic_aplicada NUMERIC(8, 4),
            data_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );

        -- LIMPEZA DE SEGURANÇA (Zera as tabelas para não duplicar dados caso rode 2 vezes)
        --TRUNCATE contas_virtuais, ordens_executadas, posicoes_carteira, perfil_investidor, historico_recomendacoes RESTART IDENTITY CASCADE;

        -- INSERÇÃO DOS PERFIS BASE
        INSERT INTO perfil_investidor (nome_usuario, tolerancia_risco_var, horizonte_investimento_meses, perfil_comportamental)
        VALUES 
            ('Conservador', -0.05, 36, 'Conservador'),
            ('Moderado', -0.10, 24, 'Moderado'),
            ('Arrojado', -0.15, 12, 'Arrojado'),
            ('Day Trader', -0.20, 1, 'Agressivo');

        -- INSERÇÃO DOS CLIENTES / FUNDOS
        INSERT INTO contas_virtuais (nome_cliente, perfil_risco, saldo_brl, saldo_usd) 
        VALUES 
            ('Marcelo Santos', 'Agressivo', 1000.00, 0.00),
            ('Pedro Diniz', 'Conservador', 1000.00, 0.00),
            ('Erickson Melo', 'Moderado', 1000.00, 0.00),
            ('Fundo Master', 'Arrojado', 1000.00, 0.00);
        """

        print("🏗️ Reconstruindo tabelas e inserindo dados iniciais...")
        cursor.execute(sql_script)
        conn.commit()
        
        cursor.close()
        conn.close()
        print("✅ SUCESSO ABSOLUTO! Banco de dados reconstruído e operante.")

    except Exception as e:
        print(f"❌ Erro Crítico de Conexão: {e}")
        sys.exit(1)

if __name__ == "__main__":
    restaurar_banco()