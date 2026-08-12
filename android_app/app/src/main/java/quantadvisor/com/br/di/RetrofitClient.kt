package quantadvisor.com.br.di

import android.content.Context
import quantadvisor.com.br.data.api.QuantApiService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Ponte de compatibilidade para acessar o Hilt em componentes nÃ£o-injetados
 */
object RetrofitClient {
    
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RetrofitEntryPoint {
        fun getApiService(): QuantApiService
    }

    fun getApi(context: Context): QuantApiService {
        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext, 
            RetrofitEntryPoint::class.java
        )
        return entryPoint.getApiService()
    }
}