import os
import sys
import json
import torch
import joblib 
import warnings
import numpy as np
import pandas as pd
import torch.nn as nn
import torch.optim as optim
import redis
from sklearn.preprocessing import MinMaxScaler

warnings.filterwarnings('ignore')

# =====================================================================
# POOL REDIS E CACHE DE MODELOS (OTIMIZAÇÃO HFT)
# =====================================================================
REDIS_POOL = redis.ConnectionPool(host='quant_redis', port=6379, db=0, decode_responses=True)
rdb_global = redis.Redis(connection_pool=REDIS_POOL)

# Cache em RAM para evitar leitura de SSD a cada milissegundo na inferência
MODELOS_CACHE = {}
SCALERS_CACHE_X = {}
SCALERS_CACHE_Y = {}

# =====================================================================
# ARQUITETURA DA REDE NEURAL (PYTORCH)
# =====================================================================
class LSTMModel(nn.Module):
    def __init__(self, input_size=1, hidden_layer_size=50, num_layers=2, output_size=1, dropout=0.2):
        super(LSTMModel, self).__init__()
        self.hidden_layer_size = hidden_layer_size
        self.num_layers = num_layers
        self.lstm = nn.LSTM(input_size, hidden_layer_size, num_layers, batch_first=True, dropout=dropout)
        self.linear = nn.Linear(hidden_layer_size, output_size)

    def forward(self, input_seq):
        h_0 = torch.zeros(self.num_layers, input_seq.size(0), self.hidden_layer_size).to(input_seq.device)
        c_0 = torch.zeros(self.num_layers, input_seq.size(0), self.hidden_layer_size).to(input_seq.device)
        lstm_out, _ = self.lstm(input_seq, (h_0, c_0))
        ultimo_passo = lstm_out[:, -1, :]
        previsao = self.linear(ultimo_passo)
        return previsao

# =====================================================================
# 1. FUNÇÃO DE INFERÊNCIA RÁPIDA (Com Cache em RAM)
# =====================================================================
def prever_lstm_rapido(ticker, historico_precos, historico_volumes, volume_atual, ibov_atual, dolar_atual, selic_atual, lookback=20):
    caminho_modelo = f"modelos_salvos/lstm_{ticker}.pth"
    caminho_scaler_X = f"modelos_salvos/scaler_X_{ticker}.save"
    caminho_scaler_y = f"modelos_salvos/scaler_y_{ticker}.save"
    
    try:
        if not historico_precos or len(historico_precos) < lookback:
            return {"sucesso": False, "erro": "Histórico insuficiente para criar o Tensor."}

        # Carrega no Cache se não existir (Leitura de Disco apenas 1x)
        if ticker not in MODELOS_CACHE:
            if not os.path.exists(caminho_modelo) or not os.path.exists(caminho_scaler_X):
                return {"sucesso": False, "erro": f"Modelo para {ticker} não treinado."}
            
            # Carrega e salva os scalers na RAM
            SCALERS_CACHE_X[ticker] = joblib.load(caminho_scaler_X)
            SCALERS_CACHE_Y[ticker] = joblib.load(caminho_scaler_y)
            
            # 🚀 NOVA ESTRUTURA DE FEATURES (Volume Z-Score Injetado)
            features_treino = ['Close', 'Retorno', 'Volume_Z', 'IBOV', 'Dolar', 'Selic']
            device = torch.device("cuda" if torch.cuda.is_available() else "mps" if torch.backends.mps.is_available() else "cpu")
            
            modelo = LSTMModel(input_size=len(features_treino), hidden_layer_size=50, num_layers=2, output_size=1).to(device)
            modelo.load_state_dict(torch.load(caminho_modelo, map_location=device, weights_only=True))
            modelo.eval()
            MODELOS_CACHE[ticker] = {"modelo": modelo, "device": device}

        scaler_features = SCALERS_CACHE_X[ticker]
        scaler_target = SCALERS_CACHE_Y[ticker]
        modelo_info = MODELOS_CACHE[ticker]
        modelo = modelo_info["modelo"]
        device = modelo_info["device"]

        # Montagem do Tensor com a dimensão de Fluxo
        df = pd.DataFrame({'Close': historico_precos, 'Volume': historico_volumes})
        
        # Atualiza o último volume com o tick ao vivo do painel
        if volume_atual > 0:
            df.loc[df.index[-1], 'Volume'] = volume_atual
            
        df['Retorno'] = df['Close'].pct_change()
        
        # 🧠 O SEGREDO DO HFT: Z-Score de Volume (Evita Exploding Gradients)
        std_volume = df['Volume'].rolling(lookback).std().replace(0, 1) # Previne divisão por zero
        df['Volume_Z'] = (df['Volume'] - df['Volume'].rolling(lookback).mean()) / std_volume
        df['Volume_Z'] = df['Volume_Z'].fillna(0).clip(-3.0, 3.0) # Limita os picos extremos
        
        df['IBOV'] = ibov_atual
        df['Dolar'] = dolar_atual
        df['Selic'] = selic_atual
        
        df.ffill(inplace=True); df.bfill(inplace=True)
        df.replace([np.inf, -np.inf], 0, inplace=True)
        
        features = ['Close', 'Retorno', 'Volume_Z', 'IBOV', 'Dolar', 'Selic']
        matriz_dados = df[features].values
        preco_atual = float(df['Close'].iloc[-1])
        
        dados_escalados_X = scaler_features.transform(matriz_dados)

        ultimos_dias = dados_escalados_X[-lookback:]
        X_teste = np.reshape(np.array([ultimos_dias]), (1, lookback, len(features)))
        X_teste_tensor = torch.tensor(X_teste, dtype=torch.float32).to(device)

        with torch.no_grad():
            retorno_predito_escalado = modelo(X_teste_tensor).cpu().numpy()

        retorno_predito = scaler_target.inverse_transform(retorno_predito_escalado)[0][0]
        
        variacao_esperada = float(retorno_predito) * 100
        preco_alvo = preco_atual * (1.0 + float(retorno_predito))
        
        sinal = "COMPRAR (Viés de Alta)" if variacao_esperada > 0.5 else "VENDER (Viés de Baixa)" if variacao_esperada < -0.5 else "NEUTRO (Consolidação)"
        return {
            "sucesso": True, "ticker": ticker, "preco_atual": round(preco_atual, 2),
            "previsao_t1": round(preco_alvo, 2), "variacao_projetada_perc": round(variacao_esperada, 2),
            "sinal_rede_neural": sinal, "detalhes_modelo": f"PyTorch LSTM (Em Memória / {device.type.upper()})"
        }
    except Exception as e:
        return {"sucesso": False, "erro": f"Falha na inferência LSTM: {str(e)}"}

# =====================================================================
# 2. ROTINA DE TREINAMENTO 
# =====================================================================
def treinar_e_salvar_lstm(ticker, historico_precos, historico_volumes, lookback=20, epochs=50):
    os.makedirs("modelos_salvos", exist_ok=True)
    try:
        if not historico_precos or len(historico_precos) < 100:
            return {"sucesso": False, "erro": "Histórico insuficiente."}
            
        if not historico_volumes or len(historico_volumes) != len(historico_precos):
            historico_volumes = [1.0] * len(historico_precos)

        df = pd.DataFrame({'Close': historico_precos, 'Volume': historico_volumes})
        df['Retorno'] = df['Close'].pct_change()
        df['Retorno_Alvo'] = df['Retorno'].shift(-1)
        
        # 🧠 O SEGREDO DO HFT: Z-Score de Volume
        std_volume = df['Volume'].rolling(lookback).std().replace(0, 1)
        df['Volume_Z'] = (df['Volume'] - df['Volume'].rolling(lookback).mean()) / std_volume
        df['Volume_Z'] = df['Volume_Z'].fillna(0).clip(-3.0, 3.0)
        
        tamanho_historico = len(df)
        
        try:
            ibov_str = rdb_global.get("hist:^BVSP")
            dolar_str = rdb_global.get("hist:BRL=X")
            
            ibov_full = json.loads(ibov_str) if ibov_str else [120000.0] * tamanho_historico
            dolar_full = json.loads(dolar_str) if dolar_str else [5.0] * tamanho_historico
            
            ibov_real = np.array(ibov_full[-tamanho_historico:])
            dolar_real = np.array(dolar_full[-tamanho_historico:])
            
            if len(ibov_real) < tamanho_historico:
                ibov_real = np.pad(ibov_real, (tamanho_historico - len(ibov_real), 0), 'edge')
                dolar_real = np.pad(dolar_real, (tamanho_historico - len(dolar_real), 0), 'edge')
        except Exception:
            ibov_real = np.full(tamanho_historico, 120000.0)
            dolar_real = np.full(tamanho_historico, 5.0)

        df['IBOV'] = ibov_real
        df['Dolar'] = dolar_real
        df['Selic'] = 0.1450 
        
        df.dropna(inplace=True)
        df.replace([np.inf, -np.inf], 0, inplace=True)

        features = ['Close', 'Retorno', 'Volume_Z', 'IBOV', 'Dolar', 'Selic']
        input_size = len(features)
        
        matriz_features = df[features].values
        matriz_target = df[['Retorno_Alvo']].values

        scaler_features = MinMaxScaler(feature_range=(0, 1))
        dados_escalados_X = scaler_features.fit_transform(matriz_features)
        joblib.dump(scaler_features, f"modelos_salvos/scaler_X_{ticker}.save")

        scaler_target = MinMaxScaler(feature_range=(-1, 1))
        dados_escalados_y = scaler_target.fit_transform(matriz_target)
        joblib.dump(scaler_target, f"modelos_salvos/scaler_y_{ticker}.save")

        X, y = [], []
        for i in range(lookback, len(dados_escalados_X)):
            X.append(dados_escalados_X[i-lookback:i, :])
            y.append(dados_escalados_y[i, 0])

        X, y = np.array(X), np.array(y)

        device = torch.device("cuda" if torch.cuda.is_available() else "mps" if torch.backends.mps.is_available() else "cpu")
        X_tensor = torch.tensor(X, dtype=torch.float32).to(device)
        y_tensor = torch.tensor(y, dtype=torch.float32).view(-1, 1).to(device)

        modelo = LSTMModel(input_size=input_size, hidden_layer_size=50, num_layers=2, output_size=1, dropout=0.2).to(device)
        criterio_perda = nn.MSELoss()
        otimizador = optim.Adam(modelo.parameters(), lr=0.01)

        modelo.train()
        
        use_amp = (device.type == 'cuda')
        scaler_amp = torch.amp.GradScaler() if use_amp else None

        for epoch in range(epochs):
            otimizador.zero_grad()
            if use_amp:
                with torch.autocast(device_type=device.type):
                    y_pred = modelo(X_tensor)
                    loss = criterio_perda(y_pred, y_tensor)
                scaler_amp.scale(loss).backward()
                scaler_amp.step(otimizador)
                scaler_amp.update()
            else:
                y_pred = modelo(X_tensor)
                loss = criterio_perda(y_pred, y_tensor)
                loss.backward()
                otimizador.step()

        caminho_salvamento = f"modelos_salvos/lstm_{ticker}.pth"
        torch.save(modelo.state_dict(), caminho_salvamento)
        
        if ticker in MODELOS_CACHE:
            del MODELOS_CACHE[ticker]
            
        return {"sucesso": True, "mensagem": f"Modelo {ticker} salvo e atualizado."}

    except Exception as e:
        return {"sucesso": False, "erro": f"Falha ao treinar LSTM: {str(e)}"}