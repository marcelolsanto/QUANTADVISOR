import pytest
from models.capital_recycling import selecionar_posicao_mais_fraca, avaliar_reciclagem_capital


def test_selecionar_posicao_mais_fraca():
    posicoes = [
        {"ticker": "PETR4", "z_score_atual": 1.8},
        {"ticker": "VALE3", "z_score_atual": 0.2},  # Mais fraca (|Z| = 0.2)
        {"ticker": "ITUB4", "z_score_atual": -1.5},
    ]

    fraca = selecionar_posicao_mais_fraca(posicoes)
    assert fraca is not None
    assert fraca["ticker"] == "VALE3"
    assert fraca["z_score_atual"] == 0.2


def test_avaliar_reciclagem_capital_necessaria():
    posicoes = [
        {"ticker": "AAPL", "z_score_atual": 0.3, "quantidade": 200},
        {"ticker": "MSFT", "z_score_atual": 1.4, "quantidade": 150},
    ]

    novo_sinal = {"ticker_a": "NVDA", "z_score_atual": 2.8}

    # Saldo atual de $2.000 < MARGIN_REQUIRED $5.000, mas novo Z=2.8 é extremo
    res = avaliar_reciclagem_capital(
        posicoes_abertas=posicoes,
        novo_sinal=novo_sinal,
        buying_power_atual=2000.0,
        margin_required=5000.0,
        min_z_score_extremo=2.3
    )

    assert res["reciclagem_necessaria"] is True
    assert res["posicao_liquidar"]["ticker"] == "AAPL"
    assert res["payload_fechamento"]["action"] == "CLOSE_POSITION"
    assert res["payload_fechamento"]["asset_a"] == "AAPL"
    assert res["payload_fechamento"]["target_qty"] == 200


def test_avaliar_reciclagem_capital_desnecessaria_saldo_suficiente():
    posicoes = [{"ticker": "AAPL", "z_score_atual": 0.3, "quantidade": 200}]
    novo_sinal = {"ticker_a": "NVDA", "z_score_atual": 2.8}

    res = avaliar_reciclagem_capital(
        posicoes_abertas=posicoes,
        novo_sinal=novo_sinal,
        buying_power_atual=10000.0,
        margin_required=5000.0
    )

    assert res["reciclagem_necessaria"] is False
    assert "suficiente" in res["motivo"]
