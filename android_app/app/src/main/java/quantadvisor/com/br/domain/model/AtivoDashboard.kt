package quantadvisor.com.br.domain.model

data class AtivoDashboard(
    val ticker: String,
    val nome: String = "",
    val precoAtual: Double = 0.0,
    val variacaoDia: Double = 0.0,
    val zScore: Double = 0.0,
    val recomendacaoIA: String = "NEUTRO"
)
