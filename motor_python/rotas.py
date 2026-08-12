import pandas as pd
import numpy as np
import riskfolio as rp
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

router = APIRouter()

# Modelo esperado via POST do Golang
class PayloadOtimizacao(BaseModel):
    usuario_id: int
    precos_historicos: dict  # Preços de fechamento diário dos ativos

@router.post("/api/otimizar/hrp")
def otimizar_carteira_hrp(payload: PayloadOtimizacao):
    try:
        # 1. Converter o JSON recebido em DataFrame do Pandas
        df_precos = pd.DataFrame(payload.precos_historicos)
        
        # 2. Calcular os retornos diários (diferenciação fracionária ou simples)
        retornos = df_precos.pct_change().dropna()
        
        if retornos.empty:
            raise HTTPException(status_code=400, detail="Série temporal insuficiente.")

        # 3. Construir o objeto de Portfólio do Riskfolio
        port = rp.HCPortfolio(returns=retornos)

        # 4. Configurar os parâmetros do modelo HRP
        modelo = 'HRP'           # Hierarchical Risk Parity
        correlacao = 'pearson'   # Tipo de correlação (pode usar 'spearman' também)
        medida_risco = 'MV'      # MV = Variância (Risco clássico), pode usar 'CVaR' (Risco de Cauda)

        # 5. Executar o motor matemático
        pesos_otimizados = port.optimization(
            model=modelo,
            codependence=correlacao,
            rm=medida_risco,
            rf=0.0,              # Taxa livre de risco (Risk Free Rate)
            linkage='single',    # Algoritmo de agrupamento hierárquico
            leaf_order=True
        )

        # 6. Formatar a saída
        # Transforma a série do pandas em um dicionário: {"PETR4.SA": 0.15, "VALE3.SA": 0.12}
        pesos_dict = pesos_otimizados['weights'].to_dict()

        # Opcional: Arredondar e filtrar poeira estatística (pesos menores que 0.5%)
        alocacao_limpa = {
            ativo: round(peso * 100, 2) 
            for ativo, peso in pesos_dict.items() 
            if peso > 0.005
        }

        # Retorna o payload final que o Go consumirá para exibir no React
        return {
            "sucesso": True,
            "metodo": "Hierarchical Risk Parity (HRP)",
            "alocacao_ideal_perc": alocacao_limpa
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Erro na engenharia de matrizes: {str(e)}")