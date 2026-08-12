package quantadvisor.com.br.ui.navigation

sealed class NavRoutes(val route: String) {
    object Login : NavRoutes("login")
    object Terminal : NavRoutes("terminal?userId={userId}") {
        fun createRoute(userId: Int? = null) = if (userId != null) "terminal?userId=$userId" else "terminal"
    }
    object GlobalManagement : NavRoutes("global_management")
    object Crm : NavRoutes("crm")
    object Perfil : NavRoutes("perfil")
    object News : NavRoutes("news")
    object Compliance : NavRoutes("compliance")
    object Accounting : NavRoutes("accounting")
    object Institutional : NavRoutes("institutional")
    object Backtest : NavRoutes("backtest")
    object MonteCarlo : NavRoutes("monte_carlo")
    object AssetAnalysis : NavRoutes("asset_analysis/{ticker}") {
        fun createRoute(ticker: String) = "asset_analysis/$ticker"
    }
    object Portfolio : NavRoutes("portfolio")
    object BatchOperations : NavRoutes("batch_operations")
    object AddUser : NavRoutes("add_user")
    object Trade : NavRoutes("trade/{ticker}/{price}") {
        fun createRoute(ticker: String, price: Double) = "trade/$ticker/$price"
    }
    
    // Rotas com argumentos
    object EditUser : NavRoutes("edit_user/{userId}") {
        fun createRoute(userId: Int) = "edit_user/$userId"
    }
    
    object Calibration : NavRoutes("calibration/{userId}") {
        fun createRoute(userId: Int) = "calibration/$userId"
    }
}
