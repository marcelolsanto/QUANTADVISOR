package handlers

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"quantadvisor/internal/database"
)

// O tipo Client é um canal (pipe) que transporta texto (JSON) para um navegador específico
type Client chan []byte

var (
	// Dicionário de abas abertas no React (Clientes Conectados)
	Clients = make(map[Client]bool)
	// Canal por onde entram as cotações novas (Buffered para evitar descartes sob alta carga)
	Broadcast = make(chan []byte, 2048)
	// Canais de controle de conexão
	Register   = make(chan Client)
	Unregister = make(chan Client)
)

// StartSSEHub é o coração do sistema. Fica rodando em background para sempre.
func StartSSEHub() {
	for {
		select {
		case client := <-Register:
			Clients[client] = true
			log.Printf("🔌 Novo Terminal React conectado. Total: %d", len(Clients))

		case client := <-Unregister:
			if _, ok := Clients[client]; ok {
				delete(Clients, client)
				close(client)
				log.Printf("🔌 Terminal React desconectado. Total: %d", len(Clients))
			}

		case message := <-Broadcast:
			// Quando chega uma cotação nova, dispara para todos os clientes conectados
			for client := range Clients {
				select {
				case client <- message:
					// Mensagem enviada com sucesso para o pipe do cliente
				default:
					// Se o cliente travou/lento, removemos para não travar o servidor
					close(client)
					delete(Clients, client)
				}
			}
		}
	}
}

// @Summary Stream de Cotações (Tempo Real)
// @Description Abre uma conexão persistente SSE para empurrar deltas de cotação para o Frontend.
// @Tags Sistema
// @Router /stream/mercado [get]
func SSEMarketStream(w http.ResponseWriter, r *http.Request) {
	// 1. Headers obrigatórios para transformar a rota HTTP comum num "Tubo" SSE
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	// Se tiver problemas de CORS, o seu middleware global já resolve, mas garantimos aqui:
	w.Header().Set("Access-Control-Allow-Origin", "*")

	// Verifica se o servidor web suporta "Flushing" (empurrar dados sem fechar a conexão)
	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "Streaming não suportado pelo servidor.", http.StatusInternalServerError)
		return
	}

	// 2. Registra este novo navegador no nosso Hub
	clientChan := make(Client, 1000)
	Register <- clientChan

	// 3. Garante que removemos o cliente quando ele fechar a aba
	defer func() {
		Unregister <- clientChan
	}()

	// 4. O Loop Infinito: Fica aguardando mensagens chegarem no canal deste cliente
	for {
		select {
		case msg, ok := <-clientChan:
			if !ok {
				return // Canal fechado pelo Hub, encerra a goroutine e libera a CPU
			}
			// O protocolo SSE OBRIGA o uso do prefixo "data: " e duas quebras de linha "\n\n"
			fmt.Fprintf(w, "data: %s\n\n", msg)
			flusher.Flush() // Empurra o dado imediatamente pela rede

		case <-r.Context().Done():
			// O usuário fechou a aba ou mudou de página
			return
		}
	}
}