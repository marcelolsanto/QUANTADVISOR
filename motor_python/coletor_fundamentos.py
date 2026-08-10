import json
import time
import concurrent.futures
import redis
import yfinance as yf

# Conexão com o Banco de Dados em Memória (Rede Docker)
rdb = redis.Redis(host='quant_redis', port=6379, db=0, decode_responses=True)

# ==============================================================================
# CARTEIRA GLOBAL DE MERCADO
# ==============================================================================
ativos = [
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
	"AAPL", "MSFT", "GOOGL", "GOOG", "AMZN", "NVDA", "TSLA", "META", "NFLX", "AMD", 
	"INTC", "TSM", "AVGO", "QCOM", "ASML", "ADBE", "CRM", "ORCL", "CSCO", "IBM", 
	"TXN", "AMAT", "MU", "LRCX", "SNOW", "PLTR", "UBER", "ABNB", "PANW", "CDNS", 
	"ADSK", "SHOP", "SQ", "PYPL", "TXN", "SHOP", "NOW", "INTU", "PYPL", "GME", 
	"AMC", "BABA", "NIO", "DIS", "LCID", "SNDL", "ROKU", "COIN", "HOOD", "RBLX",
	"BRK-B", "JPM", "V", "MA", "BAC", "WFC", "GS", "MS", "AXP", "C", 
	"BLK", "SCHW", "PNC", "USB", "TFC", "SPGI", "MCO", "CB", "PGR", "TRV", 
	"AIG", "PYPL", "COF", "DFS", "BK", "STT", "ALL", "MET", "PRU", "AFL",
	"UNH", "JNJ", "LLY", "ABBV", "MRK", "PFE", "TMO", "ABT", "DHR", "BMY", 
	"AMGN", "CVS", "CI", "ISRG", "GILD", "VRTX", "REGN", "ZTS", "BDX", "SYK", 
	"BSX", "MDT", "ELV", "HUM", "CNC", "BAX", "DXCM", "ILMN", "IDXX", "IQV",
	"HD", "MCD", "NKE", "LOW", "SBUX", "BKNG", "TJX", "MAR", "ORLY", "GM", 
	"F", "CMG", "AZO", "YUM", "HLT", "DHI", "LEN", "TSCO", "ROST", "EBAY",
	"WMT", "PG", "KO", "PEP", "COST", "PM", "MO", "CL", "EL", "KDP", 
	"GIS", "SYY", "MDLZ", "STZ", "HSY", "KHC", "GIS", "ADM", "TSN", "CAG",
	"CAT", "DE", "UNP", "HON", "UPS", "LMT", "RTX", "BA", "GE", "GD", 
	"NOC", "ETN", "ITW", "CSX", "NSC", "WM", "MMM", "FDX", "PH", "CTAS", 
	"CPRT", "PCAR", "FAST", "URI", "ODFL", "DAL", "UAL", "LUV", "ALK", "JBHT",
	"XOM", "CVX", "COP", "SLB", "EOG", "MPC", "PSX", "VLO", "OXY", "KMI", 
	"WMB", "HAL", "BKR", "DVN", "FANG", "MRO", "TRGP", "EQT", "APA", "CTRA",
	"CMCSA", "VZ", "T", "TMUS", "EA", "TTWO", "NEE", "DUK", "SO", "D", 
	"AEP", "SRE", "EXC", "XEL", "ED", "PEG", "WEC", "ES", "AWK", "ETR",
	"PLD", "AMT", "EQIX", "CCI", "SPG", "O", "VICI", "PSA", "EXR", "WELL", 
	"AVB", "EQR", "DLR", "SBAC", "CBRE", "WY", "VTR", "ARE", "MAA", "ESS",
	"SPY", "QQQ", "DIA", "VTI", "VOO", "IWM", "TLT", "EEM", "EFA", "ARKK", 
	"XLE", "XLF", "XLK", "XLV", "XLY", "XLP", "XLI", "XLU", "XLRE", "XLB",
	"PETR4", "VALE3", "ITUB4", "MGLU3", "B3SA3", "BBAS3", "BBDC4", "BBSE3", 
	"BRFS3", "CCRO3", "CMIG4", "CPFE3", "CPLE6", "CRFB3", "CSAN3", "CSNA3", 
	"CYRE3", "ELET3", "ELET6", "EMBR3", "ENEV3", "ENGI11", "EQTL3", "FLRY3", 
	"GGBR4", "GOAU4", "HAPV3", "HYPE3", "JBSS3", "KLBN11", "LREN3", "LWSA3", 
	"MRFG3", "MRVE3", "MULT3", "NTCO3", "PCAR3", "PETR3", "PRIO3", "RADL3", 
	"RAIL3", "RENT3", "SANB11", "SBSP3", "SLCE3", "SMTO3", "SUZB3", "TAEE11", 
	"TIMS3", "TOTS3", "UGPA3", "USIM5", "VALE3", "VBBR3", "WEGE3", "YDUQ3",
	"BOVA11", "IVVB11", "SMAL11", "DIVO11", "HASH11", "NASD11", "XINA11", 
	"KNRI11", "HGLG11", "XPLG11",
    "ROBO", "SPCU", "STRC", "MCLmain", "M2KM6", "SCHP",
]

# Dicionário para traduzir os setores produtivos do Yahoo
TRADUCAO_SETOR = {
    "Technology": "Tecnologia",
    "Financial Services": "Serviços Financeiros",
    "Healthcare": "Saúde e Fármacos",
    "Consumer Cyclical": "Consumo Cíclico (Varejo)",
    "Industrials": "Indústria Pesada",
    "Energy": "Energia (Petróleo e Gás)",
    "Basic Materials": "Materiais Básicos (Mineração)",
    "Consumer Defensive": "Consumo Defensivo (Bens Essenciais)",
    "Utilities": "Utilidade Pública (Saneamento e Eletricidade)",
    "Real Estate": "Setor Imobiliário (REITs e Incorporadoras)",
    "Communication Services": "Serviços de Comunicação"
}

def processar_ativo(ticker):
    try:
        tem_numero = any(char.isdigit() for char in ticker)
        ticker_yf = f"{ticker}.SA" if tem_numero and not ticker.endswith('.SA') else ticker
        
        ativo = yf.Ticker(ticker_yf)
        info = ativo.info
        
        # Extração de Identidade
        nome = info.get("longName") or info.get("shortName") or ticker
        setor_en = info.get("sector", "Setor Não Especificado")
        setor = TRADUCAO_SETOR.get(setor_en, setor_en)
        
        industria = info.get("industry", "Indústria Global")
        empregados = info.get("fullTimeEmployees", "N/A")
        
        # Extração de Residência e História
        pais = info.get("country", "N/A")
        cidade = info.get("city", "N/A")
        site = info.get("website", "N/A")
        resumo_en = info.get("longBusinessSummary", "Resumo executivo não disponível.")
        
        # Montando o Dossiê Institucional que aparecerá no React
        resumo_formatado = f"📍 Residência Fiscal (Sede): {cidade}, {pais}\n🌐 Site Oficial: {site}\n\n📖 História e Operação:\n{resumo_en}"
        
        # Métricas Financeiras
        ebitda = info.get("ebitda", 1)
        total_debt = info.get("totalDebt", 0)
        margem = info.get("profitMargins", 0)
        
        # Molde JSON que o Frontend React espera receber
        dados_mock = {
            "quoteSummary": {
                "result": [{
                    "quoteType": {
                        "longName": nome
                    },
                    "assetProfile": {
                        "sector": setor,
                        "industry": industria,
                        "fullTimeEmployees": empregados,
                        "longBusinessSummary": resumo_formatado
                    },
                    "financialData": {
                        "totalDebt": {"raw": total_debt}, 
                        "ebitda": {"raw": ebitda},
                        "profitMargins": {"raw": margem}
                    }
                }]
            }
        }
        
        rdb.set(f"fund:{ticker}", json.dumps(dados_mock))
        print(f"✅ Sucesso: {ticker.ljust(6)} -> {nome} ({setor})")
        
    except Exception as e:
        print(f"❌ Erro em {ticker}: Não foi possível resgatar o perfil institucional.")

def rodar():
    print(f"🚀 Iniciando extração do Raio-X Institucional para {len(ativos)} empresas...")
    # Usando Multi-Threading para baixar as 400 empresas de Wall Street e B3 em poucos segundos
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
        executor.map(processar_ativo, ativos)
    print("🏁 Ingestão concluída! Pode abrir o painel do React.")

if __name__ == "__main__":
    rodar()