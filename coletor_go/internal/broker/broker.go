package broker

import (
	"log"

	"quantadvisor/internal/models"
)

// BrokerAdapter define o contrato universal de comunicação com corretoras (EUA & Brasil)
type BrokerAdapter interface {
	Connect() error
	SubscribeTicker(ticker string) error
	PlaceOrder(req models.OrdemRequest) (string, error)
	GetBuyingPower() (float64, error)
}

// NewBrokerAdapter é a fábrica (Factory Function) que seleciona o adaptador correto
// com base na jurisdição ("USD" para Alpaca/Wall St ou "BRL" para BTG Pactual/B3)
func NewBrokerAdapter(jurisdiction string) BrokerAdapter {
	if jurisdiction == "USD" {
		log.Println("🔌 [BROKER FACTORY] Instanciando AlpacaAdapter (Wall St / USD)")
		return NewAlpacaAdapter()
	}
	log.Println("🔌 [BROKER FACTORY] Instanciando BTGAdapter (B3 / BRL)")
	return NewBTGAdapter()
}
