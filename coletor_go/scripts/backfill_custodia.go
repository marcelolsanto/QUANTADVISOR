// ================================================================================
// ARQUIVO: coletor_go/scripts/backfill_custodia.go
// ================================================================================

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

// Estruturas para mapeamento de dados
type Ordem struct {
    UsuarioID int
    Ticker    string
    Tipo      string
    Qtd       int
    PrecoExec float64
    DataHora  time.Time
}

type PosicaoState struct {
    Quantidade      int
    PrecoMedio      float64
    UltimoPreco     float64 // Preço MTM (Fechamento)
    UltimoPrecoExec float64 // Preço de Execução (Compra/Venda)
}

func getEnvOrDefault(key, fallback string) string {
    if val := os.Getenv(key); val != "" {
        return val
    }
    return fallback
}

func main() {
    // 1. Conexão com o Banco de Dados
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
        log.Fatalf("❌ Erro fatal ao conectar no banco: %v", err)
    }
    defer db.Close()

    log.Println("🚀 Iniciando Máquina do Tempo: Reconstrução do Histórico de Custódia...")
    fmt.Println("-------------------------------------------------------------------------")

    // 2. Busca TODAS as ordens executadas em ordem cronológica absoluta
    queryOrdens := `SELECT usuario_id, ticker, tipo_ordem, quantidade, preco_execucao, data_hora 
                    FROM ordens_executadas ORDER BY data_hora ASC`
    
    rows, err := db.Query(queryOrdens)
    if err != nil {
        log.Fatalf("❌ Erro ao ler ordens: %v", err)
    }
    defer rows.Close()

    var ordens []Ordem
    for rows.Next() {
        var o Ordem
        if err := rows.Scan(&o.UsuarioID, &o.Ticker, &o.Tipo, &o.Qtd, &o.PrecoExec, &o.DataHora); err == nil {
            ordens = append(ordens, o)
        }
    }


    if len(ordens) == 0 {
        log.Println("📭 Nenhuma ordem encontrada para processar.")
        return
    }

    // 3. Define a janela temporal de processamento (Do primeiro trade até hoje)
    dataInicio := ordens[0].DataHora
    dataInicio = time.Date(dataInicio.Year(), dataInicio.Month(), dataInicio.Day(), 0, 0, 0, 0, dataInicio.Location())
    
    loc, _ := time.LoadLocation("America/Sao_Paulo")
    hoje := time.Now().In(loc)
    dataFim := time.Date(hoje.Year(), hoje.Month(), hoje.Day(), 0, 0, 0, 0, loc)

    // 4. Inicia a Máquina de Estados na RAM: map[UsuarioID]map[Ticker]*PosicaoState
    estadoGlobal := make(map[int]map[string]*PosicaoState)
    
    // Índice para rastrear qual ordem estamos processando
    idxOrdem := 0
    totalOrdens := len(ordens)

    // 5. Inicia a Transação de Alta Performance para Inserção em Lote
    tx, err := db.Begin()
    if err != nil {
        log.Fatalf("❌ Erro ao abrir transação: %v", err)
    }

    // O 'Prepared Statement' é compilado no banco uma única vez, acelerando os inserts em até 80%
    stmt, err := tx.Prepare(`
        INSERT INTO historico_custodia_diaria (usuario_id, data_fechamento, ticker, quantidade, preco_fechamento, valor_posicao)
        VALUES ($1, $2, $3, $4, $5, $6)
        ON CONFLICT (usuario_id, data_fechamento, ticker)
        DO UPDATE SET quantidade = EXCLUDED.quantidade, preco_fechamento = EXCLUDED.preco_fechamento, valor_posicao = EXCLUDED.valor_posicao;
    `)
    if err != nil {
        log.Fatalf("❌ Erro ao preparar statement: %v", err)
    }
    defer stmt.Close()

    registrosGerados := 0

    // 6. O Loop Temporal: Avança dia a dia
    for d := dataInicio; !d.After(dataFim); d = d.AddDate(0, 0, 1) {
        diaStr := d.Format("2006-01-02")
        
        // A. Aplica todas as ordens que pertencem a este dia específico
        for idxOrdem < totalOrdens {
            o := ordens[idxOrdem]
            ordemDia := time.Date(o.DataHora.Year(), o.DataHora.Month(), o.DataHora.Day(), 0, 0, 0, 0, o.DataHora.Location())
            
            // Se a ordem for do futuro em relação ao nosso loop atual, paramos de processar
            if ordemDia.After(d) {
                break
            }

            // Inicializa o mapa do usuário se não existir
            if estadoGlobal[o.UsuarioID] == nil {
                estadoGlobal[o.UsuarioID] = make(map[string]*PosicaoState)
            }
            
            // Inicializa o ativo para o usuário se não existir
            if estadoGlobal[o.UsuarioID][o.Ticker] == nil {
                estadoGlobal[o.UsuarioID][o.Ticker] = &PosicaoState{Quantidade: 0, PrecoMedio: 0.0, UltimoPreco: o.PrecoExec}
            }

            pos := estadoGlobal[o.UsuarioID][o.Ticker]

            if o.Tipo == "COMPRA" {
                novoCusto := (float64(pos.Quantidade) * pos.PrecoMedio) + (float64(o.Qtd) * o.PrecoExec)
                pos.Quantidade += o.Qtd
                pos.PrecoMedio = novoCusto / float64(pos.Quantidade)
                pos.UltimoPreco = o.PrecoExec 
                pos.UltimoPrecoExec = o.PrecoExec // Memoriza o preço de compra
            } else if o.Tipo == "VENDA" {
                pos.Quantidade -= o.Qtd
                pos.UltimoPreco = o.PrecoExec
                pos.UltimoPrecoExec = o.PrecoExec // Memoriza o preço de venda
                if pos.Quantidade < 0 {
                    pos.Quantidade = 0
                }
            }
            
            idxOrdem++
        }

        // B. Fotografia de Fim de Dia (EOD Snapshot): Varre o estado da RAM e injeta no Banco
        for uID, ativos := range estadoGlobal {
            for ticker, pos := range ativos {
                // Só registra se o cliente ainda tiver o ativo na carteira
                if pos.Quantidade > 0 {
                    valorPosicao := float64(pos.Quantidade) * pos.UltimoPreco
                    
                    // Executa o Insert engatilhado
                    _, err := stmt.Exec(uID, diaStr, ticker, pos.Quantidade, pos.UltimoPreco, valorPosicao)
                    if err != nil {
                        log.Printf("⚠️ Falha ao inserir snapshot %s para user %d ativo %s: %v", diaStr, uID, ticker, err)
                    } else {
                        registrosGerados++
                        
                        // LÓGICA DE EXIBIÇÃO NA TELA (FILTRO DE DATAS ADICIONADO AQUI)
                        if diaStr == "2026-07-16" || diaStr == "2026-07-16" {
                            fmt.Printf("📸 [%s] Usuário: %d | Ativo: %-6s | Qtd: %-4d | Preço MTM: R$ %6.2f | Valor: R$ %.2f\n", 
                                diaStr, uID, ticker, pos.Quantidade, pos.UltimoPreco, valorPosicao)
                        }
                    }
                }
            }
        }
    }

    // 7. Confirma (Commit) todos os inserts no PostgreSQL em uma única operação atômica
    err = tx.Commit()
    if err != nil {
        log.Printf("⚠️ Falha ao inserir snapshot %s...", diaStr, uID, ticker, err)
    } else {
        registrosGerados++
        
        // Auditoria visual: Preço de Compra vs Preço MTM
        fmt.Printf("📸 [%s] Usuário: %d | Ativo: %-6s | Qtd: %-4d | Preço Compra: R$ %6.2f | Preço MTM: R$ %6.2f | Valor: R$ %.2f\n", 
            diaStr, uID, ticker, pos.Quantidade, pos.UltimoPrecoExec, pos.UltimoPreco, valorPosicao)
    }

    fmt.Println("-------------------------------------------------------------------------")
    log.Printf("🏁 Backfilling concluído com sucesso! %d snapshots diários de ativos foram gerados no banco de dados.", registrosGerados)
}