package handlers_test

import (
	"testing"
	"time"

	"quantadvisor/internal/handlers"
)

func TestSSEHub_NonBlockingBroadcastAndCleanup(t *testing.T) {
	// Inicia o Hub SSE em uma goroutine
	go handlers.StartSSEHub()

	// 1. Cria um cliente responsivo (canal bufferizado)
	clientResponsive := make(handlers.Client, 5)
	handlers.Register <- clientResponsive

	// 2. Cria um cliente travado (canal sem buffer)
	clientSlow := make(handlers.Client)
	handlers.Register <- clientSlow

	// Pequena pausa para garantir processamento dos registros
	time.Sleep(50 * time.Millisecond)

	// 3. Dispara transmissão via Broadcast
	mensagem := []byte(`{"ticker":"PETR4","preco":38.50}`)

	done := make(chan bool)
	go func() {
		handlers.Broadcast <- mensagem
		done <- true
	}()

	select {
	case <-done:
		// Sucesso: envio de broadcast não ficou bloqueado pelo cliente lento
	case <-time.After(1 * time.Second):
		t.Fatal("ERRO: Broadcast SSE bloqueou aguardando cliente lento (bloqueio detectado)")
	}

	// 4. Verifica se o cliente responsivo recebeu a mensagem
	select {
	case msgRec := <-clientResponsive:
		if string(msgRec) != string(mensagem) {
			t.Errorf("Mensagem incorreta recebida: esperava %s, obteve %s", string(mensagem), string(msgRec))
		}
	case <-time.After(500 * time.Millisecond):
		t.Error("Cliente responsivo não recebeu mensagem SSE a tempo")
	}

	// Limpeza
	handlers.Unregister <- clientResponsive
}
