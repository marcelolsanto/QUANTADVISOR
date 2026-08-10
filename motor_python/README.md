# 🧠 QuantAdvisor - Python FastAPI Swagger API & Math Engine (`motor_python`)

O **Motor Python** é a camada de inteligência quantitativa da plataforma **QuantAdvisor**. Construído sobre **FastAPI**, **PyTorch**, **Statsmodels** e **SciPy**, ele disponibiliza uma API REST documentada nativamente via **OpenAPI 3.0 / Swagger UI**.

---

## 📚 Documentação Swagger Interativa (FastAPI)

O FastAPI gera automaticamente a interface de testes interativa **Swagger UI** e a documentação **ReDoc**:

* **Swagger UI (HTTP Direto)**: `http://localhost:8000/docs`
* **Swagger UI (Via Proxy Nginx)**: `https://localhost/py/docs`
* **ReDoc (Documentação Visual)**: `https://localhost/py/redoc`
* **OpenAPI 3.0 JSON Schema**: `http://localhost:8000/openapi.json`

---

## 📡 Tabela de Endpoints OpenAPI Documentados

| Método | Endpoint | Tags Swagger | Descrição & Modelos Pydantic |
| :--- | :--- | :--- | :--- |
| `GET` | `/health` | `Status` | Checagem de saúde da API e estatísticas de memória |
| `POST` | `/api/pairs-trading/analisar` | `Econometria & HFT` | Teste de Cointegração Engle-Granger, Z-score e publicação no Redis |
| `POST` | `/api/otimizar/hrp_nlp` | `Econometria & Risco` | Hierarchical Risk Parity (HRP) com Ponderação NLP FinBERT |
| `POST` | `/api/monte-carlo/simular` | `Processos Estocásticos` | Simulação de 10.000 trajetórias Merton Jump-Diffusion |
| `POST` | `/api/ml/prever` | `Inteligência Preditiva` | Inferência temporal intradiária PyTorch LSTM |

---

## 🧪 Bateria de Testes

```bash
docker compose exec quant_motor_python python3 -m pytest tests/
docker compose exec quant_motor_python python3 test_quant_live_flow.py
```
