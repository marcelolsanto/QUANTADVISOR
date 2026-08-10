import pytest
from fastapi.testclient import TestClient
from api import app

client = TestClient(app)

class TestMotorAPI:

    def test_rota_nlp_sentimento(self):
        payload = {
            'textos': [
                'Empresa anuncia lucro recorde e expansão global.', 
                'Ações despencam após escândalo de corrupção.'
            ]
        }
        
        response = client.post('/nlp/sentimento', json=payload)
        
        assert response.status_code == 200, f'A API quebrou e retornou: {response.status_code}'
        dados = response.json()
        
        assert dados['sucesso'] is True
        assert 'score_finbert' in dados
        assert isinstance(dados['score_finbert'], float)
        
        print(f"\n✅ Integração HTTP Perfeita! O FinBERT leu as notícias e retornou o Score: {dados['score_finbert']}")