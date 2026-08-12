package quantadvisor.com.br.ui.screens.terminal

import android.app.Application
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import quantadvisor.com.br.data.model.AuditoriaResponse
import quantadvisor.com.br.data.model.LogMercado
import quantadvisor.com.br.data.model.NetworkResult
import quantadvisor.com.br.data.repository.MarketRepository
import quantadvisor.com.br.session.MarketSession

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalViewModelTest {

    private lateinit var viewModel: TerminalViewModel
    private val application = mockk<Application>(relaxed = true)
    private val repository = mockk<MarketRepository>(relaxed = true)
    private val marketSession = mockk<MarketSession>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Mock default current market
        every { marketSession.currentMarket } returns MutableStateFlow("BRL")
        
        // Mock successful auditoria response
        val mockLogs = listOf(
            LogMercado(ativo = "PETR4", sinal = "COMPRA FORTE", precoAtual = 36.50, zScore = 1.2),
            LogMercado(ativo = "VALE3", sinal = "VENDA", precoAtual = 60.10, zScore = -2.1)
        )
        coEvery { repository.getAuditoriaMercado() } returns NetworkResult.Success(
            AuditoriaResponse(sucesso = true, regime = "BULL", total = 2, recomendacoes = mockLogs)
        )

        viewModel = TerminalViewModel(application, repository, marketSession)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when polling starts, uiState is populated with simulated ticks`() = runTest(testDispatcher) {
        // Advance time to trigger polling
        advanceTimeBy(3000)
        
        val state = viewModel.uiState.value
        
        assertTrue("Devia estar conectado", state.isConnected)
        assertEquals("A auditoria devia ter 2 itens", 2, state.auditoria.size)
        // Since we take up to 4 items and delay in the polling logic, logs should be populated
        assertTrue("Live Flow logs não devem estar vazios", state.logs.isNotEmpty())
    }
}
