package quantadvisor.com.br.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.collectLatest
import quantadvisor.com.br.*
import quantadvisor.com.br.data.model.UsuarioResumo
import quantadvisor.com.br.di.NetworkEvents
import quantadvisor.com.br.di.RetrofitClient
import quantadvisor.com.br.ui.screens.*
import quantadvisor.com.br.ui.screens.crm.CentralGestaoScreen
import quantadvisor.com.br.ui.screens.login.LoginScreen
import quantadvisor.com.br.ui.screens.terminal.LiveTerminalScreen

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = NavRoutes.Login.route
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    // ðŸš¨ ESCUTAR EXPIRAÃ‡ÃƒO DE SESSÃƒO (LOGOUT AUTOMÃTICO)
    LaunchedEffect(Unit) {
        NetworkEvents.sessionExpired.collectLatest {
            SecurityManager.limparSessao(context)
            navController.navigate(NavRoutes.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // ðŸ”’ BIOMETRIA NO INÃCIO (Se jÃ¡ estiver logado)
    var isAuthenticatedByBiometrics by remember { 
        mutableStateOf(startDestination == NavRoutes.Login.route) 
    }

    if (!isAuthenticatedByBiometrics && activity != null) {
        if (BiometricHelper.isBiometricAvailable(context)) {
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = "Acesso Seguro",
                subtitle = "Confirme sua digital para entrar no terminal",
                onSuccess = { isAuthenticatedByBiometrics = true },
                onError = { /* Opcional: mostrar erro ou fechar app */ }
            )
        } else {
            isAuthenticatedByBiometrics = true // Fallback se biometria nÃ£o disponÃ­vel
        }
    }

    if (isAuthenticatedByBiometrics) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            // --- LOGIN ---
            composable(NavRoutes.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(NavRoutes.Perfil.route) {
                            popUpTo(NavRoutes.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            // --- TERMINAL ---
            composable(
                route = NavRoutes.Terminal.route,
                arguments = listOf(navArgument("userId") { 
                    type = NavType.IntType
                    defaultValue = -1 
                })
            ) { backStackEntry ->
                val userIdArg = backStackEntry.arguments?.getInt("userId") ?: -1
                
                LiveTerminalScreen(
                    userIdFromNav = userIdArg,
                    onLogoutClick = {
                        SecurityManager.limparSessao(context)
                        navController.navigate(NavRoutes.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onBackClick = { navController.popBackStack() },
                    onNewsClick = { navController.navigate(NavRoutes.News.route) },
                    onGlobalClick = { navController.navigate(NavRoutes.GlobalManagement.route) },
                    onBacktestClick = { navController.navigate(NavRoutes.Backtest.route) },
                    onMonteCarloClick = { navController.navigate(NavRoutes.MonteCarlo.route) },
                    onAssetAnalysisClick = { navController.navigate(NavRoutes.AssetAnalysis.route) }
                )
            }

            // --- CRM (CENTRAL DE GESTÃƒO) ---
            composable(NavRoutes.Crm.route) {
                CentralGestaoScreen(
                    onEditUser = { user ->
                        navController.navigate(NavRoutes.EditUser.createRoute(user.id))
                    },
                    onCalibrateUser = { user ->
                        navController.navigate(NavRoutes.Calibration.createRoute(user.id))
                    },
                    onNavigateToProfile = { navController.navigate(NavRoutes.Perfil.route) },
                    onNavigateToTerminal = { user ->
                        navController.navigate(NavRoutes.Terminal.createRoute(user.id))
                    },
                    onNavigateToGlobal = { navController.navigate(NavRoutes.GlobalManagement.route) }
                )
            }

            // --- NEWS ---
            composable(NavRoutes.News.route) {
                NewsScreen(onBackClick = { navController.popBackStack() })
            }

            // --- CALIBRAGEM ---
            composable(
                route = NavRoutes.Calibration.route,
                arguments = listOf(navArgument("userId") { type = NavType.IntType })
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getInt("userId") ?: 0
                var userDetail by remember { mutableStateOf<UsuarioResumo?>(null) }
                
                LaunchedEffect(userId) {
                    if (userId > 0) {
                        val resp = RetrofitClient.getApi(context).obterInfoUsuario(userId)
                        if (resp.isSuccessful) userDetail = resp.body()
                    }
                }

                CalibragemScreen(
                    user = userDetail,
                    onBackClick = { navController.popBackStack() },
                    onCalibrationSuccess = { navController.popBackStack() }
                )
            }

            // --- EDITAR USUÃRIO ---
            composable(
                route = NavRoutes.EditUser.route,
                arguments = listOf(navArgument("userId") { type = NavType.IntType })
            ) { backStackEntry ->
                val userId = backStackEntry.arguments?.getInt("userId") ?: 0
                var userDetail by remember { mutableStateOf<UsuarioResumo?>(null) }
                
                LaunchedEffect(userId) {
                    if (userId > 0) {
                        val resp = RetrofitClient.getApi(context).obterInfoUsuario(userId)
                        if (resp.isSuccessful) userDetail = resp.body()
                    }
                }

                userDetail?.let { user ->
                    EditarUsuarioScreen(
                        user = user,
                        onBackClick = { navController.popBackStack() },
                        onSaveSuccess = { navController.popBackStack() }
                    )
                }
            }

            // --- PERFIL GESTOR ---
            composable(NavRoutes.Perfil.route) {
                PerfilGestorScreen(
                    onBackClick = { navController.navigate(NavRoutes.Crm.route) },
                    onEditProfileClick = { user ->
                        navController.navigate(NavRoutes.EditUser.createRoute(user.id))
                    },
                    onCalibrateRobotClick = { user ->
                        navController.navigate(NavRoutes.Calibration.createRoute(user.id))
                    },
                    onLogoutSuccess = {
                        navController.navigate(NavRoutes.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            // --- GESTÃƒO GLOBAL (AUM) ---
            composable(NavRoutes.GlobalManagement.route) {
                GlobalManagementScreen(
                    onTerminalClick = { navController.navigate(NavRoutes.Terminal.route) },
                    onPortfolioClick = { navController.navigate(NavRoutes.Portfolio.route) },
                    onCrmClick = { navController.navigate(NavRoutes.Crm.route) },
                    onProfileClick = { navController.navigate(NavRoutes.Perfil.route) },
                    onComplianceClick = { navController.navigate(NavRoutes.Compliance.route) },
                    onInstitutionalClick = { navController.navigate(NavRoutes.Institutional.route) },
                    onAssetAnalysisClick = { navController.navigate(NavRoutes.AssetAnalysis.route) },
                    onBatchOperationsClick = { navController.navigate(NavRoutes.BatchOperations.route) }
                )
            }

            // --- COMPLIANCE ---
            composable(NavRoutes.Compliance.route) {
                ComplianceScreen(
                    onBackClick = { navController.popBackStack() },
                    onTerminalClick = { navController.navigate(NavRoutes.Terminal.route) },
                    onPortfolioClick = { navController.navigate(NavRoutes.GlobalManagement.route) },
                    onCrmClick = { navController.navigate(NavRoutes.Crm.route) },
                    onProfileClick = { navController.navigate(NavRoutes.Perfil.route) },
                    onAccountingClick = { navController.navigate(NavRoutes.Accounting.route) }
                )
            }

            // --- CONTÃBIL ---
            composable(NavRoutes.Accounting.route) {
                AccountingScreen(onBackClick = { navController.popBackStack() })
            }

            // --- INSTITUCIONAL (TEARSHEET) ---
            composable(NavRoutes.Institutional.route) {
                InstitutionalTearsheetScreen(
                    onTerminalClick = { navController.navigate(NavRoutes.Terminal.route) },
                    onPortfolioClick = { navController.navigate(NavRoutes.GlobalManagement.route) },
                    onCrmClick = { navController.navigate(NavRoutes.Crm.route) },
                    onProfileClick = { navController.navigate(NavRoutes.Perfil.route) }
                )
            }

            // --- BACKTEST ---
            composable(NavRoutes.Backtest.route) {
                BacktestScreen(onBackClick = { navController.popBackStack() })
            }

            // --- MONTE CARLO ---
            composable(NavRoutes.MonteCarlo.route) {
                MonteCarloScreen(onBackClick = { navController.popBackStack() })
            }

            // --- ASSET ANALYSIS (RAIO-X) ---
            composable(NavRoutes.AssetAnalysis.route) {
                AssetAnalysisScreen(onBack = { navController.popBackStack() })
            }

            // --- PORTFOLIO ---
            composable(NavRoutes.Portfolio.route) {
                PortfolioScreen(onBackClick = { navController.popBackStack() })
            }

            // --- BATCH OPERATIONS (CARRINHO NOTURNO) ---
            composable(NavRoutes.BatchOperations.route) {
                BatchOperationsScreen(onBackClick = { navController.popBackStack() })
            }
        }
    }
}