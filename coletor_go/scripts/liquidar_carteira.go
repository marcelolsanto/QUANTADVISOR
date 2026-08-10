//go:build ignore

package main

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"log"
	"os"
	"strings"

	_ "github.com/lib/pq"
	"github.com/redis/go-redis/v9"
)

// DolarGlobal Fixo para simulação, mas idealmente buscaria da AwesomeAPI aqui
var DolarGlobal float64 = 5.50

func getEnvOrDefault(key, fallback string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return fallback
}

func main() {
	// 1. Conecta ao PostgreSQL e ao Redis
	connStr := os.Getenv("DATABASE_URL")
	if connStr == "" {
		dbHost := getEnvOrDefault("DB_HOST", "quantadvisor_pg")
		dbPort := getEnvOrDefault("DB_PORT", "5432")
		dbUser := getEnvOrDefault("DB_USER", "devuser")
		dbPass := getEnvOrDefault("DB_PASSWORD", "devpassword")
		dbName := getEnvOrDefault("DB_NAME", "devdb")
		connStr = fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=disable", dbHost, dbPort, dbUser, dbPass, dbName)
	}
	db, err := sql.Open("postgres", connStr)
	if err != nil {
		log.Fatalf("Erro ao conectar no banco: %v", err)
	}
	defer db.Close()

	rdb := redis.NewClient(&redis.Options{Addr: "quant_redis:6379"})
	ctx := context.Background()

	// 2. Busca todas as posições em aberto de todos os clientes (Agora lê a MOEDA)
	rows, err := db.Query("SELECT usuario_id, ticker, quantidade_total, preco_medio, COALESCE(moeda, 'BRL') FROM posicoes_carteira")
	if err != nil {
		log.Fatalf("Erro ao buscar posições: %v", err)
	}
	defer rows.Close()

	// 3. Inicia a transação atômica (tudo ou nada)
	tx, err := db.Begin()
	if err != nil {
		log.Fatalf("Erro ao iniciar transação: %v", err)
	}

	var lucroTotalGlobalBRL float64 = 0.0
	var posicoesLiquidadas int = 0

	fmt.Println("🔄 Iniciando Liquidação a Mercado (Regime de Caixa Multi-Moeda)...")
	fmt.Println("---------------------------------------------------------")

	for rows.Next() {
		var uID int
		var ticker, moeda string
		var qtd int
		var pMedio float64
		rows.Scan(&uID, &ticker, &qtd, &pMedio, &moeda)

		// A. Busca a marcação a mercado real no Redis
		val, errRedis := rdb.Get(ctx, "ticker:"+ticker).Result()
		precoAtual := pMedio // Fallback de segurança: se não achar, vende no zero a zero
		
		if errRedis == nil {
			var dados map[string]interface{}
			json.Unmarshal([]byte(val), &dados)
			if p, ok := dados["preco_atual"].(float64); ok {
				precoAtual = p
			}
		}

		// B. Matemática da Operação na Moeda Nativa
		valorCompraNativo := float64(qtd) * pMedio
		valorVendaNativo := float64(qtd) * precoAtual
		lucroDaOperacaoNativo := valorVendaNativo - valorCompraNativo

		// C. Conversão para P&L Global em Reais
		taxaCambio := 1.0
		if moeda == "USD" {
			taxaCambio = DolarGlobal
		}
		lucroDaOperacaoBRL := lucroDaOperacaoNativo * taxaCambio
		lucroTotalGlobalBRL += lucroDaOperacaoBRL

		// D. Injeta o dinheiro no Bolso Correto do Usuário
		var queryAtualizaConta string
		if moeda == "USD" {
			queryAtualizaConta = "UPDATE contas_virtuais SET saldo_usd = saldo_usd + $1, lucro_acumulado = lucro_acumulado + $2 WHERE usuario_id = $3"
		} else {
			queryAtualizaConta = "UPDATE contas_virtuais SET saldo_brl = saldo_brl + $1, lucro_acumulado = lucro_acumulado + $2 WHERE usuario_id = $3"
		}
		
		tx.Exec(queryAtualizaConta, valorVendaNativo, lucroDaOperacaoBRL, uID)
		
		// E. Zera a Posição e os Lotes Fiscais (FIFO)
		tx.Exec("DELETE FROM posicoes_carteira WHERE usuario_id = $1 AND ticker = $2", uID, ticker)
		tx.Exec("UPDATE lotes_fiscais SET quantidade_atual = 0 WHERE usuario_id = $1 AND ticker = $2", uID, ticker)

		// F. Registra o recibo da Ordem para o Histórico da Tela
		tx.Exec("INSERT INTO ordens_executadas (usuario_id, ticker, tipo_ordem, quantidade, preco_execucao, moeda, taxa_cambio_momento, volume_brl) VALUES ($1, $2, 'VENDA', $3, $4, $5, $6, $7)", 
			uID, ticker, qtd, precoAtual, moeda, taxaCambio, (valorVendaNativo * taxaCambio))

		simboloMoeda := "R$"
		if moeda == "USD" { simboloMoeda = "$" }
		
		fmt.Printf("✅ %s | Qtd: %d | Venda: %s %.2f | Lucro/Prejuízo: R$ %.2f\n", ticker, qtd, simboloMoeda, valorVendaNativo, lucroDaOperacaoBRL)
		posicoesLiquidadas++
	}

	// 4. Confirma todas as alterações no banco de dados
	err = tx.Commit()
	if err != nil {
		log.Fatalf("Erro ao commitar transação: %v", err)
	}

	fmt.Println("---------------------------------------------------------")
	if posicoesLiquidadas == 0 {
		fmt.Println("🤷 Nenhuma posição encontrada. A carteira já estava vazia.")
	} else {
		fmt.Printf("🎉 Liquidação Concluída! %d posições fechadas.\n", posicoesLiquidadas)
		fmt.Printf("💰 Resultado (PnL) Realizado (Global BRL): R$ %.2f\n", lucroTotalGlobalBRL)
	}
}