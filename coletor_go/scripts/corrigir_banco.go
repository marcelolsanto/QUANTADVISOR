//go:build ignore

package main

import (
	"database/sql"
	"fmt"
	"log"
	"os"

	_ "github.com/lib/pq"
)

func getEnvOrDefault(key, fallback string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return fallback
}

func main() {
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
		log.Fatalf("Erro ao conectar: %v", err)
	}
	defer db.Close()

	// Lista de ativos americanos que precisam de conversão no banco de dados
	ativosAmericanos := []string{"PFE", "AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "TSLA", "META", "JPM", "V"}

	for _, ticker := range ativosAmericanos {
		// Corrige o Preço Médio na carteira
		db.Exec("UPDATE posicoes_carteira SET preco_medio = preco_medio * 5.50 WHERE ticker = $1", ticker)
		// Corrige o Lote Fiscal
		db.Exec("UPDATE lotes_fiscais SET preco_compra = preco_compra * 5.50 WHERE ticker = $1", ticker)
		// Corrige o Histórico de Ordens
		db.Exec("UPDATE ordens_executadas SET preco_execucao = preco_execucao * 5.50 WHERE ticker = $1", ticker)
	}

	fmt.Println("✅ Banco de dados atualizado! Os preços de compra foram convertidos de Dólar para Real.")
}