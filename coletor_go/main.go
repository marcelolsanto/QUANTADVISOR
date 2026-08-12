package main

import (
	"context"
	"io"
	"log"
	"net/http"
	"os"
	"time"

	_ "quantadvisor/docs" // Importa os arquivos gerados pelo 'swag init'
	"quantadvisor/internal/broker"
	"quantadvisor/internal/database"
	"quantadvisor/internal/handlers"
	"quantadvisor/internal/messaging"
	"quantadvisor/internal/middleware"
	"quantadvisor/internal/services"

	httpSwagger "github.com/swaggo/http-swagger"
)
// @title QuantAdvisor API
// @version 1.0
// @description Motor de Ingestão e Gestão Patrimonial.
// @host 100.95.28.45:8080
// @BasePath /api
// @securityDefinitions.apikey BearerAuth
// @in header
// @name Authorization
func main() {
	// 1. Inicializa dependências globais
	database.Conectar()
	database.ConectarRedis()

	// 🔌 Inicia o Sincronizador de Saldo Alpaca / Wall St com Redis (Feedback Loop HFT)
	alpacaAdapter := broker.NewBrokerAdapter("USD")
	go func() {
		if err := alpacaAdapter.Connect(); err != nil {
			log.Printf("⚠️ [ALPACA WS] Erro ao inicializar conexão WebSocket: %v", err)
		}
		messaging.StartWalletSync(context.Background(), alpacaAdapter, 10*time.Second)
	}()
	
	// Rotinas Agendadas em Background (Cron)
	go services.AgendarDespertadorBolsa()
	
	// 👇 NOVOS MONITORES DAY TRADE (B3 + WALL ST) 👇
	go services.AgendarZeragemDayTradeB3()
	go services.AgendarZeragemDayTradeUSA()

	// Inicia o agendador automático normal
	go services.AgendarFechamentoMercado()
	go services.LoopMonitoramentoMtM()
	go services.AgendarRendimentoCaixaCDI()
	
	// 👇 NOVOS MONITORES MOC (B3 + WALL ST) 👇
	go services.AgendarVarreduraLeilaoFechamentoB3()
	go services.AgendarVarreduraLeilaoFechamentoUSA()
	// 2. Configuração do Log
	ficheiroLog, err := os.OpenFile("auditoria_ingestao.log", os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0666)
	if err != nil {
		log.Fatalf("Erro ao criar ficheiro de log: %v", err)
	}
	defer ficheiroLog.Close()

	multiEscritor := io.MultiWriter(os.Stdout, ficheiroLog)
	log.SetOutput(multiEscritor)
	log.SetFlags(log.Ldate | log.Ltime)
	log.Println("🚀 [ORQUESTRADOR] Motor de Ingestão HFT iniciado. Auditoria ativada.")
	// 3. Esteira de Ingestão
	go services.IniciarCronJob()
	// Inicia o cérebro do SSE em background
	go handlers.StartSSEHub()
	// Inicia um ouvinte para o Redis Pub/Sub com reconexão automática e recover contra Panics
	go func() {
		for {
			func() {
				defer func() {
					if r := recover(); r != nil {
						log.Printf("🚨 [RECOVER] Falha no Redis Pub/Sub: %v", r)
					}
				}()
				ctx := context.Background()
				if database.Rdb == nil {
					return
				}
				pubsub := database.Rdb.Subscribe(ctx, "market_ticks", "cotacoes")
				defer pubsub.Close()
				ch := pubsub.Channel()
				log.Println("🎧 [SSE] Golang escutando cotações em tempo real nos canais 'market_ticks' e 'cotacoes'...")
				for msg := range ch {
					select {
					case handlers.Broadcast <- []byte(msg.Payload):
					default:
						log.Println("⚠️ [SSE HUB] Buffer de transmissão cheio. Descartando tick para evitar congelamento.")
					}
				}
			}()
			log.Println("⚠️ Conexão com Redis perdida. Tentando reconectar em 2s...")
			time.Sleep(2 * time.Second)
		}
	}()
	// 4. Registra as rotas no MUX
	mux := http.NewServeMux()
	// --- DOCUMENTAÇÃO SWAGGER ---
	//Acesse em: http://100.95.28.45:8080/swagger/index.html
	mux.HandleFunc("/swagger/", httpSwagger.WrapHandler)
	// --- ROTAS ABERTAS ---
	mux.HandleFunc("/api/login", handlers.Login)
	mux.HandleFunc("/api/usuarios/solicitar-cadastro", handlers.SolicitarCadastro)
	mux.HandleFunc("/api/usuarios/validar-cadastro", handlers.ValidarCadastro)
	mux.HandleFunc("/api/wallet/buying-power", handlers.HandlerGetBuyingPower)
	mux.HandleFunc("/api/hft/signals", handlers.HandlerGetSinaisHFT)
	mux.HandleFunc("/api/hft/twap-history", handlers.HandlerGetExecucoesTWAP)
	// --- ROTAS PROTEGIDAS (JWT) ---
	// Em: --- ROTAS PROTEGIDAS (JWT) ---
	mux.HandleFunc("/api/ordem", middleware.ProtegerRota(handlers.Ordem))
	mux.HandleFunc("/api/cambio", middleware.ProtegerRota(handlers.RealizarCambio)) // 👈 NOVA ROTA AQUI!
	mux.HandleFunc("/api/carteira", middleware.ProtegerRota(handlers.Carteira))
	mux.HandleFunc("/api/historico", middleware.ProtegerRota(handlers.HistoricoOrdens))
	mux.HandleFunc("/api/carrinho", middleware.ProtegerRota(handlers.ListarCarrinho))
	mux.HandleFunc("/api/adicionar-carrinho", middleware.ProtegerRota(handlers.AdicionarAoCarrinho))
	mux.HandleFunc("/api/carrinho/limpar", middleware.ProtegerRota(handlers.LimparCarrinho))
	mux.HandleFunc("/api/ativo/detalhes", middleware.ProtegerRota(handlers.DetalhesAtivo))
	mux.HandleFunc("/api/usuarios/criar", middleware.ProtegerRota(handlers.CriarConta))
	mux.HandleFunc("/api/parametros", func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodGet {
			middleware.ProtegerRota(handlers.HandlerGetParametros)(w, r)
		} else if r.Method == http.MethodPut || r.Method == http.MethodPost {
			middleware.ProtegerRota(handlers.HandlerUpdateParametros)(w, r)
		} else if r.Method == http.MethodOptions {
			handlers.SetCORS(w)
		} else {
			http.Error(w, "Método não permitido", http.StatusMethodNotAllowed)
		}
	})
	// --- ROTAS DE GESTÃO E COMPLIANCE ---
	mux.HandleFunc("/api/usuarios", middleware.ProtegerRota(handlers.ListarUsuarios))
	mux.HandleFunc("/api/usuarios/editar", middleware.ProtegerRota(handlers.EditarConta))
	mux.HandleFunc("/api/usuarios/deletar", middleware.ProtegerRota(handlers.DeletarConta))
	mux.HandleFunc("/api/usuario", middleware.ProtegerRota(handlers.BuscarUsuarioInfo))
	mux.HandleFunc("/api/perfis", middleware.ProtegerRota(handlers.ListarPerfis))
	mux.HandleFunc("/api/compliance/lancamentos", middleware.ProtegerRota(handlers.ListarLancamentosContabeis))
	mux.HandleFunc("/api/compliance/lotes", middleware.ProtegerRota(handlers.ListarLotesFiscais))
	mux.HandleFunc("/api/compliance/resumo-fiscal", middleware.ProtegerRota(handlers.BuscarResumoFiscalMensal))
	mux.HandleFunc("/api/dashboard/historico-ativos", middleware.ProtegerRota(handlers.HandlerDashboardHistoricoAtivos))
	// --- ROTAS DE IA E QUANT ---
	mux.HandleFunc("/api/ingestao/iniciar", handlers.TriggerManual)
	mux.HandleFunc("/api/auditoria", handlers.Auditoria)
	mux.HandleFunc("/api/backtest", middleware.ProtegerRota(handlers.Backtest))
	mux.HandleFunc("/api/risco", middleware.ProtegerRota(handlers.RiscoSistemico))
	mux.HandleFunc("/api/montecarlo", middleware.ProtegerRota(handlers.SimularMonteCarlo))
	mux.HandleFunc("/api/otimizar", middleware.ProtegerRota(handlers.OtimizarCarteira))
	mux.HandleFunc("/api/portfolio/projecao", middleware.ProtegerRota(handlers.ProjecaoClasses))
	mux.HandleFunc("/api/ml/prever", middleware.ProtegerRota(handlers.PreverLSTM))
	mux.HandleFunc("/api/agente/causalidade", middleware.ProtegerRota(handlers.AgenteCausalidade))
	mux.HandleFunc("/api/stream/mercado", handlers.SSEMarketStream)
	mux.HandleFunc("/api/dashboard/resumo", middleware.ProtegerRota(handlers.HandlerDashboardResumo))
	mux.HandleFunc("/api/dashboard/historico", middleware.ProtegerRota(handlers.HandlerDashboardHistorico))
	mux.HandleFunc("/api/dashboard/macro", middleware.ProtegerRota(handlers.HandlerDashboardMacro))
	mux.HandleFunc("/api/piloto/toggle", middleware.ProtegerRota(handlers.TogglePiloto))
	// --- ROTAS INSTITUCIONAIS (TEARSHEET B2B) ---
	mux.HandleFunc("/api/institucional/resumo", middleware.ProtegerRota(handlers.HandlerResumoEstrategia))
	mux.HandleFunc("/api/institucional/curva-capital", middleware.ProtegerRota(handlers.HandlerCurvaCapital))
	mux.HandleFunc("/api/institucional/replay", middleware.ProtegerRota(handlers.HandlerReplayDecisao))
	// 5. Inicia servidor
	log.Println("🚀 Servidor Mestre Golang rodando na porta 8080...")
	err = http.ListenAndServe("0.0.0.0:8080", middleware.CORSMiddleware(mux))
	if err != nil {
		log.Fatalf("Falha crítica ao iniciar servidor: %v", err)
	}
}