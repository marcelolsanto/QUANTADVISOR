package quantadvisor.com.br.data.api

import quantadvisor.com.br.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface QuantApiService {

    // ==========================================
    // AUTENTICAÃ‡ÃƒO E USUÃRIOS
    // ==========================================
    @POST("login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("usuarios/solicitar-cadastro")
    suspend fun solicitarCadastro(@Body request: NovaContaRequest): Response<GenericResponse>

    @POST("usuarios/validar-cadastro")
    suspend fun validarCadastro(@Body request: ValidarCadastroRequest): Response<GenericResponse>

    @GET("usuarios")
    suspend fun listarUsuarios(): Response<List<UsuarioResumo>>

    @GET("usuario")
    suspend fun obterInfoUsuario(@Query("id") id: Int? = null): Response<UsuarioResumo>

    @POST("usuarios/editar")
    suspend fun editarUsuario(@Body request: EditarContaRequest): Response<GenericResponse>

    @POST("usuarios/deletar")
    suspend fun deletarUsuario(@Body request: DeletarContaRequest): Response<GenericResponse>

    @GET("perfis")
    suspend fun listarPerfis(): Response<List<PerfilInvestidor>>

    // ==========================================
    // DASHBOARD E PORTFÃ“LIO
    // ==========================================
    @GET("carteira")
    suspend fun getCarteira(@Query("usuario_id") usuarioId: Int? = null): Response<CarteiraResponse>

    @GET("dashboard/resumo")
    suspend fun getResumoDashboard(@Query("usuario_id") usuarioId: Int): Response<ResumoDashboard>

    @GET("dashboard/macro")
    suspend fun getDashboardMacro(): Response<MacroResponse>

    @GET("ativo/detalhes")
    suspend fun getDetalhesAtivo(@Query("ticker") ticker: String): Response<AssetAnalysisResponse>

    @GET("dashboard/historico")
    suspend fun getDashboardHistorico(@Query("usuario_id") usuarioId: Int): Response<List<PontoHistorico>>

    @GET("dashboard/historico-ativos")
    suspend fun getDashboardHistoricoAtivos(@Query("usuario_id") usuarioId: Int): Response<List<Map<String, Any>>>

    // ==========================================
    // OPERAÃ‡Ã•ES E TERMINAL
    // ==========================================
    @GET("auditoria")
    suspend fun getAuditoriaMercado(): Response<AuditoriaResponse>

    @POST("ordem")
    suspend fun enviarOrdem(@Body request: OrdemRequest): Response<GenericResponse>

    @POST("cambio")
    suspend fun realizarCambio(@Body request: CambioRequest): Response<GenericResponse>

    @POST("piloto/toggle")
    suspend fun togglePilotoAutomatico(@Body request: TogglePilotoReq): Response<GenericResponse>

    @GET("historico")
    suspend fun getHistoricoOrdens(@Query("usuario_id") usuarioId: Int? = null): Response<List<OrdemExecutada>>

    @GET("carrinho")
    suspend fun listarCarrinho(@Query("usuario_id") usuarioId: Int? = null): Response<List<CarrinhoItem>>

    @POST("adicionar-carrinho")
    suspend fun adicionarCarrinho(@Body request: OrdemRequest): Response<GenericResponse>

    @POST("carrinho/limpar")
    suspend fun limparCarrinho(@Body request: LimparCarrinhoReq): Response<GenericResponse>

    @GET("parametros")
    suspend fun getParametros(@Query("usuario_id") usuarioId: Int? = null): Response<ParametrosOperacionais>

    @POST("parametros")
    suspend fun configurarRobo(@Body request: ParametrosOperacionais): Response<GenericResponse>

    // ==========================================
    // COMPLIANCE
    // ==========================================
    @GET("compliance/lancamentos")
    suspend fun listarLancamentos(@Query("usuario_id") usuarioId: Int): Response<List<ItemLancamento>>

    @GET("compliance/lotes")
    suspend fun listarLotes(@Query("usuario_id") usuarioId: Int): Response<List<LoteFiscal>>

    @GET("compliance/resumo-fiscal")
    suspend fun getResumoFiscal(@Query("usuario_id") usuarioId: Int, @Query("ano_mes") anoMes: String): Response<ResumoFiscalMensal>

    // ==========================================
    // IA E QUANT
    // ==========================================
    @POST("ingestao/iniciar")
    suspend fun iniciarIngestaoManual(): Response<GenericResponse>

    @GET("backtest")
    suspend fun backtest(@Query("ticker") ticker: String): Response<BacktestResponse>

    @GET("risco")
    suspend fun getRiscoSistemico(): Response<RiscoResponse>

    @GET("montecarlo")
    suspend fun monteCarlo(@Query("ticker") ticker: String): Response<MonteCarloResponse>

    @POST("otimizar")
    suspend fun otimizarCarteira(@Query("usuario_id") usuarioId: Int): Response<GenericResponse>

    @GET("portfolio/projecao")
    suspend fun getPortfolioProjecao(@Query("usuario_id") usuarioId: Int? = null): Response<ProjecaoResponse>

    @POST("ml/prever")
    suspend fun preverLSTM(@Query("ticker") ticker: String): Response<LstmResponse>

    @POST("agente/causalidade")
    suspend fun agenteCausalidade(@Body request: Map<String, String>): Response<Map<String, Any>>

    // ==========================================
    // INSTITUCIONAL
    // ==========================================
    @GET("institucional/resumo")
    suspend fun getInstitucionalResumo(@Query("usuario_id") usuarioId: Int? = null): Response<ResumoEstrategia>

    @GET("institucional/curva-capital")
    suspend fun getCurvaCapital(@Query("usuario_id") usuarioId: Int? = null): Response<List<PontoCurvaCapital>>

    @GET("institucional/replay")
    suspend fun getReplayDecisao(@Query("usuario_id") usuarioId: Int? = null): Response<List<ReplayDecisao>>
}

interface ExternalNewsApi {
    @GET
    suspend fun getGoogleNews(@Url url: String): Response<Rss2JsonRoot>
}