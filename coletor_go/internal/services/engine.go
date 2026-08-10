package services

import (
	"bytes"
	"encoding/json"
	"encoding/xml"
	"os"
	"os/exec"
	"fmt"
	"io"
	"log"
	"math"
	"net/http"
	"quantadvisor/internal/database"
	"quantadvisor/internal/models"
	"quantadvisor/internal/risk"
	"strconv"
	"strings"
	"sync"
	"time"
)

var HTTPClient = &http.Client{
	Timeout: 15 * time.Second,
	Transport: &http.Transport{
		MaxIdleConns:        100,
		MaxIdleConnsPerHost: 100,
		IdleConnTimeout:     90 * time.Second,
		Proxy:               nil, 
	},
}
var httpClient = HTTPClient

func GetPythonEngineURL() string {
	url := os.Getenv("PYTHON_ENGINE_URL")
	if url == "" {
		return "http://motor_python:8000"
	}
	return strings.TrimSuffix(url, "/")
}

func GetGoEngineURL() string {
	url := os.Getenv("GO_ENGINE_URL")
	if url == "" {
		return "http://coletor_go:8080"
	}
	return strings.TrimSuffix(url, "/")
}

var TaxaSelicGlobal float64 = 0.1450
var DolarGlobal float64 = 5.0873

var muIngestao sync.Mutex
var IngestaoEmAndamento bool

var CarteiraMercado = []string{
	"AALR3", "ABCB4", "ABEV3", "AERI3", "AESB3", "AFLT3", "AGRO3", "ALLD3", "ALOS3", "ALPA3", 
    "ALPA4", "ALUP11", "AMAR3", "AMBP3", "AMER3", "ANIM3", "ASAI3", "AURE3", "AZEV3", "AZEV4", 
    "AZZA3", "B3SA3", "BAZA3", "BBAS3", "BBDC3", "BBDC4", "BBSE3", "BDLL3", "BDLL4", "BEEF3", 
    "BEES3", "BEES4", "BGIP3", "BGIP4", "BIOM3", "BLAU3", "BLUT3", "BLUT4", "BMEB3", "BMEB4", 
    "BMIN3", "BMIN4", "BMKS3", "BMOB3", "BOAS3", "BOBA3", "BOBA4", "BPAC11", "BPAN4", "BRAP3", 
    "BRAP4", "BRAV3", "BRFS3", "BRIT3", "BRIV3", "BRIV4", "BRKM3", "BRKM5", "BRSR3", "BRSR5", 
    "BRSR6", "BSPT3", "CAMB3", "CAML3", "CASH3", "CBAV3", "CBEE3", "CCRO3", "CEAB3", "CEBR3", 
    "CEBR5", "CEBR6", "CEDO3", "CEDO4", "CEGR3", "CGAS3", "CGAS5", "CGRA3", "CGRA4", "CIEL3", 
    "CLSA3", "CMIG3", "CMIG4", "CMIN3", "COCE3", "COCE5", "COGN3", "CPFE3", "CPLE3", "CPLE6", 
    "CRIV3", "CRIV4", "CRPG3", "CRPG5", "CRPG6", "CSAB3", "CSAB4", "CSAN3", "CSIQ11", "CSMG3", 
    "CSNA3", "CSRN3", "CSRN5", "CSRN6", "CTKA3", "CTKA4", "CTNM3", "CTNM4", "CTSA3", "CTSA4", 
    "CXSE3", "CYRE3", "DASA3", "DESK3", "DEXP3", "DEXP4", "DIRR3", "DOHL3", "DOHL4", "DOTZ3", 
    "DXCO3", "EALT3", "EALT4", "ECOR3", "EEEL3", "EEEL4", "EGIE3", "EKTR3", "EKTR4", "ELEK3", 
    "ELEK4", "ELET3", "ELET6", "ELMD3", "EMAE4", "ENBR3", "ENEV3", "ENGI11", "ENJU3", "EQMA3B", 
    "EQPA3", "EQPA5", "EQPA6", "EQPA7", "EQTL3", "ESPA3", "ESTR4", "ETER3", "EUCA3", "EUCA4", 
    "EVEN3", "EZTC3", "FESA3", "FESA4", "FHER3", "FIQE3", "FLRY3", "FRAS3", "GFSA3", "GGBR3", 
    "GGBR4", "GGPS3", "GOAU3", "GOAU4", "GOLL4", "GPIV33", "GRND3", "GSHP3", "GUAR3", "HAGA3", 
    "HAGA4", "HAPV3", "HBOR3", "HBSA3", "HBTS5", "HETA3", "HETA4", "HOOT4", "HOPE3", "IFCM3", 
    "IGTI11", "PETR4", "VALE3", "ITUB4", "MGLU3", "INEP3", "INEP4", "INTB3", "IRBR3", "ISAE3",
    "ISAE4", "ITCA3", "ITIT3", "ITSA3", "ITSA4", "ITUB3", "JALL3", "JBSS3", "JFEN3", "JHSF3", 
    "JOPA3", "JOPA4", "KEPL3", "KLBN11", "KRSA3", "LAVV3", "LEVE3", "LIGT3", "LIPR3", "LLIS3", 
    "LOGG3", "LOGN3", "LREN3", "LUPA3", "LWSA3", "MDIA3", "MDNE3", "MEAL3", "MEGA3", "MERC3", 
    "MERC4", "MILS3", "MLAS3", "MNDL3", "MNPR3", "MOVI3", "MRFG3", "MRVE3", "MTRE3", "MTSA4", 
    "MULT3", "MWET3", "MWET4", "MYPK3", "NECO3", "NEOE3", "NGRD3", "NINA3", "NORD3", "NTCO3", 
    "NUTR3", "ODPV3", "OFSA3", "OIBR3", "OIBR4", "ONCO3", "ORVR3", "OSXB3", "PATI3", "PATI4", 
    "PCAR3", "PDGR3", "PDTC3", "PEAB3", "PEAB4", "PETR3", "PETZ3", "PFRM3", "PGMN3", "PINE4", 
    "PLAS3", "PMAM3", "POMO3", "POMO4", "PORT3", "POSI3", "PRIO3", "PRNR3", "PSSA3", "PTBL3",
	"PTLV3", "PTLV4","QUAL3", "RADL3", "RAIL3", "RANI3", "RAPT3", "RAPT4", "RCSL3", "RCSL4", 
    "RDNI3", "RDOR3", "RECV3", "REDE3", "RENT3", "RNEW3", "RNEW4", "RNEW11", "ROMI3", "RSID3", 
    "RZTR11","SANB11", "SAPR11", "SBSP3", "SCAR3", "SCTR3", "SCTR4", "SEER3", "SGPS3", "SHOW3", 
    "SHUL4", "SIMH3", "SLCE3", "SMFT3", "SMTO3", "SNSY5", "SQIA3", "STBP3", "SUZB3", "SYNE3",
	"TAEE11", "TASA3", "TASA4", "TCNO4", "TCSA3", "TECN3", "TELB4", "TEND3", "TGMA3", "TIMS3", 
    "TOTS3", "TPIS3", "TRAD3", "TRIS3", "TUPY3", "TXRX3", "TXRX4", "UCAS3", "UGPA3", "UNIP3", 
    "UNIP5", "UNIP6", "USIM3", "USIM5", "VAMO3", "VBBR3", "VIVA3", "VIVR3", "VIVT3", "VLID3", 
    "VSTE3", "VULC3", "VVEO3", "WEGE3", "WHRL3", "WHRL4", "WIZC3", "WLMM3", "WLMM4", "YDUQ3", 
    "ZAMP3", "BOVA11", "IVVB11", "SMAL11", "DIVO11", "HASH11", "NASD11", "XINA11", "BCFF11", 
    "ALZR11", "VILG11", "TGAR11", "MALL11", "HCTR11", "HGLG11", "XPLG11", "VGIA11", "SNAG11", 
    "RZAG11", "KNCA11", "BERK34", "JNJB34", "COCA34", "DISB34", "NFLX34", "MCDC34", "NKEB34", 
    "TSLA34", "SPCX4", "AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "TSLA", "META", "BRK-B", "JPM", 
    "V", "AMD", "INTC", "TSM", "AVGO", "QCOM", "ASML", "BAC", "WFC", "GS", "MS", "AXP", "MA", 
    "PYPL", "WMT", "COST", "TGT", "PG", "KO", "PEP", "MCD", "NKE", "UNH", "JNJ", "LLY", "ABBV",
    "MRK", "PFE", "SPY","QQQ","DIA","VTI","TLT", "KNRI11", "CPHI","SLGB",  "OMH", "GOOG", "AMC", 
	"NFLX", "PLTR", "GME", "BABA", "NIO", "DIS", "LCID","SNDL",  "GREE", "XOM", "JZXN", "NIPG", 
	"DFNS", "VIVK", "NIKI", "KIDZ", "GRML", "WGRX", "MLEC", "AEHR", "NXXT", "PDC", "BANL", "FWRD", 
	"BULL", "SPCX", "JPM", "ADBE", "BA", "NOK", "F", "SPCX", "GOOG", "MU", "BE", "WULF", "PENG",
    "ROBO", "SPCU", "STRC", "MCLmain", "M2KM6", "SCHP",
	// ==========================================
	// 🇺🇸 BIG TECHS & CRESCIMENTO (NASDAQ / NYSE)
	// ==========================================
	"AAPL", "MSFT", "GOOGL", "GOOG", "AMZN", "NVDA", "TSLA", "META", "NFLX", "AMD", 
	"INTC", "TSM", "AVGO", "QCOM", "ASML", "ADBE", "CRM", "ORCL", "CSCO", "IBM", 
	"TXN", "AMAT", "MU", "LRCX", "SNOW", "PLTR", "UBER", "ABNB", "PANW", "CDNS", 
	"ADSK", "SHOP", "SQ", "PYPL", "TXN", "SHOP", "NOW", "INTU", "PYPL", "GME", 
	"AMC", "BABA", "NIO", "DIS", "LCID", "SNDL", "ROKU", "COIN", "HOOD", "RBLX",

	// ==========================================
	// 💰 INSTITUIÇÕES FINANCEIRAS & BANCOS
	// ==========================================
	"BRK-B", "JPM", "V", "MA", "BAC", "WFC", "GS", "MS", "AXP", "C", 
	"BLK", "SCHW", "PNC", "USB", "TFC", "SPGI", "MCO", "CB", "PGR", "TRV", 
	"AIG", "PYPL", "COF", "DFS", "BK", "STT", "ALL", "MET", "PRU", "AFL",

	// ==========================================
	// 💊 SAÚDE & FARMA (HEALTHCARE)
	// ==========================================
	"UNH", "JNJ", "LLY", "ABBV", "MRK", "PFE", "TMO", "ABT", "DHR", "BMY", 
	"AMGN", "CVS", "CI", "ISRG", "GILD", "VRTX", "REGN", "ZTS", "BDX", "SYK", 
	"BSX", "MDT", "ELV", "HUM", "CNC", "BAX", "DXCM", "ILMN", "IDXX", "IQV",

	// ==========================================
	// 🛒 CONSUMO CÍCLICO & DISCRICIONÁRIO
	// ==========================================
	"HD", "MCD", "NKE", "LOW", "SBUX", "BKNG", "TJX", "MAR", "ORLY", "GM", 
	"F", "CMG", "AZO", "YUM", "HLT", "DHI", "LEN", "TSCO", "ROST", "EBAY",

	// ==========================================
	// 🥫 CONSUMO NÃO-CÍCLICO (STAPLES)
	// ==========================================
	"WMT", "PG", "KO", "PEP", "COST", "PM", "MO", "CL", "EL", "KDP", 
	"GIS", "SYY", "MDLZ", "STZ", "HSY", "KHC", "GIS", "ADM", "TSN", "CAG",

	// ==========================================
	// 🏭 INDUSTRIAIS & DEFESA
	// ==========================================
	"CAT", "DE", "UNP", "HON", "UPS", "LMT", "RTX", "BA", "GE", "GD", 
	"NOC", "ETN", "ITW", "CSX", "NSC", "WM", "MMM", "FDX", "PH", "CTAS", 
	"CPRT", "PCAR", "FAST", "URI", "ODFL", "DAL", "UAL", "LUV", "ALK", "JBHT",

	// ==========================================
	// ⚡ ENERGIA & PETRÓLEO
	// ==========================================
	"XOM", "CVX", "COP", "SLB", "EOG", "MPC", "PSX", "VLO", "OXY", "KMI", 
	"WMB", "HAL", "BKR", "DVN", "FANG", "MRO", "TRGP", "EQT", "APA", "CTRA",

	// ==========================================
	// 📡 COMUNICAÇÃO & UTILIDADES PÚBLICAS
	// ==========================================
	"CMCSA", "VZ", "T", "TMUS", "EA", "TTWO", "NEE", "DUK", "SO", "D", 
	"AEP", "SRE", "EXC", "XEL", "ED", "PEG", "WEC", "ES", "AWK", "ETR",

	// ==========================================
	// 🏢 IMOBILIÁRIO (REITs)
	// ==========================================
	"PLD", "AMT", "EQIX", "CCI", "SPG", "O", "VICI", "PSA", "EXR", "WELL", 
	"AVB", "EQR", "DLR", "SBAC", "CBRE", "WY", "VTR", "ARE", "MAA", "ESS",

	// ==========================================
	// 📊 INDICES & ETFs DE REFERÊNCIA GLOBAL
	// ==========================================
	"SPY", "QQQ", "DIA", "VTI", "VOO", "IWM", "TLT", "EEM", "EFA", "ARKK", 
	"XLE", "XLF", "XLK", "XLV", "XLY", "XLP", "XLI", "XLU", "XLRE", "XLB",

	// ==========================================
	// 🇧🇷 ATIVOS NACIONAIS (B3 - MANTIDOS)
	// ==========================================
	"PETR4", "VALE3", "ITUB4", "MGLU3", "B3SA3", "BBAS3", "BBDC4", "BBSE3", 
	"BRFS3", "CCRO3", "CMIG4", "CPFE3", "CPLE6", "CRFB3", "CSAN3", "CSNA3", 
	"CYRE3", "ELET3", "ELET6", "EMBR3", "ENEV3", "ENGI11", "EQTL3", "FLRY3", 
	"GGBR4", "GOAU4", "HAPV3", "HYPE3", "JBSS3", "KLBN11", "LREN3", "LWSA3", 
	"MRFG3", "MRVE3", "MULT3", "NTCO3", "PCAR3", "PETR3", "PRIO3", "RADL3", 
	"RAIL3", "RENT3", "SANB11", "SBSP3", "SLCE3", "SMTO3", "SUZB3", "TAEE11", 
	"TIMS3", "TOTS3", "UGPA3", "USIM5", "VALE3", "VBBR3", "WEGE3", "YDUQ3",
	"BOVA11", "IVVB11", "SMAL11", "DIVO11", "HASH11", "NASD11", "XINA11", 
	"KNRI11", "HGLG11", "XPLG11",
}

func extrairCotacaoDolar() {
	url := "https://economia.awesomeapi.com.br/last/USD-BRL"
	
	resp, err := httpClient.Get(url)
	if err == nil && resp.StatusCode == 200 {
		defer resp.Body.Close()
		body, _ := io.ReadAll(resp.Body)

		var result map[string]map[string]interface{}
		if err := json.Unmarshal(body, &result); err == nil {
			if askStr, ok := result["USDBRL"]["ask"].(string); ok {
				if cotacao, err := strconv.ParseFloat(askStr, 64); err == nil {
					DolarGlobal = cotacao
					log.Printf("💵 Cotação do Dólar atualizada na memória RAM: R$ %.4f", DolarGlobal)
					return
				}
			}
		}
	}

	log.Printf("⚠️ Erro ao atualizar Dólar via API. Mantendo último valor: R$ %.4f", DolarGlobal)
}

func normalizarMoeda(ticker string, precoExtraido float64) float64 {
	isEstrangeiro := false
	
	if !strings.ContainsAny(ticker, "0123456789") && !strings.HasSuffix(ticker, ".SA") {
		isEstrangeiro = true
	}

	if isEstrangeiro {
		precoEmReais := precoExtraido * DolarGlobal
		log.Printf("💱 Conversão Cambial Efetuada: %s | US$ %.2f -> R$ %.2f", ticker, precoExtraido, precoEmReais)
		return precoEmReais
	}

	return precoExtraido
}

func AtualizarSentimentoNoticias(ticker string) {
	urlRSS := fmt.Sprintf("https://news.google.com/rss/search?q=%s+acoes&hl=pt-BR&gl=BR&ceid=BR:pt-419", ticker)
	
	resp, err := httpClient.Get(urlRSS)
	if err != nil {
		log.Printf("⚠️ [FinBERT NLP] Erro crítico de rede ao buscar notícias para %s: %v", ticker, err)
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		log.Printf("⚠️ [FinBERT NLP] Yahoo RSS bloqueou o acesso para %s. Status HTTP: %d", ticker, resp.StatusCode)
		return
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		log.Printf("⚠️ [FinBERT NLP] Falha ao ler o Body da resposta para %s: %v", ticker, err)
		return
	}

	var rss models.RSS
	err = xml.Unmarshal(body, &rss)
	if err != nil {
		log.Printf("⚠️ [FinBERT NLP] Falha no parse XML do RSS para %s: %v", ticker, err)
		return
	}

	var noticias []string
	for _, item := range rss.Channel.Items {
		noticias = append(noticias, item.Title)
	}

	if len(noticias) == 0 {
		return
	}

	payload := map[string]interface{}{"textos": noticias}
	payloadBytes, _ := json.Marshal(payload)
	
	respPy, err := httpClient.Post(GetPythonEngineURL()+"/nlp/sentimento", "application/json", bytes.NewBuffer(payloadBytes))
	if err != nil {
		log.Printf("❌ [FinBERT NLP] Microserviço Python Indisponível (Falha ao inferir sentimento de %s): %v", ticker, err)
		return
	}
	defer respPy.Body.Close()

	var pyResult map[string]interface{}
	json.NewDecoder(respPy.Body).Decode(&pyResult)

	if score, ok := pyResult["score_finbert"].(float64); ok {
		database.Rdb.Set(database.Ctx, "nlp:"+ticker, score, time.Hour*24)
		log.Printf("📰 [FinBERT NLP] Sentimento capturado para %s: %.2f", ticker, score)
	}
}

func PersistirResultadoQuant(res models.ResultadoCalculoPython) {
	queryPostgres := `
		INSERT INTO historico_recomendacoes 
		(ticker_ativo, preco_analisado, z_score_calculado, var_diario_calculado, decisao_ia, taxa_selic_aplicada)
		VALUES ($1, $2, $3, $4, $5, $6);
	`
	_, err := database.Conn.Exec(queryPostgres, res.Ticker, res.PrecoAtual, res.ZScore, res.RiscoVar/100.0, res.Sinal, 0.1050)
	if err != nil {
		log.Printf("❌ [POSTGRES] Erro ao comitar histórico de %s: %v", res.Ticker, err)
	}

	payloadRam := map[string]interface{}{
		"ativo":               res.Ticker,
		"preco_atual":         res.PrecoAtual,
		"z_score":             res.ZScore,
		"risco_var":           res.RiscoVar,
		"distancia_vwap_perc": res.DistanciaVwapPerc, // 👈 INJETE AQUI PARA SALVAR NO REDIS
		"volume_zscore":       res.VolumeZScore,
		"kelly_recomendado":   res.KellyRecomendado,
		"sinal":               res.Sinal,
		"sinais_perfil":       res.SinaisPerfil,
		"classe":              res.Classe,
		"fonte":               res.Fonte,
	}

	jsonBytes, err := json.Marshal(payloadRam)
	if err == nil {
		chaveRedis := fmt.Sprintf("ticker:%s", res.Ticker)
		database.Rdb.Set(database.Ctx, chaveRedis, jsonBytes, 0)
		
		database.Rdb.SAdd(database.Ctx, "ativos_set", res.Ticker)
		
		histBytes, _ := json.Marshal(res.HistoricoPrecos)
		database.Rdb.Set(database.Ctx, "hist:"+res.Ticker, histBytes, 0)
		database.Rdb.Publish(database.Ctx, "market_ticks", jsonBytes)
	}

	log.Printf("🔵 [ORQUESTRADOR GO] Ativo %s persistido com sucesso! [Postgres + Redis Set]", res.Ticker)
}

func ExecutarIngestaoEmLote(tickers []string) {
	muIngestao.Lock()
	if IngestaoEmAndamento {
		log.Println("⚠️ [ORQUESTRADOR] Cancelado: Uma esteira de ingestão já está em execução. Evitando sobrecarga.")
		muIngestao.Unlock()
		return
	}
	IngestaoEmAndamento = true
	muIngestao.Unlock()

	defer func() {
		muIngestao.Lock()
		IngestaoEmAndamento = false
		muIngestao.Unlock()
	}()

	log.Println("⚡ [HANDLERS TRIGGER] Ingestão manual ou agendada iniciada. Roteando para a Esteira Síncrona...")
	extrairTaxaSelic()
	extrairCotacaoDolar()
	ExecutarEsteiraSincrona()
}

func IniciarCronJob() {
    log.Println("⚡ [CRONJOB] Inicializando esteira contínua HFT e Agendadores Noturnos...")

    // 👇 1. DISPARO DOS AGENDADORES EM BACKGROUND (GOROUTINES) 👇
    // Essas funções ficarão "dormindo" na memória sem consumir CPU até a madrugada.
    go AgendarTreinamentoIA()
    go AgendarColetaFundamentosDiaria()

    // 2. PRIMEIRA EXECUÇÃO IMEDIATA (Warm-up do motor de precificação)
    extrairTaxaSelic()
    extrairCotacaoDolar()
    ExecutarEsteiraSincrona()

    // 3. O CORAÇÃO DO HFT (Loop infinito intradiário)
    ticker := time.NewTicker(1 * time.Minute) 
    defer ticker.Stop()
    
    for {
        select {
        case <-ticker.C:
            log.Println("🔄 [CRONJOB] Tick de 1 minuto! Iniciando nova varredura de mercado...")
            
            muIngestao.Lock()
            if !IngestaoEmAndamento {
                IngestaoEmAndamento = true
                muIngestao.Unlock()
                
                extrairTaxaSelic()
                extrairCotacaoDolar()
                ExecutarEsteiraSincrona()
                
                muIngestao.Lock()
                IngestaoEmAndamento = false
            } else {
                log.Println("⚠️ [CRONJOB] A ingestão anterior ainda não terminou. Pulando este tick.")
            }
            muIngestao.Unlock()
        }
    }
}

func ExecutarEsteiraSincrona() {
	var wg sync.WaitGroup

	log.Printf("🚀 [ORQUESTRADOR] Disparando motores de ingestão em Worker Pool para %d ativos...", len(CarteiraMercado))

	jobs := make(chan string, len(CarteiraMercado))

	numWorkers := 15
	for w := 1; w <= numWorkers; w++ {
		wg.Add(1)
		go func(workerID int) {
			defer wg.Done()
			for ticker := range jobs {
				processarGatilhoYahooPython(ticker)
				
				time.Sleep(50 * time.Millisecond)
			}
		}(w)
	}

	wg.Add(1)
	go func() {
		defer wg.Done()
		processarStreamingBrapiLote()
	}()

	for _, ticker := range CarteiraMercado {
		jobs <- ticker
	}
	close(jobs) 

	wg.Wait()
	log.Println("🏁 [ORQUESTRADOR] Ciclo completo de ingestão finalizado com sucesso!")
}

func processarStreamingBrapiLote() {
	tickersBrapi := []string{"PETR4", "VALE3", "ITUB4", "MGLU3"}
	tokenDaBrapi := "7ujuQ1UgqftKJEKNQdH33z"
	client := &http.Client{Timeout: 10 * time.Second}

	log.Printf("📡 [BRAPI] Iniciando varredura individual de %d ativos para contornar limite do plano...", len(tickersBrapi))

	for _, ticker := range tickersBrapi {
		url := fmt.Sprintf("https://brapi.dev/api/quote/%s?range=1y&interval=1d", ticker)
		
		req, err := http.NewRequest("GET", url, nil)
		if err != nil { continue }
		
		req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", tokenDaBrapi))
		req.Header.Set("User-Agent", "QuantAdvisor-Maestro/1.0")

		resp, err := client.Do(req)
		if err != nil {
			log.Printf("❌ [BRAPI] Falha de conexão para o ativo %s: %v", ticker, err)
			continue
		}

		if resp.StatusCode != 200 {
			bodyErro, _ := io.ReadAll(resp.Body)
			log.Printf("⚠️ [BRAPI] Falha para o ativo %s. Status HTTP: %d | Erro: %s", ticker, resp.StatusCode, string(bodyErro))
			resp.Body.Close()
			continue
		}

		body, _ := io.ReadAll(resp.Body)
		resp.Body.Close()

		var data map[string]interface{}
		json.Unmarshal(body, &data)
		results, ok := data["results"].([]interface{})
		if !ok || len(results) == 0 { continue }

		ativoMap, ok := results[0].(map[string]interface{})
		if !ok { continue }
		
		urlPython := fmt.Sprintf("%s/streaming/ingestao/%s/BRAPI?selic=%.4f", GetPythonEngineURL(), ticker, TaxaSelicGlobal)
		payloadJson, _ := json.Marshal(ativoMap)
		enviarParaPythonEOperar(ticker, urlPython, payloadJson, "BRAPI")

		time.Sleep(500 * time.Millisecond)
	}
	log.Println("🏁 [BRAPI] Varredura de ativos individuais concluída com sucesso!")
}

func processarGatilhoYahooPython(ticker string) {
	temNumero := strings.ContainsAny(ticker, "0123456789")
	tickerFormatado := ticker
	
	if temNumero && !strings.HasSuffix(ticker, ".SA") {
		tickerFormatado = ticker + ".SA"
	}

	urlYahoo := fmt.Sprintf("https://query1.finance.yahoo.com/v8/finance/chart/%s?range=1y&interval=1d", tickerFormatado)
	
	req, err := http.NewRequest("GET", urlYahoo, nil)
	if err != nil {
		log.Printf("❌ Erro ao criar requisição para %s: %v", ticker, err)
		return
	}

	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
	req.Header.Set("Accept", "application/json")

	respYahoo, err := httpClient.Do(req)
	if err != nil {
		log.Printf("❌ [GO-YAHOO] Falha de conexão com Yahoo para %s: %v", ticker, err)
		risk.GlobalCooldown.Bloquear(ticker, 5*time.Minute)
		return
	}
	defer respYahoo.Body.Close()

	if respYahoo.StatusCode == 404 {
		log.Printf("⚠️ [GO-YAHOO] Ativo %s não encontrado (404). Removendo da fila por 24h.", ticker)
		risk.GlobalCooldown.Bloquear(ticker, 24*time.Hour)
		return
	} else if respYahoo.StatusCode != 200 {
		log.Printf("⚠️ [GO-YAHOO] Yahoo respondeu com Status HTTP %d para %s. Removendo por 1h.", respYahoo.StatusCode, ticker)
		risk.GlobalCooldown.Bloquear(ticker, 1*time.Hour)
		return
	}

	body, err := io.ReadAll(respYahoo.Body)
	if err != nil {
		log.Printf("❌ Erro ao ler resposta de %s: %v", ticker, err)
		return
	}

	if len(body) < 100 {
		log.Printf("⚠️ [GO-YAHOO] Payload de %s vazio ou insuficiente. Removendo da fila por 1h.", ticker)
		risk.GlobalCooldown.Bloquear(ticker, 1*time.Hour)
		return
	}

	urlPython := fmt.Sprintf("%s/streaming/ingestao/%s/YAHOO?selic=%.4f", GetPythonEngineURL(), ticker, TaxaSelicGlobal)
	enviarParaPythonEOperar(ticker, urlPython, body, "YAHOO")
}

func enviarParaPythonEOperar(ticker string, urlPython string, payload []byte, fonte string) {
	chaveRedis := fmt.Sprintf("raw:%s:%s", fonte, ticker)
	
	errRedis := database.Rdb.Set(database.Ctx, chaveRedis, payload, 15*time.Minute).Err()

	if errRedis != nil {
		log.Printf("⚠️ [GO-REDIS] Falha ao injetar %s no Redis: %v. Acionando Fallback Python...", ticker, errRedis)
		
		payloadFallback := map[string]string{
			"chave": chaveRedis,
			"dados": string(payload),
		}
		jsonFallback, _ := json.Marshal(payloadFallback)
		
		urlFallback := GetPythonEngineURL() + "/streaming/fallback_redis"
		respFall, errFall := httpClient.Post(urlFallback, "application/json", bytes.NewBuffer(jsonFallback))
		
		if errFall != nil {
			log.Printf("❌ [CRÍTICO] Erro de rede ao chamar a API de Fallback do Python para %s: %v", ticker, errFall)
		} else {
			defer respFall.Body.Close() 
			if respFall.StatusCode != 200 {
				log.Printf("❌ [CRÍTICO] O Python tentou, mas também FALHOU ao injetar %s no Redis! (Status %d)", ticker, respFall.StatusCode)
			} else {
				log.Printf("✅ [GO-FALLBACK] Python assumiu e injetou %s no Redis com sucesso!", ticker)
			}
		}
	}

	var resp *http.Response
	var err error
	maxTentativas := 5
	
	for i := 1; i <= maxTentativas; i++ {
		resp, err = httpClient.Post(urlPython, "application/json", bytes.NewBuffer([]byte(`{}`)))
		if err == nil {
			break 
		}
		
		if i == maxTentativas {
			log.Printf("❌ [GO-CONECTOR] Motor Python (IA) offline ou não responde (%s). Abortando ativo: %s", urlPython, ticker)
			return
		}
		
		time.Sleep(10 * time.Second) 
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		bodyErro, _ := io.ReadAll(resp.Body)
		log.Printf("⚠️ [GO-CONECTOR] Python rejeitou o cálculo de %s. Status: %d | Erro: %s", ticker, resp.StatusCode, string(bodyErro))
		return
	}

	var pyResp models.ResultadoCalculoPython
	if err := json.NewDecoder(resp.Body).Decode(&pyResp); err == nil && pyResp.Sucesso {

		sinalDefinitivo := pyResp.SinaisPerfil["Arrojado"] 
		
		payloadRL := map[string]interface{}{
			"ticker":          pyResp.Ticker,
			"preco_atual":     pyResp.PrecoAtual,
			"z_score":         pyResp.ZScore,
			"sentimento_nlp":  0.0, 
			"vol_intraday":    math.Abs(pyResp.RiscoVar) / 100.0, 
			"choque_liquidez": 1.0, 
		}
		jsonRL, _ := json.Marshal(payloadRL)
		
		respRL, errRL := httpClient.Post(GetPythonEngineURL()+"/rl/decidir", "application/json", bytes.NewBuffer(jsonRL))
		
		if errRL == nil {
			defer respRL.Body.Close() 
			
			if respRL.StatusCode == 200 {
				var rlResult map[string]interface{}
				json.NewDecoder(respRL.Body).Decode(&rlResult)
				
				if sinalIA, ok := rlResult["sinal_ia"].(string); ok {
					sinalDefinitivo = sinalIA
					log.Printf("🤖 [Agente RL] O Agente PPO decided: %s para %s", sinalDefinitivo, pyResp.Ticker)
				}
			}
		}

		pyResp.Sinal = sinalDefinitivo
		PersistirResultadoQuant(pyResp)

		botResp := models.PythonResponse{
			Sucesso:           pyResp.Sucesso,
			Ticker:            pyResp.Ticker,
			Sinal:             sinalDefinitivo, 
			PrecoAtual:        pyResp.PrecoAtual, 
			ZScore:            pyResp.ZScore,
			KellyRecomendado:  pyResp.KellyRecomendado, 
			RiscoVar:          pyResp.RiscoVar, 
			DistanciaVwapPerc: pyResp.DistanciaVwapPerc, 
			VolumeZScore:      pyResp.VolumeZScore,
		}
		processarDecisaoTrading(botResp, fonte)
		
	} else if err != nil {
		log.Printf("❌ [GO-CONECTOR] Erro ao decodificar o retorno estruturado de %s: %v", ticker, err)
	}
}

func processarDecisaoTrading(pyResp models.PythonResponse, fonte string) {
	log.Printf("📊 [MAESTRO AUDITORIA] %s processado via -> %s | Preço: %.2f | Z-Score: %.2f | Sinal: %s",
		pyResp.Ticker, fonte, pyResp.PrecoAtual, pyResp.ZScore, pyResp.Sinal)

	if risk.GlobalCooldown.EmCooldown(pyResp.Ticker) { return }

	isMercadoAberto := IsMercadoAberto(pyResp.Ticker)

	zScore := pyResp.ZScore
	varAtual := pyResp.RiscoVar 

	// Ajustes de Kelly baseados no risco
	if pyResp.Sinal == "COMPRA FORTE" {
		if zScore > 1.5 {
			pyResp.Sinal = "NEUTRO"
		}
		if zScore < 0 && varAtual > 10.0 {
			pyResp.KellyRecomendado = pyResp.KellyRecomendado / 3.0
		}
		if zScore <= -1.5 && varAtual < 5.0 {
			pyResp.KellyRecomendado = pyResp.KellyRecomendado * 1.5 
		}
	}

	if pyResp.Sinal == "COMPRA FORTE" && pyResp.KellyRecomendado <= 0.01 {
		risk.GlobalCooldown.Bloquear(pyResp.Ticker, 5*time.Minute)
		pyResp.Sinal = "NEUTRO" 
	}

	rows, err := database.Conn.Query("SELECT usuario_id, saldo_brl, saldo_usd, perfil_risco, piloto_automatico FROM contas_virtuais")
	if err != nil { return }
	defer rows.Close()

	for rows.Next() {
		var usuarioID int
		var saldoBRL, saldoUSD float64
		var perfilRisco string
		var pilotoAutomatico bool 

		if err := rows.Scan(&usuarioID, &saldoBRL, &saldoUSD, &perfilRisco, &pilotoAutomatico); err != nil { continue }

		// Separação de Moeda para a Tesouraria
		precoBaseBRL := pyResp.PrecoAtual
		moedaAtivo := "BRL"
		if !strings.ContainsAny(pyResp.Ticker, "0123456789") && !strings.HasSuffix(pyResp.Ticker, ".SA") {
			precoBaseBRL = pyResp.PrecoAtual * DolarGlobal
			moedaAtivo = "USD"
		}

		// 💰 1. VETO DE TESOURARIA MULTIMOEDA (Filtro Anti-Cegueira)
		saldoMoedaNativa := saldoBRL
		if moedaAtivo == "USD" {
			saldoMoedaNativa = saldoUSD
		}

		if pyResp.Sinal == "COMPRA FORTE" {
			// Se o saldo na moeda específica não compra nem 1 cota, aborta sumariamente.
			if saldoMoedaNativa < pyResp.PrecoAtual {
				pyResp.Sinal = "NEUTRO"
				log.Printf("💰 [VETO DE TESOURARIA] Compra de %s bloqueada. User %d tem %.2f %s (Insuficiente para cotas de %.2f).", 
					pyResp.Ticker, usuarioID, saldoMoedaNativa, moedaAtivo, pyResp.PrecoAtual)
				continue
			}
		}

		// Cálculo Patrimonial
		var valorTotalCustodia float64
		queryTotal := `SELECT COALESCE(SUM(quantidade_total * preco_medio * CASE WHEN moeda = 'USD' THEN $1 ELSE 1.0 END), 0) FROM posicoes_carteira WHERE usuario_id = $2`
		errCust := database.Conn.QueryRow(queryTotal, DolarGlobal, usuarioID).Scan(&valorTotalCustodia)
		if errCust != nil { valorTotalCustodia = 0 }

		saldoGlobal := saldoBRL + (saldoUSD * DolarGlobal)
		patrimonioTotal := saldoGlobal + valorTotalCustodia

		// =========================================================================
		// 🛡️ O FREIO DE MÃO DEFINITIVO (CIRCUIT BREAKER GLOBAL)
		// =========================================================================
		var pisoMaxDrawdown float64 = -0.05
		_ = database.Conn.QueryRow("SELECT piso_max_drawdown FROM parametros_operacionais WHERE usuario_id = $1", usuarioID).Scan(&pisoMaxDrawdown)

		var lucroAcumulado float64
		_ = database.Conn.QueryRow("SELECT lucro_acumulado FROM contas_virtuais WHERE usuario_id = $1", usuarioID).Scan(&lucroAcumulado)

		// O "patrimonioTotal" atual no código usa o preco_medio, então ele representa o CUSTO BASE (Investimento Inicial)
		investimentoBase := patrimonioTotal - lucroAcumulado 
		if investimentoBase <= 0 { investimentoBase = 1000.0 }

		// Para saber se estamos perdendo dinheiro agora, vamos pegar o último patrimônio marcado a mercado da fotografia diária
		var ultimoPatrimonioMarcado float64
		_ = database.Conn.QueryRow("SELECT patrimonio_total FROM historico_patrimonial WHERE usuario_id = $1 ORDER BY data_fechamento DESC LIMIT 1", usuarioID).Scan(&ultimoPatrimonioMarcado)

		if ultimoPatrimonioMarcado > 0 {
			drawdownAtual := (ultimoPatrimonioMarcado / investimentoBase) - 1.0

			if pyResp.Sinal == "COMPRA FORTE" && drawdownAtual <= pisoMaxDrawdown {
				pyResp.Sinal = "NEUTRO"
				log.Printf("🛑 [CIRCUIT BREAKER GLOBAL] Compra de %s VETADA para User %d. O Patrimônio caiu %.2f%% (Limite: %.2f%%). Conta travada para proteção de capital.", pyResp.Ticker, usuarioID, drawdownAtual*100, pisoMaxDrawdown*100)
				continue // 👈 Isso corta a execução aqui, ignorando o restante da lógica de compra.
			}
		}
		// =========================================================================

		kellyPuro := pyResp.KellyRecomendado
		varAtualPerc := math.Abs(pyResp.RiscoVar)

		fatorVaR := 1.0
		if varAtualPerc > 8.0 {
			fatorVaR = 8.0 / varAtualPerc 
		}

		var multiplicadorPerfil float64
		var tetoMaximoPerc float64

		switch perfilRisco {
		case "Conservador":
			multiplicadorPerfil = 0.15; tetoMaximoPerc = 0.03      
		case "Moderado":
			multiplicadorPerfil = 0.25; tetoMaximoPerc = 0.05      
		case "Arrojado":
			multiplicadorPerfil = 0.50; tetoMaximoPerc = 0.08      
		case "Agressivo":
			multiplicadorPerfil = 0.75; tetoMaximoPerc = 0.12      
		default:
			multiplicadorPerfil = 0.25; tetoMaximoPerc = 0.05
		}

		fracaoSegura := math.Min(kellyPuro * multiplicadorPerfil * fatorVaR, tetoMaximoPerc)
		targetFinanceiro := patrimonioTotal * fracaoSegura

		var qtdCustodia float64
		_ = database.Conn.QueryRow("SELECT quantidade_total FROM posicoes_carteira WHERE usuario_id = $1 AND ticker = $2", usuarioID, pyResp.Ticker).Scan(&qtdCustodia)
		
		posicaoAtualFinanceiraBRL := qtdCustodia * precoBaseBRL
		deltaFinanceiro := targetFinanceiro - posicaoAtualFinanceiraBRL
		threshold := precoBaseBRL * 1.0 
		podeExecutar := isMercadoAberto && pilotoAutomatico

		isModoApenasSaida := false
		if moedaAtivo == "USD" {
			locNY, _ := time.LoadLocation("America/New_York")
			agoraNY := time.Now().In(locNY)
			tempoDecimalNY := float64(agoraNY.Hour()) + float64(agoraNY.Minute())/60.0
			isModoApenasSaida = tempoDecimalNY >= 19.66 
		} else {
			locBR, _ := time.LoadLocation("America/Sao_Paulo")
			agoraBR := time.Now().In(locBR)
			tempoDecimalBR := float64(agoraBR.Hour()) + float64(agoraBR.Minute())/60.0
			isModoApenasSaida = tempoDecimalBR >= 16.66 
		}

		if perfilRisco == "Agressivo" && isModoApenasSaida {
			if pyResp.Sinal == "COMPRA FORTE" {
				if pyResp.VolumeZScore >= 1.5 || pyResp.ZScore <= -2.0 {
					log.Printf("🌙 [OVERNIGHT AUTORIZADO] Compra de %s mantida! Fluxo institucional detectado no fechamento.", pyResp.Ticker)
				} else {
					pyResp.Sinal = "NEUTRO"
					log.Printf("🛑 [TRAVA MOC] Sugestão de COMPRA em %s bloqueada (User %d). Pregão em zeragem.", pyResp.Ticker, usuarioID)
				}
			}
		}

		// 🛑 2. MÁQUINA DE ESTADOS: STOP LOSS E TIME STOP ATÔMICO
		if pyResp.Sinal == "LIQUIDACAO_TOTAL" {
			if qtdCustodia > 0 {
				log.Printf("🛑 [HARD STOP] Ejetando 100%% da posição: %d cotas de %s a mercado (User %d).", int(qtdCustodia), pyResp.Ticker, usuarioID)
				rotearOrdem(usuarioID, pyResp.Ticker, "VENDA", int(qtdCustodia), pyResp.PrecoAtual, podeExecutar)
			}
			continue
		}

		// 🎯 3. MÁQUINA DE ESTADOS: SCALE-OUT NO LUCRO (Loop Fracionado)
		if pyResp.Sinal == "REALIZACAO_PARCIAL" {
			if qtdCustodia > 0 {
				// Fatiando em 33% por operação
				fracaoVenda := 0.33
				loteVenda := int(math.Floor(qtdCustodia * fracaoVenda))
				
				// Se a fatia for menor que 1 ou restarem "farelos" (2 cotas), limpa tudo.
				if loteVenda == 0 || qtdCustodia <= 2 {
					loteVenda = int(qtdCustodia)
				}

				log.Printf("🎯 [SCALE-OUT] Fatiando lucro: Vendendo %d cotas de %s. Restam %d na carteira (User %d).", 
					loteVenda, pyResp.Ticker, int(qtdCustodia)-loteVenda, usuarioID)
				rotearOrdem(usuarioID, pyResp.Ticker, "VENDA", loteVenda, pyResp.PrecoAtual, podeExecutar)
			}
			continue
		}

		// Mantém o Stop Loss Estrutural original por VaR (Opcional, atua como dupla checagem do Python)
		if qtdCustodia > 0 {
			var precoMedioNativo float64
			errPm := database.Conn.QueryRow("SELECT preco_medio FROM posicoes_carteira WHERE usuario_id = $1 AND ticker = $2", usuarioID, pyResp.Ticker).Scan(&precoMedioNativo)

			if errPm == nil && precoMedioNativo > 0 {
				limitePerda := math.Abs(pyResp.RiscoVar) / 100.0 * 1.5 
				if limitePerda < 0.02 { limitePerda = 0.02 } 
				if limitePerda > 0.04 { limitePerda = 0.04 } 

				precoStopLoss := precoMedioNativo * (1.0 - limitePerda)

				if pyResp.PrecoAtual < precoStopLoss {
					log.Printf("🛑 [CRO STOP LOSS] Quebra Estrutural em %s! Caiu abaixo de %.2f%%. Liquidando (User %d).", pyResp.Ticker, limitePerda*100, usuarioID)
					rotearOrdem(usuarioID, pyResp.Ticker, "VENDA", int(qtdCustodia), pyResp.PrecoAtual, podeExecutar)
					continue 
				}
			}
		}

		if pyResp.Sinal == "NEUTRO" {
			continue
		}

		// 🛒 Execução final da Compra
		saldoDaOperacaoBRL := saldoBRL
		if moedaAtivo == "USD" {
			saldoDaOperacaoBRL = saldoUSD * DolarGlobal
		}

		if pyResp.Sinal == "COMPRA FORTE" && deltaFinanceiro > threshold {
			if deltaFinanceiro > saldoDaOperacaoBRL {
				deltaFinanceiro = saldoDaOperacaoBRL
			}

			if deltaFinanceiro >= precoBaseBRL && saldoDaOperacaoBRL > 0 {
				loteCompra := int(math.Floor(deltaFinanceiro / precoBaseBRL))
				if loteCompra > 0 {
					log.Printf("🤖 [ROBÔ] Comprando %d cotas de %s para User %d", loteCompra, pyResp.Ticker, usuarioID)
					rotearOrdem(usuarioID, pyResp.Ticker, "COMPRA", loteCompra, pyResp.PrecoAtual, podeExecutar)
				}
			}
		} else if pyResp.Sinal == "ALERTA DE VENDA" && deltaFinanceiro < -threshold {
			// Fallback para o Alerta de Venda Clássico
			loteVenda := int(math.Floor(math.Abs(deltaFinanceiro) / precoBaseBRL))
			if qtdCustodia > 0 && loteVenda > 0 {
				loteFinal := int(math.Min(float64(loteVenda), qtdCustodia))
				log.Printf("🤖 [ROBÔ] IA detectou risco. Vendendo %d cotas de %s para User %d", loteFinal, pyResp.Ticker, usuarioID)
				rotearOrdem(usuarioID, pyResp.Ticker, "VENDA", loteFinal, pyResp.PrecoAtual, podeExecutar)
			}
		} else {
			ExecutarBuyTheDipOportunista(usuarioID, pyResp.Ticker, pyResp.PrecoAtual, pyResp.ZScore, saldoDaOperacaoBRL)
		}
	}
}

func rotearOrdem(uID int, ticker string, tipo string, qtd int, preco float64, mercadoAberto bool) {
	dataAtual := time.Now().Format("2006-01-02")
	
	chaveLock := fmt.Sprintf("lock:v2:ordem:%d:%s:%s:%s", uID, ticker, tipo, dataAtual)

	sucesso, errRedis := database.Rdb.SetNX(database.Ctx, chaveLock, "processado", 12*time.Hour).Result()
	if errRedis == nil && !sucesso {
		return 
	}

	moeda := "BRL"
	taxaCambio := 1.0

	if !strings.ContainsAny(ticker, "0123456789") && !strings.HasSuffix(ticker, ".SA") {
		moeda = "USD"
		taxaCambio = DolarGlobal 
	}

	volumeMoedaNativa := preco * float64(qtd)
	volumeBRL := volumeMoedaNativa * taxaCambio

	payload := map[string]interface{}{
		"usuario_id":          uID, 
		"ticker":              ticker, 
		"tipo_ordem":          tipo, 
		"quantidade":          qtd, 
		"preco":               preco, 
		"moeda":               moeda,
		"taxa_cambio_momento": taxaCambio,
		"volume_brl":          volumeBRL,
	}
	jsonData, _ := json.Marshal(payload)

	var urlDestino string
	if mercadoAberto {
		urlDestino = GetGoEngineURL() + "/api/ordem"
	} else {
		urlDestino = GetGoEngineURL() + "/api/adicionar-carrinho"
		log.Printf("💤 [MERCADO FECHADO] Sugestão de %s de %s enviada para o Carrinho Noturno (Cliente %d)", tipo, ticker, uID)
	}

	req, err := http.NewRequest("POST", urlDestino, bytes.NewBuffer(jsonData))
	if err != nil { 
		risk.GlobalCooldown.Bloquear(ticker, 10*time.Minute)
		return 
	}
	
	req.Header.Set("Content-Type", "application/json")
	botSecret := os.Getenv("INTERNAL_BOT_SECRET")
	if botSecret == "" {
		botSecret = "quantadvisor_internal_master_777_!@"
	}
	req.Header.Set("X-Internal-Bot", botSecret) 

	resp, err := httpClient.Do(req)
	
	if err != nil { 
		risk.GlobalCooldown.Bloquear(ticker, 10*time.Minute)
		log.Printf("❌ Falha de rede ao rotear ordem de %s. Cadeado liberado. Erro: %v", ticker, err)
		return 
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		risk.GlobalCooldown.Bloquear(ticker, 10*time.Minute)
		bodyErro, _ := io.ReadAll(resp.Body)
		log.Printf("⚠️ [ROBÔ] Ordem de %s REJEITADA pela corretora/banco. Status: %d | Motivo: %s", ticker, resp.StatusCode, string(bodyErro))
	}
}

func enviarOrdemInterna(uID int, ticker string, tipo string, qtd int, preco float64) {
	payload := map[string]interface{}{
		"usuario_id": uID, "ticker": ticker, "tipo_ordem": tipo, "quantidade": qtd, "preco": preco,
	}
	jsonData, _ := json.Marshal(payload)

	urlDestino := GetGoEngineURL() + "/api/ordem"
	resp, err := httpClient.Post(urlDestino, "application/json", bytes.NewBuffer(jsonData))
	
	if err != nil {
		log.Printf("❌ [ORQUESTRADOR] ALERTA CRÍTICO: Falha ao rotear ordem para %s (%s). Motivo: %v", ticker, tipo, err)
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		bodyErro, _ := io.ReadAll(resp.Body)
		log.Printf("⚠️ [ORQUESTRADOR] Ordem de %s REJEITADA pela API. Status HTTP: %d | Motivo: %s", ticker, resp.StatusCode, string(bodyErro))
	}
}

func extrairTaxaSelic() {
	url := "https://api.bcb.gov.br/dados/serie/bcdata.sgs.432/dados/ultimos/1?formato=json"
	resp, err := httpClient.Get(url) 
	if err == nil && resp.StatusCode == 200 {
		defer resp.Body.Close()
		body, _ := io.ReadAll(resp.Body)
		
		var dados []map[string]interface{}
		if err := json.Unmarshal(body, &dados); err == nil && len(dados) > 0 {
			if selicStr, ok := dados[0]["valor"].(string); ok {
				if s, err := strconv.ParseFloat(selicStr, 64); err == nil {
					TaxaSelicGlobal = s / 100.0
					log.Printf("🏦 Taxa Selic atualizada na memória RAM do Go: %.4f", TaxaSelicGlobal)
				}
			}
		}
	}
}

func AgendarTreinamentoIA() {
	log.Println("🤖 [MLOps] Agendador de Treinamento IA ativado. Modelos serão atualizados após o pregão.")
	go dispararTreinamentoPython(CarteiraMercado)

	for {
		agora := time.Now()
		alvo := time.Date(agora.Year(), agora.Month(), agora.Day(),18,00, 0, 0, agora.Location())

		if agora.After(alvo) {
			alvo = alvo.Add(24 * time.Hour)
		}

		espera := alvo.Sub(agora)
		log.Printf("🌙 [MLOps] Próximo ciclo de treinamento de IA agendado para: %s", alvo.Format("02/01/2006 15:04:05"))
		time.Sleep(espera)

		log.Println("🔥 [MLOps] Pregão encerrado. Disparando ordem de treinamento em lote da IA...")
		dispararTreinamentoPython(CarteiraMercado)
	}
}

func dispararTreinamentoPython(tickers []string) {
	payload := map[string]interface{}{"tickers": tickers}
	jsonData, _ := json.Marshal(payload)

	url := GetPythonEngineURL() + "/treinar/batch"
	resp, err := httpClient.Post(url, "application/json", bytes.NewBuffer(jsonData))
	if err != nil {
		log.Printf("❌ [MLOps] Falha fatal ao acionar motor de treinamento Python: %v", err)
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode == 200 || resp.StatusCode == 202 {
		log.Printf("✅ [MLOps] Ordem de treinamento aceita pelo Python.")
	} else {
		log.Printf("⚠️ [MLOps] O Python recusou a ordem de treinamento. Status HTTP: %d", resp.StatusCode)
	}
}

func AgendarDespertadorBolsa() {
	log.Println("⏰ [DESPERTADOR] Ativado! O robô verificará o Carrinho Noturno a cada 5 minutos para respeitar os diferentes fusos horários.")

	ticker := time.NewTicker(5 * time.Minute)
	defer ticker.Stop()

	for {
		<-ticker.C
		executarCarrinhoNaAbertura()
	}
}

func executarCarrinhoNaAbertura() {
	rows, err := database.Conn.Query("SELECT id, usuario_id, ticker, tipo_ordem, quantidade, preco_sugerido FROM carrinho_de_ordens WHERE status = 'PENDENTE'")
	if err != nil {
		log.Printf("❌ [DESPERTADOR] Falha ao acessar o banco de dados do carrinho: %v", err)
		return
	}
	
	type OrdemCarrinho struct {
		ID        int
		UsuarioID int
		Ticker    string
		Tipo      string
		Qtd       int
		Preco     float64
	}
	var ordens []OrdemCarrinho

	for rows.Next() {
		var o OrdemCarrinho
		if err := rows.Scan(&o.ID, &o.UsuarioID, &o.Ticker, &o.Tipo, &o.Qtd, &o.Preco); err == nil {
			ordens = append(ordens, o)
		}
	}
	rows.Close() 

	if len(ordens) == 0 {
		log.Println("🛒 [DESPERTADOR] O carrinho noturno estava vazio. Nenhuma ordem executada hoje.")
		return
	}

	log.Printf("🚀 [DESPERTADOR] Processando %d ordens engatilhadas...", len(ordens))

	for _, o := range ordens {
		if !IsMercadoAberto(o.Ticker) {
			continue
		}

		payload := map[string]interface{}{
			"usuario_id": o.UsuarioID, 
			"ticker": o.Ticker, 
			"tipo_ordem": o.Tipo, 
			"quantidade": o.Qtd, 
			"preco": o.Preco,
		}
		jsonData, _ := json.Marshal(payload)
		
		resp, err := httpClient.Post(GetGoEngineURL()+"/api/ordem", "application/json", bytes.NewBuffer(jsonData))
		
		if err == nil && resp.StatusCode == 200 {
			database.Conn.Exec("DELETE FROM carrinho_de_ordens WHERE id = $1", o.ID)
			log.Printf("✅ [DESPERTADOR] %s de %s (Cliente %d) executada e removida do carrinho!", o.Tipo, o.Ticker, o.UsuarioID)
		} else {
			log.Printf("⚠️ [DESPERTADOR] Falha ao executar %s de %s. Ordem mantida no carrinho.", o.Tipo, o.Ticker)
		}
		
		if resp != nil {
			resp.Body.Close()
		}
	}
	log.Println("🏁 [DESPERTADOR] Lote de abertura processado por completo.")
}

func ForcarFechamentoManual(ano int, mes time.Month, dia int) {
	loc, _ := time.LoadLocation("America/Sao_Paulo")
	dataManual := time.Date(ano, mes, dia, 17, 5, 0, 0, loc)
	log.Printf("🛠️ [MANUAL] Forçando snapshot retroativo para o dia %s...", dataManual.Format("2006-01-02"))
	executarSnapshotPatrimonial(dataManual)
}

func AgendarFechamentoMercado() {
	log.Println("🌇 [EOD] Tesouraria ativada. Snapshot patrimonial agendado para as 17h05 (Dias Úteis).")

	for {
		loc, _ := time.LoadLocation("America/Sao_Paulo")
		agora := time.Now().In(loc)
		alvo := time.Date(agora.Year(), agora.Month(), agora.Day(), 17, 05, 0, 0, loc)

		if agora.After(alvo) || agora.Weekday() == time.Saturday || agora.Weekday() == time.Sunday {
		    for {
		        alvo = alvo.Add(24 * time.Hour)
		        if alvo.Weekday() != time.Saturday && alvo.Weekday() != time.Sunday { break }
		    }
		}

		espera := alvo.Sub(agora)
		time.Sleep(espera)
		log.Println("📸 [EOD] Mercado fechado! Iniciando Fotografia Patrimonial de todos os clientes...")
		executarSnapshotPatrimonial(time.Now().In(loc))
	}
}

func executarSnapshotPatrimonial(dataFechamento time.Time) {
	rows, err := database.Conn.Query("SELECT usuario_id, saldo_brl, saldo_usd FROM contas_virtuais")
	if err != nil {
		log.Printf("❌ [EOD] Erro ao buscar contas: %v", err)
		return
	}

	type ContaSnapshot struct {
		ID       int
		SaldoBRL float64
		SaldoUSD float64
	}
	var contas []ContaSnapshot
	for rows.Next() {
		var c ContaSnapshot
		rows.Scan(&c.ID, &c.SaldoBRL, &c.SaldoUSD)
		contas = append(contas, c)
	}
	rows.Close()

	dataFormatada := dataFechamento.Format("2006-01-02")

	for _, conta := range contas {
		var valorAcoes float64 = 0.0
		var custoAcoes float64 = 0.0

		posRows, errPos := database.Conn.Query("SELECT ticker, quantidade_total, preco_medio, COALESCE(moeda, 'BRL') FROM posicoes_carteira WHERE usuario_id = $1", conta.ID)
        if errPos == nil {
            for posRows.Next() {
                var ticker, moeda string
                var qtd int
                var precoMedio float64
                posRows.Scan(&ticker, &qtd, &precoMedio, &moeda)

                chaveRedis := fmt.Sprintf("ticker:%s", ticker)
                val, errRedis := database.Rdb.Get(database.Ctx, chaveRedis).Result()

                precoAtual := 0.0
                if errRedis == nil {
                    var dadosAtivo map[string]interface{}
                    json.Unmarshal([]byte(val), &dadosAtivo)
                    if preco, ok := dadosAtivo["preco_atual"].(float64); ok {
                        precoAtual = preco
                    }
                }

                if precoAtual == 0 { precoAtual = precoMedio }

				taxaCambio := 1.0
				if moeda == "USD" { taxaCambio = DolarGlobal }

                valorPosicaoBRL := (float64(qtd) * precoAtual) * taxaCambio
                custoPosicaoBRL := (float64(qtd) * precoMedio) * taxaCambio

                valorAcoes += valorPosicaoBRL
                custoAcoes += custoPosicaoBRL

                queryCustodia := `
                    INSERT INTO historico_custodia_diaria (usuario_id, data_fechamento, ticker, quantidade, preco_fechamento, valor_posicao)
                    VALUES ($1, $2, $3, $4, $5, $6)
                    ON CONFLICT (usuario_id, data_fechamento, ticker)
                    DO UPDATE SET quantidade = EXCLUDED.quantidade, preco_fechamento = EXCLUDED.preco_fechamento, valor_posicao = EXCLUDED.valor_posicao;
                `
                _, errCustodia := database.Conn.Exec(queryCustodia, conta.ID, dataFormatada, ticker, qtd, precoAtual, valorPosicaoBRL)
                if errCustodia != nil {
                    log.Printf("⚠️ Erro ao salvar histórico do ativo %s para o usuário %d: %v", ticker, conta.ID, errCustodia)
                }
            }
            posRows.Close()
        }

		caixaUnificado := conta.SaldoBRL + (conta.SaldoUSD * DolarGlobal)
		patrimonioTotal := caixaUnificado + valorAcoes
		lucroDiario := valorAcoes - custoAcoes

		queryInsert := `
            INSERT INTO historico_patrimonial (usuario_id, data_fechamento, saldo_caixa, valor_acoes, patrimonio_total, lucro_diario)
            VALUES ($1, $2, $3, $4, $5, $6)
            ON CONFLICT (usuario_id, data_fechamento) 
            DO UPDATE SET saldo_caixa = EXCLUDED.saldo_caixa, valor_acoes = EXCLUDED.valor_acoes, patrimonio_total = EXCLUDED.patrimonio_total, lucro_diario = EXCLUDED.lucro_diario;
        `
		_, errIns := database.Conn.Exec(queryInsert, conta.ID, dataFormatada, caixaUnificado, valorAcoes, patrimonioTotal, lucroDiario)
		if errIns != nil {
			log.Printf("⚠️ [EOD] Erro ao gravar snapshot da conta %d: %v", conta.ID, errIns)
		}
	}
	log.Printf("✅ [EOD] Fotografia Patrimonial concluída para o dia %s!", dataFormatada)
}

func ValidarVendaComIsencao(usuarioID int, ticker string, volumeVenda float64, lucroDestaVenda float64, lucroProjetadoReinvestimento float64, moeda string) models.DecisaoFiscal {
	var isencaoEstrita bool
	errConfig := database.Conn.QueryRow("SELECT modo_isencao_fiscal_estrita FROM parametros_operacionais WHERE usuario_id = $1", usuarioID).Scan(&isencaoEstrita)
	if errConfig != nil { isencaoEstrita = true }

	if !isencaoEstrita {
		return models.DecisaoFiscal{
			Aprovada:   true,
			MotivoVeto: "✅ Aprovado: Guardião Fiscal desligado pelo Gestor (Foco em Lucro Absoluto). DARF liberado.",
		}
	}
	
	if moeda == "BRL" && (strings.HasSuffix(ticker, "11") || strings.HasSuffix(ticker, "34")) {
		return models.DecisaoFiscal{
			Aprovada:   true,
			MotivoVeto: "Ativo B3 sem isenção (FII/BDR/ETF). Fluxo normal. O imposto é inevitável.",
		}
	}

	mesAtual := time.Now().Format("2006-01") 
	var volumeJaVendido float64
	var lucroAcumuladoMes float64

	query := `
		SELECT 
			COALESCE(volume_vendas_swing, 0) AS volume_vendas,
			COALESCE(lucro_realizado_swing, 0) AS lucro_acumulado
		FROM ledger_fiscal_mensal
		WHERE usuario_id = $1 AND ano_mes = $2
	`
	
	err := database.Conn.QueryRow(query, usuarioID, mesAtual).Scan(&volumeJaVendido, &lucroAcumuladoMes)
	if err != nil { return models.DecisaoFiscal{Aprovada: true} }

	novoVolume := volumeJaVendido + volumeVenda

	limiteIsencao := 20000.00
	regraNome := "B3 (R$ 20.000)"
	
	if moeda == "USD" {
		limiteIsencao = 35000.00 
		regraNome = "EUA (R$ 35.000)"
	}

	if novoVolume <= limiteIsencao {
		return models.DecisaoFiscal{
			Aprovada:   true,
			MotivoVeto: fmt.Sprintf("✅ Aprovado: Volume projetado (R$ %.2f) continua na faixa de isenção de %s.", novoVolume, regraNome),
		}
	}

	lucroTotalProjetado := lucroAcumuladoMes + lucroDestaVenda

	if lucroTotalProjetado <= 0 {
		return models.DecisaoFiscal{
			Aprovada:   true,
			MotivoVeto: fmt.Sprintf("✅ Aprovado: Limite de %s rompido, mas há prejuízo acumulado. IR = 0.", regraNome),
		}
	}

	impostoGerado := lucroTotalProjetado * 0.15 

	if lucroProjetadoReinvestimento > impostoGerado {
		return models.DecisaoFiscal{
			Aprovada:         true,
			ImpostoProjetado: impostoGerado,
			MotivoVeto:       fmt.Sprintf("⚠️ Aprovado com Ressalvas: Isenção quebrada (DARF/GCAP R$ %.2f), mas o lucro projetado supera o custo fiscal.", impostoGerado),
		}
	}

	return models.DecisaoFiscal{
		Aprovada:         false,
		ImpostoProjetado: impostoGerado,
		MotivoVeto:       fmt.Sprintf("🛑 HOLD FISCAL: Vender estourará o limite de %s e gerará R$ %.2f de DARF. Lucro não compensa. Ativo retido.", regraNome, impostoGerado),
	}
}

func processarFundamentosYahoo(ticker string) {
	temNumero := strings.ContainsAny(ticker, "0123456789")
	tickerFormatado := ticker
	if temNumero && !strings.HasSuffix(ticker, ".SA") { tickerFormatado = ticker + ".SA" }

	urlFundamentos := fmt.Sprintf("https://query2.finance.yahoo.com/v10/finance/quoteSummary/%s?modules=financialData,defaultKeyStatistics,assetProfile,quoteType", tickerFormatado)

	req, err := http.NewRequest("GET", urlFundamentos, nil)
	if err != nil { return }
	req.Header.Set("User-Agent", "Mozilla/5.0")

	resp, err := httpClient.Do(req)
	if err != nil || resp.StatusCode != 200 { return }
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err == nil && len(body) > 100 {
		var validaJson map[string]interface{}
		if errJson := json.Unmarshal(body, &validaJson); errJson == nil {
			if _, ok := validaJson["quoteSummary"]; ok {
				chaveRedis := fmt.Sprintf("fund:%s", ticker)
				database.Rdb.Set(database.Ctx, chaveRedis, body, 24*time.Hour)
				log.Printf("📊 [FUNDAMENTOS] Perfil institucional e balanço salvos na RAM para %s", ticker)
			}
		} else {
			log.Printf("⚠️ [FUNDAMENTOS] Yahoo bloqueou a requisição direta de %s. Mantendo dados em cache.", ticker)
		}
	}
}

func min(a, b int) int {
	if a < b { return a }
	return b
}

// ======================================================================
// ⚡ MOTOR FAST-TRACK: MARCAÇÃO A MERCADO (MtM) EM TEMPO REAL
// ======================================================================

func AtualizarPrecosMtM() {
	rows, err := database.Conn.Query("SELECT DISTINCT ticker FROM posicoes_carteira")
	if err != nil { return }
	defer rows.Close()

	var tickersAtivos []string
	for rows.Next() {
		var t string
		if err := rows.Scan(&t); err == nil { tickersAtivos = append(tickersAtivos, t) }
	}

	if len(tickersAtivos) == 0 { return }

	for _, ticker := range tickersAtivos {
		tickerYF := ticker
		if !strings.HasSuffix(tickerYF, ".SA") && strings.ContainsAny(tickerYF, "0123456789") {
			tickerYF += ".SA"
		}

		url := fmt.Sprintf("https://query1.finance.yahoo.com/v8/finance/chart/%s?range=1d&interval=1m", tickerYF)
		req, _ := http.NewRequest("GET", url, nil)
		req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")

		resp, err := httpClient.Do(req)
		if err == nil && resp.StatusCode == 200 {
			defer resp.Body.Close()
			var yfData struct {
				Chart struct {
					Result []struct {
						Meta struct {
							RegularMarketPrice float64 `json:"regularMarketPrice"`
							Currency           string  `json:"currency"`
						} `json:"meta"`
					} `json:"result"`
				} `json:"chart"`
			}

			if err := json.NewDecoder(resp.Body).Decode(&yfData); err == nil && len(yfData.Chart.Result) > 0 {
				precoReal := yfData.Chart.Result[0].Meta.RegularMarketPrice

				if precoReal > 0 {
					chaveRedis := fmt.Sprintf("ticker:%s", ticker)
					valAtual, errR := database.Rdb.Get(database.Ctx, chaveRedis).Result()
								
					dados := make(map[string]interface{})
					if errR == nil { json.Unmarshal([]byte(valAtual), &dados) }
				
					dados["preco_atual"] = precoReal
					dados["ticker"] = ticker
				
					novoJson, _ := json.Marshal(dados)
					database.Rdb.Set(database.Ctx, chaveRedis, novoJson, 24*time.Hour)
				}
			}
		}
		time.Sleep(300 * time.Millisecond)
	}
	log.Println("⚡ [MtM] Marcação a Mercado ao vivo atualizada para todos os clientes!")
}

func LoopMonitoramentoMtM() {
	for {
		loc, _ := time.LoadLocation("America/Sao_Paulo")
		agora := time.Now().In(loc)
		hora := agora.Hour()

		if agora.Weekday() != time.Saturday && agora.Weekday() != time.Sunday && hora >= 10 && hora < 18 {
			AtualizarPrecosMtM()
		}
		time.Sleep(1 * time.Minute)
	}
}

// ======================================================================
// 🏦 MOTOR DE RENTABILIDADE (OVERNIGHT / CDI)
// ======================================================================

func AgendarRendimentoCaixaCDI() {
	log.Println("🏦 [TESOURARIA] Motor de CDI ativado. Rendimento do caixa agendado para as 23:50 (Dias Úteis).")
	for {
		loc, _ := time.LoadLocation("America/Sao_Paulo")
		agora := time.Now().In(loc)
		alvo := time.Date(agora.Year(), agora.Month(), agora.Day(), 23, 50, 0, 0, loc)

		if agora.After(alvo) || agora.Weekday() == time.Saturday || agora.Weekday() == time.Sunday {
			for {
				alvo = alvo.Add(24 * time.Hour)
				if alvo.Weekday() != time.Saturday && alvo.Weekday() != time.Sunday { break }
			}
		}
		time.Sleep(alvo.Sub(agora))
		log.Println("🌙 [TESOURARIA] Madrugada chegou! Aplicando juros do CDI no caixa em BRL dos clientes...")
		executarRendimentoCDI()
	}
}

func executarRendimentoCDI() {
	taxaDiaria := math.Pow(1.0+TaxaSelicGlobal, 1.0/252.0) - 1.0
	tx, err := database.Conn.Begin()
	if err != nil { return }

	rows, err := tx.Query("SELECT usuario_id, saldo_brl FROM contas_virtuais WHERE saldo_brl > 0")
	if err != nil {
		tx.Rollback()
		return
	}

	type ClienteCDI struct {
		ID    int
		Saldo float64
	}
	var clientes []ClienteCDI
	for rows.Next() {
		var c ClienteCDI
		rows.Scan(&c.ID, &c.Saldo)
		clientes = append(clientes, c)
	}
	rows.Close()

	for _, c := range clientes {
		rendimento := c.Saldo * taxaDiaria
		_, err = tx.Exec("UPDATE contas_virtuais SET saldo_brl = saldo_brl + $1 WHERE usuario_id = $2", rendimento, c.ID)
		if err != nil { continue }

		historico := fmt.Sprintf("Rendimento Diário de Caixa (Overnight a %.2f%% a.a.)", TaxaSelicGlobal*100)
		tx.Exec(`INSERT INTO lancamentos_contabeis (usuario_id, data_liquidacao, conta_debito, conta_credito, valor, historico) 
                 VALUES ($1, CURRENT_DATE, 'TESOURARIA_BANCO', 'RENDIMENTO_CDI', $2, $3)`, c.ID, rendimento, historico)
	}
	tx.Commit()
	log.Printf("✅ [CDI] Rendimento diário de %.5f%% aplicado a %d contas com sucesso!", taxaDiaria*100, len(clientes))
}

// ======================================================================
// ⚔️ MOTOR TÁTICO: BUY THE DIP (COMPRA NA BAIXA COM CAIXA LIVRE)
// ======================================================================

func ExecutarBuyTheDipOportunista(usuarioID int, ticker string, precoAtual float64, zScore float64, saldoDisponivelBRL float64) {
	if zScore > -2.0 || saldoDisponivelBRL < 5000.0 { return }

	loc, _ := time.LoadLocation("America/Sao_Paulo")
	hora := time.Now().In(loc).Hour()

	if hora < 14 || hora >= 17 { return }

	municaoTatica := saldoDisponivelBRL * 0.05
	loteCompra := int(math.Floor(municaoTatica / precoAtual))

	if loteCompra > 0 {
		log.Printf("⚔️ [BUY THE DIP] Oportunidade de Pânico detectada em %s (Z-Score: %.2f). Alocando 5%% do caixa (R$ %.2f) para compra na baixa (User %d)...", ticker, zScore, municaoTatica, usuarioID)
		rotearOrdem(usuarioID, ticker, "COMPRA", loteCompra, precoAtual, true)
	}
}

// IsMercadoAberto verifica o fuso horário e a jurisdição do ativo para liberar a operação
func IsMercadoAberto(ticker string) bool {
	locBR, _ := time.LoadLocation("America/Sao_Paulo")
	agoraBR := time.Now().In(locBR)

	if agoraBR.Weekday() == time.Saturday || agoraBR.Weekday() == time.Sunday { return false }

	isMercadoAmericano := !strings.ContainsAny(ticker, "0123456789") && !strings.HasSuffix(ticker, ".SA")

	if isMercadoAmericano {
		locNY, _ := time.LoadLocation("America/New_York")
		agoraNY := time.Now().In(locNY)
		
		horaNY := agoraNY.Hour()
		minutoNY := agoraNY.Minute()
		tempoDecimalNY := float64(horaNY) + float64(minutoNY)/60.0

		return tempoDecimalNY >= 4.0 && tempoDecimalNY < 20.0
	}

	horaBR := agoraBR.Hour()
	minutoBR := agoraBR.Minute()
	tempoDecimalBR := float64(horaBR) + float64(minutoBR)/60.0
	
	return tempoDecimalBR >= 10.0 && tempoDecimalBR < 18.0
}

func AgendarColetaFundamentosDiaria() {
	for {
		agora := time.Now()
		alvo := time.Date(agora.Year(), agora.Month(), agora.Day(), 19, 0, 0, 0, agora.Location())

		if agora.After(alvo) { alvo = alvo.Add(24 * time.Hour) }

		espera := alvo.Sub(agora)
		log.Printf("🏢 [CRONJOB] Varredura Fundamentalista agendada para: %v", alvo.Format("02/01/2006 15:04:05"))

		time.Sleep(espera)
		log.Println("🚀 [CRONJOB] Iniciando extração de Dívida, Margem e Múltiplos...")
		
		cmd := exec.Command("python", "/app/motor_python/coletor_fundamentos.py")
		saida, err := cmd.CombinedOutput()
		if err != nil {
			log.Printf("❌ [CRONJOB] Erro ao atualizar fundamentos: %s\nSaída: %s", err, string(saida))
		} else {
			log.Printf("✅ [CRONJOB] Fundamentos atualizados no Redis com sucesso!\n%s", string(saida))
		}
	}
}

// ======================================================================
// ⏰ 1. MONITORES DE ZERAGEM DE DAY TRADE (16:45 BRT e 19:45 ET)
// ======================================================================

func AgendarZeragemDayTradeB3() {
	log.Println("⏱️ [DAY TRADE B3] Monitor ativado. Zeragem compulsória do Brasil às 16:45 BRT.")
	for {
		loc, _ := time.LoadLocation("America/Sao_Paulo")
		agora := time.Now().In(loc)
		alvo := time.Date(agora.Year(), agora.Month(), agora.Day(), 16, 45, 0, 0, loc)

		if agora.After(alvo) || agora.Weekday() == time.Saturday || agora.Weekday() == time.Sunday {
			for {
				alvo = alvo.Add(24 * time.Hour)
				if alvo.Weekday() != time.Saturday && alvo.Weekday() != time.Sunday { break }
			}
		}
		time.Sleep(alvo.Sub(agora))
		log.Println("🚨 [DAY TRADE B3] 16:45! Zerando posições expostas no Brasil (BRL)...")
		executarZeragemAgressivos("BRL")
	}
}

func AgendarZeragemDayTradeUSA() {
	log.Println("⏱️ [DAY TRADE EUA] Monitor ativado. Zeragem compulsória de Wall St. às 19:45 ET.")
	for {
		loc, _ := time.LoadLocation("America/New_York")
		agora := time.Now().In(loc)
		alvo := time.Date(agora.Year(), agora.Month(), agora.Day(), 19, 45, 0, 0, loc)

		if agora.After(alvo) || agora.Weekday() == time.Saturday || agora.Weekday() == time.Sunday {
			for {
				alvo = alvo.Add(24 * time.Hour)
				if alvo.Weekday() != time.Saturday && alvo.Weekday() != time.Sunday { break }
			}
		}
		time.Sleep(alvo.Sub(agora))
		log.Println("🚨 [DAY TRADE EUA] 19:45 ET! Zerando posições expostas nos Estados Unidos (USD)...")
		executarZeragemAgressivos("USD")
	}
}

func executarZeragemAgressivos(jurisdicao string) {
	rows, err := database.Conn.Query("SELECT usuario_id FROM contas_virtuais WHERE perfil_risco = 'Agressivo'")
	if err != nil { return }
	defer rows.Close()

	var ids []int
	for rows.Next() {
		var id int
		rows.Scan(&id)
		ids = append(ids, id)
	}

	for _, uid := range ids {
		posRows, _ := database.Conn.Query("SELECT ticker, quantidade_total, COALESCE(moeda, 'BRL') FROM posicoes_carteira WHERE usuario_id = $1", uid)
		for posRows.Next() {
			var ticker, moeda string
			var qtd int
			posRows.Scan(&ticker, &qtd, &moeda)

			if moeda != jurisdicao { continue }

			chaveRedis := fmt.Sprintf("ticker:%s", ticker)
			val, errRedis := database.Rdb.Get(database.Ctx, chaveRedis).Result()
			precoVendaLimitada := 0.0
			volumeZScore := 0.0
			zScore := 0.0
			
			if errRedis == nil {
				var dados map[string]interface{}
				json.Unmarshal([]byte(val), &dados)
				
				if ask, ok := dados["ask"].(float64); ok && ask > 0 {
					precoVendaLimitada = ask
				} else if p, ok := dados["preco_atual"].(float64); ok {
					precoVendaLimitada = p * 1.001 
				}

				if vz, ok := dados["volume_zscore"].(float64); ok { volumeZScore = vz }
				if zs, ok := dados["z_score"].(float64); ok { zScore = zs }
			}

			// 👇 A PROTEÇÃO CONTRA A PERDA DO OVERNIGHT DRIFT 👇
			if volumeZScore >= 1.5 || zScore <= -2.0 {
				log.Printf("🛡️ [HOLD INTELIGENTE] O ativo %s apresentou forte assimetria no fechamento. Perfil Agressivo levará a posição para amanhã!", ticker)
				continue 
			}

			if qtd > 0 && precoVendaLimitada > 0 {
				rotearOrdem(uid, ticker, "VENDA_LIMITADA", qtd, precoVendaLimitada, true)
				log.Printf("🧊 [DAY TRADE SMART] %d cotas de %s (User %d) enviadas à Pedra (Limitada) a %s %.2f.", qtd, ticker, uid, jurisdicao, precoVendaLimitada)
			}
		}
		posRows.Close()
	}
}

// ======================================================================
// 🧹 2. MOTORES MARKET-ON-CLOSE (LIMPEZA DE LEILÃO EM D+0)
// ======================================================================

func AgendarVarreduraLeilaoFechamentoB3() {
	log.Println("🧹 [MOC B3] Varredura agendada para 16:54 BRT.")
	for {
		loc, _ := time.LoadLocation("America/Sao_Paulo")
		agora := time.Now().In(loc)
		alvo := time.Date(agora.Year(), agora.Month(), agora.Day(), 16, 54, 0, 0, loc)

		if agora.After(alvo) || agora.Weekday() == time.Saturday || agora.Weekday() == time.Sunday {
			for {
				alvo = alvo.Add(24 * time.Hour)
				if alvo.Weekday() != time.Saturday && alvo.Weekday() != time.Sunday { break }
			}
		}
		time.Sleep(alvo.Sub(agora))
		log.Println("🔥 [MOC B3] 16:54! Forçando liquidação a mercado no Brasil...")
		executarMarketOnClose("BRL")
	}
}

func AgendarVarreduraLeilaoFechamentoUSA() {
	log.Println("🧹 [MOC EUA] Varredura agendada para 19:54 ET.")
	for {
		loc, _ := time.LoadLocation("America/New_York")
		agora := time.Now().In(loc)
		alvo := time.Date(agora.Year(), agora.Month(), agora.Day(), 19, 54, 0, 0, loc)

		if agora.After(alvo) || agora.Weekday() == time.Saturday || agora.Weekday() == time.Sunday {
			for {
				alvo = alvo.Add(24 * time.Hour)
				if alvo.Weekday() != time.Saturday && alvo.Weekday() != time.Sunday { break }
			}
		}
		time.Sleep(alvo.Sub(agora))
		log.Println("🔥 [MOC EUA] 19:54 ET! Forçando liquidação a mercado nos EUA...")
		executarMarketOnClose("USD")
	}
}

func executarMarketOnClose(jurisdicao string) {
	rows, err := database.Conn.Query("SELECT usuario_id FROM contas_virtuais WHERE perfil_risco = 'Agressivo'")
	if err != nil { return }
	defer rows.Close()

	var ids []int
	for rows.Next() {
		var id int
		rows.Scan(&id)
		ids = append(ids, id)
	}

	for _, uid := range ids {
		posRows, _ := database.Conn.Query("SELECT ticker, quantidade_total, COALESCE(moeda, 'BRL') FROM posicoes_carteira WHERE usuario_id = $1", uid)
		for posRows.Next() {
			var ticker, moeda string
			var qtd int
			posRows.Scan(&ticker, &qtd, &moeda)

			if moeda != jurisdicao { continue }

			if qtd > 0 {
				chaveRedis := fmt.Sprintf("ticker:%s", ticker)
				val, errRedis := database.Rdb.Get(database.Ctx, chaveRedis).Result()
				precoMercado := 0.0
				volumeZScore := 0.0
				zScore := 0.0
				
				if errRedis == nil {
					var dados map[string]interface{}
					json.Unmarshal([]byte(val), &dados)
					if p, ok := dados["preco_atual"].(float64); ok {
						precoMercado = p
					}
					if vz, ok := dados["volume_zscore"].(float64); ok { volumeZScore = vz }
					if zs, ok := dados["z_score"].(float64); ok { zScore = zs }
				}

				if volumeZScore >= 1.5 || zScore <= -2.0 {
					continue 
				}

				if precoMercado > 0 {
					rotearOrdem(uid, ticker, "VENDA", qtd, precoMercado, true)
					log.Printf("🔥 [MOC %s] Posição de %d cotas de %s (User %d) liquidada A MERCADO!", jurisdicao, qtd, ticker, uid)
				}
			}
		}
		posRows.Close()
	}
}