import os
import logging
from rl_trader import treinar_e_salvar_agente_rl
from lstm_predictor import treinar_e_salvar_lstm
import yfinance as yf

logging.basicConfig(level=logging.INFO, format='%(message)s')

# Pegue alguns ativos do seu log que estão dando erro para testar
ativos_alvo = [
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

def aquecer_modelos():
    os.makedirs("modelos_salvos", exist_ok=True)
    logging.info("🔥 Iniciando o Aquecimento Global das IAs...")

    for ticker in ativos_alvo:
        logging.info(f"\n======================================")
        logging.info(f"🤖 Treinando Agentes para: {ticker}")
        logging.info(f"======================================")
        
        try:
            # 1. Treina o PPO (Reinforcement Learning)
            res_rl = treinar_e_salvar_agente_rl(ticker)
            if not res_rl.get("sucesso"):
                logging.error(f"❌ Erro RL {ticker}: {res_rl.get('erro')}")

            # 2. Treina a LSTM (Requer baixar histórico para o lookback)
            ticker_sa = f"{ticker}.SA" if not ticker.endswith(".SA") else ticker
            df = yf.download(ticker_sa, period="1y", interval="1d", progress=False, threads=False)
            
            if not df.empty and len(df) >= 100:
                fechamentos = df['Close'].dropna().values.flatten().tolist()
                res_lstm = treinar_e_salvar_lstm(ticker, fechamentos)
                if not res_lstm.get("sucesso"):
                    logging.error(f"❌ Erro LSTM {ticker}: {res_lstm.get('erro')}")
            else:
                logging.warning(f"⚠️ Histórico insuficiente para LSTM de {ticker}")
                
        except Exception as e:
            logging.error(f"🔥 Falha catastrófica em {ticker}: {e}")

    logging.info("\n✅ Aquecimento Concluído! O FastAPI agora encontrará os arquivos.")

if __name__ == "__main__":
    aquecer_modelos()