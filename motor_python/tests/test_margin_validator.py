import pytest
import numpy as np
from models.pairs_trading import PairsTradingAnalyzer


def test_margin_validator_insuficiente(monkeypatch):
    class MockRedis:
        def get(self, key):
            if key == "hft:wallet:buying_power":
                return "2000.00"  # Menor que MARGIN_REQUIRED (5000.0)
            return None

        def publish(self, channel, message):
            pytest.fail("O sinal NÃO deveria ter sido publicado quando o saldo é insuficiente!")

    monkeypatch.setattr("models.pairs_trading.get_redis_client", lambda: MockRedis())

    np.random.seed(42)
    x = np.linspace(100, 150, 50)
    y = 2.0 * x + 5.0
    y[-1] += 25.0  # Força Z-Score alto (> 2.0) -> SHORT_SPREAD

    analyzer = PairsTradingAnalyzer(threshold_z=2.0, margin_required=5000.0)
    res = analyzer.analisar(y, x, ticker_a="VALE3", ticker_b="GGBR4", publish_redis=True)

    assert res["sinal"] == "SHORT_SPREAD"
    assert res["bloqueado_margem"] is True
    assert res["publicado_redis"] is False
    assert res["buying_power_atual"] == 2000.0
    assert "Poder de Compra insuficiente" in res["descricao"]


def test_margin_validator_suficiente(monkeypatch):
    published = []

    class MockRedis:
        def get(self, key):
            if key == "hft:wallet:buying_power":
                return "15000.00"  # Maior que MARGIN_REQUIRED (5000.0)
            return None

        def publish(self, channel, message):
            published.append(message)
            return 1

    monkeypatch.setattr("models.pairs_trading.get_redis_client", lambda: MockRedis())
    monkeypatch.setattr("messaging.redis_pub.get_redis_client", lambda: MockRedis())

    np.random.seed(42)
    x = np.linspace(100, 150, 50)
    y = 2.0 * x + 5.0
    y[-1] += 25.0  # Força Z-Score alto (> 2.0) -> SHORT_SPREAD

    analyzer = PairsTradingAnalyzer(threshold_z=2.0, margin_required=5000.0)
    res = analyzer.analisar(y, x, ticker_a="PETR4", ticker_b="PETR3", publish_redis=True)

    assert res["sinal"] == "SHORT_SPREAD"
    assert res["bloqueado_margem"] is False
    assert res["publicado_redis"] is True
    assert res["buying_power_atual"] == 15000.0
    assert len(published) == 1
