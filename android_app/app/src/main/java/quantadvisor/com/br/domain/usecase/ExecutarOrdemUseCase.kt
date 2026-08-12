package quantadvisor.com.br.domain.usecase

import quantadvisor.com.br.data.model.GenericResponse
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.model.OrdemRequest
import quantadvisor.com.br.data.repository.MarketRepository
import javax.inject.Inject

class ExecutarOrdemUseCase @Inject constructor(
    private val repository: MarketRepository
) {
    suspend operator fun invoke(ordem: OrdemRequest): NetworkResult<GenericResponse> {
        return repository.enviarOrdem(ordem)
    }
}
