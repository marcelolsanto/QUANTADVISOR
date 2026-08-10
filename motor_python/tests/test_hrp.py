import pytest
import numpy as np
import pandas as pd
from sklearn.covariance import LedoitWolf


def test_ledoit_wolf_covariance_shrinkage():
    """Valida a precisão matemática da matriz de covariância Encolhida (Ledoit-Wolf)."""
    np.random.seed(42)
    # 100 dias de retornos de 4 ativos
    retornos = np.random.normal(0, 0.02, (100, 4))
    df_retornos = pd.DataFrame(retornos, columns=["PETR4", "VALE3", "ITUB4", "WEGE3"])

    lw = LedoitWolf()
    matriz_encolhida = lw.fit(df_retornos).covariance_

    assert matriz_encolhida.shape == (4, 4)
    # A matriz de covariância deve ser simétrica e positiva definida
    assert np.allclose(matriz_encolhida, matriz_encolhida.T)
    autovalores = np.linalg.eigvals(matriz_encolhida)
    assert np.all(autovalores > 0)


def test_hrp_weight_normalization_and_nlp_tilting():
    """Valida se a normalização de pesos HRP com inclinação de sentimento soma exatamente 1.0."""
    pesos_base = {"PETR4": 0.40, "VALE3": 0.30, "ITUB4": 0.30}
    sentimentos = {"PETR4": 0.20, "VALE3": -0.10, "ITUB4": 0.00}

    pesos_ajustados = {}
    for ativo, peso in pesos_base.items():
        score_nlp = sentimentos.get(ativo, 0.0)
        multiplicador = max(0.1, 1.0 + score_nlp)
        pesos_ajustados[ativo] = peso * multiplicador

    soma_total = sum(pesos_ajustados.values())
    pesos_finais = {ativo: (peso / soma_total) for ativo, peso in pesos_ajustados.items()}

    # A soma dos pesos finais deve ser exatamente 1.0
    assert pytest.approx(sum(pesos_finais.values()), abs=1e-6) == 1.0
    # PETR4 teve sentimento positivo (+0.20), seu peso deve ter aumentado em relação aos outros
    assert pesos_finais["PETR4"] > pesos_base["PETR4"]
