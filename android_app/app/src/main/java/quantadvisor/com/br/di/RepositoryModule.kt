package quantadvisor.com.br.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import quantadvisor.com.br.data.api.ExternalNewsApi
import quantadvisor.com.br.data.api.QuantApiService
import quantadvisor.com.br.data.repository.MarketRepository
import javax.inject.Singleton
import com.google.gson.Gson

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideMarketRepository(
        api: QuantApiService,
        newsApi: ExternalNewsApi,
        okHttpClient: OkHttpClient
    ): MarketRepository {
        return MarketRepository(api, newsApi, okHttpClient, Gson())
    }
}