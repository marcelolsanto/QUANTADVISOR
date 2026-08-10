
import { useEffect, useState, useMemo } from 'react';
import { getAuditoria, dispararIngestao, getUsuarios, realizarLogoutWeb } from './services/api';
import { LoginScreen } from './components/LoginScreen';
import { DashboardChart } from './components/DashboardChart';
import { MtMTable } from './components/MtMTable';
import { StatCards } from './components/StatCards';
import { Portfolio } from './components/Portfolio';
import { HistoricoTable } from './components/HistoricoTable';
import { AccountManager } from './components/AccountManager';
import { RiskModal } from './components/RiskModal';
import { PortfolioVision } from './components/PortfolioVision';
import CarrinhoSugestoes from './components/CarrinhoSugestoes';
import { RiskCROModal } from './components/RiskCROModal';
import { ComplianceView } from './components/ComplianceView';
import { UserMenu } from './components/UserMenu';
import PainelGestao from "./components/PainelGestao";
import { TearsheetB2B } from './components/TearsheetB2B';
import { ConfigMotorModal } from './components/ConfigMotorModal';
import { LiveTerminal } from './components/LiveTerminal';
import { NewsTicker } from './components/NewsTicker';
import { AnalyticsBenchmark } from './components/AnalyticsBenchmark';
import { theme } from './theme';
import { Toaster, toast } from 'sonner';

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [usuarioLogado, setUsuarioLogado] = useState({
    nome: localStorage.getItem('@QuantAdvisor:nome_web') || '',
    role: localStorage.getItem('@QuantAdvisor:role_web') || ''
  });

  const myUserId = Number(localStorage.getItem('@QuantAdvisor:user_id_web'));
  const isGestor = usuarioLogado.role === 'GESTOR' || myUserId === 1;

  const [telaAtiva, setTelaAtiva] = useState(isGestor ? 'GESTAO' : 'TERMINAL');
  const [contaAtiva, setContaAtiva] = useState(isGestor ? null : myUserId);
  const [perfilContaAtiva, setPerfilContaAtiva] = useState('SOFISTICADO');

  const [data, setData] = useState([]);
  const [regimeMercado, setRegimeMercado] = useState('ANALISANDO...');
  const [loading, setLoading] = useState(true);
  const [modalCROAberto, setModalCROAberto] = useState(false);
  const [mensagemLoader, setMensagemLoader] = useState('⏳ O Motor Python está processando a econometria...');
  const [erroBackend, setErroBackend] = useState('');
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [listaUsuarios, setListaUsuarios] = useState([]);
  const [modalRiscoAberto, setModalRiscoAberto] = useState(false);
  const [sortConfig, setSortConfig] = useState({ key: 'z_score', direction: 'asc' });

  const [mercadoAtivo, setMercadoAtivo] = useState('BRL');
  const [configModalGlobal, setConfigModalGlobal] = useState(null);

  useEffect(() => {
    const handler = (e) => setConfigModalGlobal(e.detail);
    window.addEventListener('abrirConfigMotor', handler);
    return () => window.removeEventListener('abrirConfigMotor', handler);
  }, []);

  const carregarMatematica = async () => {
    try {
      const res = await getAuditoria();
      if (res.data && res.data.sucesso) {
        setData(res.data.recomendacoes || []);
        // 👇 SALVA O REGIME AQUI
        setRegimeMercado(res.data.regime || 'DESCONHECIDO');
        setErroBackend('');
      } else {
        setErroBackend(res.data?.erro || "O motor Python não retornou dados.");
      }
    } catch (err) {
      setErroBackend("Falha de conexão. O motor pode estar offline.");
    }
  };

  const carregarDropdownUsuarios = async () => {
    try {
      const res = await getUsuarios();
      // 🛡️ PROTEÇÃO: Garante Array
      setListaUsuarios(res.data || []);
    } catch (err) {
      console.error("Falha ao buscar contas");
    }
  };

  useEffect(() => {
    if (!isAuthenticated) return;
    let isMounted = true;
    let timerId = null;

    carregarDropdownUsuarios();

    const fetchData = async () => {
      if (!isMounted) return;
      try {
        if (!document.hidden) {
          await carregarMatematica();
        }
      } catch (error) {
        console.error("Erro no polling de matemática:", error);
      } finally {
        if (isMounted) {
          setLoading(false);
          timerId = setTimeout(fetchData, 10000);
        }
      }
    };

    fetchData();
    return () => {
      isMounted = false;
      if (timerId) clearTimeout(timerId);
    };
  }, [contaAtiva, isAuthenticated]);

  // 🛡️ CÁLCULO SEGURO: Evita Crash se o item.ativo for undefined
  const dadosPorPais = useMemo(() => {
    if (!Array.isArray(data)) return [];
    return data.filter(item => {
      const nomeAtivo = item?.ativo || '';
      const isEstrangeiro = !/\d/.test(nomeAtivo) && !nomeAtivo.endsWith('.SA');
      return mercadoAtivo === 'USD' ? isEstrangeiro : !isEstrangeiro;
    });
  }, [data, mercadoAtivo]);

  // 🛡️ ORDENAÇÃO SEGURA: Trata null e undefined antes do localeCompare
  const dadosOrdenados = useMemo(() => {
    if (!Array.isArray(dadosPorPais) || dadosPorPais.length === 0) return [];
    let dadosFiltrados = [...dadosPorPais];
    dadosFiltrados.sort((a, b) => {
      let valA = a[sortConfig.key];
      let valB = b[sortConfig.key];
      if (sortConfig.key === 'ativo') {
        const strA = String(valA || '');
        const strB = String(valB || '');
        return sortConfig.direction === 'asc' ? strA.localeCompare(strB) : strB.localeCompare(strA);
      }
      const numA = Number(valA) || 0;
      const numB = Number(valB) || 0;
      return sortConfig.direction === 'asc' ? numA - numB : numB - numA;
    });
    return dadosFiltrados;
  }, [dadosPorPais, sortConfig]);

  const handleSortClick = (key) => {
    let direction = 'asc';
    if (sortConfig.key === key && sortConfig.direction === 'asc') direction = 'desc';
    setSortConfig({ key, direction });
  };

  const handleRefresh = async () => {
    setIsRefreshing(true);
    setLoading(true);
    setErroBackend('');
    try {
      setMensagemLoader('📡 Conectando ao Yahoo Finance...');
      await dispararIngestao();
      setMensagemLoader('🧠 Ingestão concluída! Rodando modelos...');
      await carregarMatematica();
    } catch (err) {
      setErroBackend("Erro ao atualizar o mercado.");
    } finally {
      setIsRefreshing(false);
      setLoading(false);
      setMensagemLoader('⏳ O Motor Python está processando a econometria...');
    }
  };

  const entrarNoTerminal = (id, perfil) => {
    setContaAtiva(id);
    setPerfilContaAtiva(perfil);
    carregarDropdownUsuarios();
    setTelaAtiva('TERMINAL');
  };

  const navButtonStyle = (isAtiva) => ({
    padding: '12px 24px', backgroundColor: isAtiva ? theme.bg : 'transparent', color: isAtiva ? theme.info : theme.textMuted,
    border: 'none', borderRadius: '8px 8px 0 0', fontWeight: '600', cursor: 'pointer',
    borderBottom: isAtiva ? `3px solid ${theme.info}` : '3px solid transparent', transition: 'all 0.2s ease', fontSize: '0.95rem'
  });

  const sortButtonStyle = (key) => {
    const isAtivo = sortConfig.key === key;
    return {
      padding: '6px 12px', backgroundColor: isAtivo ? 'rgba(59, 130, 246, 0.15)' : theme.cardBg, color: isAtivo ? theme.info : theme.textMuted,
      border: `1px solid ${isAtivo ? theme.info : theme.border}`, borderRadius: '4px', cursor: 'pointer', fontSize: '0.8rem',
      fontWeight: 'bold', transition: 'all 0.2s ease', display: 'flex', alignItems: 'center', gap: '5px'
    };
  };

  const handleLogout = () => {
    realizarLogoutWeb();
    setIsAuthenticated(false);
    setContaAtiva(null);
    setUsuarioLogado({ nome: '', role: '' });
  };

  if (!isAuthenticated) {
    return (
      <LoginScreen onLoginSuccess={(dados) => {
        const userId = Number(dados.usuario_id);
        const gestorCheck = dados.role === 'GESTOR' || userId === 1;

        setUsuarioLogado({ nome: dados.nome, role: dados.role });

        setTelaAtiva(gestorCheck ? 'GESTAO' : 'TERMINAL');
        setContaAtiva(gestorCheck ? null : userId);

        setIsAuthenticated(true);
      }} />
    );
  }

  // Busca segura do nome de usuário
  const nomeOperadorLogado = (Array.isArray(listaUsuarios) ? listaUsuarios : []).find(u => u.id === contaAtiva)?.nome || 'Operador';

  return (
    <div style={{ backgroundColor: theme.bg, minHeight: '100vh', color: theme.textMain }}>
      <Toaster richColors position="top-right" theme="dark" expand={true} closeButton />
      <div style={{ backgroundColor: theme.cardBg, borderBottom: `1px solid ${theme.border}`, padding: '0 40px', height: '70px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <h2 style={{ color: theme.textMain, margin: 0, letterSpacing: '1px' }}>
            <span style={{ color: theme.info }}>Quant</span>Advisor
          </h2>
          <span style={{
            fontSize: '0.75rem', padding: '4px 10px', borderRadius: '12px',
            backgroundColor: mercadoAtivo === 'BRL' ? 'rgba(16, 185, 129, 0.15)' : 'rgba(59, 130, 246, 0.15)',
            color: mercadoAtivo === 'BRL' ? theme.compra : theme.info,
            border: `1px solid ${mercadoAtivo === 'BRL' ? theme.compra : theme.info}`,
            fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '6px'
          }}>
            <span style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: mercadoAtivo === 'BRL' ? theme.compra : theme.info }}></span>
            {mercadoAtivo === 'BRL' ? '🇧🇷 B3 (BRL)' : '🇺🇸 Wall St. (USD)'}
          </span>
        </div>

        <div className="mobile-nav" style={{ display: 'flex', gap: '5px', height: '100%', alignItems: 'flex-end' }}>
          <button style={navButtonStyle(telaAtiva === 'TERMINAL')} onClick={() => setTelaAtiva('TERMINAL')} disabled={!contaAtiva}>
            💻 Terminal de Operações
          </button>

          <button style={navButtonStyle(telaAtiva === 'DASHBOARD')} onClick={() => setTelaAtiva('DASHBOARD')} disabled={!contaAtiva}>
            📈 Painel de Gestão
          </button>

          <button style={navButtonStyle(telaAtiva === 'PORTFOLIO')} onClick={() => setTelaAtiva('PORTFOLIO')} disabled={!contaAtiva}>
            📊 Visão de Portfólio
          </button>
          <button style={navButtonStyle(telaAtiva === 'COMPLIANCE')} onClick={() => setTelaAtiva('COMPLIANCE')} disabled={!contaAtiva}>
            📓 Compliance & Fiscal
          </button>
          <button style={navButtonStyle(telaAtiva === 'ANALYTICS')} onClick={() => setTelaAtiva('ANALYTICS')} disabled={!contaAtiva}>
            📈 Analytics & Benchmarks
          </button>
          {isGestor && (
            <button style={navButtonStyle(telaAtiva === 'TEARSHEET')} onClick={() => setTelaAtiva('TEARSHEET')} disabled={!contaAtiva}>
              🏛️ Institucional (B2B)
            </button>
          )}
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <UserMenu
              nome={usuarioLogado.nome}
              role={usuarioLogado.role}
              onLogout={handleLogout}
              setTelaAtiva={setTelaAtiva}
              mercadoAtivo={mercadoAtivo}
              setMercadoAtivo={setMercadoAtivo}
            />
          </div>
        </div>
      </div>

      <div className="mobile-container" style={{ padding: '40px', maxWidth: '1400px', margin: '0 auto', width: '100%' }}>

        {telaAtiva === 'TEARSHEET' && contaAtiva && (
          <TearsheetB2B />
        )}

        {telaAtiva === 'GESTAO' && (
          <AccountManager onSelecionarConta={entrarNoTerminal} isGestor={isGestor} myUserId={myUserId} />
        )}

        {telaAtiva === 'TERMINAL' && contaAtiva && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

            <div className="lg:col-span-2 space-y-6">

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '15px', borderRadius: '8px', flexWrap: 'wrap', gap: '15px' }}>
                <div style={{ display: 'flex', gap: '10px' }}>
                  <button onClick={handleRefresh} disabled={isRefreshing} style={{ padding: '8px 15px', backgroundColor: isRefreshing ? theme.border : theme.alerta, color: '#fff', border: 'none', borderRadius: '4px', cursor: isRefreshing ? 'wait' : 'pointer', fontWeight: 'bold' }}>
                    {isRefreshing ? '🔄 Atualizando...' : '⚡ Sincronizar Mercado'}
                  </button>
                  {/* 👇 O NOVO SEMÁFORO DE REGIME AQUI 👇 */}
                  <div style={{
                    padding: '8px 15px',
                    borderRadius: '4px',
                    fontWeight: 'bold',
                    fontSize: '0.9rem',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    border: `1px solid ${regimeMercado.includes('BULL') ? theme.compra : (regimeMercado.includes('BEAR') ? theme.venda : theme.alerta)}`,
                    backgroundColor: regimeMercado.includes('BULL') ? 'rgba(16, 185, 129, 0.1)' : (regimeMercado.includes('BEAR') ? 'rgba(239, 68, 68, 0.1)' : 'rgba(245, 158, 11, 0.1)'),
                    color: regimeMercado.includes('BULL') ? theme.compra : (regimeMercado.includes('BEAR') ? theme.venda : theme.alerta)
                  }}>
                    {regimeMercado.includes('BULL') ? '🐂' : (regimeMercado.includes('BEAR') ? '🐻' : '🦀')} {regimeMercado}
                  </div>
                  <button onClick={() => setModalRiscoAberto(true)} style={{ padding: '8px 15px', backgroundColor: '#8e44ad', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}>
                    🛡️ Risco Sistêmico
                  </button>
                  <button onClick={() => setModalCROAberto(true)} style={{ padding: '8px 15px', backgroundColor: theme.info, color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}>
                    🧠 Consultar CRO (IA)
                  </button>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '15px', padding: '15px', }}>
                  <label style={{ fontWeight: 'bold', color: theme.textMuted }}>Operando como:</label>

                  {isGestor ? (
                    <select value={contaAtiva} onChange={(e) => {
                      const selectedId = Number(e.target.value);
                      const conta = (Array.isArray(listaUsuarios) ? listaUsuarios : []).find(u => u.id === selectedId);
                      setContaAtiva(selectedId);
                      if (conta) setPerfilContaAtiva(conta.perfil_risco);
                    }}
                      style={{ padding: '8px', borderRadius: '4px', border: `1px solid ${theme.info}`, backgroundColor: theme.bg, color: theme.textMain, fontWeight: 'bold', cursor: 'pointer', outline: 'none' }}
                    >
                      {(Array.isArray(listaUsuarios) ? listaUsuarios : []).map(u => (
                        <option key={u.id} value={u.id}>{u.id} - {u.nome} ({u.perfil_risco})</option>
                      ))}
                    </select>
                  ) : (
                    <span style={{ padding: '8px 15px', borderRadius: '4px', backgroundColor: 'rgba(59, 130, 246, 0.1)', color: theme.info, border: `1px solid ${theme.info}`, fontWeight: 'bold' }}>
                      {usuarioLogado.nome} (Sua Conta)
                    </span>
                  )}
                </div>
              </div>

              {loading && (
                <div style={{ marginBottom: '30px', marginTop: '30px' }}>
                  <StatCards loading={true} />
                  <div style={{ padding: '30px', textAlign: 'center', backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, borderRadius: '8px', marginBottom: '20px' }}>
                    <p style={{ color: theme.info, fontWeight: 'bold', fontSize: '1.1rem' }}>{mensagemLoader}</p>
                  </div>
                </div>
              )}

              {!loading && erroBackend && (
                <div style={{ padding: '20px', backgroundColor: 'rgba(239, 68, 68, 0.1)', border: `1px solid ${theme.venda}`, color: theme.venda, borderRadius: '8px' }}>
                  <h3 style={{ margin: '0 0 10px 0' }}>⚠️ Curto-Circuito no Backend</h3>
                  <p style={{ margin: 0 }}><strong>Diagnóstico:</strong> {erroBackend}</p>
                </div>
              )}

              {!loading && !erroBackend && Array.isArray(dadosPorPais) && dadosPorPais.length > 0 && (
                <div style={{ marginBottom: '30px', marginTop: '30px', gap: '15px', minHeight: '460px', }}>
                  <StatCards data={dadosPorPais} perfilUsuario={perfilContaAtiva} loading={loading} />
                  <Portfolio marketData={dadosPorPais} usuarioId={contaAtiva} />
                  {/* 👇 TERMINAL MATRIX REALOCADO AQUI (ACIMA DO GRÁFICO) 👇 */}
                  <div style={{ marginBottom: '24px' }}>
                    <LiveTerminal perfilUsuario={perfilContaAtiva} />
                  </div>
                  {/* 👆 ==================================================== 👆 */}
                  {configModalGlobal && (
                    <ConfigMotorModal
                      usuarioIdInicial={configModalGlobal.id}
                      nomeClienteInicial={configModalGlobal.nome}
                      clientes={listaUsuarios}
                      onClose={() => setConfigModalGlobal(null)}
                    />
                  )}

                  {/* 👇 O SEU NOVO RODAPÉ DE NOTÍCIAS 👇 */}
                  {isAuthenticated && contaAtiva && <NewsTicker usuarioId={contaAtiva} perfilUsuario={perfilContaAtiva} />}
                  <DashboardChart data={dadosOrdenados} perfilUsuario={perfilContaAtiva} />
                  <div style={{ display: 'flex', gap: '10px', alignItems: 'center', marginBottom: '30px', marginTop: '-10px', flexWrap: 'wrap' }}>
                    <span style={{ fontSize: '0.8rem', color: theme.textMuted, fontWeight: 'bold', textTransform: 'uppercase', marginRight: '5px' }}>
                      ⚙️ Ordenar Gráfico por:
                    </span>
                    <button onClick={() => handleSortClick('z_score')} style={sortButtonStyle('z_score')}>
                      🎯 Oportunidade (Z-Score) {sortConfig.key === 'z_score' ? (sortConfig.direction === 'asc' ? '🔼' : '🔽') : '↕️'}
                    </button>
                    <button onClick={() => handleSortClick('risco_var')} style={sortButtonStyle('risco_var')}>
                      ⚠️ Risco Máximo (VaR) {sortConfig.key === 'risco_var' ? (sortConfig.direction === 'asc' ? '🔼' : '🔽') : '↕️'}
                    </button>
                    <button onClick={() => handleSortClick('ativo')} style={sortButtonStyle('ativo')}>
                      🔤 Ativo (A-Z) {sortConfig.key === 'ativo' ? (sortConfig.direction === 'asc' ? '🔼' : '🔽') : '↕️'}
                    </button>
                  </div>
                  <MtMTable data={dadosPorPais} usuarioId={contaAtiva} nomeUsuario={nomeOperadorLogado} defaultPerfil={perfilContaAtiva} />
                  <HistoricoTable usuarioId={contaAtiva} />
                </div>
              )}

              {!loading && !erroBackend && Array.isArray(dadosPorPais) && dadosPorPais.length === 0 && (
                <div style={{ padding: '40px', textAlign: 'center', backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, borderRadius: '8px' }}>
                  <p style={{ color: theme.textMuted, fontStyle: 'italic' }}>Nenhuma ação detectada para este país ({mercadoAtivo}). O banco de dados pode estar vazio.</p>
                </div>
              )}
            </div>

            <div className="lg:col-span-1">
              <CarrinhoSugestoes usuarioId={contaAtiva} />
            </div>

          </div>
        )}

        {telaAtiva === 'COMPLIANCE' && contaAtiva && (
          <ComplianceView usuarioId={contaAtiva} />
        )}

        {telaAtiva === 'ANALYTICS' && contaAtiva && (
          <AnalyticsBenchmark usuarioId={contaAtiva} />
        )}

        {telaAtiva === 'PORTFOLIO' && contaAtiva && (
          <PortfolioVision usuarioId={contaAtiva} />
        )}

        {telaAtiva === 'DASHBOARD' && contaAtiva && (
          <PainelGestao usuarioId={contaAtiva} isGestor={isGestor} />
        )}

      </div>
      {modalRiscoAberto && <RiskModal onClose={() => setModalRiscoAberto(false)} />}
      {modalCROAberto && <RiskCROModal onClose={() => setModalCROAberto(false)} />}

      {/* 👇 PASSANDO A LISTA DE USUÁRIOS PARA O MODAL 👇 */}
      {configModalGlobal && (
        <ConfigMotorModal
          usuarioIdInicial={configModalGlobal.id}
          nomeClienteInicial={configModalGlobal.nome}
          clientes={listaUsuarios}
          onClose={() => setConfigModalGlobal(null)}
        />
      )}
    </div>
  );
}

export default App;

