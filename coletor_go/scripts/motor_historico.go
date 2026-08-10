//go:build ignore

package main

import (
	"database/sql"
	"fmt"
	"log"
	"os"
	"time"

	_ "github.com/lib/pq"
)

// Estruturas de Dados
type Ordem struct {
	DataHora   time.Time
	Ticker     string
	Tipo       string // "COMPRA" ou "VENDA"
	Quantidade int
	PrecoExec  float64
}

func getEnvOrDefault(key, fallback string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return fallback
}

func main() {
	// 1. Conexão com o banco
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
		log.Fatal("❌ Erro ao conectar no banco: ", err)
	}
	defer db.Close()

	// 2. Busca TODOS os usuários cadastrados
	rowsUsuarios, err := db.Query("SELECT usuario_id FROM contas_virtuais ORDER BY usuario_id")
	if err != nil {
		log.Fatal("❌ Erro ao buscar usuários: ", err)
	}
	var usuarios []int
	for rowsUsuarios.Next() {
		var id int
		rowsUsuarios.Scan(&id)
		usuarios = append(usuarios, id)
	}
	rowsUsuarios.Close()

	// 3. Prepara a janela de tempo
	dataInicio, _ := time.Parse("2006-01-02", "2026-06-22")
	loc, _ := time.LoadLocation("America/Sao_Paulo")
	
	// 👇 A CORREÇÃO DE EOD: Define a data final como ONTEM (subtrai 1 dia)
	dataFim := time.Now().In(loc).AddDate(0, 0, -1)

	precosHistoricos := carregarPrecos(db)

	// 4. Cria o arquivo SQL automaticamente
	arquivoSQL, err := os.Create("patch_patrimonio.sql")
	if err != nil {
		log.Fatal("❌ Erro ao criar o arquivo SQL: ", err)
	}
	defer arquivoSQL.Close()

	arquivoSQL.WriteString("-- ===========================================================\n")
	arquivoSQL.WriteString("-- SCRIPT SQL DE RESTAURAÇÃO DE HISTÓRICO PATRIMONIAL (MtM)\n")
	arquivoSQL.WriteString("-- GERADO AUTOMATICAMENTE PELO MOTOR GOLANG\n")
	arquivoSQL.WriteString("-- ===========================================================\n\n")

	log.Println("🚀 Gerando histórico para TODAS as carteiras (Até ontem)...")

	linhasGeradas := 0

	// 5. Loop por todas as carteiras
	for _, uID := range usuarios {
		arquivoSQL.WriteString(fmt.Sprintf("-- Reconstruindo Histórico para o Cliente ID: %d\n", uID))
		arquivoSQL.WriteString(fmt.Sprintf("DELETE FROM historico_patrimonial WHERE usuario_id = %d AND data_fechamento >= '2026-06-22';\n\n", uID))

		// Define saldo inicial inteligente
		saldoInicial := 1000.00
		

		ordens := carregarOrdens(db, uID)
		caixaLivre := saldoInicial
		custodia := make(map[string]int)
		ultimoPreco := make(map[string]float64)

		for d := dataInicio; d.Before(dataFim) || d.Equal(dataFim); d = d.AddDate(0, 0, 1) {
			diaAtualStr := d.Format("2006-01-02")
			teveOperacaoOuCotacao := false

			// Processa ordens
			for _, ordem := range ordens {
				if ordem.DataHora.Format("2006-01-02") == diaAtualStr {
					if ordem.Tipo == "COMPRA" {
						custodia[ordem.Ticker] += ordem.Quantidade
						caixaLivre -= float64(ordem.Quantidade) * ordem.PrecoExec
						ultimoPreco[ordem.Ticker] = ordem.PrecoExec
					} else if ordem.Tipo == "VENDA" {
						custodia[ordem.Ticker] -= ordem.Quantidade
						caixaLivre += float64(ordem.Quantidade) * ordem.PrecoExec
					}
					teveOperacaoOuCotacao = true
				}
			}

			// Marcação a Mercado
			valorAcoes := 0.0
			for ticker, qtd := range custodia {
				if qtd > 0 {
					precoFechamento := obterPreco(precosHistoricos, ticker, diaAtualStr)
					if precoFechamento > 0 {
						ultimoPreco[ticker] = precoFechamento
						teveOperacaoOuCotacao = true
					} else {
						precoFechamento = ultimoPreco[ticker]
					}
					valorAcoes += float64(qtd) * precoFechamento
				}
			}

			patrimonioTotal := caixaLivre + valorAcoes
			lucroAcumulado := patrimonioTotal - saldoInicial

			// Só gera a linha de SQL se a conta já começou a operar
			if teveOperacaoOuCotacao || patrimonioTotal != saldoInicial {
				linhaSQL := fmt.Sprintf("INSERT INTO historico_patrimonial (usuario_id, data_fechamento, saldo_caixa, valor_acoes, patrimonio_total, lucro_diario) VALUES (%d, '%s', %.2f, %.2f, %.2f, %.2f);\n", 
					uID, diaAtualStr, caixaLivre, valorAcoes, patrimonioTotal, lucroAcumulado)
				
				arquivoSQL.WriteString(linhaSQL)
				linhasGeradas++
			}
		}
		arquivoSQL.WriteString("\n")
	}

	fmt.Println("---------------------------------------------------------")
	log.Printf("✅ SUCESSO! O arquivo 'patch_patrimonio.sql' foi gerado limitando até ontem.")
	fmt.Println("👉 Para injetar no banco, saia do contêiner e rode o comando:")
	fmt.Println("docker exec -i quantadvisor_pg psql -U devuser -d devdb < coletor_go/patch_patrimonio.sql")
	fmt.Println("---------------------------------------------------------")
}

func carregarOrdens(db *sql.DB, usuarioID int) []Ordem {
	query := `SELECT data_hora, ticker, tipo_ordem, quantidade, preco_execucao FROM ordens_executadas WHERE usuario_id = $1 ORDER BY data_hora ASC`
	rows, err := db.Query(query, usuarioID)
	if err != nil { return []Ordem{} }
	defer rows.Close()

	var ordens []Ordem
	for rows.Next() {
		var o Ordem
		rows.Scan(&o.DataHora, &o.Ticker, &o.Tipo, &o.Quantidade, &o.PrecoExec)
		ordens = append(ordens, o)
	}
	return ordens
}

func carregarPrecos(db *sql.DB) map[string]map[string]float64 {
	query := `SELECT ticker_ativo, TO_CHAR(data_hora, 'YYYY-MM-DD'), preco_analisado FROM historico_recomendacoes`
	rows, err := db.Query(query)
	if err != nil { return make(map[string]map[string]float64) }
	defer rows.Close()

	precos := make(map[string]map[string]float64)
	for rows.Next() {
		var ticker, data string
		var preco float64
		rows.Scan(&ticker, &data, &preco)
		if precos[ticker] == nil { precos[ticker] = make(map[string]float64) }
		precos[ticker][data] = preco
	}
	return precos
}

func obterPreco(precos map[string]map[string]float64, ticker string, data string) float64 {
	if precos == nil || precos[ticker] == nil { return 0.0 }
	if valor, existe := precos[ticker][data]; existe { return valor }
	return 0.0 
}