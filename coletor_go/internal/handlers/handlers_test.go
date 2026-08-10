package handlers

import (
	"bytes"
	"net/http"
	"net/http/httptest"
	"quantadvisor/internal/database"
	"testing"

	"github.com/DATA-DOG/go-sqlmock"
)

func TestAdicionarAoCarrinho_API(t *testing.T) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("Erro no mock DB: %v", err)
	}
	defer db.Close()
	database.Conn = db

	mock.ExpectExec("INSERT INTO carrinho_de_ordens").
		WithArgs(1, "WEGE3", "COMPRA", 200, 45.50).
		WillReturnResult(sqlmock.NewResult(1, 1))

	jsonPayload := []byte(`{"usuario_id": 1, "ticker": "WEGE3", "tipo_ordem": "COMPRA", "quantidade": 200, "preco": 45.50}`)

	req, err := http.NewRequest("POST", "/api/adicionar-carrinho", bytes.NewBuffer(jsonPayload))
	if err != nil {
		t.Fatal(err)
	}
	req.Header.Set("Content-Type", "application/json")

	rr := httptest.NewRecorder()
	handler := http.HandlerFunc(AdicionarAoCarrinho)
	handler.ServeHTTP(rr, req)

	if status := rr.Code; status != http.StatusOK {
		t.Errorf("❌ FALHA: A API retornou status %v, mas esperávamos %v (200 OK)", status, http.StatusOK)
	}

	respostaCorreta := `{"sucesso": true, "mensagem": "Sugestão adicionada ao carrinho de análise!"}`
	if rr.Body.String() != respostaCorreta {
		t.Errorf("❌ FALHA: A API retornou o JSON errado.\nRetornou: %v\nEsperado: %v", rr.Body.String(), respostaCorreta)
	} else {
		t.Logf("✅ SUCESSO HTTP 200! A API leu o JSON do React e gravou no banco com sucesso.")
	}

	if err := mock.ExpectationsWereMet(); err != nil {
		t.Errorf("❌ FALHA DB: O INSERT não chegou no banco de dados: %v", err)
	}
}
