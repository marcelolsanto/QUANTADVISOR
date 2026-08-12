import os
import json
import logging
from datetime import datetime, timezone
from typing import Dict, Any, Optional

logger = logging.getLogger(__name__)

try:
    import redis
    REDIS_AVAILABLE = True
except ImportError:
    REDIS_AVAILABLE = False


_REDIS_POOL = None

def get_redis_client():
    """Retorna um cliente Redis reutilizando um ConnectionPool global para alta concorrência."""
    global _REDIS_POOL
    if not REDIS_AVAILABLE:
        raise RuntimeError("A biblioteca 'redis' não está instalada. Execute 'pip install redis'.")

    if _REDIS_POOL is None:
        host = os.getenv("REDIS_HOST", "quant_redis")
        port = int(os.getenv("REDIS_PORT", 6379))
        password = os.getenv("REDIS_PASSWORD", None) or None
        _REDIS_POOL = redis.ConnectionPool(host=host, port=port, password=password, decode_responses=True)
        
    return redis.Redis(connection_pool=_REDIS_POOL)


def publicar_sinal_hft(
    strategy: str,
    action: str,
    asset_a: str,
    asset_b: str,
    target_qty: int = 1000,
    extra_data: Optional[Dict[str, Any]] = None,
    channel: str = "hft:signals"
) -> bool:
    """
    Publica um sinal de trading quantitativo no barramento Pub/Sub Redis (canal hft:signals)
    para consumo imediato pelo motor de execução HFT em Go.
    """
    try:
        r = get_redis_client()
        payload = {
            "strategy": strategy,
            "action": action,
            "asset_a": asset_a,
            "asset_b": asset_b,
            "target_qty": target_qty,
            "timestamp": datetime.now(timezone.utc).isoformat()
        }
        if extra_data:
            payload.update(extra_data)

        payload_json = json.dumps(payload)
        subscribers = r.publish(channel, payload_json)
        print(f"📡 [REDIS PUB HFT] Sinal publicado no canal '{channel}' ({subscribers} inscritos): {payload_json}")
        logger.info(f"📡 [REDIS PUB HFT] Sinal publicado no canal '{channel}' ({subscribers} inscritos): {payload_json}")
        return True
    except Exception as e:
        logger.error(f"❌ [REDIS PUB HFT] Erro ao publicar sinal no Redis: {e}")
        return False
