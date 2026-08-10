# 🐹 QuantAdvisor - Golang Engine Swagger API & Core Docs (`coletor_go`)

O **Coletor Go** é o motor central de alta performance da plataforma **QuantAdvisor**, responsável pela ingestão de preços em tempo real, execução algorítmica de ordens (*TWAP Engine*), sincronização contínua de margem de garantia e transmissão SSE.

---

## 📚 Documentação Swagger Interativa

A documentação interativa OpenAPI 2.0 / Swagger UI do motor Go é servida nativamente via middleware `httpSwagger`:

* **URL Interativa (HTTP)**: `http://localhost:8080/swagger/index.html`
* **URL via Proxy Nginx (HTTPS)**: `https://localhost/swagger/index.html`
* **Especificação Swagger JSON**: `http://localhost:8080/swagger/doc.json`
* **Arquivos do Gerador**: [`coletor_go/docs/docs.go`](file:///home/marcelosantos_dev/Documentos/meu-ambiente-dev/QUANTADVISOR/coletor_go/docs/docs.go), [`swagger.json`](file:///home/marcelosantos_dev/Documentos/meu-ambiente-dev/QUANTADVISOR/coletor_go/docs/swagger.json), [`swagger.yaml`](file:///home/marcelosantos_dev/Documentos/meu-ambiente-dev/QUANTADVISOR/coletor_go/docs/swagger.yaml)

### Como Atualizar o Swagger no Código Go
Cada controller possui anotações GoDoc estruturadas. Exemplo (`hft_wallet_handlers.go`):

```go
// HandlerGetBuyingPower retorna o poder de compra sincronizado no Redis via Alpaca/BTG
// @Summary Buscar Poder de Compra (Buying Power)
// @Description Retorna o limite de margem e poder de compra atualizado via feedback loop Redis Alpaca/BTG
// @Tags Wallet & HFT
// @Produce json
// @Success 200 {object} BuyingPowerResponse
// @Router /wallet/buying-power [get]
```

Para regenerar a documentação Swagger após adicionar/alterar rotas:
```bash
docker compose exec quant_coletor_go /root/go/bin/swag init
```

---

## 📡 Tabela Completa de Endpoints Documentados no Swagger

### 1. **Autenticação & Gestão de Usuários**
| Método | Endpoint | Tags Swagger | Descrição |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/login` | `1. Autenticação` | Autentica o usuário e emite Token JWT |
| `POST` | `/api/usuarios/solicitar-cadastro` | `1. Autenticação` | Solicitação pública de cadastro de cliente |
| `GET` | `/api/usuarios` | `Usuários` | Lista clientes (Requer perfil GESTOR) |

### 2. **Wallet & HFT Execution (Wall St / B3)**
| Método | Endpoint | Tags Swagger | Descrição |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/wallet/buying-power` | `Wallet & HFT` | Poder de compra em tempo real via Redis |
| `GET` | `/api/hft/signals` | `Wallet & HFT` | Histórico de sinais quantitativos recebidos |
| `GET` | `/api/hft/twap-history` | `Wallet & HFT` | Histórico de fatias de ordens TWAP |
| `POST` | `/api/ordem` | `Ordens` | Submissão direta de ordens de compra/venda |
| `POST` | `/api/cambio` | `Câmbio` | Execução de remessas cambiais BRL <-> USD |

### 3. **Streaming & Analytics**
| Método | Endpoint | Tags Swagger | Descrição |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/stream/mercado` | `Streaming` | Feed Server-Sent Events (SSE) ao vivo |
| `GET` | `/api/dashboard/macro` | `Dashboard` | Visão consolidada macro da mesa de operações |

---

## 🧪 Suíte de Testes
```bash
docker compose exec quant_coletor_go go test -v ./...
```
