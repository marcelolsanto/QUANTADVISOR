import pytest
from messaging.redis_pub import publicar_sinal_hft


def test_publicar_sinal_hft_mock(monkeypatch):
	class MockRedis:
		def publish(self, channel, message):
			assert channel == "hft:signals"
			assert "pairs_trading" in message
			assert "SHORT_SPREAD" in message
			return 1

	monkeypatch.setattr("messaging.redis_pub.get_redis_client", lambda: MockRedis())

	success = publicar_sinal_hft(
		strategy="pairs_trading",
		action="SHORT_SPREAD",
		asset_a="AAPL",
		asset_b="MSFT",
		target_qty=1000
	)

	assert success is True
