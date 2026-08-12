package quantadvisor.com.br.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import quantadvisor.com.br.data.model.AssetAnalysis
import quantadvisor.com.br.data.model.AssetAnalysisResponse
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.repository.MarketRepository
import javax.inject.Inject

data class AssetAnalysisUiState(
    val asset: AssetAnalysis? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = ""
)

@HiltViewModel
class AssetAnalysisViewModel @Inject constructor(
    private val repository: MarketRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssetAnalysisUiState())
    val uiState: StateFlow<AssetAnalysisUiState> = _uiState.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun analyzeAsset(ticker: String = _uiState.value.searchQuery) {
        if (ticker.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.getDetalhesAtivo(ticker)) {
                is NetworkResult.Success<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val data = (result as NetworkResult.Success<AssetAnalysisResponse>).data
                    val ia = data.ia_status ?: emptyMap()
                    
                    // Extração de Fundamentos (estilo Web)
                    @Suppress("UNCHECKED_CAST")
                    val quoteSummary = data.fundamentos?.get("quoteSummary") as? Map<String, Any>
                    @Suppress("UNCHECKED_CAST")
                    val results = quoteSummary?.get("result") as? List<Map<String, Any>>
                    val firstResult = results?.getOrNull(0)
                    
                    @Suppress("UNCHECKED_CAST")
                    val quoteType = firstResult?.get("quoteType") as? Map<String, Any>
                    @Suppress("UNCHECKED_CAST")
                    val profile = firstResult?.get("assetProfile") as? Map<String, Any>
                    @Suppress("UNCHECKED_CAST")
                    val financialData = firstResult?.get("financialData") as? Map<String, Any>
                    
                    val companyName = (quoteType?.get("longName") as? String) ?: (quoteType?.get("shortName") as? String) ?: ticker
                    val sector = (profile?.get("sector") as? String) ?: "Setor Não Classificado"
                    val industry = (profile?.get("industry") as? String) ?: "Indústria Global"
                    val employees = (profile?.get("fullTimeEmployees") as? Double)?.toInt()?.toString() ?: "N/A"
                    val summary = (profile?.get("longBusinessSummary") as? String) ?: ""
                    val city = (profile?.get("city") as? String) ?: ""
                    val country = (profile?.get("country") as? String) ?: ""
                    val website = (profile?.get("website") as? String) ?: ""
                    
                    // Métricas Financeiras
                    @Suppress("UNCHECKED_CAST")
                    val ebitdaMap = financialData?.get("ebitda") as? Map<String, Any>
                    val ebitda = (ebitdaMap?.get("raw") as? Double) ?: 1.0
                    @Suppress("UNCHECKED_CAST")
                    val debtMap = financialData?.get("totalDebt") as? Map<String, Any>
                    val debt = (debtMap?.get("raw") as? Double) ?: 0.0
                    val divEbitda = if (ebitda != 0.0) debt / ebitda else 0.0
                    
                    @Suppress("UNCHECKED_CAST")
                    val marginMap = financialData?.get("profitMargins") as? Map<String, Any>
                    val margin = ((marginMap?.get("raw") as? Double) ?: 0.0) * 100.0

                    val mappedAsset = AssetAnalysis(
                        ticker = ticker,
                        nome_empresa = companyName,
                        preco_atual = (ia["preco_atual"] as? Double) ?: 0.0,
                        z_score = (ia["z_score"] as? Double) ?: 0.0,
                        volatilidade = (ia["risco_var"] as? Double) ?: 0.0,
                        ai_decision = (ia["sinal"] as? String) ?: "NEUTRO",
                        setor = sector,
                        industria = industry,
                        colaboradores = employees,
                        resumo = summary,
                        sede = if (city.isNotEmpty()) "$city, $country" else country,
                        website = website,
                        divida_ebitda = divEbitda,
                        margem_liquida = margin,
                        historico_precos = data.historico
                    )
                    _uiState.update { it.copy(asset = mappedAsset, isLoading = false) }
                }
                is NetworkResult.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                NetworkResult.Loading -> {
                    _uiState.update { it.copy(isLoading = true) }
                }
            }
        }
    }
}
