
import { useState, useMemo, useEffect, memo } from 'react';
import { AssetDeepDive } from './AssetDeepDive';
import { TradeModal } from './TradeModal';
import { theme } from '../theme';

// 🌍 FUNÇÃO AUXILIAR DE MOEDA NATIVA
const formatarMoedaNativa = (valor, ticker) => {
  const tickerStr = ticker || '';
  const isEstrangeiro = !/\d/.test(tickerStr) && !tickerStr.endsWith('.SA');
  const moeda = isEstrangeiro ? 'USD' : 'BRL';
  return new Intl.NumberFormat(isEstrangeiro ? 'en-US' : 'pt-BR', {
    style: 'currency', currency: moeda, minimumFractionDigits: 2
  }).format(Number(valor) || 0);
};

export const MtMTable = memo(({ data, usuarioId, defaultPerfil, nomeUsuario }) => {
  const [filterSinal, setFilterSinal] = useState('TODOS');
  const [filtroMoeda, setFiltroMoeda] = useState('TODOS'); // 🌍 [TODOS | BRL | USD]
  const [buscaTicker, setBuscaTicker] = useState('');
  const [sortConfig, setSortConfig] = useState({ key: 'z_score', direction: 'ascending' });
  const [ativoSelecionado, setAtivoSelecionado] = useState(null);
  const [ativoRaioX, setAtivoRaioX] = useState(null);

  const [perfilUsuario, setPerfilUsuario] = useState(defaultPerfil || 'Arrojado');

  useEffect(() => {
    if (defaultPerfil) setPerfilUsuario(defaultPerfil);
  }, [defaultPerfil]);

  const filteredData = useMemo(() => {
    if (!data || data.length === 0) return [];
    return data.filter(row => {
      const nomeAtivo = row.ativo || row.ticker || '';
      const isEstrangeiro = !/\d/.test(nomeAtivo) && !nomeAtivo.endsWith('.SA');

      const matchSinal = filterSinal === 'TODOS' || (row.sinais_perfil && row.sinais_perfil[perfilUsuario] === filterSinal);
      const matchTicker = nomeAtivo.toLowerCase().includes(buscaTicker.toLowerCase());
      
      let matchMoeda = true;
      if (filtroMoeda === 'BRL') matchMoeda = !isEstrangeiro;
      if (filtroMoeda === 'USD') matchMoeda = isEstrangeiro;

      return matchSinal && matchTicker && matchMoeda;
    });
  }, [data, filterSinal, perfilUsuario, buscaTicker, filtroMoeda]);

  const sortedData = useMemo(() => {
    let sortableItems = [...filteredData];
    if (sortConfig !== null) {
      sortableItems.sort((a, b) => {
        let valA = sortConfig.key === 'sinal' ? (a.sinais_perfil ? a.sinais_perfil[perfilUsuario] : a.sinal) : (a[sortConfig.key] ?? '');
        let valB = sortConfig.key === 'sinal' ? (b.sinais_perfil ? b.sinais_perfil[perfilUsuario] : b.sinal) : (b[sortConfig.key] ?? '');
        if (typeof valA === 'string' || typeof valB === 'string') {
          return sortConfig.direction === 'ascending' ? String(valA).localeCompare(String(valB)) : String(valB).localeCompare(String(valA));
        }
        if (valA < valB) return sortConfig.direction === 'ascending' ? -1 : 1;
        if (valA > valB) return sortConfig.direction === 'ascending' ? 1 : -1;
        return 0;
      });
    }
    return sortableItems;
  }, [filteredData, sortConfig, perfilUsuario]);

  if (!data || data.length === 0) return null;

  const requestSort = (key) => {
    let direction = 'ascending';
    if (sortConfig && sortConfig.key === key && sortConfig.direction === 'ascending') direction = 'descending';
    setSortConfig({ key, direction });
  };

  const getSortIcon = (key) => {
    if (!sortConfig || sortConfig.key !== key) return ' ↕️';
    return sortConfig.direction === 'ascending' ? ' 🔼' : ' 🔽';
  };

  const thStyle = {
    padding: '12px', borderBottom: `2px solid ${theme.border}`, cursor: 'pointer', 
    userSelect: 'none', transition: '0.2s', color: theme.textMuted,
    position: 'sticky', top: 0, backgroundColor: theme.bg, zIndex: 10
  };

  const inputStyle = {
    padding: '8px 12px', borderRadius: '4px', border: `1px solid ${theme.border}`,
    backgroundColor: theme.bg, color: theme.textMain, outline: 'none'
  };

  const toggleBtnStyle = (ativo) => ({
    padding: '6px 12px', borderRadius: '4px', border: `1px solid ${ativo ? theme.info : theme.border}`,
    backgroundColor: ativo ? 'rgba(59, 130, 246, 0.15)' : theme.bg,
    color: ativo ? theme.info : theme.textMuted, fontWeight: 'bold', cursor: 'pointer', fontSize: '0.8rem'
  });

  return (
    <div style={{ marginTop: '20px', backgroundColor: theme.cardBg, borderRadius: '8px', padding: '20px', border: `1px solid ${theme.border}` }}>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: `1px solid ${theme.border}`, paddingBottom: '15px', flexWrap: 'wrap', gap: '15px' }}>
        <h3 style={{ color: theme.textMain, margin: 0 }}>Auditoria de Operações (MtM)</h3>

        <div style={{ display: 'flex', gap: '15px', alignItems: 'center', flexWrap: 'wrap' }}>
          
          {/* 🌍 TOGGLES DE MOEDA (FASE 3) */}
          <div style={{ display: 'flex', gap: '5px', backgroundColor: theme.bg, padding: '4px', borderRadius: '6px', border: `1px solid ${theme.border}` }}>
            <button onClick={() => setFiltroMoeda('TODOS')} style={toggleBtnStyle(filtroMoeda === 'TODOS')}>🌍 Global</button>
            <button onClick={() => setFiltroMoeda('BRL')} style={toggleBtnStyle(filtroMoeda === 'BRL')}>🇧🇷 Brasil</button>
            <button onClick={() => setFiltroMoeda('USD')} style={toggleBtnStyle(filtroMoeda === 'USD')}>🇺🇸 EUA</button>
          </div>

          <input type="text" placeholder="🔍 Buscar ativo..." value={buscaTicker} onChange={(e) => setBuscaTicker(e.target.value)} style={inputStyle} />

          <select value={filterSinal} onChange={(e) => setFilterSinal(e.target.value)} style={{ ...inputStyle, cursor: 'pointer' }}>
            <option value="TODOS">🧾 Todos os Sinais</option>
            <option value="COMPRA FORTE">✅ Compra Forte</option>
            <option value="ALERTA DE VENDA">⚠️ Venda</option>
            <option value="NEUTRO">⏸️ Neutro</option>
          </select>
        </div>
      </div>

      <div style={{ overflowX: 'auto', overflowY: 'auto', maxHeight: '400px', marginTop: '15px', borderRadius: '8px', border: `1px solid ${theme.border}` }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.95rem', position: 'relative' }}>
          <thead>
            <tr>
              <th style={{...thStyle, width: '50px'}}>#</th>
              <th style={thStyle} onClick={() => requestSort('ativo')}>Ativo{getSortIcon('ativo')}</th>
              <th style={thStyle} onClick={() => requestSort('preco_atual')}>Preço{getSortIcon('preco_atual')}</th>
              <th style={thStyle} onClick={() => requestSort('z_score')}>Z-Score{getSortIcon('z_score')}</th>
              <th style={thStyle} onClick={() => requestSort('distancia_vwap_perc')}>Δ VWAP{getSortIcon('distancia_vwap_perc')}</th>
              <th style={thStyle} onClick={() => requestSort('risco_var')}>VaR (99%){getSortIcon('risco_var')}</th>
              <th style={thStyle} onClick={() => requestSort('sinal')}>Decisão da IA{getSortIcon('sinal')}</th>
              <th style={{ ...thStyle, textAlign: 'center' }}>Operação</th>
            </tr>
          </thead>
          
          <tbody>
            {sortedData.map((row, index) => {
              const sinalAtual = row.sinais_perfil ? row.sinais_perfil[perfilUsuario] : (row.sinal || 'NEUTRO');
              const isCompra = sinalAtual === 'COMPRA FORTE';
              const isVenda = sinalAtual === 'ALERTA DE VENDA';

              const nomeAtivo = row.ativo || row.ticker || 'Desconhecido';
              const zScore = row.z_score || 0;
              const riscoVar = row.risco_var || 0;
              const distVwap = row.distancia_vwap_perc || 0;

              return (
                <tr key={index} style={{ borderBottom: `1px solid ${theme.border}` }} onMouseOver={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.03)'} onMouseOut={(e) => e.currentTarget.style.backgroundColor = 'transparent'}>
                  <td style={{ padding: '12px', color: theme.textMuted, fontWeight: 'bold' }}>{index + 1}</td>
                  
                  <td style={{ padding: '12px', fontWeight: 'bold', color: theme.info }}>
                    <button 
                      onClick={() => setAtivoRaioX({ ...row, sinalFormatado: sinalAtual, ativo: nomeAtivo })}
                      style={{ background: 'none', border: 'none', color: theme.info, fontWeight: 'bold', cursor: 'pointer', textDecoration: 'underline', padding: 0 }}
                    >
                      {nomeAtivo}
                    </button>
                  </td>
                  
                  {/* 💱 PREÇO NATIVO (USD se for estrangeiro, BRL se for B3) */}
                  <td style={{ padding: '12px', fontFamily: 'monospace', color: theme.textMuted }}>
                    {formatarMoedaNativa(row.preco_atual, nomeAtivo)}
                  </td>

                  <td style={{ padding: '12px', color: theme.textMain }}>{zScore.toFixed(2)}</td>
                  <td style={{ 
                    padding: '12px', 
                    color: distVwap < -1.5 ? theme.compra : (distVwap > 1.5 ? theme.venda : theme.textMuted), 
                    fontWeight: 'bold' 
                  }}>
                    {distVwap > 0 ? '+' : ''}{distVwap.toFixed(2)}%
                  </td>
                  <td style={{ padding: '12px', color: riscoVar < -8 ? theme.venda : (riscoVar < -5 ? theme.alerta : theme.compra), fontWeight: 'bold' }}>
                    {riscoVar.toFixed(2)}%
                  </td>
                  <td style={{ padding: '12px' }}>
                    <span style={{
                      padding: '4px 8px', borderRadius: '4px', fontSize: '0.8rem', fontWeight: 'bold',
                      backgroundColor: isCompra ? 'rgba(16, 185, 129, 0.15)' : (isVenda ? 'rgba(239, 68, 68, 0.15)' : theme.bg),
                      color: isCompra ? theme.compra : (isVenda ? theme.venda : theme.textMuted),
                      border: `1px solid ${isCompra ? theme.compra : (isVenda ? theme.venda : theme.border)}`
                    }}>
                      {sinalAtual}
                    </span>
                  </td>
                  <td style={{ padding: '12px', textAlign: 'center' }}>
                    <button onClick={() => setAtivoSelecionado({ ...row, ativo: nomeAtivo })} style={{ padding: '6px 12px', backgroundColor: theme.info, color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}>
                      Negociar
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>

          <tfoot style={{ position: 'sticky', bottom: 0, backgroundColor: theme.bg, zIndex: 10, boxShadow: '0 -4px 6px -1px rgba(0,0,0,0.1)' }}>
            <tr>
              <td colSpan="7" style={{ padding: '12px 20px', textAlign: 'right', color: theme.textMuted, fontWeight: 'bold', borderTop: `2px solid ${theme.border}` }}>
                Ativos exibidos: <span style={{ color: theme.textMain, fontSize: '1.1rem', marginLeft: '5px' }}>{sortedData.length}</span>
              </td>
            </tr>
          </tfoot>
        </table>

        {sortedData.length === 0 && (
          <p style={{ textAlign: 'center', color: theme.textMuted, marginTop: '20px', fontStyle: 'italic' }}>Nenhum ativo encontrado para este filtro de moeda.</p>
        )}
      </div>

      {ativoSelecionado && <TradeModal ativo={ativoSelecionado} onClose={() => setAtivoSelecionado(null)} usuarioId={usuarioId} />}
      {ativoRaioX && <AssetDeepDive ativoData={ativoRaioX} onClose={() => setAtivoRaioX(null)} usuarioId={usuarioId} />}
    </div>
  );
});

