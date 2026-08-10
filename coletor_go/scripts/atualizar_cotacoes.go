//go:build ignore

package main

import (
	"database/sql"
	"encoding/csv"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strconv"
	"time"

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
		log.Fatal(err)
	}
	defer db.Close()

	// 1. Pegar todos os tickers unicos que você operou
	rows, err := db.Query("SELECT DISTINCT ticker FROM ordens_executadas")
	if err != nil {
		log.Fatal(err)
	}
	
	for rows.Next() {
		var ticker string
		rows.Scan(&ticker)
		fmt.Printf("📥 Buscando histórico de: %s...\n", ticker)
		
		// 2. Buscar dados no Yahoo Finance
		// Período de 01/06/2026 até hoje
		url := fmt.Sprintf("https://query1.finance.yahoo.com/v7/finance/download/%s.SA?period1=1717200000&period2=2000000000&interval=1d&events=history", ticker)
		
		client := &http.Client{Timeout: 15 * time.Second}
		resp, err := client.Get(url)
		if err != nil { continue }
		defer resp.Body.Close()

		// 3. Processar CSV
		reader := csv.NewReader(resp.Body)
		reader.Read() // Pula o cabeçalho
		
		for {
			record, err := reader.Read()
			if err == io.EOF { break }
			
			dateStr := record[0] // Data
			closePrice := record[4] // Preço de Fechamento

			price, _ := strconv.ParseFloat(closePrice, 64)

			// 4. Salvar no banco
			_, err = db.Exec(`
				INSERT INTO historico_recomendacoes (ticker_ativo, data_hora, preco_analisado)
				VALUES ($1, $2, $3)
				ON CONFLICT (ticker_ativo, data_hora) 
				DO UPDATE SET preco_analisado = EXCLUDED.preco_analisado`, 
				ticker, dateStr, price)
			
			if err != nil {
				log.Printf("Erro ao salvar %s em %s: %v", ticker, dateStr, err)
			}
		}
	}
	fmt.Println("✅ Atualização de preços finalizada!")
}