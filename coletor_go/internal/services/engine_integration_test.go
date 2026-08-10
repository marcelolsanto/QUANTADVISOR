package services

import (
	"testing"
	"quantadvisor/internal/database"
	"github.com/DATA-DOG/go-sqlmock"
)

func TestValidarVendaComIsencao_IntegracaoDB(t *testing.T) {
	// 1. Cria um Banco de Dados falso (Mock)
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("Erro ao criar mock do banco: %v", err)
	}
	defer db.Close()

	// 2. Substitui a conexão global verdadeira do PostgreSQL pelo nosso Mock
	database.Conn = db

	// 3. Simula a primeira query: O Gestor ativou o Guardião Fiscal? (Retornamos TRUE)
	mock.ExpectQuery("SELECT modo_isencao_fiscal_estrita FROM parametros_operacionais").
		WithArgs(1).
		WillReturnRows(sqlmock.NewRows([]string{"modo_isencao_fiscal_estrita"}).AddRow(true))

	// 4. Simula a segunda query: O cliente já vendeu 15k neste mês e tem 500 de lucro
	mock.ExpectQuery("SELECT COALESCE").
		WithArgs(1, sqlmock.AnyArg()).
		WillReturnRows(sqlmock.NewRows([]string{"volume_vendas", "lucro_acumulado"}).AddRow(15000.00, 500.00))

	// 5. O GATILHO: Tentamos vender 10k de PETR4. (15k + 10k = 25k -> Estoura o limite de 20k!)
	decisao := ValidarVendaComIsencao(1, "PETR4", 10000.00, 2000.00, 0.0, "BRL")

	// 6. Avaliação do Resultado
	if decisao.Aprovada {
		t.Errorf("❌ FALHA DE INTEGRAÇÃO: O Guardião Fiscal falhou e aprovou a venda! Cliente vai pagar DARF.")
	} else {
		t.Logf("✅ SUCESSO! O Banco de Dados comunicou corretamente e o Guardião bloqueou: %s", decisao.MotivoVeto)
	}

	// 7. Garante que o código realmente rodou as querys que esperávamos
	if err := mock.ExpectationsWereMet(); err != nil {
		t.Errorf("❌ O código não fez as consultas SQL esperadas: %v", err)
	}
}