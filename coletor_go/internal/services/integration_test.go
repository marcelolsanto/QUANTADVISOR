package services_test

import (
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
	"time"

	"quantadvisor/internal/services"
)

func TestGetPythonEngineURL_FallbackEAmbiente(t *testing.T) {
	envOriginal := os.Getenv("PYTHON_ENGINE_URL")
	defer os.Setenv("PYTHON_ENGINE_URL", envOriginal)

	// Case 1: Sem variável definida -> Fallback padrão para http://motor_python:8000
	os.Unsetenv("PYTHON_ENGINE_URL")
	urlPadrao := services.GetPythonEngineURL()
	if urlPadrao != "http://motor_python:8000" {
		t.Errorf("URL padrão incorreta. Esperado 'http://motor_python:8000', obtido '%s'", urlPadrao)
	}

	// Case 2: Com variável customizada com barra no final -> Deve remover a barra final
	os.Setenv("PYTHON_ENGINE_URL", "http://custom-python-service:9000/")
	urlCustom := services.GetPythonEngineURL()
	if urlCustom != "http://custom-python-service:9000" {
		t.Errorf("Tratamento de URL customizada incorreto. Esperado 'http://custom-python-service:9000', obtido '%s'", urlCustom)
	}
}

func TestHTTPClient_Timeout15Segundos(t *testing.T) {
	// Cria servidor mock que atrasa a resposta além do limite de 15s para simular travamento
	tsLento := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		time.Sleep(16 * time.Second)
		w.WriteHeader(http.StatusOK)
	}))
	defer tsLento.Close()

	// Servidor rápido para comparar
	tsRapido := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"status":"ok"}`))
	}))
	defer tsRapido.Close()

	// 1. Testa se requisição rápida responde normalmente
	resp, err := services.HTTPClient.Get(tsRapido.URL)
	if err != nil {
		t.Fatalf("Erro inesperado em requisição rápida: %v", err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Errorf("Status esperado 200, obtido %d", resp.StatusCode)
	}

	// 2. Testa se requisição > 15s sofre timeout
	inicio := time.Now()
	_, errTimeout := services.HTTPClient.Get(tsLento.URL)
	duracao := time.Since(inicio)

	if errTimeout == nil {
		t.Error("Esperava erro de Timeout de 15s na chamada lenta, mas a requisição obteve sucesso")
	}

	if duracao > 16*time.Second {
		t.Errorf("O HTTPClient demorou %.2fs para cancelar, além dos 15s configurados", duracao.Seconds())
	}
}
