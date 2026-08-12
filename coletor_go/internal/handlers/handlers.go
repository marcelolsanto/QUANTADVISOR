package handlers

import (
	"bytes"
	"database/sql"
	"context"
	"crypto/rand"
	"encoding/json"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"math"
	"math/big"
	"net/http"
	"net/url"
	"quantadvisor/internal/database"
	"quantadvisor/internal/middleware"
	"quantadvisor/internal/models"
	"quantadvisor/internal/services"
	"strconv"
	"strings"
	"time"

	"github.com/dgrijalva/jwt-go"
	"golang.org/x/crypto/bcrypt"
)

// =========================================================
// 📡 INFRAESTRUTURA DE STREAMING (SSE - Server-Sent Events)
// =========================================================

// O tipo Client é um canal que transporta JSON para um navegador específico
type Client chan []byte

var (
	// Dicionário de abas abertas no React
	Clients = make(map[Client]bool)
	// Canal por onde entram as cotações novas (Buffered com capacidade para picos de HFT)
	Broadcast = make(chan []byte, 10000)
	// Canais de controle de conexão com buffer para evitar bloqueios
	Register   = make(chan Client, 256)
	Unregister = make(chan Client, 256)
)

// StartSSEHub é o coração do sistema. Fica rodando em background no main.go
func StartSSEHub() {
	for {
		select {
		case client := <-Register:
			Clients[client] = true
			log.Printf("🔌 Novo Terminal React conectado. Total: %d", len(Clients))

		case client := <-Unregister:
			if _, ok := Clients[client]; ok {
				delete(Clients, client)
				close(client)
				log.Printf("🔌 Terminal React desconectado. Total: %d", len(Clients))
			}

		case message := <-Broadcast:
			// Dispara a cotação para todos os navegadores abertos
			for client := range Clients {
				select {
				case client <- message:
				default:
					// Se o canal do cliente encheu momentaneamente (React/Nginx lento), 
					// descarta apenas esse tick para aquele cliente em vez de desconectá-lo brutalmente
				}
			}
		}
	}
}

// @Summary Stream de Cotações (Tempo Real)
// @Description Estabelece um túnel SSE (Server-Sent Events) unidirecional e persistente. O orquestrador Go assina os eventos de mercado no Redis (Pub/Sub) e repassa os 'ticks' de cotação em tempo real e baixa latência diretamente para os painéis do Frontend, dispensando WebSockets pesados.
// @Tags Sistema
// @Router /stream/mercado [get]
func SSEMarketStream(w http.ResponseWriter, r *http.Request) {
	// 1. Headers obrigatórios para transformar a rota HTTP comum num "Tubo" SSE
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	// Se tiver problemas de CORS, o seu middleware global já resolve, mas garantimos aqui:
	w.Header().Set("Access-Control-Allow-Origin", "*")

	// Verifica se o servidor web suporta "Flushing" (empurrar dados sem fechar a conexão)
	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "Streaming não suportado pelo servidor.", http.StatusInternalServerError)
		return
	}

	// 2. Registra este novo navegador no nosso Hub
	clientChan := make(Client, 2000) 
	Register <- clientChan

	// 3. Garante que removemos o cliente quando ele fechar a aba
	defer func() {
		Unregister <- clientChan
	}()

	// 4. O Loop Infinito: Fica aguardando mensagens chegarem no canal deste cliente
	for {
		select {
		case msg, ok := <-clientChan:
			if !ok {
				return // Canal fechado pelo Hub, encerra a goroutine e libera a CPU
			}
			// O protocolo SSE OBRIGA o uso do prefixo "data: " e duas quebras de linha "\n\n"
			fmt.Fprintf(w, "data: %s\n\n", msg)
			flusher.Flush() // Empurra o dado imediatamente pela rede

		case <-r.Context().Done():
			// O usuário fechou a aba ou mudou de página
			return
		}
	}
}

// @Summary Toggle Piloto Automático
// @Description Habilita ou desabilita a execução autônoma de ordens pelo robô de rebalanceamento. 
// @Tags Operações
// @Accept json
// @Produce json
// @Security BearerAuth
// @Param request body models.TogglePilotoReq true "Dados do Toggle"
// @Success 200 {object} map[string]interface{}
// @Router /piloto/toggle [post]
func TogglePiloto(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" { return }

	// Descobre quem está fazendo a requisição
	idLogado, roleLogado := getAuth(r)

	var req models.TogglePilotoReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Payload inválido"}`, http.StatusBadRequest)
		return
	}

	// 🛡️ TRAVA PARA O CLIENTE: Cliente só pode alterar o piloto da PRÓPRIA conta
	if roleLogado != "GESTOR" && idLogado != req.UsuarioID {
		http.Error(w, `{"sucesso": false, "erro": "Acesso negado. Você só pode ativar a IA da sua própria conta."}`, http.StatusForbidden)
		return
	}

	// 🛡️ TRAVA PARA O GESTOR: Gestor não pode editar clientes de outro Gestor
	if roleLogado == "GESTOR" && idLogado != 1 && idLogado != req.UsuarioID {
		var donoID int
		errCheck := database.Conn.QueryRow("SELECT gestor_id FROM contas_virtuais WHERE usuario_id = $1", req.UsuarioID).Scan(&donoID)
		
		if errCheck != nil || donoID != idLogado {
			http.Error(w, `{"sucesso": false, "erro": "Operação bloqueada! Esta conta pertence a outra carteira institucional."}`, http.StatusForbidden)
			return
		}
	}

	// Se passou por todas as barreiras, atualiza o banco de dados
	_, err := database.Conn.Exec("UPDATE contas_virtuais SET piloto_automatico = $1 WHERE usuario_id = $2", req.Estado, req.UsuarioID)
	if err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Erro ao atualizar banco"}`, http.StatusInternalServerError)
		return
	}

	w.Write([]byte(`{"sucesso": true}`))
}

// Define os headers de segurança e CORS do React
func SetCORS(w http.ResponseWriter) {
}

// @Summary Auditoria de Mercado
// @Description Amostra a temperatura do mercado financeiro em tempo real. Consulta a base 'In-Memory' (Redis SET) para consolidar recomendações atômicas de Z-Score, Risco de Variância e Sinais de Inteligência Artificial para todos os ativos em monitoramento.
// @Tags Sistema
// @Produce json
// @Success 200 {object} map[string]interface{}
// @Router /auditoria [get]
func Auditoria(w http.ResponseWriter, r *http.Request) {
	// 🛡️ 1. BLINDAGEM ANTI-CRASH: Captura qualquer Panic e salva o servidor
	defer func() {
		if rec := recover(); rec != nil {
			log.Printf("🚨 [CRITICAL] Panic evitado na Auditoria: %v", rec)
			http.Error(w, `{"sucesso": false, "erro": "O motor matemático falhou internamente."}`, http.StatusInternalServerError)
		}
	}()

	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	log.Println("📊 [MAESTRO GO] Servindo o Dashboard via Amostragem Indexada (Redis Set)...")

	// 🛡️ 2. TIMEOUT DE CONEXÃO: Se o Redis não responder em 5 seg, ele corta (evita socket hang up)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	// Recupera os tickers do SET
	ativos, err := database.Rdb.SMembers(ctx, "ativos_set").Result()
	var recomendacoes []string

	if err == nil && len(ativos) > 0 {
		var chavesReais []string
		for _, ativo := range ativos {
			chavesReais = append(chavesReais, fmt.Sprintf("ticker:%s", ativo))
		}

		if len(chavesReais) > 0 {
			valores, errMget := database.Rdb.MGet(ctx, chavesReais...).Result()
			if errMget == nil {
				for _, val := range valores {
					// 🛡️ 3. Tratamento de tipo seguro
					if strVal, ok := val.(string); ok {
						recomendacoes = append(recomendacoes, strVal)
					}
				}
			}
		}
	}

	// Injeta a Renda Fixa estática
	rendaFixaJson := `{"ativo": "Tesouro IPCA+ 2035", "preco_atual": 2250.45, "z_score": -2.10, "risco_var": -4.15, "sinal": "COMPRA FORTE", "classe": "RENDA FIXA", "sinais_perfil": {"Agressivo": "COMPRA FORTE", "Conservador": "COMPRA FORTE", "Moderado": "COMPRA FORTE", "Arrojado": "COMPRA FORTE"}}`
	recomendacoes = append(recomendacoes, rendaFixaJson)

	// 👇 BUSCA O REGIME NO REDIS
	regimeAtual, errRegime := database.Rdb.Get(ctx, "regime_mercado_atual").Result()
	if errRegime != nil {
		regimeAtual = "ANALISANDO..." // Fallback se a IA ainda estiver calculando
	}

	// 👇 INJETA A CHAVE "regime" NO JSON DE RESPOSTA
	jsonResposta := fmt.Sprintf(`{"sucesso": true, "regime": "%s", "total": %d, "recomendacoes": [%s]}`,
		regimeAtual,
		len(recomendacoes),
		strings.Join(recomendacoes, ","))

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	w.Write([]byte(jsonResposta))
}

// @Summary Iniciar Ingestão Manual
// @Description Dispara manualmente a esteira de processamento concorrente do QuantAdvisor. O Go inicia de forma assíncrona o download em lote de séries temporais (YAHOO/BRAPI) e delega a calibragem matemática e estatística ao motor Python no background.
// @Tags Sistema
// @Success 200 {object} map[string]interface{}
// @Router /ingestao/iniciar [post]
func TriggerManual(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	// 1. DISPARO ASSÍNCRONO (Goroutine)
	// Isso joga a carga pesada para o segundo plano e libera o handler imediatamente
	go func() {
		log.Println("📡 [QuantAdvisor] Ingestão em lote iniciada em background...")

		// Executa todo o pipeline de downloads
		services.ExecutarIngestaoEmLote(services.CarteiraMercado)

		// 2. IMPRESSÃO EXATA NO CONSOLE
		// Assim que o loop de cima terminar, printa o JSON desejado no terminal do seu servidor
		log.Println(`{"sucesso": true, "mensagem": "Ingestão concluída"}`)
	}()

	// 3. RESPOSTA IMEDIATA
	// O Go responde instantaneamente com HTTP 200, evitando que o navegador fique em "reload"
	w.WriteHeader(http.StatusOK)
	w.Write([]byte(`{"sucesso": true, "mensagem": "Esteira de processamento ativada no console"}`))
}

// @Summary Listar Usuários
// @Description Retorna o diretório de contas virtuais cadastradas. Implementa controle de acesso (RBAC).
// @Tags Usuários
// @Produce json
// @Security BearerAuth
// @Success 200 {array} models.UsuarioResumo
// @Router /usuarios [get]
func ListarUsuarios(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" { return }

	idLogado, roleLogado := getAuth(r)

	// 🛡️ CORREÇÃO: Busca as duas moedas do banco de dados (saldo_brl, saldo_usd)
	query := `SELECT usuario_id, nome_cliente, perfil_risco, saldo_brl, saldo_usd, 
			  COALESCE(email, ''), COALESCE(whatsapp, ''), COALESCE(login, ''), 
			  COALESCE(lucro_acumulado, 0.0), COALESCE(role, 'CLIENTE'), 
			  COALESCE(data_cadastro, CURRENT_TIMESTAMP), COALESCE(piloto_automatico, false)
			  FROM contas_virtuais WHERE 1=1 `

	// ✨ A MÁGICA DA HIERARQUIA ACONTECE AQUI
	if idLogado == 1 {
		// 👑 MASTER ADMIN: Vê absolutamente todos os clientes e gestores
	} else if roleLogado == "GESTOR" {
		// 💼 SUB-GESTOR: Vê a si próprio E os clientes que ele mesmo criou
		query += fmt.Sprintf(" AND (usuario_id = %d OR gestor_id = %d) ", idLogado, idLogado)
	} else {
		// 👤 CLIENTE COMUM: Só enxerga a si mesmo
		query += fmt.Sprintf(" AND usuario_id = %d ", idLogado)
	}

	query += " ORDER BY usuario_id DESC"

	rows, err := database.Conn.Query(query)
	if err != nil {
		http.Error(w, `{"erro": "Falha ao buscar usuários"}`, http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	var usuarios []models.UsuarioResumo
	for rows.Next() {
		var u models.UsuarioResumo
		// 🛡️ CORREÇÃO: Lê o SaldoBRL e SaldoUSD
		rows.Scan(&u.ID, &u.Nome, &u.Perfil, &u.SaldoBRL, &u.SaldoUSD, &u.Email, &u.Whatsapp, &u.Login, &u.Lucro, &u.Role, &u.DataCadastro, &u.PilotoAutomatico)
		usuarios = append(usuarios, u)
	}
	
	if usuarios == nil { usuarios = []models.UsuarioResumo{} }
	json.NewEncoder(w).Encode(usuarios)
}

// @Summary Editar Conta
// @Description Modifica os metadados cadastrais e o perfil de risco de uma conta existente. O sistema avalia dinamicamente se a senha foi fornecida; caso omitida, o update ocorre de forma parcial, preservando a credencial anterior. Acesso restrito via JWT.
// @Tags Usuários
// @Accept json
// @Produce json
// @Security BearerAuth
// @Param request body models.EditarContaRequest true "Dados da conta"
// @Success 200 {object} map[string]interface{}
// @Router /usuarios/editar [post]
func EditarConta(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	idLogado, roleLogado := getAuth(r)
	var req models.EditarContaRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Payload inválido"}`, http.StatusBadRequest)
		return
	}

	// 🛡️ TRAVA DE SEGURANÇA BÁSICA: Cliente só edita a si próprio
	if roleLogado != "GESTOR" && idLogado != req.ID {
		http.Error(w, `{"sucesso": false, "erro": "Acesso negado!"}`, http.StatusForbidden)
		return
	}

	// 🛡️ TRAVA DE JURISDIÇÃO (Sub-Gestor não pode editar cliente de outro Sub-Gestor)
	// Só entra nessa verificação se for Gestor, não for o Master (ID 1) e não estiver editando a si mesmo.
	if roleLogado == "GESTOR" && idLogado != 1 && idLogado != req.ID {
		var donoID int
		errCheck := database.Conn.QueryRow("SELECT gestor_id FROM contas_virtuais WHERE usuario_id = $1", req.ID).Scan(&donoID)
		if errCheck != nil || donoID != idLogado {
			http.Error(w, `{"sucesso": false, "erro": "Operação bloqueada! Esta conta pertence a outra carteira institucional."}`, http.StatusForbidden)
			return
		}
	}

	var err error

	// Lógica Inteligente: Só atualiza a senha se o usuário digitou uma nova
	if req.Senha != "" {
		hashedBytes, errHash := bcrypt.GenerateFromPassword([]byte(req.Senha), bcrypt.DefaultCost)
		if errHash != nil {
			http.Error(w, `{"sucesso": false, "erro": "Erro ao processar senha"}`, http.StatusInternalServerError)
			return
		}
		query := `UPDATE contas_virtuais 
				  SET nome_cliente = $1, perfil_risco = $2, email = $3, whatsapp = $4, login = $5, senha = $6, role = $7, piloto_automatico = $8
				  WHERE usuario_id = $9`
		_, err = database.Conn.Exec(query, req.NomeCliente, req.PerfilRisco, req.Email, req.Whatsapp, req.Login, string(hashedBytes), req.Role, req.PilotoAutomatico, req.ID)
	} else {
		query := `UPDATE contas_virtuais 
				  SET nome_cliente = $1, perfil_risco = $2, email = $3, whatsapp = $4, login = $5, role = $6, piloto_automatico = $7
				  WHERE usuario_id = $8`
		_, err = database.Conn.Exec(query, req.NomeCliente, req.PerfilRisco, req.Email, req.Whatsapp, req.Login, req.Role, req.PilotoAutomatico, req.ID)
	}

	// Tratamento de erros do Banco de Dados
	if err != nil {
		erroString := err.Error()

		// Se tentar mudar para um Login que já pertence a outra pessoa
		if strings.Contains(erroString, "unique constraint") {
			http.Error(w, `{"sucesso": false, "erro": "Este Login já está em uso por outro cliente!"}`, http.StatusConflict)
			return
		}

		log.Printf("❌ ERRO DB (EditarConta): %v", err)
		http.Error(w, `{"sucesso": false, "erro": "Erro ao atualizar dados no banco."}`, http.StatusInternalServerError)
		return
	}

	w.Write([]byte(`{"sucesso": true, "mensagem": "Dados atualizados com sucesso!"}`))
}

// @Summary Criar Conta
// @Description Aloca uma nova 'Conta Virtual' no ecossistema transacional. Associa o cliente a um Perfil de Risco e credita o saldo simulado inicial. Estabelece a estrutura fundamental para a custódia de ativos e livros de registros diários.
// @Tags Usuários
// @Accept json
// @Produce json
// @Param request body models.NovaContaRequest true "Dados do novo cliente"
// @Success 200 {object} map[string]interface{}
// @Router /usuarios/criar [post]
func CriarConta(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" { return }

	// ✨ TRAVA DE SEGURANÇA ADICIONADA
	_, roleLogado := getAuth(r)
	if roleLogado != "GESTOR" {
		http.Error(w, `{"sucesso": false, "erro": "Acesso negado. Apenas Gestores podem criar contas manuais."}`, http.StatusForbidden)
		return
	}

	var req models.NovaContaRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Payload inválido"}`, http.StatusBadRequest)
		return
	}

	// Adicione piloto_automatico na query e no Exec
	idLogado, roleLogado := getAuth(r)
	if roleLogado != "GESTOR" && idLogado != 1 {
		http.Error(w, `{"sucesso": false, "erro": "Acesso negado. Apenas Gestores podem criar contas manuais."}`, http.StatusForbidden)
		return
	}

	// 🛡️ CORREÇÃO: O novo saldo vai cair exclusivamente em BRL e a senha é criptografada via Bcrypt.
	hashedBytes, errHash := bcrypt.GenerateFromPassword([]byte(req.Senha), bcrypt.DefaultCost)
	if errHash != nil {
		http.Error(w, `{"sucesso": false, "erro": "Erro ao processar senha"}`, http.StatusInternalServerError)
		return
	}

	query := `INSERT INTO contas_virtuais (nome_cliente, perfil_risco, saldo_brl, email, whatsapp, login, senha, role, piloto_automatico, gestor_id) 
			  VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`

	_, err := database.Conn.Exec(query, req.NomeCliente, req.PerfilRisco, req.SaldoInicial, req.Email, req.Whatsapp, req.Login, string(hashedBytes), req.Role, req.PilotoAutomatico, idLogado)
	if err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Erro ao criar conta no banco (Login já existe?)"}`, http.StatusInternalServerError)
		return
	}

	w.Write([]byte(`{"sucesso": true, "mensagem": "Conta criada com sucesso!"}`))
}

// @Summary Deletar Conta
// @Description Remove irrevogavelmente uma conta do ecossistema. Executa deleção em cascata (Cascade Delete) das posições em custódia na tabela 'posicoes_carteira' antes de remover a 'conta_virtual', assegurando a integridade relacional do banco. Exclusivo para Gestores.
// @Tags Usuários
// @Accept json
// @Security BearerAuth
// @Param request body models.DeletarContaRequest true "ID do usuário"
// @Success 200 {object} map[string]interface{}
// @Router /usuarios/deletar [post]
func DeletarConta(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}
	
	// ✨ CORREÇÃO AQUI: Lendo o idLogado em vez de descartar com '_'
	idLogado, roleLogado := getAuth(r)

	// 🛡️ TRAVA: Apenas Gestores podem deletar contas
	if roleLogado != "GESTOR" {
		http.Error(w, `{"sucesso": false, "erro": "Acesso negado! Apenas gestores podem excluir contas."}`, http.StatusForbidden)
		return
	}

	var req models.DeletarContaRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Payload inválido"}`, http.StatusBadRequest)
		return
	}

	// 🛡️ TRAVA DE JURISDIÇÃO
	if idLogado != 1 {
		var donoID int
		errCheck := database.Conn.QueryRow("SELECT gestor_id FROM contas_virtuais WHERE usuario_id = $1", req.ID).Scan(&donoID)
		if errCheck != nil || (req.ID != idLogado && donoID != idLogado) {
			http.Error(w, `{"sucesso": false, "erro": "Operação bloqueada! Esta conta pertence a outra carteira institucional."}`, http.StatusForbidden)
			return
		}
	}

	// Primeiro exclui posições para não dar erro de chave estrangeira
	database.Conn.Exec("DELETE FROM posicoes_carteira WHERE usuario_id = $1", req.ID)
	_, err := database.Conn.Exec("DELETE FROM contas_virtuais WHERE usuario_id = $1", req.ID)

	if err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Erro ao excluir"}`, http.StatusInternalServerError)
		return
	}
	
	w.Write([]byte(`{"sucesso": true, "mensagem": "Usuário removido!"}`))
}

// @Summary Enviar Ordem
// @Description Motor atômico de roteamento e liquidação. Executa as validações de saldo, recalcula o preço médio da custódia, avalia regras fiscais complexas (Isenção de 20k, limites de SWING/DAY TRADE), lança partidas dobradas e efetiva a mutação patrimonial na base relacional separando ativos BRL e USD.
// @Tags Operações
// @Accept json
// @Produce json
// @Security BearerAuth
// @Param request body models.OrdemRequest true "Dados da Ordem"
// @Success 200 {object} map[string]interface{}
// @Router /ordem [post]
func Ordem(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	// LÊ O PAYLOAD APENAS UMA VEZ
	var req models.OrdemRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Payload inválido"}`, http.StatusBadRequest)
		return
	}

	// 🛡️ NOVO RELÓGIO MULTI-MERCADO
	if !services.IsMercadoAberto(req.Ticker) {
		w.WriteHeader(http.StatusForbidden)
		json.NewEncoder(w).Encode(map[string]interface{}{
			"sucesso": false,
			"erro":    "Mercado Fechado: O pregão do ativo " + req.Ticker + " não está aberto neste horário.",
		})
		return
	}

	// ====================================================================
	// 🌍 DETECÇÃO INTELIGENTE DE MOEDA E VOLUME FINANCEIRO
	// ====================================================================
	moedaDaOperacao := req.Moeda
	taxaCambioNoAto := req.TaxaCambioMomento
	impactoFinanceiroBRL := req.VolumeBRL

	// Fallback de contingência caso a ordem venha manualmente do painel (React)
	if moedaDaOperacao == "" {
		moedaDaOperacao = "BRL"
		taxaCambioNoAto = 1.0
		
		// Heurística de isolamento: Se não tem número nem .SA, é ação americana.
		if !strings.ContainsAny(req.Ticker, "0123456789") && !strings.HasSuffix(req.Ticker, ".SA") {
			moedaDaOperacao = "USD"
			taxaCambioNoAto = services.DolarGlobal
			if taxaCambioNoAto <= 0 { taxaCambioNoAto = 5.50 } // Teto seguro
		}
		impactoFinanceiroBRL = (req.Preco * float64(req.Quantidade)) * taxaCambioNoAto
	}

	// Calcula o volume bruto exato na moeda em que o ativo é negociado
	volumeMoedaNativa := req.Preco * float64(req.Quantidade)

	idLogado, roleLogado := getAuth(r)

	// 🛡️ TRAVA: Se for cliente comum, ignora o ID enviado no JSON e força o ID do Token
	if roleLogado != "GESTOR" && idLogado != 1 {
		req.UsuarioID = idLogado
	}

	tx, err := database.Conn.Begin()
	if err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Erro de transação"}`, http.StatusInternalServerError)
		return
	}

	if req.TipoOrdem == "COMPRA" {
		// 1. Validação de saldo multi-moeda (Alpaca Redis para USD vs DB para BRL)
		if moedaDaOperacao == "USD" {
			var buyingPower float64 = volumeMoedaNativa
			if database.Rdb != nil {
				valStr, errRedis := database.Rdb.Get(database.Ctx, "hft:wallet:buying_power").Result()
				if errRedis == nil && valStr != "" {
					fmt.Sscanf(valStr, "%f", &buyingPower)
				} else {
					_ = database.Conn.QueryRow("SELECT saldo_usd FROM contas_virtuais WHERE usuario_id = $1", req.UsuarioID).Scan(&buyingPower)
				}
			} else {
				_ = database.Conn.QueryRow("SELECT saldo_usd FROM contas_virtuais WHERE usuario_id = $1", req.UsuarioID).Scan(&buyingPower)
			}

			if buyingPower < volumeMoedaNativa {
				tx.Rollback()
				http.Error(w, fmt.Sprintf(`{"sucesso": false, "erro": "Saldo insuficiente em USD na Alpaca (buying_power: US$ %.2f) para realizar a compra"}`, buyingPower), http.StatusPaymentRequired)
				return
			}

			// Desconta saldo_usd da conta virtual sem travar se a Alpaca autorizou via buying_power
			_, _ = tx.Exec("UPDATE contas_virtuais SET saldo_usd = GREATEST(0, saldo_usd - $1) WHERE usuario_id = $2", volumeMoedaNativa, req.UsuarioID)
		} else {
			queryDescontoSaldo := "UPDATE contas_virtuais SET saldo_brl = saldo_brl - $1 WHERE usuario_id = $2 AND saldo_brl >= $1"
			res, err := tx.Exec(queryDescontoSaldo, volumeMoedaNativa, req.UsuarioID)
			if err != nil || database.RowsAffected(res) == 0 {
				tx.Rollback()
				http.Error(w, `{"sucesso": false, "erro": "Saldo insuficiente em BRL para realizar a compra"}`, http.StatusPaymentRequired)
				return
			}
		}

		// 2. A Custódia é salva em Moeda Nativa (USD/BRL)
		queryPosicao := `
			INSERT INTO posicoes_carteira (usuario_id, ticker, quantidade_total, preco_medio, moeda)
			VALUES ($1, $2, $3, $4, $5)
			ON CONFLICT (usuario_id, ticker)
			DO UPDATE SET 
				preco_medio = ((posicoes_carteira.quantidade_total * posicoes_carteira.preco_medio) + (EXCLUDED.quantidade_total * EXCLUDED.preco_medio)) / (posicoes_carteira.quantidade_total + EXCLUDED.quantidade_total),
				quantidade_total = posicoes_carteira.quantidade_total + EXCLUDED.quantidade_total,
				moeda = EXCLUDED.moeda;
		`
		if _, err = tx.Exec(queryPosicao, req.UsuarioID, req.Ticker, req.Quantidade, req.Preco, moedaDaOperacao); err != nil {
			tx.Rollback()
			http.Error(w, `{"sucesso": false, "erro": "Erro na alocação da custódia"}`, http.StatusInternalServerError)
			return
		}

		// 3. Criação do Lote Fiscal Estrito e Fricção de Mercado (SEC/FINRA vs B3)
		var custoEstimadoNativo float64
		if moedaDaOperacao == "USD" {
			// Taxas EUA: SEC Fee (~0.00278% sobre volume) + FINRA TAF ($0.000166 por cota)
			custoEstimadoNativo = (volumeMoedaNativa * 0.0000278) + (float64(req.Quantidade) * 0.000166)
		} else {
			// Taxas Brasil: Emolumentos + Liquidação B3 (~0.03% sobre volume)
			custoEstimadoNativo = volumeMoedaNativa * 0.0003 
		}

		queryLote := `
			INSERT INTO lotes_fiscais (usuario_id, ticker, data_entrada, quantidade_inicial, quantidade_atual, preco_compra, custos_b3)
			VALUES ($1, $2, CURRENT_DATE, $3, $4, $5, $6)
		`
		tx.Exec(queryLote, req.UsuarioID, req.Ticker, req.Quantidade, req.Quantidade, req.Preco, custoEstimadoNativo)

		// 4. Partidas Dobradas
		historicoContabil := fmt.Sprintf("Compra de %d cotas de %s (%s)", req.Quantidade, req.Ticker, moedaDaOperacao)
		tx.Exec(`INSERT INTO lancamentos_contabeis (usuario_id, data_liquidacao, conta_debito, conta_credito, valor, historico) 
				 VALUES ($1, CURRENT_DATE + INTERVAL '2 days', 'ACOES_A_RECEBER_D2', 'CONTAS_A_PAGAR_B3_D2', $2, $3)`,
			req.UsuarioID, volumeMoedaNativa, historicoContabil)

	} else if req.TipoOrdem == "VENDA" {
		// 1. Validação de consistência básica da custódia total
		var qtdCustodia int
		var precoMedioCustodia float64
		errCustodia := tx.QueryRow("SELECT quantidade_total, preco_medio FROM posicoes_carteira WHERE usuario_id = $1 AND ticker = $2", req.UsuarioID, req.Ticker).Scan(&qtdCustodia, &precoMedioCustodia)

		if errCustodia != nil || qtdCustodia < req.Quantidade {
			tx.Rollback()
			http.Error(w, `{"sucesso": false, "erro": "Cotas insuficientes na custódia geral"}`, http.StatusNotAcceptable)
			return
		}

		// =====================================================================
		// 🛡️ GUARDIÃO FISCAL (Inteligência de Isenção de R$ 20k ou 35k)
		// =====================================================================
		volumeMedioHistoricoBRL := (float64(req.Quantidade) * precoMedioCustodia) * taxaCambioNoAto
		lucroEstimadoVenda := impactoFinanceiroBRL - volumeMedioHistoricoBRL

		var lucroProjetadoReinvestimento float64 = 0.0

		// 👉 Envia a Moeda para o Guardião aplicar a lei correta
		decisao := services.ValidarVendaComIsencao(req.UsuarioID, req.Ticker, impactoFinanceiroBRL, lucroEstimadoVenda, lucroProjetadoReinvestimento, moedaDaOperacao)

		if !decisao.Aprovada {
			tx.Rollback()
			mensagemErro := fmt.Sprintf(`{"sucesso": false, "erro": "%s"}`, decisao.MotivoVeto)
			http.Error(w, mensagemErro, http.StatusForbidden)
			log.Printf("🛑 HOLD FISCAL ATIVADO: %s | %s", req.Ticker, decisao.MotivoVeto)
			return
		}

		// Variáveis de controle para o loop de casamento fiscal
		qtdRestanteParaCasar := req.Quantidade
		var lucroTotalDayTrade float64 = 0.0
		var lucroTotalSwingTrade float64 = 0.0
		var volumeVendaSwing float64 = 0.0

		// 2. BUSCA POR COMPRAS FEITAS HOJE (Potencial Day Trade)
		rowsHoje, errHoje := tx.Query("SELECT id, quantidade_atual, preco_compra FROM lotes_fiscais WHERE usuario_id = $1 AND ticker = $2 AND data_entrada = CURRENT_DATE AND quantidade_atual > 0 ORDER BY id ASC", req.UsuarioID, req.Ticker)

		if errHoje == nil {
			for rowsHoje.Next() && qtdRestanteParaCasar > 0 {
				var loteID int
				var qtdDisponivelNoLote int
				var precoCompraLote float64
				rowsHoje.Scan(&loteID, &qtdDisponivelNoLote, &precoCompraLote)

				qtdCasada := qtdDisponivelNoLote
				if qtdRestanteParaCasar < qtdDisponivelNoLote {
					qtdCasada = qtdRestanteParaCasar
				}

				// Cálculo do lucro real do Day Trade do lote (Convertido para Reais)
				lucroLoteDT := ((req.Preco * taxaCambioNoAto) - (precoCompraLote * taxaCambioNoAto)) * float64(qtdCasada)
				lucroTotalDayTrade += lucroLoteDT

				tx.Exec("UPDATE lotes_fiscais SET quantidade_atual = quantidade_atual - $1 WHERE id = $2", qtdCasada, loteID)
				qtdRestanteParaCasar -= qtdCasada
			}
			rowsHoje.Close()
		}

		// 3. SE SOBROU QUANTIDADE: O excedente é tratado como Swing Trade (B3) ou Exterior (Wall St)
		var volumeVendaExterior float64 = 0.0
		var lucroTotalExterior float64 = 0.0

		if qtdRestanteParaCasar > 0 {
			volCalculado := (float64(qtdRestanteParaCasar) * req.Preco) * taxaCambioNoAto
			custoHistorico := (float64(qtdRestanteParaCasar) * precoMedioCustodia) * taxaCambioNoAto
			lucroCalculado := volCalculado - custoHistorico
			
			if moedaDaOperacao == "USD" {
				volumeVendaExterior = volCalculado
				lucroTotalExterior = lucroCalculado
			} else {
				volumeVendaSwing = volCalculado
				lucroTotalSwingTrade = lucroCalculado
			}
		}

		// 4. ATUALIZAÇÃO DO SALDO DEVOLVENDO O DINHEIRO PARA O BOLSO CORRETO
		tx.Exec("UPDATE posicoes_carteira SET quantidade_total = quantidade_total - $1 WHERE usuario_id = $2 AND ticker = $3", req.Quantidade, req.UsuarioID, req.Ticker)
		tx.Exec("DELETE FROM posicoes_carteira WHERE usuario_id = $1 AND ticker = $2 AND quantidade_total = 0", req.UsuarioID, req.Ticker)

		if moedaDaOperacao == "USD" {
			tx.Exec("UPDATE contas_virtuais SET saldo_usd = saldo_usd + $1 WHERE usuario_id = $2", volumeMoedaNativa, req.UsuarioID)
		} else {
			tx.Exec("UPDATE contas_virtuais SET saldo_brl = saldo_brl + $1 WHERE usuario_id = $2", volumeMoedaNativa, req.UsuarioID)
		}

		// 5. MEMÓRIA DO LIVRO DIÁRIO
		historicoContabil := fmt.Sprintf("Venda de %d %s (%s). [Lucro B3: R$ %.2f | Lucro EUA: R$ %.2f | DT: R$ %.2f]", req.Quantidade, req.Ticker, moedaDaOperacao, lucroTotalSwingTrade, lucroTotalExterior, lucroTotalDayTrade)
		tx.Exec(`INSERT INTO lancamentos_contabeis (usuario_id, data_liquidacao, conta_debito, conta_credito, valor, historico) 
				 VALUES ($1, CURRENT_DATE + INTERVAL '2 days', 'CONTAS_A_RECEBER_B3_D2', 'ACOES_ENTREGUES_D2', $2, $3)`,
			req.UsuarioID, volumeMoedaNativa, historicoContabil)

		// 6. PROCESSAMENTO DO IMPOSTO RETIDO (Dedo-Duro) E CONTA FISCAL MENSAL
		anoMesAtual := time.Now().Format("2006-01")
		var irrfDedoDuro float64 = 0.0

		// Só cobra Dedo-Duro se a jurisdição for B3 (Brasil)
		if moedaDaOperacao == "BRL" {
			if lucroTotalDayTrade > 0 {
				irrfDedoDuro += lucroTotalDayTrade * 0.01
			}
			if volumeVendaSwing > 0 {
				irrfDedoDuro += volumeVendaSwing * 0.00005
			}
		}

		queryFiscal := `
			INSERT INTO ledger_fiscal_mensal (
				usuario_id, ano_mes, volume_vendas_swing, lucro_realizado_swing, 
				volume_vendas_exterior, lucro_realizado_exterior, 
				lucro_realizado_daytrade, irrf_dedo_duro_retido
			)
			VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
			ON CONFLICT (usuario_id, ano_mes)
			DO UPDATE SET 
				volume_vendas_swing = ledger_fiscal_mensal.volume_vendas_swing + EXCLUDED.volume_vendas_swing,
				lucro_realizado_swing = ledger_fiscal_mensal.lucro_realizado_swing + EXCLUDED.lucro_realizado_swing,
				volume_vendas_exterior = ledger_fiscal_mensal.volume_vendas_exterior + EXCLUDED.volume_vendas_exterior,
				lucro_realizado_exterior = ledger_fiscal_mensal.lucro_realizado_exterior + EXCLUDED.lucro_realizado_exterior,
				lucro_realizado_daytrade = ledger_fiscal_mensal.lucro_realizado_daytrade + EXCLUDED.lucro_realizado_daytrade,
				irrf_dedo_duro_retido = ledger_fiscal_mensal.irrf_dedo_duro_retido + EXCLUDED.irrf_dedo_duro_retido;
		`
		tx.Exec(queryFiscal, req.UsuarioID, anoMesAtual, volumeVendaSwing, lucroTotalSwingTrade, volumeVendaExterior, lucroTotalExterior, lucroTotalDayTrade, irrfDedoDuro)
	}
	
	// ====================================================================
	// 7. FOTOGRAFIA FINAL COM MOEDA, CÂMBIO E REGIME MACROECONÔMICO
	// ====================================================================
	// Busca o regime atual na memória RAM (Redis)
	regimeAtual, errRegime := database.Rdb.Get(database.Ctx, "regime_mercado_atual").Result()
	if errRegime != nil || regimeAtual == "" {
		regimeAtual = "DESCONHECIDO"
	}

	tx.Exec("INSERT INTO ordens_executadas (usuario_id, ticker, tipo_ordem, quantidade, preco_execucao, moeda, taxa_cambio_momento, volume_brl, regime_mercado) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)", 
		req.UsuarioID, req.Ticker, req.TipoOrdem, req.Quantidade, req.Preco, moedaDaOperacao, taxaCambioNoAto, impactoFinanceiroBRL, regimeAtual)

	tx.Commit()

	w.Write([]byte(`{"sucesso": true, "mensagem": "Ordem executada com sucesso!"}`))
	log.Printf("⚖️ ORDEM EXECUTADA [%s]: %s | %s | Qtd: %d | Nativo: %s %.2f", regimeAtual, req.TipoOrdem, req.Ticker, req.Quantidade, moedaDaOperacao, req.Preco)
}

// @Summary Histórico de Ordens
// @Description Gera o extrato de movimentação financeira. Lista todas as ordens (Compra e Venda) já liquidadas e registradas para o usuário, ordenadas cronologicamente. Base para a auditoria temporal e relatórios de fluxo de caixa.
// @Tags Operações
// @Produce json
// @Security BearerAuth
// @Success 200 {object} map[string]interface{}
// @Router /historico [get]
func HistoricoOrdens(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	usuarioID := 1
	if idStr := r.URL.Query().Get("usuario_id"); idStr != "" {
		if parsed, err := strconv.Atoi(idStr); err == nil {
			usuarioID = parsed
		}
	}

	query := `
		SELECT id, ticker, tipo_ordem, quantidade, preco_execucao, data_hora 
		FROM ordens_executadas 
		WHERE usuario_id = $1 
		ORDER BY data_hora DESC
	`
	rows, err := database.Conn.Query(query, usuarioID)
	if err != nil {
		log.Printf("Erro ao buscar histórico: %v", err)
		http.Error(w, `{"sucesso": false, "erro": "Erro ao buscar histórico no banco"}`, http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	var historico []models.OrdemExecutada
	for rows.Next() {
		var o models.OrdemExecutada
		if err := rows.Scan(&o.ID, &o.Ticker, &o.TipoOrdem, &o.Quantidade, &o.PrecoExecucao, &o.DataHora); err != nil {
			log.Printf("Erro no scan da linha: %v", err)
			continue
		}
		historico = append(historico, o)
	}

	if historico == nil {
		historico = []models.OrdemExecutada{}
	}

	json.NewEncoder(w).Encode(map[string]interface{}{
		"sucesso": true,
		"ordens":  historico,
	})
}

// @Summary Listar Perfis
// @Description Fornece a lista de perfis de investidor comportamentais parametrizados no banco de dados (ex: Conservador, Moderado, Arrojado). Estes perfis definem os limites de tolerância ao risco utilizados nos cálculos do Critério de Kelly e da IA.
// @Tags Usuários
// @Produce json
// @Security BearerAuth
// @Success 200 {array} models.PerfilInvestidor
// @Router /perfis [get]
func ListarPerfis(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	} // Adicionado proteção de preflight CORS

	query := "SELECT id, nome_usuario, perfil_comportamental FROM perfil_investidor ORDER BY id"
	rows, err := database.Conn.Query(query)
	if err != nil {
		log.Printf("❌ ERRO DB (ListarPerfis): %v", err) // Log no terminal para diagnóstico rápido
		http.Error(w, `{"erro": "Falha ao buscar perfis de investidor"}`, http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	var perfis []models.PerfilInvestidor
	for rows.Next() {
		var p models.PerfilInvestidor
		rows.Scan(&p.ID, &p.NomeUsuario, &p.PerfilComportamental)
		perfis = append(perfis, p)
	}

	if perfis == nil {
		perfis = []models.PerfilInvestidor{}
	}

	json.NewEncoder(w).Encode(perfis)
}

// @Summary Backtest
// @Description Atua como um API Gateway para o motor Python. Transfere a solicitação de backtesting retrospectivo do ativo escolhido, onde o algoritmo de inteligência artificial (VectorBT) simula a estratégia em dados históricos reais para validar o Índice de Sharpe e Drawdown.
// @Tags IA
// @Param ticker query string true "Ticker do ativo"
// @Success 200 {object} map[string]interface{}
// @Router /backtest [get]
func Backtest(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	ticker := r.URL.Query().Get("ticker")
	if ticker == "" {
		http.Error(w, `{"sucesso": false, "erro": "Ticker não informado"}`, http.StatusBadRequest)
		return
	}

	resp, err := services.HTTPClient.Get(services.GetPythonEngineURL() + "/backtest/" + ticker)
	if err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Microsserviço de IA offline"}`, http.StatusInternalServerError)
		return
	}
	defer resp.Body.Close()

	body, _ := ioutil.ReadAll(resp.Body)
	w.WriteHeader(resp.StatusCode)
	w.Write(body)
}

// @Summary Risco Sistemico
// @Description Atua como API Gateway. Delega ao motor Python a construção dinâmica de uma matriz de correlação cruzada (Pearson) de todos os ativos da carteira em tempo real, detectando excesso de concentração de risco ou sugerindo ativos descorrelacionados (Hedge).
// @Tags IA
// @Success 200 {object} map[string]interface{}
// @Router /risco [get]
// @Summary Risco Sistemico
func RiscoSistemico(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	// Descobre quem é o usuário logado
	idLogado, _ := getAuth(r)

	// 1. Busca os ativos que estão ATUALMENTE na carteira do cliente
	rows, err := database.Conn.Query("SELECT DISTINCT ticker FROM posicoes_carteira WHERE usuario_id = $1", idLogado)
	var tickers []string
	
	if err == nil {
		defer rows.Close()
		for rows.Next() {
			var t string
			if err := rows.Scan(&t); err == nil {
				tickers = append(tickers, t)
			}
		}
	}

	// 2. Adiciona os "Portos Seguros" e Benchmarks do mercado para garantir que o gráfico seja útil
	// mesmo se o cliente tiver apenas 1 ou nenhum ativo na carteira.
	benchmarks := []string{"BOVA11", "IVVB11", "SMAL11", "SPY", "QQQ"}
	for _, b := range benchmarks {
		jaExiste := false
		for _, t := range tickers {
			if t == b {
				jaExiste = true
				break
			}
		}
		if !jaExiste {
			tickers = append(tickers, b)
		}
	}

	// Monta o payload para o microsserviço Python
	req := models.RiscoRequestPython{Tickers: tickers}
	payloadBytes, _ := json.Marshal(req)

	resp, err := services.HTTPClient.Post(services.GetPythonEngineURL()+"/risco", "application/json", bytes.NewBuffer(payloadBytes))
	
	if err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Microsserviço de IA offline ou excedeu o tempo limite."}`, http.StatusInternalServerError)
		return
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(resp.StatusCode)
	w.Write(body)
}

func ServeHeatmap(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	caminhoImagem := "/workspace/QUANTADVISOR/motor_python/heatmap.png"
	http.ServeFile(w, r, caminhoImagem)
}

// @Summary Monte Carlo
// @Description Atua como API Gateway. Delega ao motor Python o cálculo de projeções estocásticas utilizando o modelo matemático Merton Jump-Diffusion, desenhando milhares de trajetórias de preços futuras (Gaps de Poisson e Movimentos Brownianos) e extraindo os percentis 5%, 50% e 95%.
// @Tags IA
// @Param ticker query string true "Ticker"
// @Success 200 {object} map[string]interface{}
// @Router /montecarlo [get]
func SimularMonteCarlo(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	ticker := r.URL.Query().Get("ticker")
	if ticker == "" {
		http.Error(w, `{"sucesso": false, "erro": "Ticker não informado"}`, http.StatusBadRequest)
		return
	}

	resp, err := services.HTTPClient.Get(services.GetPythonEngineURL() + "/montecarlo/" + ticker)
	if err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Microsserviço de IA offline"}`, http.StatusInternalServerError)
		return
	}
	defer resp.Body.Close()

	body, _ := ioutil.ReadAll(resp.Body)
	w.WriteHeader(resp.StatusCode)
	w.Write(body)
}

// @Summary Projeção
// @Description Calcula a expectativa de crescimento de longo prazo do patrimônio do cliente em diferentes veículos (Renda Fixa e Variável). Combina os retornos históricos reais da carteira de ações com as curvas de juros atuais da B3 (Selic, IPCA+, CDBs), descontando a inflação (Efeito Fisher) e impostos.
// @Tags IA
// @Success 200 {object} map[string]interface{}
// @Router /portfolio/projecao [get]
func ProjecaoClasses(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	usuarioID := 1
	if idStr := r.URL.Query().Get("usuario_id"); idStr != "" {
		if parsed, err := strconv.Atoi(idStr); err == nil {
			usuarioID = parsed
		}
	}

	// 🛡️ CORREÇÃO: Lê o saldo BRL e USD separados
	var caixaBRL, caixaUSD float64
	_ = database.Conn.QueryRow("SELECT saldo_brl, saldo_usd FROM contas_virtuais WHERE usuario_id = $1", usuarioID).Scan(&caixaBRL, &caixaUSD)

	rows, _ := database.Conn.Query("SELECT ticker, quantidade_total, preco_medio FROM posicoes_carteira WHERE usuario_id = $1", usuarioID)
	defer rows.Close()

	valoresAcoes := make(map[string]float64)
	for rows.Next() {
		var ticker string
		var qtd int
		var precoMedio float64
		rows.Scan(&ticker, &qtd, &precoMedio)
		valoresAcoes[ticker] = float64(qtd) * precoMedio
	}

	payload := map[string]interface{}{
		"valores_acoes": valoresAcoes,
		"caixa_livre":   caixaBRL,
		"caixa_usd":     caixaUSD, // 👈 Envia o USD separado para o Python somar com a taxa de câmbio
		"taxa_selic":    services.TaxaSelicGlobal,
	}
	payloadBytes, _ := json.Marshal(payload)

	resp, err := services.HTTPClient.Post(services.GetPythonEngineURL()+"/projecao", "application/json", bytes.NewBuffer(payloadBytes))
	if err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Microsserviço de IA offline"}`, http.StatusInternalServerError)
		return
	}
	defer resp.Body.Close()

	body, _ := ioutil.ReadAll(resp.Body)
	w.WriteHeader(resp.StatusCode)
	w.Write(body)
}

// ==========================================
// FUNÇÕES INTEGRADAS AO FASTAPI (MICROSERVIÇO)
// ==========================================

// @Summary Resumo da Carteira
// @Description Agrega e retorna a custódia patrimonial atual do usuário, com filtro estrito de Jurisdição (Moeda).
// @Tags Operações
// @Produce json
// @Security BearerAuth
// @Success 200 {object} map[string]interface{}
// @Router /carteira [get]
func Carteira(w http.ResponseWriter, r *http.Request) {
	defer func() {
		if rec := recover(); rec != nil {
			log.Printf("🚨 [CRITICAL] Panic evitado na Carteira: %v", rec)
			http.Error(w, `{"sucesso": false, "erro": "Erro crítico ao carregar dados da carteira."}`, http.StatusInternalServerError)
		}
	}()

	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	idLogado, roleLogado := getAuth(r)
	usuarioID := idLogado

	// Filtro de Gestor
	if (roleLogado == "GESTOR" || idLogado == 1) && r.URL.Query().Get("usuario_id") != "" {
		if parsed, err := strconv.Atoi(r.URL.Query().Get("usuario_id")); err == nil {
			usuarioID = parsed
		}
	}

	// 🌟 NOVO: O filtro de jurisdição (BRL ou USD)
	moedaFiltro := r.URL.Query().Get("moeda")

	var resp models.CarteiraResponse
	resp.Sucesso = true

	// Lê os caixas separadamente do banco
	err := database.Conn.QueryRow("SELECT nome_cliente, saldo_brl, saldo_usd FROM contas_virtuais WHERE usuario_id = $1", usuarioID).Scan(&resp.NomeCliente, &resp.SaldoBRL, &resp.SaldoUSD)
	if err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Conta não encontrada"}`, http.StatusNotFound)
		return
	}

	// 🌟 LÓGICA DE ISOLAMENTO: Filtra a query de banco de dados com base na moeda
	query := "SELECT ticker, quantidade_total, preco_medio, moeda FROM posicoes_carteira WHERE usuario_id = $1"
	var rows *sql.Rows
	if moedaFiltro != "" && moedaFiltro != "TODOS" {
		query += " AND moeda = $2"
		rows, err = database.Conn.Query(query, usuarioID, moedaFiltro)
	} else {
		rows, err = database.Conn.Query(query, usuarioID)
	}

	if err == nil {
		defer rows.Close() 
		for rows.Next() {
			var p models.Posicao
			
			if errScan := rows.Scan(&p.Ticker, &p.Quantidade, &p.PrecoMedio, &p.Moeda); errScan == nil {
				// Buscar preço em tempo real no Redis
				chaveRedis := fmt.Sprintf("ticker:%s", p.Ticker)
				valRedis, errRedis := database.Rdb.Get(database.Ctx, chaveRedis).Result()
				
				if errRedis == nil {
					var dadosAtivo map[string]interface{}
					if errJson := json.Unmarshal([]byte(valRedis), &dadosAtivo); errJson == nil {
						if pAtual, ok := dadosAtivo["preco_atual"].(float64); ok {
							precoCopia := pAtual
							p.PrecoAtual = &precoCopia
							p.StatusCotacao = "OK"

							// Calcula o PnL na moeda nativa do ativo
							lucroNativo := (pAtual - p.PrecoMedio) * float64(p.Quantidade)
							
							// A carteira converte o lucro para BRL global apenas no painel macro, 
							// mas na linha do ativo o lucro financeiro permanece bruto e na moeda nativa.
							if p.Moeda == "USD" {
								p.LucroPrejuizoFinanceiro = lucroNativo * services.DolarGlobal
							} else {
								p.LucroPrejuizoFinanceiro = lucroNativo
							}

							if p.PrecoMedio > 0 {
								p.LucroPrejuizoPercentual = ((pAtual / p.PrecoMedio) - 1.0) * 100.0
							}

						} else {
							p.PrecoAtual = nil
							p.StatusCotacao = "INDISPONIVEL"
						}
					}
				} else {
					p.PrecoAtual = nil
					p.StatusCotacao = "INDISPONIVEL"
				}

				resp.Posicoes = append(resp.Posicoes, p)
			}
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
}

// @Summary Otimizar Carteira (HRP + NLP) Isolada por Moeda
// @Description Rebalanceamento quantitativo separado por Jurisdição. Não mistura BRL com USD.
// @Tags IA
// @Router /otimizar [post]
func OtimizarCarteira(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	w.Header().Set("Content-Type", "application/json")

	usuarioID := 1
	if idStr := r.URL.Query().Get("usuario_id"); idStr != "" {
		if parsed, err := strconv.Atoi(idStr); err == nil {
			usuarioID = parsed
		}
	}

	// 🌟 NOVO: Lê a jurisdição do terminal (BRL ou USD)
	moedaFiltro := r.URL.Query().Get("moeda")
	if moedaFiltro == "" {
		moedaFiltro = "BRL" // Padrão B3
	}

	var caixaBRL, caixaUSD float64
	var perfilRisco string

	err := database.Conn.QueryRow("SELECT saldo_brl, saldo_usd, perfil_risco FROM contas_virtuais WHERE usuario_id = $1", usuarioID).Scan(&caixaBRL, &caixaUSD, &perfilRisco)
	if err != nil {
		w.WriteHeader(http.StatusNotFound)
		w.Write([]byte(`{"sucesso": false, "erro": "Conta não encontrada"}`))
		return
	}

	// 🌟 LÓGICA DE ISOLAMENTO: Busca a custódia apenas daquela moeda!
	queryPosicoes := "SELECT ticker, quantidade_total, preco_medio FROM posicoes_carteira WHERE usuario_id = $1 AND moeda = $2"
	rows, err := database.Conn.Query(queryPosicoes, usuarioID, moedaFiltro)
	if err != nil {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte(`{"sucesso": false, "erro": "Erro ao ler carteira no banco"}`))
		return
	}
	defer rows.Close()

	tickers := []string{}
	quantidadesAtuais := make(map[string]float64)

	for rows.Next() {
		var ticker string
		var qtd float64
		var precoMedio float64
		rows.Scan(&ticker, &qtd, &precoMedio)

		tickers = append(tickers, ticker)
		quantidadesAtuais[ticker] = qtd
	}

	// 🌟 ISOLAMENTO DO CAIXA: Envia apenas o caixa da jurisdição correspondente para a IA alocar
	caixaOperacional := caixaBRL
	if moedaFiltro == "USD" {
		caixaOperacional = caixaUSD
	}

	payload := map[string]interface{}{
		"usuario_id":  usuarioID,
		"tickers":     tickers,
		"quantidades": quantidadesAtuais,
		"caixa_livre": caixaOperacional, // Manda apenas o dinheiro disponível daquela moeda
		"caixa_usd":   0.0,              // Anulamos a outra variável para o Python não misturar
		"perfil":      perfilRisco,
		"jurisdicao":  moedaFiltro,      // Avisa o Python qual é o universo de ativos alvo
	}
	payloadBytes, _ := json.Marshal(payload)

	log.Printf("DEBUG: Enviando Carteira %s para Otimização HRP", moedaFiltro)

	// Comunicação com o FastAPI
	fastApiURL := services.GetPythonEngineURL() + "/api/otimizar/hrp_nlp"
	resp, err := services.HTTPClient.Post(fastApiURL, "application/json", bytes.NewBuffer(payloadBytes))
	if err != nil {
		log.Printf("Falha na comunicação HTTP com o motor quantitativo: %v", err)
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte(`{"sucesso": false, "erro": "Microsserviço Quantitativo off-line ou inacessível"}`))
		return
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)
	w.WriteHeader(resp.StatusCode)
	w.Write(body)
}

// @Summary Prever LSTM
// @Description Dispara a inferência da Rede Neural Recorrente (Long Short-Term Memory). Utilizando a arquitetura 'Shared Bus', envia apenas um sinal (Trigger) para o Python ler a série temporal na RAM (Redis) e devolver a projeção algorítmica do preço D+1 do ativo.
// @Tags IA
// @Accept json
// @Produce json
// @Security BearerAuth
// @Success 200 {object} map[string]interface{}
// @Router /ml/prever [post]
func PreverLSTM(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	ticker := r.URL.Query().Get("ticker")

	payload := map[string]interface{}{
		"ticker": ticker,
	}
	payloadBytes, _ := json.Marshal(payload)
	log.Printf("DEBUG: Disparando sinal LSTM via Shared Bus (Ticker: %s)", ticker)

	resp, err := services.HTTPClient.Post(services.GetPythonEngineURL()+"/lstm", "application/json", bytes.NewBuffer(payloadBytes))
	if err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Microsserviço de IA indisponível"}`, http.StatusInternalServerError)
		return
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)

	w.WriteHeader(resp.StatusCode)
	w.Write(body)
}

// @Summary Adicionar Carrinho
// @Description Intercepta ordens sugeridas pela inteligência artificial ou agendadas pelo usuário fora do horário de pregão. Em vez de liquidar a transação na B3, persiste os metadados da ordem em estado 'PENDENTE' na fila do Carrinho Noturno.
// @Tags Carrinho
// @Accept json
// @Security BearerAuth
// @Param request body models.OrdemRequest true "Dados"
// @Success 200 {object} map[string]interface{}
// @Router /adicionar-carrinho [post]
func AdicionarAoCarrinho(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	var req models.OrdemRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Payload inválido"}`, http.StatusBadRequest)
		return
	}

	// Insere no carrinho em vez de executar na custódia
	query := `INSERT INTO carrinho_de_ordens (usuario_id, ticker, tipo_ordem, quantidade, preco_sugerido, status) 
              VALUES ($1, $2, $3, $4, $5, 'PENDENTE')`

	_, err := database.Conn.Exec(query, req.UsuarioID, req.Ticker, req.TipoOrdem, req.Quantidade, req.Preco)
	if err != nil {
		log.Printf("Erro ao salvar no carrinho: %v", err)
		http.Error(w, `{"sucesso": false, "erro": "Erro ao salvar sugestão"}`, http.StatusInternalServerError)
		return
	}

	w.Write([]byte(`{"sucesso": true, "mensagem": "Sugestão adicionada ao carrinho de análise!"}`))
}

// @Summary Listar Carrinho
// @Description Retorna o pool de ordens 'PENDENTES' aguardando execução na abertura do próximo pregão. Utilizado pelo robô despertador e pelo painel do usuário para visualização prévia da intenção de rebalanceamento.
// @Tags Carrinho
// @Security BearerAuth
// @Success 200 {array} models.CarrinhoItem
// @Router /carrinho [get]
func ListarCarrinho(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	usuarioID := 1 // Fallback padrão
	if idStr := r.URL.Query().Get("usuario_id"); idStr != "" {
		if parsed, err := strconv.Atoi(idStr); err == nil {
			usuarioID = parsed
		}
	}

	rows, err := database.Conn.Query("SELECT id, ticker, tipo_ordem, quantidade, preco_sugerido FROM carrinho_de_ordens WHERE usuario_id = $1 AND status = 'PENDENTE'", usuarioID)
	if err != nil {
		http.Error(w, `{"erro": "Falha ao buscar carrinho"}`, http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	type CarrinhoItem struct {
		ID     int     `json:"id"`
		Ticker string  `json:"ticker"`
		Tipo   string  `json:"tipo"`
		Qtd    int     `json:"quantidade"`
		Preco  float64 `json:"preco"`
	}

	var itens []CarrinhoItem
	for rows.Next() {
		var i CarrinhoItem
		rows.Scan(&i.ID, &i.Ticker, &i.Tipo, &i.Qtd, &i.Preco)
		itens = append(itens, i)
	}
	json.NewEncoder(w).Encode(itens)
}

// @Summary Limpar Carrinho
// @Description Processa a limpeza e rejeição explícita de ordens. Remove em lote (Batch Delete) registros específicos do Carrinho Noturno, abortando sugestões geradas pela IA que o cliente ou o Gestor decidiram não acatar.
// @Tags Carrinho
// @Accept json
// @Security BearerAuth
// @Param request body models.LimparCarrinhoReq true "IDs para deletar"
// @Success 200 {object} map[string]interface{}
// @Router /carrinho/limpar [post]
func LimparCarrinho(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	var req models.LimparCarrinhoReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Payload inválido"}`, http.StatusBadRequest)
		return
	}

	// Deleta os "fantasmas" aprovados do banco de dados
	for _, id := range req.IDs {
		database.Conn.Exec("DELETE FROM carrinho_de_ordens WHERE id = $1", id)
	}

	w.Write([]byte(`{"sucesso": true, "mensagem": "Carrinho atualizado com sucesso!"}`))
}

// @Summary Agente Causalidade
// @Description Encaminha prompts de raciocínio lógico financeiro para a IA Generativa (LLM). O modelo cruza os logs internos do motor estatístico com os dados macroeconômicos do mercado para explicar as decisões da rede neural de forma didática.
// @Tags IA
// @Accept json
// @Security BearerAuth
// @Success 200 {object} map[string]interface{}
// @Router /agente/causalidade [post]
func AgenteCausalidade(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	// Repassa o JSON recebido diretamente para o motor Python
	bodyBytes, _ := ioutil.ReadAll(r.Body)
	resp, err := services.HTTPClient.Post(services.GetPythonEngineURL()+"/agente/causalidade", "application/json", bytes.NewBuffer(bodyBytes))
	if err != nil {
		log.Printf("Falha na comunicação HTTP com o Agente IA: %v", err)
		http.Error(w, `{"sucesso": false, "erro": "Agente LLM off-line"}`, http.StatusInternalServerError)
		return
	}
	defer resp.Body.Close()

	respBody, _ := ioutil.ReadAll(resp.Body)
	w.WriteHeader(resp.StatusCode)
	w.Write(respBody)
}

// @Summary Listar Lancamentos
// @Description Acesso ao Livro Diário do sistema (Compliance). Retorna todos os lançamentos contábeis gerados nas operações do cliente, refletindo os lançamentos de débito e crédito no formato de partidas dobradas e suas respectivas datas de liquidação na B3 (D+2).
// @Tags Compliance
// @Security BearerAuth
// @Success 200 {array} models.ItemLancamento
// @Router /compliance/lancamentos [get]
func ListarLancamentosContabeis(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	usuarioID := r.URL.Query().Get("usuario_id")
	if usuarioID == "" {
		http.Error(w, `{"erro": "usuario_id obrigatório"}`, http.StatusBadRequest)
		return
	}

	query := `SELECT id, conta_debito, conta_credito, valor, historico, data_lancamento, data_liquidacao 
			  FROM lancamentos_contabeis WHERE usuario_id = $1 ORDER BY data_lancamento DESC`

	rows, err := database.Conn.Query(query, usuarioID)
	if err != nil {
		http.Error(w, `{"erro": "Falha ao buscar lançamentos"}`, http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	type ItemLancamento struct {
		ID         int     `json:"id"`
		Debito     string  `json:"conta_debito"`
		Credito    string  `json:"conta_credito"`
		Valor      float64 `json:"valor"`
		Historico  string  `json:"historico"`
		Data       string  `json:"data_lancamento"`
		Liquidacao string  `json:"data_liquidacao"`
	}

	var lancamentos []ItemLancamento
	for rows.Next() {
		var l ItemLancamento
		rows.Scan(&l.ID, &l.Debito, &l.Credito, &l.Valor, &l.Historico, &l.Data, &l.Liquidacao)
		lancamentos = append(lancamentos, l)
	}

	if lancamentos == nil {
		lancamentos = []ItemLancamento{}
	}
	json.NewEncoder(w).Encode(lancamentos)
}

// @Summary Listar Lotes
// @Description Acesso ao módulo fiscal de custódia (FIFO). Retorna os lotes granulares de ações compradas pelo cliente, essenciais para a Inteligência Artificial e a Tesouraria calcularem com precisão os preços de aquisição, isenções tributárias e o custo marginal de cada cota.
// @Tags Compliance
// @Security BearerAuth
// @Success 200 {array} models.LoteFiscal
// @Router /compliance/lotes [get]
func ListarLotesFiscais(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	usuarioID := r.URL.Query().Get("usuario_id")
	query := `SELECT id, ticker, data_entrada, quantidade_inicial, quantidade_atual, preco_compra, custos_b3 
			  FROM lotes_fiscais WHERE usuario_id = $1 AND quantidade_atual > 0 ORDER BY data_entrada ASC`

	rows, err := database.Conn.Query(query, usuarioID)
	if err != nil {
		http.Error(w, `{"erro": "Falha ao buscar lotes"}`, http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	var lotes []models.LoteFiscal
	for rows.Next() {
		var lf models.LoteFiscal
		var dataEntrada time.Time
		rows.Scan(&lf.ID, &lf.Ticker, &dataEntrada, &lf.QuantidadeInicial, &lf.QuantidadeAtual, &lf.PrecoCompra, &lf.CustosB3)
		// Você pode formatar a data aqui ou repassar como string mudando a struct
		lotes = append(lotes, lf)
	}
	if lotes == nil {
		lotes = []models.LoteFiscal{}
	}
	json.NewEncoder(w).Encode(lotes)
}

// @Summary Resumo Fiscal
// @Description Executa a Malha Fina fiscal completa separando jurisdições (B3 e Exterior).
// @Tags Compliance
// @Router /compliance/resumo-fiscal [get]
func BuscarResumoFiscalMensal(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" { return }

	usuarioID := r.URL.Query().Get("usuario_id")
	mesAlvo := r.URL.Query().Get("ano_mes")

	query := `SELECT ano_mes, volume_vendas_swing, lucro_realizado_swing, 
					 COALESCE(volume_vendas_exterior, 0), COALESCE(lucro_realizado_exterior, 0),
					 lucro_realizado_daytrade, irrf_dedo_duro_retido 
			  FROM ledger_fiscal_mensal 
			  WHERE usuario_id = $1 AND ano_mes <= $2 ORDER BY ano_mes ASC`

	rows, err := database.Conn.Query(query, usuarioID, mesAlvo)
	if err != nil {
		http.Error(w, `{"erro": "Falha ao processar malha fina fiscal"}`, http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	var prejAcumSwing, prejAcumExt, prejAcumDT float64
	res := models.ResumoFiscalMensal{AnoMes: mesAlvo, IsentoSwing: true, IsentoExterior: true}
	encontrouMesAtual := false

	for rows.Next() {
		var mes string
		var volSwing, lucroSwing, volExt, lucroExt, lucroDT, irrf float64
		rows.Scan(&mes, &volSwing, &lucroSwing, &volExt, &lucroExt, &lucroDT, &irrf)

		isentoSwingNesteMes := volSwing <= 20000
		isentoExtNesteMes := volExt <= 35000

		if mes == mesAlvo {
			encontrouMesAtual = true
			res.VolumeVendasSwing = volSwing
			res.LucroRealizadoSwing = lucroSwing
			res.VolumeVendasExterior = volExt
			res.LucroRealizadoExterior = lucroExt
			res.LucroRealizadoDayTrade = lucroDT
			res.IrrfDedoDuro = irrf
			res.PrejuizoAnteriorSwing = prejAcumSwing
			res.PrejuizoAnteriorExterior = prejAcumExt
			res.PrejuizoAnteriorDT = prejAcumDT
			res.IsentoSwing = isentoSwingNesteMes
			res.IsentoExterior = isentoExtNesteMes
		}

		// B3 (SWING TRADE)
		if lucroSwing < 0 {
			prejAcumSwing += math.Abs(lucroSwing)
		} else if lucroSwing > 0 {
			if !isentoSwingNesteMes {
				if prejAcumSwing >= lucroSwing {
					prejAcumSwing -= lucroSwing
					if mes == mesAlvo { res.BaseCalculoSwing = 0 }
				} else {
					if mes == mesAlvo { res.BaseCalculoSwing = lucroSwing - prejAcumSwing }
					prejAcumSwing = 0
				}
			} else {
				if mes == mesAlvo { res.BaseCalculoSwing = 0 }
			}
		}

		// EXTERIOR (EUA)
		if lucroExt < 0 {
			prejAcumExt += math.Abs(lucroExt)
		} else if lucroExt > 0 {
			if !isentoExtNesteMes {
				if prejAcumExt >= lucroExt {
					prejAcumExt -= lucroExt
					if mes == mesAlvo { res.BaseCalculoExterior = 0 }
				} else {
					if mes == mesAlvo { res.BaseCalculoExterior = lucroExt - prejAcumExt }
					prejAcumExt = 0
				}
			} else {
				if mes == mesAlvo { res.BaseCalculoExterior = 0 }
			}
		}

		// DAY TRADE (SEM ISENÇÃO)
		if lucroDT < 0 {
			prejAcumDT += math.Abs(lucroDT)
		} else if lucroDT > 0 {
			if prejAcumDT >= lucroDT {
				prejAcumDT -= lucroDT
				if mes == mesAlvo { res.BaseCalculoDT = 0 }
			} else {
				if mes == mesAlvo { res.BaseCalculoDT = lucroDT - prejAcumDT }
				prejAcumDT = 0
			}
		}
	}

	if !encontrouMesAtual {
		res.PrejuizoAnteriorSwing = prejAcumSwing
		res.PrejuizoAnteriorExterior = prejAcumExt
		res.PrejuizoAnteriorDT = prejAcumDT
	}

	// 5. Calcula o Imposto Devido
	res.ImpostoSwing = res.BaseCalculoSwing * 0.15
	res.ImpostoExterior = res.BaseCalculoExterior * 0.15
	res.ImpostoDT = res.BaseCalculoDT * 0.20

	impostoBrutoTotal := res.ImpostoSwing + res.ImpostoExterior + res.ImpostoDT
	darfFinal := impostoBrutoTotal - res.IrrfDedoDuro

	if darfFinal < 0 {
		res.DarfAPagar = 0
	} else {
		res.DarfAPagar = darfFinal
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(res)
}

// @Summary Info Usuário
// @Summary Buscar Informações do Usuário
// @Description Retorna informações cadastrais e patrimoniais da conta de um usuário.
// @Tags Usuários
// @Security BearerAuth
// @Param id query int false "ID do Usuário"
// @Success 200 {object} models.UsuarioResumo
// @Router /usuario [get]
func BuscarUsuarioInfo(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	usuarioIDStr := r.URL.Query().Get("id")
	idLogado, _ := getAuth(r)

	var targetID int
	if usuarioIDStr != "" {
		targetID, _ = strconv.Atoi(usuarioIDStr)
	}
	if targetID <= 0 {
		targetID = idLogado
	}
	if targetID <= 0 {
		targetID = 1
	}

	var u models.UsuarioResumo
	query := `SELECT usuario_id, nome_cliente, perfil_risco, saldo_brl, saldo_usd, 
			  COALESCE(email, ''), COALESCE(whatsapp, ''), COALESCE(login, ''), 
			  COALESCE(lucro_acumulado, 0.0), COALESCE(role, 'CLIENTE'), 
			  COALESCE(data_cadastro, CURRENT_TIMESTAMP), COALESCE(piloto_automatico, false)
			  FROM contas_virtuais WHERE usuario_id = $1`

	err := database.Conn.QueryRow(query, targetID).Scan(
		&u.ID, &u.Nome, &u.Perfil, &u.SaldoBRL, &u.SaldoUSD, 
		&u.Email, &u.Whatsapp, &u.Login, &u.Lucro, &u.Role, 
		&u.DataCadastro, &u.PilotoAutomatico,
	)

	if err != nil {
		u.ID = targetID
		u.Nome = "Investidor Padrão"
		u.Email = "sem-email@quantadvisor.com.br"
		u.Whatsapp = "(00) 00000-0000"
		u.Perfil = "Moderado"
		u.Role = "CLIENTE"
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(u)
}

// @Summary Login
// @Description Ponto de entrada do sistema de segurança.
// @Tags Auth
// @Accept json
// @Produce json
// @Param request body models.LoginRequest true "Credenciais"
// @Success 200 {object} models.LoginResponse
// @Router /login [post]
func Login(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		w.WriteHeader(http.StatusOK) // 👈 O antídoto do congelamento
		return
	}

	var req models.LoginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Dados inválidos"}`, http.StatusBadRequest)
		return
	}

	w.Header().Set("Content-Type", "application/json")

	// ✨ CORREÇÃO: Agora buscamos a ROLE diretamente do banco de dados!
	var id int
	var nome, senhaDB, role string

	query := "SELECT usuario_id, nome_cliente, senha, COALESCE(role, 'CLIENTE') FROM contas_virtuais WHERE login = $1"
	err := database.Conn.QueryRow(query, req.Login).Scan(&id, &nome, &senhaDB, &role)

	if err != nil {
		w.WriteHeader(http.StatusUnauthorized)
		json.NewEncoder(w).Encode(models.LoginResponse{Sucesso: false, Erro: "Login ou senha incorretos"})
		return
	}

	errBcrypt := bcrypt.CompareHashAndPassword([]byte(senhaDB), []byte(req.Senha))
	senhaValida := (errBcrypt == nil)

	// Fallback para senhas legadas em texto puro (ex: 'admin', '123456' das sementes do banco)
	if !senhaValida && senhaDB == req.Senha {
		senhaValida = true
		// Auto-upgrade transparente da senha legada para hash bcrypt no banco
		if hashedBytes, errHash := bcrypt.GenerateFromPassword([]byte(req.Senha), bcrypt.DefaultCost); errHash == nil {
			_, _ = database.Conn.Exec("UPDATE contas_virtuais SET senha = $1 WHERE usuario_id = $2", string(hashedBytes), id)
		}
	}

	if !senhaValida {
		w.WriteHeader(http.StatusUnauthorized)
		json.NewEncoder(w).Encode(models.LoginResponse{Sucesso: false, Erro: "Login ou senha incorretos"})
		return
	}

	// 🛡️ TRAVA MASTER: Garante que o usuário raiz (ID 1) seja sempre Gestor
	if id == 1 {
		role = "GESTOR"
	}

	tokenString := gerarTokenJWT(id, role)

	respostaCliente := models.LoginResponse{
		Sucesso:   true,
		Token:     tokenString,
		UsuarioID: id,
		Nome:      nome,
		Role:      role, // O React vai receber a Role verdadeira agora!
	}

	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(respostaCliente)
}

// Função auxiliar para criar o token (Adicione no final do handlers.go)
func gerarTokenJWT(usuarioID int, role string) string {
	expirationTime := time.Now().Add(7 * 24 * time.Hour)
	chaveSecreta := middleware.GetJWTSecretKey()

	claims := &models.JWTClaims{
		UsuarioID: usuarioID,
		Role:      role,
		StandardClaims: jwt.StandardClaims{
			ExpiresAt: expirationTime.Unix(),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	tokenString, _ := token.SignedString(chaveSecreta)
	return tokenString
}

// Helper para ler rapidamente quem é o dono do Token
func getAuth(r *http.Request) (int, string) {
	id, _ := r.Context().Value("usuario_id").(int)
	role, _ := r.Context().Value("role").(string)
	return id, role
}

// @Summary Solicitar Cadastro (Passo 1)
// @Description Inicia o fluxo de Onboarding Seguro (KYC). Valida colisões de login/email na base de dados, gera um OTP (One-Time Password) criptograficamente seguro e aciona um worker assíncrono para despachar a chave temporária via SMS/WhatsApp usando a API Twilio.
// @Router /usuarios/solicitar-cadastro [post]
func SolicitarCadastro(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	var req models.NovaContaRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Dados inválidos"}`, http.StatusBadRequest)
		return
	}

	// 1. Verifica se o login ou email já existem no banco
	var existe int
	database.Conn.QueryRow("SELECT 1 FROM contas_virtuais WHERE login = $1 OR email = $2", req.Login, req.Email).Scan(&existe)
	if existe == 1 {
		http.Error(w, `{"sucesso": false, "erro": "Login ou E-mail já cadastrado!"}`, http.StatusConflict)
		return
	}

	// 2. GERAÇÃO DE CÓDIGO ALEATÓRIO (Criptograficamente Seguro)
	max := big.NewInt(1000000) // Gera um número entre 0 e 999999
	numeroAleatorio, _ := rand.Int(rand.Reader, max)
	codigoOTP := fmt.Sprintf("%06d", numeroAleatorio.Int64()) // Garante sempre 6 dígitos (ex: 004512)

	// 3. Salva os dados do usuário + código no Redis com validade de 5 minutos
	payloadRedis, _ := json.Marshal(req)
	chaveRedis := fmt.Sprintf("cadastro_otp:%s", req.Email)

	database.Rdb.Set(database.Ctx, chaveRedis, payloadRedis, 5*time.Minute)
	database.Rdb.Set(database.Ctx, "otp_code:"+req.Email, codigoOTP, 5*time.Minute)

	// 4. DISPARO VIA WHATSAPP (Goroutine para não travar a tela do usuário)
	go dispararSMS(req.Whatsapp, codigoOTP)

	resposta := map[string]interface{}{
		"sucesso":      true,
		"mensagem":     "Código de 6 dígitos gerado com sucesso!",
		"codigo_teste": codigoOTP, // O código agora vai chegar limpo no React
	}

	// SÓ DEIXE ESTAS DUAS LINHAS DE ENVIO NO FINAL:
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resposta)
}

// @Summary Validar Código e Criar Conta (Passo 2)
// @Description Conclui a aprovação de Onboarding. Verifica o código OTP em tempo real contra o cache transiente do Redis. Após o aceite, extrai os metadados persistidos provisoriamente e grava a conta virtual de forma permanente e atômica no PostgreSQL.
// @Router /usuarios/validar-cadastro [post]
func ValidarCadastro(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	var req struct {
		Email  string `json:"email"`
		Codigo string `json:"codigo"`
	}
	json.NewDecoder(r.Body).Decode(&req)

	// 1. Busca o código no Redis
	codigoSalvo, err := database.Rdb.Get(database.Ctx, "otp_code:"+req.Email).Result()
	if err != nil || codigoSalvo != req.Codigo {
		http.Error(w, `{"sucesso": false, "erro": "Código inválido ou expirado!"}`, http.StatusUnauthorized)
		return
	}

	// 2. Recupera os dados originais do cadastro que estavam esperando no Redis
	dadosSalvos, err := database.Rdb.Get(database.Ctx, "cadastro_otp:"+req.Email).Result()
	if err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Sessão expirada. Refaça o cadastro."}`, http.StatusRequestTimeout)
		return
	}

	var novaConta models.NovaContaRequest
	json.Unmarshal([]byte(dadosSalvos), &novaConta)

	hashedBytes, errHash := bcrypt.GenerateFromPassword([]byte(novaConta.Senha), bcrypt.DefaultCost)
	if errHash != nil {
		http.Error(w, `{"sucesso": false, "erro": "Erro ao processar senha."}`, http.StatusInternalServerError)
		return
	}

	// 3. Insere a conta definitiva no PostgreSQL
	// 🛡️ CORREÇÃO: Agora respeita o Saldo, o Perfil do usuário e define piloto como inativo no primeiro acesso
	query := `INSERT INTO contas_virtuais (nome_cliente, perfil_risco, saldo_brl, email, whatsapp, login, senha, role, piloto_automatico) 
			  VALUES ($1, $2, $3, $4, $5, $6, $7, 'CLIENTE', false)`

	// Usa as variáveis da 'novaConta' extraídas do Redis (digitadas pelo usuário)
	_, errDb := database.Conn.Exec(query, novaConta.NomeCliente, novaConta.PerfilRisco, novaConta.SaldoInicial, novaConta.Email, novaConta.Whatsapp, novaConta.Login, string(hashedBytes))

	if errDb != nil {
		http.Error(w, `{"sucesso": false, "erro": "Erro ao gravar no banco de dados."}`, http.StatusInternalServerError)
		return
	}

	// 4. Limpa o Redis para evitar reuso
	database.Rdb.Del(database.Ctx, "otp_code:"+req.Email, "cadastro_otp:"+req.Email)

	w.Write([]byte(`{"sucesso": true, "mensagem": "Conta ativada com sucesso! Você já pode fazer o login."}`))
}

// Função responsável por fazer a ponte com o Gateway de SMS (ex: Twilio)
func dispararSMS(numero string, codigoOTP string) {
	log.Printf("📱 [MOCK SMS - EM DESENVOLVIMENTO] Enviado para %s: %s", numero, codigoOTP)
	// 1. Mensagem curta e sem formatação especial (SMS padrão)
	mensagem := fmt.Sprintf("QuantAdvisor: Seu codigo de ativacao e %s. Valido por 5 minutos.", codigoOTP)

	// 2. Limpeza do número para o padrão internacional E.164 (+5511999999999)
	numeroLimpo := strings.ReplaceAll(numero, " ", "")
	numeroLimpo = strings.ReplaceAll(numeroLimpo, "(", "")
	numeroLimpo = strings.ReplaceAll(numeroLimpo, ")", "")
	numeroLimpo = strings.ReplaceAll(numeroLimpo, "-", "")

	if !strings.HasPrefix(numeroLimpo, "55") {
		numeroLimpo = "55" + numeroLimpo
	}
	numeroDestino := "+" + numeroLimpo

	// =========================================================================
	// 🔌 CREDENCIAIS TWILIO (Crie uma conta grátis em twilio.com para obter)
	// =========================================================================
	accountSid := "USa60a86638e42043965b3eb61513bd779"
	authToken := "DCKPZYC3DV85NHX5PS2H5XFS"
	numeroTwilio := "+5511972980409" // Ex: +1234567890

	// Monta a URL da API da Twilio
	urlStr := "https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json"

	// O formato que as APIs de SMS usam é form-urlencoded
	formData := url.Values{}
	formData.Set("To", numeroDestino)
	formData.Set("From", numeroTwilio)
	formData.Set("Body", mensagem)

	// 3. Monta a Requisição HTTP POST
	req, err := http.NewRequest("POST", urlStr, strings.NewReader(formData.Encode()))
	if err != nil {
		log.Printf("❌ [SMS] Erro ao montar requisição: %v", err)
		return
	}

	// Autenticação Básica (Padrão de segurança da Twilio)
	req.SetBasicAuth(accountSid, authToken)
	req.Header.Add("Content-Type", "application/x-www-form-urlencoded")

	// 4. Dispara o SMS
	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)

	if err != nil {
		log.Printf("❌ [SMS] Erro de rede ao contatar a operadora: %v", err)
		// Fallback inteligente para não travar seus testes
		log.Printf("📱 [MOCK SMS] Enviado para %s: %s", numeroDestino, codigoOTP)
		return
	}
	defer resp.Body.Close()

	// 5. Auditoria
	if resp.StatusCode >= 200 && resp.StatusCode < 300 {
		log.Printf("✅ [SMS] Código enviado com sucesso para %s!", numeroDestino)
	} else {
		bodyErro, _ := io.ReadAll(resp.Body)
		log.Printf("⚠️ [SMS] Operadora recusou o envio (Status %d): %s", resp.StatusCode, string(bodyErro))
		// Fallback inteligente
		log.Printf("📱 [MOCK SMS] Enviado para %s: %s", numeroDestino, codigoOTP)
	}
}

// @Summary Resumo do Dashboard (KPIs)
// @Description Retorna o caixa livre, custo de aquisição e património total do utilizador para alimentar os cartões de topo.
// @Tags Dashboard
// @Produce json
// @Security BearerAuth
// @Param usuario_id query int true "ID do Utilizador"
// @Success 200 {object} models.ResumoDashboard
// @Router /dashboard/resumo [get]
func HandlerDashboardResumo(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	uIDStr := r.URL.Query().Get("usuario_id")
	usuarioID, err := strconv.Atoi(uIDStr)
	if err != nil {
		http.Error(w, `{"erro": "usuario_id invalido"}`, http.StatusBadRequest)
		return
	}

	var resumo models.ResumoDashboard
	var caixaBRL, caixaUSD float64

	// 🛡️ Busca os dois caixas e consolida no Dashboard
	errCaixa := database.Conn.QueryRow("SELECT saldo_brl, saldo_usd FROM contas_virtuais WHERE usuario_id = $1", usuarioID).Scan(&caixaBRL, &caixaUSD)
	if errCaixa != nil {
		resumo.CaixaLivre = 0
	} else {
		resumo.CaixaLivre = caixaBRL + (caixaUSD * services.DolarGlobal)
	}

	// 💱 REMOVIDO O SUM() DO SQL, SOMATÓRIO FEITO AQUI COM CÂMBIO
	rowsPos, errPos := database.Conn.Query("SELECT quantidade_total, preco_medio, COALESCE(moeda, 'BRL') FROM posicoes_carteira WHERE usuario_id = $1", usuarioID)
	var custoTotalBRL float64 = 0
	
	if errPos == nil {
		defer rowsPos.Close()
		for rowsPos.Next() {
			var q, pm float64
			var moeda string
			rowsPos.Scan(&q, &pm, &moeda)
			
			if moeda == "USD" {
				custoTotalBRL += (q * pm) * services.DolarGlobal
			} else {
				custoTotalBRL += (q * pm)
			}
		}
	}
	resumo.CustoAquisicao = custoTotalBRL
	resumo.Patrimonio = resumo.CaixaLivre + resumo.CustoAquisicao

	json.NewEncoder(w).Encode(resumo)
}

// @Summary Histórico de Evolução Patrimonial
// @Description Retorna os pontos de dados dos últimos 30 dias, mais o estado "Ao Vivo", para desenhar o gráfico de linha.
// @Tags Dashboard
// @Produce json
// @Security BearerAuth
// @Param usuario_id query int true "ID do Utilizador"
// @Success 200 {array} models.PontoHistorico
// @Router /dashboard/historico [get]
func HandlerDashboardHistorico(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	uIDStr := r.URL.Query().Get("usuario_id")
	usuarioID, err := strconv.Atoi(uIDStr)
	if err != nil {
		http.Error(w, `{"erro": "usuario_id invalido"}`, http.StatusBadRequest)
		return
	}

	rows, err := database.Conn.Query(`
		SELECT TO_CHAR(data_fechamento, 'DD/MM'), patrimonio_total 
		FROM historico_patrimonial 
		WHERE usuario_id = $1 
		ORDER BY data_fechamento ASC LIMIT 30
	`, usuarioID)

	if err != nil {
		http.Error(w, `{"erro": "falha ao buscar historico"}`, http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	var historico []models.PontoHistorico
	for rows.Next() {
		var p models.PontoHistorico
		if err := rows.Scan(&p.Data, &p.Patrimonio); err == nil {
			historico = append(historico, p)
		}
	}

	// =========================================================
	// 🟢 PONTO FLUTUANTE: Calcula o "Ao Vivo" (Presente)
	// =========================================================
	var caixaBRL, caixaUSD float64
	database.Conn.QueryRow("SELECT saldo_brl, saldo_usd FROM contas_virtuais WHERE usuario_id = $1", usuarioID).Scan(&caixaBRL, &caixaUSD)
	saldoLivre := caixaBRL + (caixaUSD * services.DolarGlobal)

	var valorAcoesAoVivo float64 = 0.0
	// 💱 LÊ A MOEDA PARA O PONTO "AO VIVO"
	posRows, _ := database.Conn.Query("SELECT ticker, quantidade_total, preco_medio, COALESCE(moeda, 'BRL') FROM posicoes_carteira WHERE usuario_id = $1", usuarioID)
	
	for posRows.Next() {
		var ticker, moeda string
		var qtd int
		var precoMedio float64
		posRows.Scan(&ticker, &qtd, &precoMedio, &moeda)

		precoAtual := precoMedio 
		
		val, errRedis := database.Rdb.Get(database.Ctx, "ticker:"+ticker).Result()
		if errRedis == nil {
			var dados map[string]interface{}
			json.Unmarshal([]byte(val), &dados)
			if p, ok := dados["preco_atual"].(float64); ok {
				precoAtual = p
			}
		}

		taxa := 1.0
		if moeda == "USD" { taxa = services.DolarGlobal }

		valorAcoesAoVivo += (float64(qtd) * precoAtual) * taxa
	}
	posRows.Close()

	historico = append(historico, models.PontoHistorico{
		Data:       "Ao Vivo",
		Patrimonio: saldoLivre + valorAcoesAoVivo,
	})

	json.NewEncoder(w).Encode(historico)
}

// @Summary Visão Macro (AUM)
// @Description Exclusivo para Gestores. Retorna o AUM total (Assets Under Management), caixa global e evolução consolidada de todos os clientes vinculados.
// @Tags Dashboard
// @Produce json
// @Security BearerAuth
// @Success 200 {object} map[string]interface{}
// @Router /dashboard/macro [get]
// @Summary Visão Macro (AUM)
// @Description Exclusivo para Gestores. Retorna o AUM total (Assets Under Management), caixa global e evolução consolidada de todos os clientes vinculados.
// @Tags Dashboard
// @Produce json
// @Security BearerAuth
// @Success 200 {object} map[string]interface{}
// @Router /dashboard/macro [get]
func HandlerDashboardMacro(w http.ResponseWriter, r *http.Request) {
	defer func() {
		if rec := recover(); rec != nil {
			log.Printf("🚨 [CRITICAL] Panic evitado no Dashboard Macro: %v", rec)
			http.Error(w, `{"sucesso": false, "erro": "Erro ao compilar dados macro."}`, http.StatusInternalServerError)
		}
	}()

	SetCORS(w)
	if r.Method == "OPTIONS" { return }

	idLogado, roleLogado := getAuth(r)
	if roleLogado != "GESTOR" && idLogado != 1 {
		http.Error(w, `{"erro": "Acesso negado. Apenas Gestores."}`, http.StatusForbidden)
		return
	}

	type ClienteMacro struct {
		UsuarioID      int     `json:"usuario_id"`
		Nome           string  `json:"nome"`
		Perfil         string  `json:"perfil"`
		
		// 🌍 Visão Global (Convertida para BRL para fins de ranking)
		CaixaGlobal      float64 `json:"caixa_global"`
		CustodiaGlobal   float64 `json:"custodia_global"`
		PatrimonioGlobal float64 `json:"patrimonio_global"`
		LucroGlobal      float64 `json:"lucro_global"`

		// 🇧🇷 Jurisdição Brasil (Valores Nativos em R$)
		CaixaBRL       float64 `json:"caixa_brl"`
		CustoBRL       float64 `json:"custo_brl"`
		CustodiaBRL    float64 `json:"custodia_brl"`
		PatrimonioBRL  float64 `json:"patrimonio_brl"`
		LucroBRL       float64 `json:"lucro_brl"`

		// 🇺🇸 Jurisdição EUA (Valores Nativos em US$)
		CaixaUSD       float64 `json:"caixa_usd"`
		CustoUSD       float64 `json:"custo_usd"`
		CustodiaUSD    float64 `json:"custodia_usd"`
		PatrimonioUSD  float64 `json:"patrimonio_usd"`
		LucroUSD       float64 `json:"lucro_usd"`
	}

	type MacroResponse struct {
		AUM               float64                  `json:"aum_total"`
		CaixaGlobal       float64                  `json:"caixa_global"`
		CustodiaGlobal    float64                  `json:"custodia_global"`
		TotalClientes     int                      `json:"total_clientes"`
		CotacaoDolarAtiva float64                  `json:"cotacao_dolar_ativa"`
		RegimeAtual       string                   `json:"regime_atual"` // 👈 NOVA LINHA
		Clientes          []ClienteMacro           `json:"clientes"`
		HistoricoClientes []map[string]interface{} `json:"historico_clientes"` 
	}

	var resp MacroResponse
	clientesMap := make(map[int]*ClienteMacro)

	queryContas := `SELECT usuario_id, nome_cliente, perfil_risco, saldo_brl, saldo_usd FROM contas_virtuais WHERE 1=1 `
	if idLogado != 1 && roleLogado == "GESTOR" {
		queryContas += fmt.Sprintf(" AND (usuario_id = %d OR gestor_id = %d) ", idLogado, idLogado)
	}

	rowsContas, err := database.Conn.Query(queryContas)
	if err == nil {
		defer rowsContas.Close()
		for rowsContas.Next() {
			var c ClienteMacro
			if err := rowsContas.Scan(&c.UsuarioID, &c.Nome, &c.Perfil, &c.CaixaBRL, &c.CaixaUSD); err == nil {
				c.CaixaGlobal = c.CaixaBRL + (c.CaixaUSD * services.DolarGlobal)
				clientesMap[c.UsuarioID] = &c
			}
		}
	}

	// 💱 LÊ A MOEDA PARA O CÁLCULO DE AUM SEPARADO
	queryPosicoes := `SELECT usuario_id, ticker, quantidade_total, preco_medio, COALESCE(moeda, 'BRL') FROM posicoes_carteira`
	rowsPos, err := database.Conn.Query(queryPosicoes)
	if err == nil {
		defer rowsPos.Close()
		for rowsPos.Next() {
			var uID int
			var ticker, moeda string
			var qtd, pMedio float64

			if errScan := rowsPos.Scan(&uID, &ticker, &qtd, &pMedio, &moeda); errScan == nil {
				if cliente, ok := clientesMap[uID]; ok {
					
					precoAtual := pMedio 
					chaveRedis := fmt.Sprintf("ticker:%s", ticker)
					valRedis, errRedis := database.Rdb.Get(database.Ctx, chaveRedis).Result()
					if errRedis == nil {
						var dadosAtivo map[string]interface{}
						if errJson := json.Unmarshal([]byte(valRedis), &dadosAtivo); errJson == nil {
							if pAtual, ok := dadosAtivo["preco_atual"].(float64); ok {
								precoAtual = pAtual
							}
						}
					}

					if moeda == "USD" {
						cliente.CustoUSD += (qtd * pMedio)
						cliente.CustodiaUSD += (qtd * precoAtual)
					} else {
						cliente.CustoBRL += (qtd * pMedio)
						cliente.CustodiaBRL += (qtd * precoAtual)
					}
				}
			}
		}
	}

	clientesValidos := make(map[int]*ClienteMacro)
	for uID, c := range clientesMap {
		if c.CustoBRL > 0 || c.CustoUSD > 0 || c.CaixaGlobal > 0 { 
			c.PatrimonioBRL = c.CaixaBRL + c.CustodiaBRL
			c.LucroBRL = c.CustodiaBRL - c.CustoBRL
			
			c.PatrimonioUSD = c.CaixaUSD + c.CustodiaUSD
			c.LucroUSD = c.CustodiaUSD - c.CustoUSD

			c.CustodiaGlobal = c.CustodiaBRL + (c.CustodiaUSD * services.DolarGlobal)
			c.PatrimonioGlobal = c.CaixaGlobal + c.CustodiaGlobal
			c.LucroGlobal = c.CustodiaGlobal - (c.CustoBRL + (c.CustoUSD * services.DolarGlobal))
			
			resp.AUM += c.PatrimonioGlobal
			resp.CaixaGlobal += c.CaixaGlobal
			resp.CustodiaGlobal += c.CustodiaGlobal
			resp.TotalClientes++
			resp.CotacaoDolarAtiva = services.DolarGlobal

			resp.Clientes = append(resp.Clientes, *c)
			clientesValidos[uID] = c
		}
	}

	queryHistorico := `
		SELECT TO_CHAR(h.data_fechamento, 'DD/MM'), c.usuario_id, c.nome_cliente, h.patrimonio_total, COALESCE(h.lucro_diario, 0)
		FROM historico_patrimonial h
		JOIN contas_virtuais c ON h.usuario_id = c.usuario_id
		WHERE h.data_fechamento >= CURRENT_DATE - INTERVAL '30 days'
		ORDER BY h.data_fechamento ASC
	`
	rowsHist, errHist := database.Conn.Query(queryHistorico)
	
	mapaDias := make(map[string]map[string]interface{})
	var ordemDias []string

	if errHist == nil {
		defer rowsHist.Close()
		for rowsHist.Next() {
			var dataFormatada, nomeCliente string
			var usuarioID int
			var patrimonio, lucroDiario float64
			
			if err := rowsHist.Scan(&dataFormatada, &usuarioID, &nomeCliente, &patrimonio, &lucroDiario); err == nil {
				if _, ok := clientesValidos[usuarioID]; ok {
					if _, existe := mapaDias[dataFormatada]; !existe {
						mapaDias[dataFormatada] = make(map[string]interface{})
						mapaDias[dataFormatada]["data"] = dataFormatada
						ordemDias = append(ordemDias, dataFormatada)
					}
					mapaDias[dataFormatada][nomeCliente] = patrimonio
					mapaDias[dataFormatada][nomeCliente+"_lucro"] = lucroDiario
				}
			}
		}
	}

	for _, dia := range ordemDias {
		resp.HistoricoClientes = append(resp.HistoricoClientes, mapaDias[dia])
	}

	if len(clientesValidos) > 0 {
		if len(resp.HistoricoClientes) == 0 {
			pontoAbertura := make(map[string]interface{})
			pontoAbertura["data"] = "Abertura"
			for _, c := range clientesValidos {
				// Usa as variáveis novas
				pontoAbertura[c.Nome] = c.CaixaGlobal + c.CustoBRL + (c.CustoUSD * services.DolarGlobal)
				pontoAbertura[c.Nome+"_lucro"] = 0.0 
			}
			resp.HistoricoClientes = append(resp.HistoricoClientes, pontoAbertura)
		}

		pontoAoVivo := make(map[string]interface{})
		pontoAoVivo["data"] = "Ao Vivo"
		for _, c := range clientesValidos {
			// Usa as variáveis novas globais
			pontoAoVivo[c.Nome] = c.PatrimonioGlobal
			pontoAoVivo[c.Nome+"_lucro"] = c.LucroGlobal
		}
		resp.HistoricoClientes = append(resp.HistoricoClientes, pontoAoVivo)
	}

	if resp.HistoricoClientes == nil {
		resp.HistoricoClientes = make([]map[string]interface{}, 0)
	}

	// Lê o regime ao vivo do Redis
	regimeStr, errR := database.Rdb.Get(r.Context(), "regime_mercado_atual").Result()
	if errR != nil || regimeStr == "" { regimeStr = "ANALISANDO..." }
	resp.RegimeAtual = regimeStr

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
}

// @Summary Detalhes do Ativo (Raio-X)
// @Description Puxa o histórico de preços, fundamentos e estado da IA diretamente da RAM.
// @Router /ativo/detalhes [get]
func DetalhesAtivo(w http.ResponseWriter, r *http.Request) {
    SetCORS(w)
    if r.Method == "OPTIONS" { return }

    ticker := r.URL.Query().Get("ticker")
    if ticker == "" {
        http.Error(w, `{"sucesso": false, "erro": "Ticker não informado"}`, http.StatusBadRequest)
        return
    }

    ctx := database.Ctx
    histStr, _ := database.Rdb.Get(ctx, "hist:"+ticker).Result()
    if histStr == "" { histStr = "[]" }

    fundStr, _ := database.Rdb.Get(ctx, "fund:"+ticker).Result()
    if fundStr == "" { fundStr = "{}" }

    iaStr, _ := database.Rdb.Get(ctx, "ticker:"+ticker).Result()
    if iaStr == "" { iaStr = "{}" }

    jsonResposta := fmt.Sprintf(`{"sucesso": true, "historico": %s, "fundamentos": %s, "ia_status": %s}`, histStr, fundStr, iaStr)

    w.Header().Set("Content-Type", "application/json")
    w.Write([]byte(jsonResposta))
}

// @Summary Histórico de Ativos (Gráfico de Barras/Área)
// @Description Retorna os dados dos últimos 30 dias separados por ticker, formatados diretamente para consumo no Recharts (React).
// @Tags Dashboard
// @Produce json
// @Security BearerAuth
// @Param usuario_id query int true "ID do Utilizador"
// @Success 200 {array} map[string]interface{}
// @Router /dashboard/historico-ativos [get]
func HandlerDashboardHistoricoAtivos(w http.ResponseWriter, r *http.Request) {
    w.Header().Set("Content-Type", "application/json")
    w.Header().Set("Access-Control-Allow-Origin", "*")

    uIDStr := r.URL.Query().Get("usuario_id")
    usuarioID, err := strconv.Atoi(uIDStr)
    if err != nil {
        http.Error(w, `{"erro": "usuario_id invalido"}`, http.StatusBadRequest)
        return
    }

    // Busca os últimos 30 dias de cada ativo
    query := `
        SELECT TO_CHAR(data_fechamento, 'DD/MM'), ticker, valor_posicao 
        FROM historico_custodia_diaria 
        WHERE usuario_id = $1 
        AND data_fechamento >= CURRENT_DATE - INTERVAL '30 days'
        ORDER BY data_fechamento ASC
    `
    rows, err := database.Conn.Query(query, usuarioID)
    if err != nil {
        http.Error(w, `{"erro": "falha ao buscar historico de ativos"}`, http.StatusInternalServerError)
        return
    }
    defer rows.Close()

    // Pivoteia as linhas do SQL para o formato que o Recharts entende:
    // [{ data: "22/06", PETR4: 1500.00, VALE3: 800.00 }, { data: "23/06", ... }]
    mapaDias := make(map[string]map[string]interface{})
    var ordemDias []string

    for rows.Next() {
        var dataForm, ticker string
        var valor float64
        rows.Scan(&dataForm, &ticker, &valor)

        if _, existe := mapaDias[dataForm]; !existe {
            mapaDias[dataForm] = make(map[string]interface{})
            mapaDias[dataForm]["data"] = dataForm
            ordemDias = append(ordemDias, dataForm)
        }
        mapaDias[dataForm][ticker] = valor
    }

    var historicoFormatado []map[string]interface{}
    for _, dia := range ordemDias {
        historicoFormatado = append(historicoFormatado, mapaDias[dia])
    }

    if historicoFormatado == nil {
        historicoFormatado = make([]map[string]interface{}, 0)
    }

    json.NewEncoder(w).Encode(historicoFormatado)
}

// Estrutura para receber o pedido de câmbio
type CambioRequest struct {
	UsuarioID   int     `json:"usuario_id"`
	Direcao     string  `json:"direcao"` // "BRL_PARA_USD" ou "USD_PARA_BRL"
	ValorOrigem float64 `json:"valor_origem"`
}

// @Summary Realizar Câmbio (Remessa Internacional)
// @Description Executa a conversão entre BRL e USD aplicando Spread e IOF.
// @Tags Operações
// @Accept json
// @Produce json
// @Security BearerAuth
// @Router /cambio [post]
func RealizarCambio(w http.ResponseWriter, r *http.Request) {
	SetCORS(w)
	if r.Method == "OPTIONS" {
		return
	}

	var req CambioRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Payload inválido"}`, http.StatusBadRequest)
		return
	}

	idLogado, roleLogado := getAuth(r)
	if roleLogado != "GESTOR" && idLogado != 1 {
		req.UsuarioID = idLogado // Garante que o cliente só mexa na própria conta
	}

	// 1. Inicia transação atômica
	tx, err := database.Conn.Begin()
	if err != nil {
		http.Error(w, `{"sucesso": false, "erro": "Erro de transação no banco"}`, http.StatusInternalServerError)
		return
	}

	// Parâmetros Fiscais e Comerciais (Hardcoded para o MVP, podem vir do banco depois)
	cotacaoComercial := services.DolarGlobal
	if cotacaoComercial <= 0 {
		cotacaoComercial = 5.50 // Fallback de segurança
	}
	taxaIOF := 0.0038 // 0.38% para investimentos
	spread := 0.015   // 1.50% de taxa da corretora

	var valorDestino, iofAplicado, cotacaoEfetiva float64
	var queryAtualizarSaldos string

	if req.Direcao == "BRL_PARA_USD" {
		// Desconta o IOF do montante em Reais
		iofAplicado = req.ValorOrigem * taxaIOF
		valorBaseBRL := req.ValorOrigem - iofAplicado

		// Aplica o Spread encarecendo o Dólar
		cotacaoEfetiva = cotacaoComercial * (1.0 + spread)
		
		// Calcula quantos Dólares o cliente vai receber
		valorDestino = valorBaseBRL / cotacaoEfetiva

		// SQL para tirar do bolso BRL e colocar no bolso USD
		queryAtualizarSaldos = `
			UPDATE contas_virtuais 
			SET saldo_brl = saldo_brl - $1, saldo_usd = saldo_usd + $2 
			WHERE usuario_id = $3 AND saldo_brl >= $1
		`
	} else if req.Direcao == "USD_PARA_BRL" {
		// O Spread barateia o Dólar na hora de repatriar
		cotacaoEfetiva = cotacaoComercial * (1.0 - spread)
		
		// Valor bruto em Reais
		valorBrutoBRL := req.ValorOrigem * cotacaoEfetiva
		
		// IOF é descontado em Reais
		iofAplicado = valorBrutoBRL * taxaIOF
		valorDestino = valorBrutoBRL - iofAplicado

		// SQL para tirar do bolso USD e colocar no bolso BRL
		queryAtualizarSaldos = `
			UPDATE contas_virtuais 
			SET saldo_usd = saldo_usd - $1, saldo_brl = saldo_brl + $2 
			WHERE usuario_id = $3 AND saldo_usd >= $1
		`
	} else {
		tx.Rollback()
		http.Error(w, `{"sucesso": false, "erro": "Direção cambial inválida."}`, http.StatusBadRequest)
		return
	}

	// 2. Executa a mutação dos saldos
	res, err := tx.Exec(queryAtualizarSaldos, req.ValorOrigem, valorDestino, req.UsuarioID)
	if err != nil || database.RowsAffected(res) == 0 {
		tx.Rollback()
		http.Error(w, `{"sucesso": false, "erro": "Saldo insuficiente para o câmbio."}`, http.StatusPaymentRequired)
		return
	}

	// 3. Registra o recibo da operação na tabela historico_cambio
	queryRecibo := `
		INSERT INTO historico_cambio (usuario_id, direcao, valor_origem, valor_destino, cotacao_comercial, spread_aplicado, iof_aplicado, vet_cotacao_efetiva)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
	`
	_, err = tx.Exec(queryRecibo, req.UsuarioID, req.Direcao, req.ValorOrigem, valorDestino, cotacaoComercial, spread*100, iofAplicado, cotacaoEfetiva)
	if err != nil {
		tx.Rollback()
		http.Error(w, `{"sucesso": false, "erro": "Erro ao gerar recibo de câmbio."}`, http.StatusInternalServerError)
		return
	}

	tx.Commit()

	resposta := map[string]interface{}{
		"sucesso": true,
		"mensagem": "Câmbio liquidado com sucesso!",
		"dados_operacao": map[string]interface{}{
			"cotacao_comercial": cotacaoComercial,
			"cotacao_efetiva_vet": cotacaoEfetiva,
			"iof_reais": iofAplicado,
			"valor_creditado": valorDestino,
		},
	}
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resposta)
}