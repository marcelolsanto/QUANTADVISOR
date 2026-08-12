package quantadvisor.com.br

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import quantadvisor.com.br.ui.navigation.AppNavHost
import quantadvisor.com.br.ui.navigation.NavRoutes
import quantadvisor.com.br.ui.theme.BgColor

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val tokenSalvo = SecurityManager.getToken(this)
            val startDest = if (tokenSalvo != null) NavRoutes.Perfil.route else NavRoutes.Login.route

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = BgColor
            ) {
                AppNavHost(startDestination = startDest)
            }
        }
    }
}