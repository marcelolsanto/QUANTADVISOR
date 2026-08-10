# 💻 QuantAdvisor - Frontend Dashboard & API Integration (`quant-dashboard`)

O **QuantDashboard** é a interface web reativa da plataforma **QuantAdvisor**. Desenvolvido com **React 19**, **Vite 8**, **Recharts** e **Vite PWA**, ele consome as APIs documentadas via Swagger das engines Go e Python.

---

## 📚 Acesso às APIs Swagger no Ambiente

As interfaces de documentação interativa das APIs consumidas pelo frontend estão disponíveis nos seguintes endereços:

* **Golang REST API Swagger UI**: `https://localhost/swagger/index.html`
* **Python FastAPI Math Engine Swagger UI**: `https://localhost/py/docs`
* **Especificação Go Swagger JSON**: `http://localhost:8080/swagger/doc.json`
* **Especificação Python OpenAPI JSON**: `http://localhost:8000/openapi.json`

---

## 🛠️ Serviços de API no Frontend (`src/services/`)

* [`src/services/api.ts`](file:///home/marcelosantos_dev/Documentos/meu-ambiente-dev/QUANTADVISOR/quant-dashboard/src/services/api.ts): Cliente Axios centralizador de chamadas REST com interceptor de autenticação JWT (`BearerToken`) e tratamento de exceções.
* [`src/services/stream.js`](file:///home/marcelosantos_dev/Documentos/meu-ambiente-dev/QUANTADVISOR/quant-dashboard/src/services/stream.js): Assinante do barramento de cotações em tempo real via Server-Sent Events (SSE) no endpoint `/api/stream/mercado`.

---

## 🛠️ Comandos de Build & Linting

```bash
docker compose exec quant_dashboard npm run lint
docker compose exec quant_dashboard npm run build
```
