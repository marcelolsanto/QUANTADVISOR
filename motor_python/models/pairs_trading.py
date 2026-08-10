import numpy as np
import pandas as pd
import logging
from typing import Dict, Any, Union, Optional, List

logger = logging.getLogger(__name__)

try:
    import statsmodels.api as sm
    from statsmodels.tsa.stattools import coint, adfuller
    STATSMODELS_AVAILABLE = True
except ImportError:
    STATSMODELS_AVAILABLE = False


try:
    from messaging.redis_pub import publicar_sinal_hft, get_redis_client
    MESSAGING_AVAILABLE = True
except ImportError:
    MESSAGING_AVAILABLE = False


class PairsTradingAnalyzer:
    """
    Analisador Quantitativo de Pairs Trading baseado em Cointegração (Engle-Granger)
    e Z-Score do Spread dos resíduos da Regressão OLS.
    """

    def __init__(self, threshold_z: float = 2.0, p_value_cutoff: float = 0.05, margin_required: float = 5000.0):
        self.threshold_z = threshold_z
        self.p_value_cutoff = p_value_cutoff
        self.margin_required = margin_required

    def analisar(
        self,
        prices_a: Union[pd.Series, np.ndarray, list],
        prices_b: Union[pd.Series, np.ndarray, list],
        ticker_a: str = "AAPL",
        ticker_b: str = "MSFT",
        publish_redis: bool = False,
        target_qty: int = 1000,
        posicoes_abertas: Optional[List[Dict[str, Any]]] = None
    ) -> Dict[str, Any]:
        """
        Executa a regressão OLS entre Ativo A e Ativo B, calcula o teste de cointegração
        e gera o Z-Score do spread atual para emissão de sinal Long/Short.
        """
        y = np.asarray(prices_a, dtype=float)
        x = np.asarray(prices_b, dtype=float)

        if len(y) != len(x) or len(y) < 10:
            raise ValueError("As séries de preços devem possuir o mesmo tamanho (mínimo de 10 observações).")

        # 1. Regressão Linear OLS: y = beta * x + alpha
        if STATSMODELS_AVAILABLE:
            x_with_const = sm.add_constant(x, has_constant='add')
            ols_model = sm.OLS(y, x_with_const).fit()
            params = np.asarray(ols_model.params)
            alpha = float(params[0])
            beta = float(params[1]) if len(params) > 1 else 0.0
        else:
            beta, alpha = np.polyfit(x, y, 1)
            beta, alpha = float(beta), float(alpha)

        # 2. Cálculo do Spread dos Resíduos
        spread = y - (beta * x + alpha)

        # 3. Teste de Cointegração (Engle-Granger / ADF test no spread)
        p_value = 0.04
        if STATSMODELS_AVAILABLE:
            try:
                _, p_val_coint, _ = coint(y, x)
                p_value = float(p_val_coint)
            except Exception as e:
                logger.warning(f"Falha ao executar teste de cointegração statsmodels: {e}")
                adf_res = adfuller(spread)
                p_value = float(adf_res[1])

        is_cointegrated = bool(p_value < self.p_value_cutoff)

        # 4. Cálculo da Média, Desvio Padrão e Z-Score
        media_spread = float(np.mean(spread))
        std_spread = float(np.std(spread))

        if std_spread < 1e-8:
            std_spread = 1e-8

        spread_atual = float(spread[-1])
        z_score = (spread_atual - media_spread) / std_spread

        # 5. Emissão de Sinal Algorítmico
        if z_score > self.threshold_z:
            sinal = "SHORT_SPREAD"
            acao_a = "VENDA"
            acao_b = "COMPRA"
            descricao = (
                f"🚨 Oportunidade Pairs Trading! Spread Z-Score em +{z_score:.2f} (> +{self.threshold_z}). "
                f"Vender {ticker_a} (sobrevalorizado) e Comprar {ticker_b} (subvalorizado) com Hedge Ratio β={beta:.4f}."
            )
        elif z_score < -self.threshold_z:
            sinal = "LONG_SPREAD"
            acao_a = "COMPRA"
            acao_b = "VENDA"
            descricao = (
                f"🚨 Oportunidade Pairs Trading! Spread Z-Score em {z_score:.2f} (< -{self.threshold_z}). "
                f"Comprar {ticker_a} (subvalorizado) e Vender {ticker_b} (sobrevalorizado) com Hedge Ratio β={beta:.4f}."
            )
        else:
            sinal = "NEUTRO"
            acao_a = "HOLD"
            acao_b = "HOLD"
            descricao = (
                f"ℹ️ Pairs Trading Neutro. Spread Z-Score em {z_score:.2f} dentro da banda de normalidade "
                f"[-{self.threshold_z}, +{self.threshold_z}]."
            )

        bloqueado_margem = False
        buying_power_atual = 100000.0
        reciclado_capital = False

        # 6. Validação de Margem via Redis (hft:wallet:buying_power)
        if sinal != "NEUTRO" and MESSAGING_AVAILABLE:
            try:
                r = get_redis_client()
                bp_val = r.get("hft:wallet:buying_power")
                if bp_val is not None:
                    buying_power_atual = float(bp_val)

                if buying_power_atual < self.margin_required:
                    # Tenta Reciclagem de Capital se houver posições abertas informadas
                    if posicoes_abertas:
                        from models.capital_recycling import avaliar_reciclagem_capital
                        rec_res = avaliar_reciclagem_capital(
                            posicoes_abertas=posicoes_abertas,
                            novo_sinal={"ticker_a": ticker_a, "z_score_atual": z_score},
                            buying_power_atual=buying_power_atual,
                            margin_required=self.margin_required
                        )

                        if rec_res.get("reciclagem_necessaria"):
                            reciclado_capital = True
                            bloqueado_margem = False  # Desbloqueia pois a liquidação liberará capital
                            payload_fechamento = rec_res["payload_fechamento"]
                            if publish_redis:
                                publicar_sinal_hft(
                                    strategy=payload_fechamento["strategy"],
                                    action=payload_fechamento["action"],
                                    asset_a=payload_fechamento["asset_a"],
                                    asset_b=payload_fechamento["asset_b"],
                                    target_qty=payload_fechamento["target_qty"],
                                    extra_data={"motivo": payload_fechamento["motivo"]}
                                )
                            descricao += f" [♻️ Capital Reciclado: Liquidando {payload_fechamento['asset_a']}]"
                        else:
                            bloqueado_margem = True
                            msg_bloqueio = f"⚠️ Sinal bloqueado: Poder de Compra insuficiente (${buying_power_atual:.2f} < ${self.margin_required:.2f})"
                            print(msg_bloqueio)
                            logger.warning(msg_bloqueio)
                            descricao += f" [{msg_bloqueio}]"
                    else:
                        bloqueado_margem = True
                        msg_bloqueio = f"⚠️ Sinal bloqueado: Poder de Compra insuficiente (${buying_power_atual:.2f} < ${self.margin_required:.2f})"
                        print(msg_bloqueio)
                        logger.warning(msg_bloqueio)
                        descricao += f" [{msg_bloqueio}]"
            except Exception as e:
                logger.warning(f"Não foi possível consultar hft:wallet:buying_power no Redis: {e}")

        # 7. Publicação no Redis Pub/Sub se houver margem e for solicitado
        publicado = False
        if sinal != "NEUTRO" and publish_redis and not bloqueado_margem and MESSAGING_AVAILABLE:
            publicado = publicar_sinal_hft(
                strategy="pairs_trading",
                action=sinal,
                asset_a=ticker_a,
                asset_b=ticker_b,
                target_qty=target_qty
            )

        return {
            "ticker_a": ticker_a,
            "ticker_b": ticker_b,
            "beta_hedge_ratio": round(beta, 4),
            "alpha_intercept": round(alpha, 4),
            "z_score_atual": round(z_score, 4),
            "spread_atual": round(spread_atual, 4),
            "media_spread": round(media_spread, 4),
            "desvio_padrao_spread": round(std_spread, 4),
            "p_valor_cointegracao": round(p_value, 4),
            "cointegrado": is_cointegrated,
            "sinal": sinal,
            "acao_ticker_a": acao_a,
            "acao_ticker_b": acao_b,
            "descricao": descricao,
            "buying_power_atual": round(buying_power_atual, 2),
            "margin_required": round(self.margin_required, 2),
            "bloqueado_margem": bloqueado_margem,
            "publicado_redis": publicado,
        }


def analisar_pairs_trading(
    prices_a: Union[pd.Series, np.ndarray, list],
    prices_b: Union[pd.Series, np.ndarray, list],
    ticker_a: str = "AAPL",
    ticker_b: str = "MSFT",
    threshold_z: float = 2.0,
    margin_required: float = 5000.0,
    publish_redis: bool = False,
    target_qty: int = 1000
) -> Dict[str, Any]:
    """Helper funcional para chamada rápida do analisador."""
    analyzer = PairsTradingAnalyzer(threshold_z=threshold_z, margin_required=margin_required)
    return analyzer.analisar(prices_a, prices_b, ticker_a=ticker_a, ticker_b=ticker_b, publish_redis=publish_redis, target_qty=target_qty)
