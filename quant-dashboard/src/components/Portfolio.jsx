
import { useState, useEffect, useMemo } from 'react';
import api, { enviarOrdem, adicionarAoCarrinho, otimizarCarteira } from '../services/api';
import { AssetDeepDive } from './AssetDeepDive';
import { TradeModal } from './TradeModal';
import { CambioModal } from './CambioModal';
import { theme } from '../theme';
import { FilterBar } from './FilterBar';

// 🌍 FUNÇÃO AUXILIAR DE MOEDA NATIVA
const formatarMoedaNativa = (valor, ticker) => {
  const isEstrangeiro = !/\d/.test(ticker) && !ticker.endsWith('.SA');
  const moeda = isEstrangeiro ? 'USD' : 'BRL';
  return new Intl.NumberFormat(isEstrangeiro ? 'en-US' : 'pt-BR', {
    style: 'currency', currency: moeda, minimumFractionDigits: 2
  }).format(Number(valor) || 0);
};

export const Portfolio = ({ marketData, usuarioId, children }) => {
  const [carteira, setCarteira] = useState(null);
  const [erro, setErro] = useState(false);
  const [buscaTicker, setBuscaTicker] = useState('');
  const [filtroStatus, setFiltroStatus] = useState('TODOS');
  
  // 💱 ESTADOS DE CÂMBIO E MODAL
  const [cotacaoDolar, setCotacaoDolar] = useState(5.00);
  const [modalCambioAberto, setModalCambioAberto] = useState(false);

  const [otimizacao, setOtimizacao] = useState(null);
  const [loadingOtimizacao, setLoadingOtimizacao] = useState(false);
  const [erroOtimizacao, setErroOtimizacao] = useState('');

  const [sortConfig, setSortConfig] = useState({ key: 'ticker', direction: 'ascending' });
  const [ativoSelecionado, setAtivoSelecionado] = useState(null);
  const [ativoRaioX, setAtivoRaioX] = useState(null);

  const formatarDinheiro = (valor) => {
    const numero = Number(valor);
    if (isNaN(numero)) return "R$ 0,00";
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency', currency: 'BRL', minimumFractionDigits: 2, maximumFractionDigits: 2
    }).format(numero);
  };

  // 💱 BUSCA O DÓLAR UMA VEZ PARA OS CÁLCULOS GLOBAIS
  useEffect(() => {
    fetch('https://economia.awesomeapi.com.br/last/USD-BRL')
      .then(r => r.json())
      .then(d => setCotacaoDolar(Number(d.USDBRL.ask)))
      .catch(() => console.error("Falha ao buscar Dólar, usando fallback."));
  }, []);

  const fetchCarteira = async () => {
    try {
      const res = await api.get(`/api/carteira?usuario_id=${usuarioId}`);
      setCarteira(res.data);
      setErro(false);
    } catch (err) {
      setErro(true);
    }
  };

  useEffect(() => {
    let isMounted = true;
    let timerId = null;

    const loopCarteira = async () => {
      if (!isMounted) return;
      if (!document.hidden) await fetchCarteira(); // 👈 Adicione o if (!document.hidden)
      if (isMounted) timerId = setTimeout(loopCarteira, 15000);
    };

    loopCarteira();
    
    // Escuta eventos de Câmbio ou Carrinho para forçar a recarga
    window.addEventListener('carrinhoAtualizado', fetchCarteira);

    return () => {
      isMounted = false;
      if (timerId) clearTimeout(timerId);
      window.removeEventListener('carrinhoAtualizado', fetchCarteira);
    };
  }, [usuarioId]);

  const executarOtimizacao = async () => {
    setLoadingOtimizacao(true);
    setErroOtimizacao('');
    try {
      const res = await otimizarCarteira(usuarioId);
      if (res.data.sucesso) {
        setOtimizacao(res.data);
      } else {
        setErroOtimizacao(res.data.erro || "O motor retornou erro.");
        setOtimizacao(null);
      }
    } catch (err) {
      let msgErro = "Erro desconhecido";
      if (err.response?.data) {
        const data = err.response.data;
        if (typeof data === 'string') {
            try { msgErro = JSON.parse(data).erro || JSON.parse(data).detail || data; } catch { msgErro = data; }
        } else {
            msgErro = data.erro || data.detail || "Erro de rede";
        }
      } else if (err.message) { msgErro = err.message; }
      if (typeof msgErro === 'string') msgErro = msgErro.replace(/^"|"$/g, '');
      setErroOtimizacao(msgErro);
      setOtimizacao(null);
    } finally {
      setLoadingOtimizacao(false);
    }
  };

  // 🛡️ PREPARAÇÃO SEGURA COM CONVERSÃO CAMBIAL NO AGREGADO
  let valorAlocadoBRL = 0;
  let pnlTotalBRL = 0;

  const posicoesEnriquecidas = carteira?.posicoes?.map(pos => {
    const cotacaoAoVivo = marketData?.find(m => m.ativo === pos.ticker);
    const precoAtual = cotacaoAoVivo ? cotacaoAoVivo.preco_atual : pos.preco_medio;
    
    const isEstrangeiro = !/\d/.test(pos.ticker) && !pos.ticker.endsWith('.SA');
    const taxa = isEstrangeiro ? cotacaoDolar : 1;

    // Valores Nativos (Para a Tabela)
    const valorPosicaoNativo = pos.quantidade * precoAtual;
    const custoPosicaoNativo = pos.quantidade * pos.preco_medio;
    const lucroNativo = valorPosicaoNativo - custoPosicaoNativo;
    const percentual = custoPosicaoNativo !== 0 ? (lucroNativo / custoPosicaoNativo) * 100 : 0;
    
    // Valores em Reais (Para o Agregado do Cabeçalho)
    valorAlocadoBRL += (valorPosicaoNativo * taxa);
    pnlTotalBRL += (lucroNativo * taxa);

    return { ...pos, precoAtual, valorPosicao: valorPosicaoNativo, lucro: lucroNativo, percentual };
  }) || [];

  // 💱 CÁLCULO DO PATRIMÔNIO CONSOLIDANDO AS DUAS MOEDAS
  const caixaBRL = carteira?.saldo_brl || 0;
  const caixaUSD = carteira?.saldo_usd || 0;
  const patrimonioTotalBRL = caixaBRL + (caixaUSD * cotacaoDolar) + valorAlocadoBRL;
  
  // 🇺🇸 CONVERSÃO PARA DÓLAR NOS CARDS GLOBAIS
  const patrimonioTotalUSD = cotacaoDolar > 0 ? (patrimonioTotalBRL / cotacaoDolar) : 0;
  const pnlTotalUSD = cotacaoDolar > 0 ? (pnlTotalBRL / cotacaoDolar) : 0;

  const posicoesFiltradas = posicoesEnriquecidas.filter(pos => {
    const bateBusca = pos.ticker.toLowerCase().includes(buscaTicker.toLowerCase());
    let bateStatus = true;
    if (filtroStatus === 'LUCRO') bateStatus = pos.lucro > 0;
    if (filtroStatus === 'PREJUIZO') bateStatus = pos.lucro < 0;
    return bateBusca && bateStatus;
  });

  const posicoesOrdenadas = useMemo(() => {
    let sortable = [...posicoesFiltradas];
    if (sortConfig !== null) {
      sortable.sort((a, b) => {
        let valA = a[sortConfig.key];
        let valB = b[sortConfig.key];
        if (typeof valA === 'string') {
          return sortConfig.direction === 'ascending' ? valA.localeCompare(valB) : valB.localeCompare(valA);
        }
        return sortConfig.direction === 'ascending' ? valA - valB : valB - valA;
      });
    }
    return sortable;
  }, [posicoesFiltradas, sortConfig]);

  const requestSort = (key) => {
    let direction = 'ascending';
    if (sortConfig && sortConfig.key === key && sortConfig.direction === 'ascending') {
      direction = 'descending';
    }
    setSortConfig({ key, direction });
  };

  const getSortIcon = (key) => {
    if (!sortConfig || sortConfig.key !== key) return ' ↕️';
    return sortConfig.direction === 'ascending' ? ' 🔼' : ' 🔽';
  };

  const processarLoteOtimizacao = async (destino) => {
    if (!window.confirm(`Deseja enviar TODAS as ordens para ${destino === 'CARRINHO' ? 'o Carrinho Noturno' : 'Execução Imediata'}?`)) return;
    setLoadingOtimizacao(true);
    try {
      let ordensProcessadas = 0;
      for (const ordem of otimizacao.receita_rebalanceamento) {
        const cotacao = marketData.find(m => m.ativo === ordem.ativo);
        const preco = cotacao ? cotacao.preco_atual : 1;
        const qtd = Math.floor(ordem.valor_brl / preco);

        if (qtd > 0) {
          const payload = { usuario_id: usuarioId, ticker: ordem.ativo, tipo_ordem: ordem.acao === 'COMPRAR' ? 'COMPRA' : 'VENDA', quantidade: qtd, preco: preco };
          if (destino === 'CARRINHO') await adicionarAoCarrinho(payload);
          else await enviarOrdem(payload);
          ordensProcessadas++;
        }
      }
      alert(`✅ Lote concluído! ${ordensProcessadas} ordens enviadas.`);
      if (destino === 'CARRINHO') window.dispatchEvent(new Event('carrinhoAtualizado'));
      setOtimizacao(null);
    } catch (err) {
      alert("⚠️ Erro ao processar lote: " + (err.response?.data?.erro || err.message));
    } finally {
      setLoadingOtimizacao(false);
    }
  };

  const thStyle = {
    padding: '15px', borderBottom: `2px solid ${theme.border}`, cursor: 'pointer', userSelect: 'none', transition: '0.2s', position: 'sticky', top: 0, backgroundColor: theme.cardBg, zIndex: 10, boxShadow: `0 2px 4px rgba(0,0,0,0.1)`
  };

  if (erro) return (<div style={{ padding: '20px', backgroundColor: 'rgba(239, 68, 68, 0.1)', color: theme.venda, border: `1px solid ${theme.venda}`, borderRadius: '8px', marginBottom: '20px' }}>⚠️ Carteira temporariamente indisponível.</div>);
  if (!carteira) return (<div style={{ padding: '20px', backgroundColor: theme.cardBg, color: theme.textMain, border: `1px solid ${theme.border}`, marginBottom: '20px', borderRadius: '8px' }}>⏳ Conectando com o Livro-Razão...</div>);

  return (
    <div style={{ marginBottom: '40px', backgroundColor: theme.cardBg, borderRadius: '12px', border: `1px solid ${theme.border}`, padding: '20px', boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)' }}>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: `1px solid ${theme.border}`, paddingBottom: '10px', marginBottom: '20px', flexWrap: 'wrap', gap: '15px' }}>
        <h3 style={{ color: theme.textMain, margin: 0 }}>💼 Posição Consolidada: {carteira.nome_cliente}</h3>
        <div style={{ display: 'flex', gap: '10px' }}>
          <button onClick={executarOtimizacao} disabled={loadingOtimizacao} style={{ padding: '8px 16px', backgroundColor: theme.info, color: '#fff', border: 'none', borderRadius: '4px', cursor: loadingOtimizacao ? 'wait' : 'pointer', fontWeight: 'bold' }}>
            {loadingOtimizacao ? '⚙️ Otimizando Portfólio...' : '⚖️ Otimizar Carteira (Markowitz)'}
          </button>
        </div>
      </div>

      {erroOtimizacao && <div style={{ padding: '15px', backgroundColor: 'rgba(239, 68, 68, 0.1)', color: theme.venda, border: `1px solid ${theme.venda}`, borderRadius: '4px', marginBottom: '20px' }}>{erroOtimizacao}</div>}

      {otimizacao && otimizacao.sucesso && (
        <div style={{ backgroundColor: 'rgba(59, 130, 246, 0.05)', padding: '20px', borderRadius: '8px', border: `1px solid ${theme.info}`, marginBottom: '30px' }}>
          <h4 style={{ margin: '0 0 15px 0', color: theme.info, display: 'flex', alignItems: 'center', gap: '8px' }}>🌟 Fronteira Eficiente Atingida</h4>
          <div style={{ display: 'flex', gap: '20px', marginBottom: '20px', flexWrap: 'wrap' }}>
            <div style={{ flex: 1, minWidth: '250px', backgroundColor: theme.bg, padding: '15px', borderRadius: '6px', borderLeft: `4px solid ${theme.info}` }}>
              <div style={{ fontSize: '0.85rem', color: theme.textMuted, marginBottom: '8px' }}>Estatísticas da Carteira Atual</div>
              <div style={{ color: theme.textMuted }}><strong style={{ color: theme.textMain }}>Retorno Anual:</strong> {otimizacao.metricas_atuais.retorno_anual}%</div>
              <div style={{ color: theme.textMuted }}><strong style={{ color: theme.textMain }}>Volatilidade:</strong> {otimizacao.metricas_atuais.risco_anual}%</div>
              <div style={{ color: theme.textMuted }}><strong style={{ color: theme.textMain }}>Índice de Sharpe:</strong> {otimizacao.metricas_atuais.sharpe}</div>
            </div>
            <div style={{ flex: 1, minWidth: '250px', backgroundColor: theme.bg, padding: '15px', borderRadius: '6px', borderLeft: `4px solid ${theme.compra}` }}>
              <div style={{ fontSize: '0.85rem', color: theme.textMuted, marginBottom: '8px' }}>Estatísticas Otimizadas (Ideal)</div>
              <div style={{ color: theme.textMuted }}><strong style={{ color: theme.textMain }}>Retorno Anual:</strong> <span style={{ color: theme.compra, fontWeight: 'bold' }}>{otimizacao.metricas_otimizadas.retorno_anual}%</span></div>
              <div style={{ color: theme.textMuted }}><strong style={{ color: theme.textMain }}>Volatilidade:</strong> <span style={{ color: theme.compra, fontWeight: 'bold' }}>{otimizacao.metricas_otimizadas.risco_anual}%</span></div>
              <div style={{ color: theme.textMuted }}><strong style={{ color: theme.textMain }}>Índice de Sharpe:</strong> <span style={{ color: theme.compra, fontWeight: 'bold' }}>{otimizacao.metricas_otimizadas.sharpe}</span></div>
            </div>
          </div>

          <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap' }}>
            <div style={{ flex: 1, minWidth: '300px' }}>
              <h5 style={{ margin: '0 0 10px 0', color: theme.textMain }}>Alocação Teórica Ideal (Pesos)</h5>
              <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', maxHeight: '250px', overflowY: 'auto', paddingRight: '5px' }}>
                {otimizacao.alocacao_ideal.map((aloc, idx) => (
                  <div key={idx} style={{ display: 'flex', justifyContent: 'space-between', padding: '5px' }}>
                    <span>{aloc.ativo}: {aloc.peso_ideal_perc}%</span>
                    <button
                      onClick={async () => {
                        try {
                          await adicionarAoCarrinho({ usuario_id: usuarioId, ticker: aloc.ativo, tipo_ordem: 'COMPRA', quantidade: Math.floor(aloc.valor_ideal_brl / (marketData.find(m => m.ativo === aloc.ativo)?.preco_atual || 1)), preco: marketData.find(m => m.ativo === aloc.ativo)?.preco_atual || 0 });
                          alert(`✅ ${aloc.ativo} enviado para o carrinho!`);
                          window.dispatchEvent(new Event('carrinhoAtualizado'));
                        } catch (e) { alert("Erro ao enviar para o carrinho"); }
                      }}
                      style={{ backgroundColor: theme.info, color: '#fff', border: 'none', padding: '5px 10px', borderRadius: '4px', cursor: 'pointer', fontSize: '0.75rem' }}
                    >🛒 Adicionar</button>
                  </div>
                ))}
              </div>
            </div>

            <div style={{ flex: 1, minWidth: '300px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                <h5 style={{ margin: 0, color: theme.textMain }}>Receita de Rebalanceamento</h5>
                {otimizacao.receita_rebalanceamento.length > 0 && (
                  <div style={{ display: 'flex', gap: '5px' }}>
                    <button onClick={() => processarLoteOtimizacao('CARRINHO')} style={{ padding: '4px 8px', backgroundColor: theme.bg, color: theme.info, border: `1px solid ${theme.info}`, borderRadius: '4px', fontSize: '0.75rem', cursor: 'pointer', fontWeight: 'bold' }}>🛒 Add Tudo</button>
                    <button onClick={() => processarLoteOtimizacao('MERCADO')} style={{ padding: '4px 8px', backgroundColor: theme.compra, color: '#fff', border: 'none', borderRadius: '4px', fontSize: '0.75rem', cursor: 'pointer', fontWeight: 'bold' }}>⚡ Executar Tudo</button>
                  </div>
                )}
              </div>
              <div style={{ maxHeight: '250px', overflowY: 'auto', paddingRight: '10px' }}>
                {otimizacao.receita_rebalanceamento.length === 0 ? (
                  <p style={{ margin: 0, fontSize: '0.9rem', color: theme.compra, fontWeight: 'bold' }}>✅ Sua carteira já está 100% otimizada.</p>
                ) : (
                  otimizacao.receita_rebalanceamento.map((ordem, idx) => (
                    <div key={idx} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 8px', borderBottom: `1px solid ${theme.border}` }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <span style={{ color: ordem.acao === 'COMPRAR' ? theme.compra : theme.venda, fontWeight: 'bold', backgroundColor: ordem.acao === 'COMPRAR' ? 'rgba(16, 185, 129, 0.1)' : 'rgba(239, 68, 68, 0.1)', padding: '4px 8px', borderRadius: '4px', fontSize: '0.8rem' }}>{ordem.acao}</span>
                        <span style={{ fontWeight: 'bold', color: theme.textMain }}>{ordem.ativo}</span>
                        {ordem.is_novo && <span style={{ fontSize: '0.65rem', backgroundColor: theme.alerta, color: '#000', padding: '2px 6px', borderRadius: '12px', fontWeight: 'bold', letterSpacing: '0.5px' }}>✨ SUGESTÃO DA IA</span>}
                      </div>
                      <span style={{ fontFamily: 'monospace', fontSize: '0.95rem', color: theme.textMain }}>{formatarDinheiro(ordem.valor_brl)}</span>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* DASHBOARD MULTI-MOEDAS */}
      <div style={{ display: 'flex', gap: '20px', marginBottom: '30px', flexWrap: 'wrap' }}>
        
        {/* CARD 1: Patrimônio Consolidado */}
        <div style={{ flex: 1, minWidth: '200px', backgroundColor: theme.bg, padding: '15px', borderRadius: '8px', borderLeft: `4px solid ${theme.info}`, border: `1px solid ${theme.border}`, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div style={{ fontSize: '0.9rem', color: theme.textMuted }}>🌍 Patrimônio Consolidado</div>
          <div style={{ marginTop: '10px' }}>
            <div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: theme.textMain }}>
              🇧🇷 {formatarDinheiro(patrimonioTotalBRL)}
            </div>
            <div style={{ fontSize: '1.1rem', fontWeight: 'bold', color: theme.textMuted, marginTop: '5px' }}>
              🇺🇸 {new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(patrimonioTotalUSD)}
            </div>
          </div>
        </div>
        
        {/* CARD 2: Caixa Livre */}
        <div style={{ flex: 1, minWidth: '200px', backgroundColor: theme.bg, padding: '15px', borderRadius: '8px', borderLeft: `4px solid ${theme.compra}`, border: `1px solid ${theme.border}`, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div style={{ fontSize: '0.9rem', color: theme.textMuted }}>Caixa Livre (Dry Powder)</div>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginTop: '10px' }}>
            <div>
              <div style={{ fontSize: '1.2rem', fontWeight: 'bold', color: theme.compra }}>
                🇧🇷 {formatarDinheiro(caixaBRL)}
              </div>
              <div style={{ fontSize: '1.2rem', fontWeight: 'bold', color: theme.compra, marginTop: '5px' }}>
                🇺🇸 {new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(caixaUSD)}
              </div>
            </div>
            <button 
              onClick={() => setModalCambioAberto(true)}
              style={{ padding: '6px 12px', backgroundColor: theme.info, color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.8rem' }}
            >
              💱 Câmbio
            </button>
          </div>
        </div>
        
        {/* CARD 3: P&L Global */}
        <div style={{ flex: 1, minWidth: '200px', backgroundColor: theme.bg, padding: '15px', borderRadius: '8px', borderLeft: pnlTotalBRL >= 0 ? `4px solid ${theme.compra}` : `4px solid ${theme.venda}`, border: `1px solid ${theme.border}`, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div style={{ fontSize: '0.9rem', color: theme.textMuted }}>P&L Global Aberto</div>
          <div style={{ marginTop: '10px' }}>
            <div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: pnlTotalBRL >= 0 ? theme.compra : theme.venda }}>
              🇧🇷 {pnlTotalBRL > 0 ? '+' : ''}{formatarDinheiro(pnlTotalBRL)}
            </div>
            <div style={{ fontSize: '1.1rem', fontWeight: 'bold', color: pnlTotalBRL >= 0 ? theme.compra : theme.venda, opacity: 0.8, marginTop: '5px' }}>
              🇺🇸 {pnlTotalUSD > 0 ? '+' : ''}{new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(pnlTotalUSD)}
            </div>
          </div>
        </div>

      </div>

      {children}

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px', flexWrap: 'wrap', gap: '15px' }}>
        <h4 style={{ color: theme.textMain, margin: 0, fontSize: '1.2rem' }}>Custódia de Ativos</h4>
        <div style={{ display: 'flex', gap: '15px', alignItems: 'center', flexWrap: 'wrap' }}>
          <input type="text" placeholder="🔍 Buscar ticker..." value={buscaTicker} onChange={(e) => setBuscaTicker(e.target.value)} style={{ padding: '8px 12px', borderRadius: '6px', backgroundColor: theme.bg, color: theme.textMain, border: `1px solid ${theme.border}`, outline: 'none' }} />
          <FilterBar
            activeFilter={filtroStatus}
            setFilter={setFiltroStatus}
            options={[
              { label: '🧾 Todos os Ativos', value: 'TODOS', color: theme.info },
              { label: '📈 Em Lucro', value: 'LUCRO', color: theme.compra },
              { label: '📉 Em Prejuízo', value: 'PREJUIZO', color: theme.venda }
            ]}
          />
        </div>
      </div>

      {posicoesOrdenadas.length === 0 ? (
        <p style={{ color: theme.textMuted, fontStyle: 'italic', textAlign: 'center', padding: '20px' }}>Nenhum ativo corresponde aos filtros.</p>
      ) : (
        <div style={{ maxHeight: '350px', overflowY: 'auto', overflowX: 'auto', borderRadius: '8px', border: `1px solid ${theme.border}`, backgroundColor: theme.bg }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.95rem', position: 'relative' }}>
            <thead>
              <tr style={{ color: theme.textMuted }}>
                <th style={thStyle} onClick={() => requestSort('ticker')}>Ativo{getSortIcon('ticker')}</th>
                <th style={thStyle} onClick={() => requestSort('quantidade')}>Qtd{getSortIcon('quantidade')}</th>
                <th style={thStyle} onClick={() => requestSort('preco_medio')}>Custo de Aquisição{getSortIcon('preco_medio')}</th>
                <th style={thStyle} onClick={() => requestSort('precoAtual')}>Marcação a Mercado{getSortIcon('precoAtual')}</th>
                <th style={thStyle} onClick={() => requestSort('lucro')}>Lucro Líquido{getSortIcon('lucro')}</th>
                <th style={thStyle} onClick={() => requestSort('percentual')}>Rentab. (%){getSortIcon('percentual')}</th>
                <th style={{ ...thStyle, textAlign: 'center' }}>Operação</th>
              </tr>
            </thead>
            <tbody>
              {posicoesOrdenadas.map((pos, idx) => (
                <tr key={idx} style={{ borderBottom: `1px solid ${theme.border}`, transition: 'background-color 0.2s' }} onMouseOver={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.02)'} onMouseOut={(e) => e.currentTarget.style.backgroundColor = 'transparent'}>

                  {/* 1. TICKER CLICÁVEL PARA RAIO-X */}
                  <td style={{ padding: '15px', fontWeight: 'bold', color: theme.info }}>
                    <button
                      onClick={() => setAtivoRaioX({ ativo: pos.ticker, preco_atual: pos.precoAtual, quantidade_carteira: pos.quantidade })}
                      style={{ background: 'none', border: 'none', color: theme.info, fontWeight: 'bold', cursor: 'pointer', textDecoration: 'underline', padding: 0 }}
                    >
                      {pos.ticker}
                    </button>
                  </td>

                  <td style={{ padding: '15px', color: theme.textMain }}>{pos.quantidade}</td>
                  {/* 🌍 APLICAÇÃO DA MOEDA NATIVA NAS CÉLULAS INDIVIDUAIS */}
                  <td style={{ padding: '15px', color: theme.textMuted }}>{formatarMoedaNativa(pos.preco_medio, pos.ticker)}</td>
                  <td style={{ padding: '15px', color: theme.info, fontWeight: 'bold' }}>{formatarMoedaNativa(pos.precoAtual, pos.ticker)}</td>
                  <td style={{ padding: '15px', color: pos.lucro >= 0 ? theme.compra : theme.venda, fontWeight: 'bold' }}>{pos.lucro >= 0 ? '+' : ''}{formatarMoedaNativa(pos.lucro, pos.ticker)}</td>
                  <td style={{ padding: '15px', color: pos.percentual >= 0 ? theme.compra : theme.venda, fontWeight: 'bold' }}>{pos.percentual >= 0 ? '+' : ''}{pos.percentual.toFixed(2)}%</td>

                  <td style={{ padding: '15px', textAlign: 'center' }}>
                    <button
                      onClick={() => setAtivoSelecionado({
                        ativo: pos.ticker,
                        preco_atual: pos.precoAtual,
                        quantidade_carteira: pos.quantidade
                      })}
                      style={{ padding: '6px 12px', backgroundColor: theme.info, color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}
                    >
                      Negociar
                    </button>
                  </td>

                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modalCambioAberto && (
        <CambioModal
          onClose={() => setModalCambioAberto(false)}
          usuarioId={usuarioId}
          saldoBRL={caixaBRL}
          saldoUSD={caixaUSD}
          cotacaoDolar={cotacaoDolar}
        />
      )}

      {ativoSelecionado && <TradeModal ativo={ativoSelecionado} onClose={() => setAtivoSelecionado(null)} usuarioId={usuarioId} />}
      {ativoRaioX && <AssetDeepDive ativoData={ativoRaioX} onClose={() => setAtivoRaioX(null)} usuarioId={usuarioId} />}
    </div>
  );
};

