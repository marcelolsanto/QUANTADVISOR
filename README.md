# 🚀 QuantAdvisor - Platform Architecture & Interactive Swagger API Docs

**QuantAdvisor** é uma plataforma institucional de trading quantitativo, análise estatística por Inteligência Artificial e execução HFT/TWAP com suporte a jurisdição dupla (**B3 / BRL** e **Wall St / USD Alpaca**).

---

## 📚 Documentação Swagger & OpenAPI das APIs

O ecossistema disponibiliza **documentação interativa Swagger (OpenAPI 2.0 / 3.0)** para todas as camadas de API do projeto:

```
                                  ┌──────────────────────────────────────────────┐
                                  │   Reverse Proxy Nginx SSL (Porta 443 / 80)   │
                                  └──────────────────────┬───────────────────────┘
                                                         │
                        ┌────────────────────────────────┴────────────────────────────────┐
                        │                                                                 │
                        ▼                                                                 ▼
           ┌──────────────────────────┐                                      ┌──────────────────────────┐
           │     Golang Engine API    │                                      │     Python AI Engine     │
           │  Swagger UI (OpenAPI 2)  │                                      │  Swagger UI (OpenAPI 3)  │
           │  https://localhost/swagger│                                      │  https://localhost/py/docs│
           └──────────────────────────┘                                      └──────────────────────────┘
```

| Camada | Tecnologia | Interface Swagger / OpenAPI | Especificação JSON / YAML |
| :--- | :--- | :--- | :--- |
| **Motor Golang** | Go + Gin + Swag | `https://localhost/swagger/index.html` | `/swagger/doc.json` / `coletor_go/docs/swagger.json` |
| **Motor Python IA** | FastAPI + Pydantic | `https://localhost/py/docs` (Swagger UI)<br>`https://localhost/py/redoc` (ReDoc) | `/py/openapi.json` |
| **Dashboard Proxy** | Nginx / Node | `https://localhost/` (Proxy Nginx unificado) | Visualização unificada de barramento |

---

## 🏛️ Visão Geral das APIs por Camada

### 1. **Golang Execution & Ingestion Engine (`/coletor_go`)**
- **Swagger URL**: `http://localhost:8080/swagger/index.html`
- **Prefix**: `/api`
- **Principais Endpoints**:
  - `POST /api/login` - Autenticação JWT e emissão de permissões (GESTOR / CLIENTE).
  - `GET /api/wallet/buying-power` - Limite de margem e poder de compra via Alpaca/BTG.
  - `GET /api/hft/signals` - Barramento de sinais quantitativos recentes.
  - `GET /api/hft/twap-history` - Log de execuções de fatias algorítmicas TWAP.
  - `POST /api/ordem` - Submissão de ordens de compra/venda (B3 & Wall St).
  - `POST /api/cambio` - Execução de remessas de conversão cambial (BRL <-> USD).
  - `GET /api/stream/mercado` - Feed Server-Sent Events (SSE) em tempo real.

### 2. **Python AI & Quantitative Engine (`/motor_python`)**
- **Swagger URL**: `http://localhost:8000/docs` (ou `https://localhost/py/docs`)
- **Prefix**: `/py` (via Nginx)
- **Principais Endpoints**:
  - `POST /api/pairs-trading/analisar` - Cointegração Engle-Granger, Z-Score e emissão HFT Pub/Sub.
  - `POST /api/otimizar/hrp_nlp` - Hierarchical Risk Parity (HRP) ponderado por sentimento FinBERT.
  - `POST /api/monte-carlo/simular` - Trajetórias estocásticas de Merton Jump-Diffusion.
  - `POST /api/ml/prever` - Inferência de séries temporais com PyTorch LSTM.

---

## ⚡ Como Regenerar a Documentação Swagger em Desenvolvimento

### 1. Regenerar especificações do Golang (`swag`)
Para atualizar `docs.go`, `swagger.json` e `swagger.yaml` após alterar anotações `@Summary` ou `@Router`:
```bash
docker compose exec quant_coletor_go /root/go/bin/swag init
```

### 2. Gerar OpenAPI JSON do Python FastAPI
A especificação OpenAPI 3.0 do FastAPI é gerada dinamicamente com base nos modelos `Pydantic` e schemas de requisição. Para inspecionar o JSON:
```bash
docker compose exec quant_motor_python curl http://localhost:8000/openapi.json
```

---

## 🧪 Bateria de Testes Automatizados

### Testes da Engine Golang
```bash
docker compose exec quant_coletor_go go test -v ./...
```

### Testes do Motor Python IA
```bash
docker compose exec quant_motor_python python3 -m pytest tests/
```

### Teste de Carga e Estresse (1.500 Usuários Simultâneos)
```bash
docker compose exec quant_motor_python python3 /workspace/tests/load/stress_test.py
```
