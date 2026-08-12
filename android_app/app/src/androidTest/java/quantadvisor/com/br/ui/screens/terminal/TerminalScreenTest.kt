package quantadvisor.com.br.ui.screens.terminal

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test
import quantadvisor.com.br.data.model.LogMercado

class TerminalScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testLiveFlowExpandsAndShowsData() {
        val mockLog = LogMercado(
            ativo = "MGLU3",
            sinal = "COMPRA FORTE",
            precoAtual = 1.50,
            zScore = 2.5,
            sinalExibicao = "COMPRA FORTE"
        )
        
        val mockUiState = TerminalUiState(
            isConnected = true,
            logs = listOf(mockLog)
        )

        composeTestRule.setContent {
            // Render just the expandable section of the UI
            ExpandableSection(
                title = "QUANT ENGINE LIVE FLOW [🟢 ONLINE]",
                count = mockUiState.logs.size,
                isExpanded = true,
                onToggle = {}
            ) {
                LiveFlowCard(log = mockLog, currentMarket = "BRL", onTickerClick = {})
            }
        }

        // Verify the title is displayed
        composeTestRule.onNodeWithText("QUANT ENGINE LIVE FLOW [🟢 ONLINE] (1)").assertIsDisplayed()

        // Verify the data inside LiveFlowCard is displayed
        composeTestRule.onNodeWithText("MGLU3").assertIsDisplayed()
        composeTestRule.onNodeWithText("R$ 1.50").assertIsDisplayed()
        composeTestRule.onNodeWithText("COMPRA FORTE").assertIsDisplayed()
    }
}
