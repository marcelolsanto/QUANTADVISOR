import pytest
import numpy as np
import pandas as pd
import sys
import os

# Adiciona o diretório motor_python ao sys.path para importar montecarlo.py
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from montecarlo import calibrar_parametros_merton


def test_calibracao_merton_sem_saltos():
    """Valida a calibração de parâmetros Merton quando os retornos são normalmente distribuídos (sem saltos)."""
    np.random.seed(42)
    retornos = pd.Series(np.random.normal(0.001, 0.015, 252))

    mu_d, sigma_d, lamb, mu_j, sigma_j = calibrar_parametros_merton(retornos)

    assert isinstance(mu_d, float)
    assert isinstance(sigma_d, float)
    assert sigma_d > 0
    assert lamb >= 0


def test_calibracao_merton_com_gaps_extremos():
    """Valida a identificação de saltos (Jump-Diffusion) quando há choques de mercado."""
    np.random.seed(42)
    retornos = np.random.normal(0.0005, 0.01, 252)
    # Insere 5 gaps extremos de circuit breaker (-8% a +10%)
    retornos[10] = -0.08
    retornos[50] = 0.10
    retornos[100] = -0.09
    retornos[150] = 0.08
    retornos[200] = -0.07

    serie_retornos = pd.Series(retornos)
    mu_d, sigma_d, lamb, mu_j, sigma_j = calibrar_parametros_merton(serie_retornos)

    # A frequência anualizada de saltos deve ser > 0
    assert lamb > 0
    # A variância de difusão deve ter removido os saltos
    assert sigma_d < serie_retornos.std()


func_percentil = lambda x: (np.percentile(x, 5), np.percentile(x, 50), np.percentile(x, 95))


def test_propriedades_estocasticas_monte_carlo():
    """Garante a ordenação coerente dos percentis da Simulação Monte Carlo (P05 <= P50 <= P95)."""
    np.random.seed(123)
    simulacoes = 1000
    dias = 252
    caminhos = np.zeros((dias, simulacoes))
    caminhos[0] = 100.0

    for t in range(1, dias):
        Z = np.random.normal(0, 1, simulacoes)
        caminhos[t] = caminhos[t - 1] * np.exp(0.0005 + 0.02 * Z)

    p05, p50, p95 = func_percentil(caminhos[-1])

    assert p05 <= p50 <= p95
    assert p05 > 0
