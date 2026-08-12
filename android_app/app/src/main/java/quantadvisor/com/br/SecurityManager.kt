package quantadvisor.com.br

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurityManager {
    private const val PREFS_NAME = "quantadvisor_secure_vault"

    private fun getEncryptedPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun salvarSessao(context: Context, token: String, usuarioId: Int, nome: String, role: String) {
        getEncryptedPrefs(context).edit().apply {
            putString("jwt_token", token)
            putInt("usuario_id", usuarioId)
            putString("nome", nome)
            putString("role", role)
            apply()
        }
    }

    fun getToken(context: Context): String? {
        return getEncryptedPrefs(context).getString("jwt_token", null)
    }

    fun getUsuarioId(context: Context): Int {
        return getEncryptedPrefs(context).getInt("usuario_id", 0)
    }

    fun limparSessao(context: Context) {
        getEncryptedPrefs(context).edit().clear().apply()
    }
}