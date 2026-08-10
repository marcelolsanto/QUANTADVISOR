
import { useState, useEffect, useMemo } from 'react';
import { getHistorico } from '../services/api';
import { theme } from '../theme';
import { FilterBar } from './FilterBar'; 

const formatarMoedaNativa = (valor, ticker) => {
  const isEstrangeiro = !/\d/.test(ticker) && !ticker.endsWith('.SA');
  const moeda = isEstrangeiro ? 'USD' : 'BRL';
  return new Intl.NumberFormat(isEstrangeiro ? 'en-US' : 'pt-BR', {
    style: 'currency', currency: moeda, minimumFractionDigits: 2
  }).format(Number(valor) || 0);
};

export const HistoricoTable = ({ usuarioId }) => {
  const [historico, setHistorico] = useState([]);
  const [loading, setLoading] = useState(false);
  const [erro, setErro] = useState(false);

  const [buscaTicker, setBuscaTicker] = useState('');
  const [filtroTipo, setFiltroTipo] = useState('TODOS');
  
  // 🌟 NOVO ESTADO: Filtro por Mês
  const [mesSelecionado, setMesSelecionado] = useState('TODOS'); 
  
  const [sortConfig, setSortConfig] = useState({ key: 'data_hora', direction: 'descending' });

  useEffect(() => {
    let isMounted = true;
    let timerId = null;

    const fetchHistorico = async () => {
      if (!isMounted) return;
      if (!document.hidden) { 
        try {
          const res = await getHistorico(usuarioId);
          if (isMounted && res.data && res.data.sucesso) setHistorico(res.data.ordens);
          if (isMounted) setErro(false);
        } catch (err) {
          if (isMounted) setErro(true);
        } 
      }
      if (isMounted) {
        setLoading(false);
        timerId = setTimeout(fetchHistorico, 15000);
      }
    };

    setLoading(true);
    fetchHistorico();

    return () => {
      isMounted = false;
      if (timerId) clearTimeout(timerId);
    };
  }, [usuarioId]);

  const historicoMapeado = historico.map(ordem => ({
    ...ordem,
    financeiro_total: ordem.quantidade * ordem.preco_execucao
  }));

  // 🌟 FILTRO COMBINADO: Tipo, Ticker e MÊS!
  const historicoFiltrado = historicoMapeado.filter(ordem => {
    const matchTipo = filtroTipo === 'TODOS' || ordem.tipo_ordem === filtroTipo;
    const matchTicker = ordem.ticker.toLowerCase().includes(buscaTicker.toLowerCase());
    
    let matchMes = true;
    if (mesSelecionado !== 'TODOS') {
        const dataO = new Date(ordem.data_hora);
        const ano = dataO.getFullYear();
        const mes = String(dataO.getMonth() + 1).padStart(2, '0');
        matchMes = `${ano}-${mes}` === mesSelecionado;
    }

    return matchTipo && matchTicker && matchMes;
  });

  const historicoOrdenado = useMemo(() => {
    let sortable = [...historicoFiltrado];
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
  }, [historicoFiltrado, sortConfig]);

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

  const volumeTotalCotas = historicoFiltrado.reduce((acc, ordem) => acc + ordem.quantidade, 0);

  const thStyle = {
    padding: '12px', borderBottom: `2px solid ${theme.border}`, cursor: 'pointer', userSelect: 'none', transition: '0.2s', position: 'sticky', top: 0, backgroundColor: theme.cardBg, zIndex: 10, boxShadow: `0 2px 4px rgba(0,0,0,0.1)`
  };

  if (erro) return (
    <div style={{ padding: '20px', backgroundColor: 'rgba(239, 68, 68, 0.1)', border: `1px solid ${theme.venda}`, color: theme.venda, borderRadius: '8px', marginTop: '20px' }}>
      ⚠️ Erro ao carregar o Histórico de Ordens.
    </div>
  );

  return (
    <div style={{ marginTop: '40px', backgroundColor: theme.cardBg, borderRadius: '12px', border: `1px solid ${theme.border}`, padding: '20px', boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)' }}>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: `1px solid ${theme.border}`, paddingBottom: '15px', marginBottom: '15px', flexWrap: 'wrap', gap: '15px' }}>
        <h3 style={{ color: theme.textMain, margin: 0 }}>📜 Extrato de Operações (Livro-Razão)</h3>

        <div style={{ display: 'flex', gap: '15px', alignItems: 'center', flexWrap: 'wrap' }}>
          {/* 🌟 NOVO: Seletor de Mês */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <label style={{ fontSize: '0.85rem', color: theme.textMuted, fontWeight: 'bold' }}>Mês:</label>
              <input
                type="month"
                value={mesSelecionado !== 'TODOS' ? mesSelecionado : ''}
                onChange={(e) => setMesSelecionado(e.target.value || 'TODOS')}
                style={{ padding: '8px 12px', borderRadius: '6px', backgroundColor: theme.bg, color: theme.textMain, border: `1px solid ${theme.border}`, outline: 'none', cursor: 'pointer' }}
              />
              {mesSelecionado !== 'TODOS' && (
                  <button onClick={() => setMesSelecionado('TODOS')} style={{ background: 'none', border: 'none', color: theme.info, cursor: 'pointer', fontSize: '0.85rem', fontWeight: 'bold' }}>Ver Tudo</button>
              )}
          </div>

          <input
            type="text"
            placeholder="🔍 Buscar ativo..."
            value={buscaTicker}
            onChange={(e) => setBuscaTicker(e.target.value)}
            style={{ padding: '8px 12px', borderRadius: '6px', backgroundColor: theme.bg, color: theme.textMain, border: `1px solid ${theme.border}`, outline: 'none', width: '150px' }}
          />
          <FilterBar 
            activeFilter={filtroTipo} 
            setFilter={setFiltroTipo} 
            options={[
              { label: '🧾 Todas', value: 'TODOS', color: theme.info },
              { label: '🟢 Compras', value: 'COMPRA', color: theme.compra },
              { label: '🔴 Vendas', value: 'VENDA', color: theme.venda }
            ]} 
          />
        </div>
      </div>

      {loading && historico.length === 0 ? (
        <p style={{ color: theme.textMuted, fontStyle: 'italic', padding: '20px', textAlign: 'center' }}>Carregando extrato...</p>
      ) : historico.length === 0 ? (
        <p style={{ textAlign: 'center', color: theme.textMuted, padding: '20px', fontStyle: 'italic' }}>
          Nenhuma ordem executada para esta conta.
        </p>
      ) : (
        <div style={{ maxHeight: '350px', overflowY: 'auto', overflowX: 'auto', borderRadius: '8px', border: `1px solid ${theme.border}`, backgroundColor: theme.bg }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.95rem', position: 'relative' }}>
            <thead>
              <tr style={{ color: theme.textMuted }}>
                <th style={thStyle} onClick={() => requestSort('id')}>Nº Ordem{getSortIcon('id')}</th>
                <th style={thStyle} onClick={() => requestSort('data_hora')}>Data / Hora{getSortIcon('data_hora')}</th>
                <th style={thStyle} onClick={() => requestSort('ticker')}>Ativo{getSortIcon('ticker')}</th>
                <th style={thStyle} onClick={() => requestSort('tipo_ordem')}>Tipo{getSortIcon('tipo_ordem')}</th>
                <th style={thStyle} onClick={() => requestSort('quantidade')}>Quantidade{getSortIcon('quantidade')}</th>
                <th style={thStyle} onClick={() => requestSort('preco_execucao')}>Preço Execução{getSortIcon('preco_execucao')}</th>
                <th style={thStyle} onClick={() => requestSort('financeiro_total')}>Financeiro Total{getSortIcon('financeiro_total')}</th>
              </tr>
            </thead>
            <tbody>
              {historicoOrdenado.length > 0 ? historicoOrdenado.map((ordem) => {
                const isCompra = ordem.tipo_ordem === 'COMPRA';
                const corTexto = isCompra ? theme.compra : theme.venda;
                const corFundo = isCompra ? 'rgba(16, 185, 129, 0.1)' : 'rgba(239, 68, 68, 0.1)';
                const dataFormatada = new Date(ordem.data_hora).toLocaleString('pt-BR');

                return (
                  <tr key={ordem.id} style={{ borderBottom: `1px solid ${theme.border}`, transition: 'background-color 0.2s' }} onMouseOver={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.02)'} onMouseOut={(e) => e.currentTarget.style.backgroundColor = 'transparent'}>
                    <td style={{ padding: '12px', color: theme.textMuted, fontWeight: 'bold' }}>#{ordem.id}</td>
                    <td style={{ padding: '12px', color: theme.textMain }}>{dataFormatada}</td>
                    <td style={{ padding: '12px', fontWeight: 'bold', color: theme.info }}>{ordem.ticker}</td>
                    <td style={{ padding: '12px' }}>
                      <span style={{ backgroundColor: corFundo, color: corTexto, padding: '4px 8px', borderRadius: '4px', fontWeight: 'bold', fontSize: '0.85rem' }}>{ordem.tipo_ordem}</span>
                    </td>
                    <td style={{ padding: '12px', color: theme.textMain }}>{ordem.quantidade}</td>
                    <td style={{ padding: '12px', fontFamily: 'monospace', color: theme.textMuted }}>
                      {formatarMoedaNativa(ordem.preco_execucao, ordem.ticker)}
                    </td>
                    <td style={{ padding: '12px', fontWeight: 'bold', color: theme.textMain }}>
                      {formatarMoedaNativa(ordem.financeiro_total, ordem.ticker)}
                    </td>
                  </tr>
                );
              }) : (
                <tr>
                  <td colSpan="7" style={{ textAlign: 'center', padding: '20px', color: theme.textMuted, fontStyle: 'italic' }}>Nenhuma operação encontrada para este mês/filtro.</td>
                </tr>
              )}
            </tbody>
            
            {historicoFiltrado.length > 0 && (
              <tfoot style={{ position: 'sticky', bottom: 0, backgroundColor: theme.cardBg, fontWeight: 'bold', boxShadow: `0 -2px 4px rgba(0,0,0,0.1)`, zIndex: 10 }}>
                <tr>
                  <td style={{ padding: '12px', color: theme.textMain }} colSpan="4">VOLUME TOTAL (EXIBIÇÃO ATUAL)</td>
                  <td style={{ padding: '12px', color: theme.textMain }}>{volumeTotalCotas.toLocaleString('pt-BR')} cotas</td>
                  <td style={{ padding: '12px' }} colSpan="2">-</td>
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      )}
    </div>
  );
};

