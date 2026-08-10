package risk

import (
	"sync"
	"time"
)

// CooldownManager encapsula o controle de bloqueio temporário
type CooldownManager struct {
	bloqueios sync.Map
}

// EmCooldown verifica se o ativo está bloqueado
func (c *CooldownManager) EmCooldown(ativo string) bool {
	valor, existe := c.bloqueios.Load(ativo)
	if !existe {
		return false
	}

	expiracao := valor.(time.Time)
	if time.Now().Before(expiracao) {
		return true // Ainda está no período de cooldown
	}

	c.bloqueios.Delete(ativo)
	return false
}

// Bloquear insere o ativo em cooldown
func (c *CooldownManager) Bloquear(ativo string, duracao time.Duration) {
	c.bloqueios.Store(ativo, time.Now().Add(duracao))
}

// Cria uma instância global que pode ser importada por outras partes do sistema
var GlobalCooldown = &CooldownManager{}