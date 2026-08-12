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
    object AssetAnalysis : NavRoutes("asset_analysis")
    object Portfolio : NavRoutes("portfolio")
    object BatchOperations : NavRoutes("batch_operations")
    
    // Rotas com argumentos
    object EditUser : NavRoutes("edit_user/{userId}") {
        fun createRoute(userId: Int) = "edit_user/$userId"
    }
    
    object Calibration : NavRoutes("calibration/{userId}") {
        fun createRoute(userId: Int) = "calibration/$userId"
    }
}