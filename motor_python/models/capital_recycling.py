import logging
from typing import List, Dict, Any, Optional

logger = logging.getLogger(__name__)


def selecionar_posicao_mais_fraca(posicoes_abertas: List[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
    """
    Dada uma lista de posições abertas contendo 'ticker' e 'z_score_atual' (ou 'z_score'),
    ordena pelo menor valor absoluto de Z-Score (|Z| próximo de 0 = posição estagnada/fraca).
    Retorna a posição mais fraca para reciclagem de capital.
    """
    if not posicoes_abertas:
        return None

    sorted_pos = sorted(
        posicoes_abertas,
        key=lambda p: abs(float(p.get("z_score_atual", p.get("z_score", 0.0))))
    )
    return sorted_pos[0]


def avaliar_reciclagem_capital(
    posicoes_abertas: List[Dict[str, Any]],
    novo_sinal: Dict[str, Any],
    buying_power_atual: float,
    margin_required: float = 5000.0,
    min_z_score_extremo: float = 2.3
) -> Dict[str, Any]:
    """
    Avalia se é necessário reciclar capital encerrando a posição mais fraca
    quando o saldo em caixa for insuficiente e o novo sinal for de alta prioridade (|Z| >= min_z_score_extremo).
    """
    z_novo = abs(float(novo_sinal.get("z_score_atual", 0.0)))

    if buying_power_atual >= margin_required:
        return {
            "reciclagem_necessaria": False,
            "motivo": "Saldo em caixa suficiente para a nova operação.",
            "posicao_liquidar": None
        }

    if z_novo < min_z_score_extremo:
        return {
            "reciclagem_necessaria": False,
            "motivo": f"Novo sinal (|Z|={z_novo:.2f}) não atinge o limiar extremo (|Z|>={min_z_score_extremo:.2f}) para justificar liquidação forçada.",
            "posicao_liquidar": None
        }

    posicao_fraca = selecionar_posicao_mais_fraca(posicoes_abertas)
    if not posicao_fraca:
        return {
            "reciclagem_necessaria": False,
            "motivo": "Nenhuma posição aberta encontrada no portfólio para liquidar.",
            "posicao_liquidar": None
        }

    z_fraca = abs(float(posicao_fraca.get("z_score_atual", posicao_fraca.get("z_score", 0.0))))
    ticker_fraco = posicao_fraca.get("ticker", posicao_fraca.get("asset_a", "DESCONHECIDO"))
    target_qty = int(posicao_fraca.get("quantidade", posicao_fraca.get("target_qty", 500)))

    msg_reciclagem = (
        f"♻️ [CAPITAL RECYCLING] Liquidando posição fraca '{ticker_fraco}' (|Z|={z_fraca:.2f}) "
        f"para liberar margem ao novo sinal de Alta Prioridade '{novo_sinal.get('ticker_a')}' (|Z|={z_novo:.2f})"
    )
    print(msg_reciclagem)
    logger.info(msg_reciclagem)

    payload_fechamento = {
        "strategy": "capital_recycling",
        "action": "CLOSE_POSITION",
        "asset_a": ticker_fraco,
        "asset_b": "",
        "target_qty": target_qty,
        "motivo": msg_reciclagem
    }

    return {
        "reciclagem_necessaria": True,
        "motivo": msg_reciclagem,
        "posicao_liquidar": posicao_fraca,
        "payload_fechamento": payload_fechamento
    }
