//go:build ignore

package main

import (
	"database/sql"
	"fmt"
	"log"
	"os"
	"strings"
	"time"

	_ "github.com/lib/pq"
)

type Ordem struct {
	DataHora   time.Time
	Ticker     string
	Tipo       string // "COMPRA" ou "VENDA"
	Quantidade int
	PrecoExec  float64
	Moeda      string
	TaxaCambio float64
}

func getEnvOrDefault(key, fallback string) string {
	if val := os.Getenv(key); val != "" {
		return val
	}
	return fallback
}

func main() {
	connStr := os.Getenv("DATABASE_URL")
	if connStr == "" {
		dbHost := getEnvOrDefault("DB_HOST", "db")
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

	dataInicio, _ := time.Parse("2006-01-02", "2026-06-22")
	loc, _ := time.LoadLocation("America/Sao_Paulo")
	dataFim := time.Now().In(loc)

	precosHistoricos := carregarPrecos(db)
	dolarRate := 5.08

	log.Println("🚀 Iniciando Reconstrução do Histórico Patrimonial e Lucro Acumulado...")

	registrosInseridos := 0

	for _, uID := range usuarios {
		// Define saldo inicial inteligente baseado no perfil da conta
		saldoInicial := 1000.00
		if uID == 1 || (uID >= 12 && uID <= 16) {
			saldoInicial = 5000000.00
		}

		ordens := carregarOrdens(db, uID)
		if len(ordens) == 0 {
			continue
		}

		caixaLivre := saldoInicial
		custodia := make(map[string]int)
		ultimoPreco := make(map[string]float64)

		inicioCliente := ordens[0].DataHora.In(loc)
		dataInicioCliente := time.Date(inicioCliente.Year(), inicioCliente.Month(), inicioCliente.Day(), 0, 0, 0, 0, loc)
		if dataInicioCliente.Before(dataInicio) {
			dataInicioCliente = dataInicio
		}

		var ultimoPatrimonio float64 = saldoInicial

		for d := dataInicioCliente; d.Before(dataFim) || d.Equal(dataFim); d = d.AddDate(0, 0, 1) {
			diaAtualStr := d.Format("2006-01-02")
			teveOperacaoOuCotacao := false

			for _, ordem := range ordens {
				if ordem.DataHora.Format("2006-01-02") == diaAtualStr {
					fx := ordem.TaxaCambio
					if fx <= 0 {
						fx = 1.0
					}
					isUS := !strings.ContainsAny(ordem.Ticker, "0123456789") && !strings.HasSuffix(ordem.Ticker, ".SA")
					if isUS && fx == 1.0 {
						fx = dolarRate
					}

					valOperacaoBRL := float64(ordem.Quantidade) * ordem.PrecoExec * fx

					if ordem.Tipo == "COMPRA" {
						custodia[ordem.Ticker] += ordem.Quantidade
						caixaLivre -= valOperacaoBRL
						ultimoPreco[ordem.Ticker] = ordem.PrecoExec
					} else if ordem.Tipo == "VENDA" {
						custodia[ordem.Ticker] -= ordem.Quantidade
						caixaLivre += valOperacaoBRL
						ultimoPreco[ordem.Ticker] = ordem.PrecoExec
					}
					teveOperacaoOuCotacao = true
				}
			}

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
					isUS := !strings.ContainsAny(ticker, "0123456789") && !strings.HasSuffix(ticker, ".SA")
					fx := 1.0
					if isUS {
						fx = dolarRate
					}
					valorAcoes += float64(qtd) * precoFechamento * fx
				}
			}

			patrimonioTotal := caixaLivre + valorAcoes
			lucroDiario := patrimonioTotal - ultimoPatrimonio
			ultimoPatrimonio = patrimonioTotal

			if teveOperacaoOuCotacao || patrimonioTotal != saldoInicial {
				queryUpsert := `
					INSERT INTO historico_patrimonial (usuario_id, data_fechamento, saldo_caixa, valor_acoes, patrimonio_total, lucro_diario)
					VALUES ($1, $2, $3, $4, $5, $6)
					ON CONFLICT (usuario_id, data_fechamento)
					DO UPDATE SET saldo_caixa = EXCLUDED.saldo_caixa, valor_acoes = EXCLUDED.valor_acoes, patrimonio_total = EXCLUDED.patrimonio_total, lucro_diario = EXCLUDED.lucro_diario;
				`
				_, errIns := db.Exec(queryUpsert, uID, diaAtualStr, caixaLivre, valorAcoes, patrimonioTotal, lucroDiario)
				if errIns != nil {
					log.Printf("⚠️ Erro ao inserir snapshot para user %d na data %s: %v", uID, diaAtualStr, errIns)
				} else {
					registrosInseridos++
				}
			}
		}

		// Atualiza lucro_acumulado na tabela contas_virtuais
		lucroAcumuladoFinal := ultimoPatrimonio - saldoInicial
		queryUpdateConta := `UPDATE contas_virtuais SET lucro_acumulado = $1 WHERE usuario_id = $2`
		_, errUpd := db.Exec(queryUpdateConta, lucroAcumuladoFinal, uID)
		if errUpd != nil {
			log.Printf("⚠️ Erro ao atualizar lucro_acumulado para user %d: %v", uID, errUpd)
		}
	}

	log.Printf("✅ [DATA RECOVERY] Concluído! %d snapshots inseridos em historico_patrimonial e lucro_acumulado atualizado para todas as contas.", registrosInseridos)
}

func carregarOrdens(db *sql.DB, usuarioID int) []Ordem {
	query := `SELECT data_hora, ticker, tipo_ordem, quantidade, preco_execucao, COALESCE(moeda, 'BRL'), COALESCE(taxa_cambio_momento, 1.0) FROM ordens_executadas WHERE usuario_id = $1 ORDER BY data_hora ASC`
	rows, err := db.Query(query, usuarioID)
	if err != nil {
		return []Ordem{}
	}
	defer rows.Close()

	var ordens []Ordem
	for rows.Next() {
		var o Ordem
		rows.Scan(&o.DataHora, &o.Ticker, &o.Tipo, &o.Quantidade, &o.PrecoExec, &o.Moeda, &o.TaxaCambio)
		ordens = append(ordens, o)
	}
	return ordens
}

func carregarPrecos(db *sql.DB) map[string]map[string]float64 {
	query := `SELECT ticker_ativo, TO_CHAR(data_hora, 'YYYY-MM-DD'), AVG(preco_analisado) FROM historico_recomendacoes GROUP BY ticker_ativo, TO_CHAR(data_hora, 'YYYY-MM-DD')`
	rows, err := db.Query(query)
	if err != nil {
		return make(map[string]map[string]float64)
	}
	defer rows.Close()

	precos := make(map[string]map[string]float64)
	for rows.Next() {
		var ticker, data string
		var preco float64
		rows.Scan(&ticker, &data, &preco)
		if precos[ticker] == nil {
			precos[ticker] = make(map[string]float64)
		}
		precos[ticker][data] = preco
	}
	return precos
}

func obterPreco(precos map[string]map[string]float64, ticker string, data string) float64 {
	if precos == nil || precos[ticker] == nil {
		return 0.0
	}
	if valor, existe := precos[ticker][data]; existe {
		return valor
	}
	return 0.0
}