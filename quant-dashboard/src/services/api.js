
import axios from 'axios';

const api = axios.create({
  // A baseURL fica vazia para o Proxy do vite.config.js assumir o roteamento
  baseURL: ''
});

// =========================================================================
// INTERCEPTOR DE AUTENTICAÇÃO (JWT)
// =========================================================================
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('@QuantAdvisor:token_web');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Interceptor de expiração de token (HTTP 401)
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      realizarLogoutWeb();
      if (window.location.pathname !== '/') {
        window.location.reload();
      }
    }
    return Promise.reject(error);
  }
);

// =========================================================================
// ROTAS DE SESSÃO E LOGIN
// =========================================================================
export const realizarLoginWeb = async (login, senha) => {
  try {
    const response = await api.post('/api/login', { login, senha });
    
    if (response.data.sucesso) {
      localStorage.setItem('@QuantAdvisor:token_web', response.data.token);
      localStorage.setItem('@QuantAdvisor:user_id_web', String(response.data.usuario_id));
      localStorage.setItem('@QuantAdvisor:role_web', response.data.role);
      localStorage.setItem('@QuantAdvisor:nome_web', response.data.nome);
      return { sucesso: true, dados: response.data };
    }
    return { sucesso: false, erro: 'Credenciais recusadas' };
  } catch (error) {
    return { sucesso: false, erro: error.response?.data?.erro || 'Erro de conexão com o servidor' };
  }
};

export const realizarLogoutWeb = () => {
  localStorage.removeItem('@QuantAdvisor:token_web');
  localStorage.removeItem('@QuantAdvisor:user_id_web');
  localStorage.removeItem('@QuantAdvisor:role_web');
  localStorage.removeItem('@QuantAdvisor:nome_web');
};

// =========================================================================
// APIs EXISTENTES DA PLATAFORMA
// =========================================================================
export const getAuditoria = () => api.get('/api/auditoria');
export const enviarOrdem = (payload) => api.post('/api/ordem', payload);
export const dispararIngestao = () => api.get('/api/ingestao/iniciar');
export const getHistorico = (usuarioId) => api.get(`/api/historico?usuario_id=${usuarioId}`);
export const getUsuarios = () => api.get('/api/usuarios');
export const criarUsuario = (payload) => api.post('/api/usuarios/criar', payload);
export const getPerfis = () => api.get('/api/perfis');
export const getBacktest = (ticker) => api.get(`/api/backtest?ticker=${ticker}`);
export const getRiscoSistemico = () => api.get('/api/risco');
export const getMonteCarlo = (ticker) => api.get(`/api/montecarlo?ticker=${ticker}`);
export const getProjecaoPortfolio = (usuarioId) => api.get(`/api/portfolio/projecao?usuario_id=${usuarioId}`);
export const getPrevisaoLSTM = (ticker) => api.get(`/api/ml/prever?ticker=${ticker}`);
export const getCotacoesEmLote = (tickers) => api.get(`/api/cotacao?ticker=${tickers}`);
export const sugerirAoCarrinho = (payload) => api.post('/api/carrinho/sugerir', payload);
export const adicionarAoCarrinho = (payload) => api.post('/api/adicionar-carrinho', payload);
export const getCarrinho = (usuarioId) => api.get(`/api/carrinho?usuario_id=${usuarioId}`);
export const limparCarrinho = (payload) => api.post('/api/carrinho/limpar', payload);
export const consultarCROSintetico = (payload) => api.post('/api/agente/causalidade', payload);
export const otimizarCarteira = (usuarioId) => api.post(`/api/otimizar?usuario_id=${usuarioId}`);
export const deletarUsuario = (payload) => api.post('/api/usuarios/deletar', payload);
export const editarUsuario = (payload) => api.post('/api/usuarios/editar', payload);
export const getLancamentosContabeis = (usuarioId) => api.get(`/api/compliance/lancamentos?usuario_id=${usuarioId}`);
export const getLotesFiscais = (usuarioId) => api.get(`/api/compliance/lotes?usuario_id=${usuarioId}`);
export const getResumoFiscal = (usuarioId, anoMes) => api.get(`/api/compliance/resumo-fiscal?usuario_id=${usuarioId}&ano_mes=${anoMes}`);
export const getUsuarioInfo = (usuarioId) => api.get(`/api/usuario?id=${usuarioId}`);
export const solicitarCadastro = (payload) => api.post('/api/usuarios/solicitar-cadastro', payload);
export const validarCadastro = (payload) => api.post('/api/usuarios/validar-cadastro', payload);
export const getDetalhesAtivo = (ticker) => api.get(`/api/ativo/detalhes?ticker=${ticker}`);
export const togglePiloto = (payload) => api.post('/api/piloto/toggle', payload);
export const getResumoInstitucional = (usuarioId) => api.get(`/api/institucional/resumo?usuario_id=${usuarioId}`);
export const getCurvaCapital = (usuarioId) => api.get(`/api/institucional/curva-capital?usuario_id=${usuarioId}`);
export const getReplayDecisao = (usuarioId) => api.get(`/api/institucional/replay?usuario_id=${usuarioId}`);
export const realizarCambio = (payload) => api.post('/api/cambio', payload);

// =========================================================================
// ⚙️ ROTAS DA MESA DE CONTROLE QUANTITATIVA
// =========================================================================
export const getParametros = (usuarioId) => api.get(`/api/parametros?usuario_id=${usuarioId}`);
export const updateParametros = (payload) => api.post('/api/parametros', payload);
// Busca a custódia atual do cliente (Marcação a Mercado)
// Busca a custódia atual do cliente (Marcação a Mercado)
export const getPosicoesAbertas = (usuarioId) => {
    const params = usuarioId ? `?usuario_id=${usuarioId}` : '';
    return api.get(`/api/carteira${params}`).then(res => {
        // Retorna apenas o array de posições para facilitar o uso no Tearsheet
        return { data: res.data.posicoes || [] };
    });
};

export default api;

