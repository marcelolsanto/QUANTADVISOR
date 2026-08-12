package quantadvisor.com.br.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import quantadvisor.com.br.SecurityManager
import quantadvisor.com.br.data.api.ExternalNewsApi
import quantadvisor.com.br.data.api.QuantApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object NetworkEvents {
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired = _sessionExpired.asSharedFlow()
    fun notifySessionExpired() { _sessionExpired.tryEmit(Unit) }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    var BASE_URL: String = "https://quantadvisor.com.br/api/"

    @Provides
    @Singleton
    fun provideCertificatePinner(): CertificatePinner {
        val leaf = "sha256/rrZvta5oNtSSjjKJnV1DBCJUrEm/qfTyu/Qyb+FpNkg="
        val intermediate = "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4="
        val root = "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="
        
        return CertificatePinner.Builder()
            .add("quantadvisor.com.br", leaf, intermediate, root)
            .add("www.quantadvisor.com.br", leaf, intermediate, root)
            .add("*.quantadvisor.com.br", leaf, intermediate, root)
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        certificatePinner: CertificatePinner
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        
        val builder = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val token = SecurityManager.getToken(context)
                val request = chain.request().newBuilder().apply {
                    if (token != null) addHeader("Authorization", "Bearer $token")
                    removeHeader("Host") 
                }.build()
                val response = chain.proceed(request)
                if (response.code == 401) NetworkEvents.notifySessionExpired()
                response
            }

        // Aplica o Certificate Pinner apenas quando for conexão de produção HTTPS
        if (BASE_URL.contains("quantadvisor.com.br") && BASE_URL.startsWith("https://")) {
            builder.certificatePinner(certificatePinner)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideQuantApiService(okHttpClient: OkHttpClient): QuantApiService {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(QuantApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideExternalNewsApi(okHttpClient: OkHttpClient): ExternalNewsApi {
        // Para chamadas externas, usamos a mesma base ou uma genÃ©rica, 
        // jÃ¡ que o endpoint usa @Url completa
        return Retrofit.Builder()
            .baseUrl("https://api.rss2json.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ExternalNewsApi::class.java)
    }
}