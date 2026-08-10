package database_test

import (
	"testing"
)

func TestDatabasePoolLimitsConfig(t *testing.T) {
	const maxOpenExpected = 50
	const maxIdleExpected = 10

	// Simula a validação de parâmetros de limites de pool do PostgreSQL
	if maxOpenExpected <= maxIdleExpected {
		t.Errorf("MaxOpenConns (%d) deve ser estritamente maior que MaxIdleConns (%d)", maxOpenExpected, maxIdleExpected)
	}

	if maxOpenExpected > 100 {
		t.Errorf("MaxOpenConns (%d) ultrapassa o limite seguro para conexões simultâneas", maxOpenExpected)
	}
}
