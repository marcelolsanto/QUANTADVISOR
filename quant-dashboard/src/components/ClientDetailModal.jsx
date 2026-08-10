
import React, { useState, useEffect, useMemo } from 'react';
import { PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ReferenceLine, ResponsiveContainer } from 'recharts';
import api from '../services/api';
import { theme } from '../theme';
import { FilterBar } from './FilterBar';
import { TradeModal } from './TradeModal';
import { AssetDeepDive } from './AssetDeepDive';

const CORES_PIZZA = ['#3b82f6', '#10b981', '#8b5cf6', '#f43f5e', '#f59e0b', '#14b8a6', '#6366f1', '#ec4899'];

// 🌍 FUNÇÃO AUXILIAR DE MOEDA NATIVA
const formatarMoedaNativa = (valor, ticker) => {
  const isEstrangeiro = !/\d/.test(ticker) && !ticker.endsWith('.SA');
  const moeda = isEstrangeiro ? 'USD' : 'BRL';
  return new Intl.NumberFormat(isEstrangeiro ? 'en-US' : 'pt-BR', {
    style: 'currency', currency: moeda, minimumFractionDigits: 2
  }).format(Number(valor) || 0);
};

export const ClientDetailModal = ({ usuarioIdInicial, nomeClienteInicial, clientes, onClose }) => {
  const [activeUserId, setActiveUserId] = useState(usuarioIdInicial);
  const [dadosCarteira, setDadosCarteira] = useState(null);
  const [loading, setLoading] = useState(true);

  // 💱 ESTADO DO CÂMBIO
  const [cotacaoDolar, setCotacaoDolar] = useState(5.00);

  const [buscaTicker, setBuscaTicker] = useState('');
  const [filtroStatus, setFiltroStatus] = useState('TODOS');
  const [sortConfig, setSortConfig] = useState({ key: 'ticker', direction: 'ascending' });
  const [ativoSelecionado, setAtivoSelecionado] = useState(null);
  const [ativoRaioX, setAtivoRaioX] = useState(null);

  // 💱 BUSCA O DÓLAR UMA VEZ
  useEffect(() => {
    fetch('https://economia.awesomeapi.com.br/last/USD-BRL')
      .then(r => r.json())
      .then(d => setCotacaoDolar(Number(d.USDBRL.ask)))
      .catch(() => {});
  }, []);

  useEffect(() => {
    const carregarDetalhes = async () => {
      setLoading(true);
      try {
        const res = await api.get(`/api/carteira?usuario_id=${activeUserId}`);
        setDadosCarteira(res.data);
      } catch (err) {
        console.error("Erro ao carregar detalhes:", err);
      } finally {
        setLoading(false);
      }
    };
    if (activeUserId) carregarDetalhes();
  }, [activeUserId]);

  const { pizzaData, barraData, tabelaData } = useMemo(() => {
    if (!dadosCarteira) return { pizzaData: [], barraData: [], tabelaData: [] };

    const pizza = [];
    const barra = [];
    const tabela = [];

    const saldoLivre = dadosCarteira.saldo_brl !== undefined ? dadosCarteira.saldo_brl : (dadosCarteira.saldo_disponivel || 0);
    if (saldoLivre > 0) {
      pizza.push({ name: 'Caixa Livre', value: saldoLivre, tipo: 'CAIXA' });
    }

    dadosCarteira.posicoes?.forEach(pos => {
      const precoAtual = pos.preco_atual !== null ? pos.preco_atual : pos.preco_medio;
      const isEstrangeiro = !/\d/.test(pos.ticker) && !pos.ticker.endsWith('.SA');
      const taxa = isEstrangeiro ? cotacaoDolar : 1;

      // Nativos (Tabela)
      const valorAlocadoNativo = pos.quantidade * precoAtual;
      const custoTotalNativo = pos.quantidade * pos.preco_medio;
      const lucroFinanceiroNativo = valorAlocadoNativo - custoTotalNativo;
      const rentabilidade_perc = custoTotalNativo > 0 ? (lucroFinanceiroNativo / custoTotalNativo) * 100 : 0;

      // Reais (Gráficos)
      const valorAlocadoBRL = valorAlocadoNativo * taxa;
      const lucroFinanceiroBRL = lucroFinanceiroNativo * taxa;

      if (valorAlocadoBRL > 0) {
        pizza.push({ name: pos.ticker, value: valorAlocadoBRL, tipo: 'ATIVO' });
      }

      barra.push({ ticker: pos.ticker, pnl: lucroFinanceiroBRL, rentabilidade_perc: rentabilidade_perc });

      tabela.push({
        ...pos,
        precoAtual,
        lucroFinanceiro: lucroFinanceiroNativo,
        rentabilidade_perc
      });
    });

    barra.sort((a, b) => b.pnl - a.pnl);
    return { pizzaData: pizza, barraData: barra, tabelaData: tabela };
  }, [dadosCarteira, cotacaoDolar]);

  const posicoesFiltradas = useMemo(() => {
    return tabelaData.filter(pos => {
      const bateBusca = pos.ticker.toLowerCase().includes(buscaTicker.toLowerCase());
      let bateStatus = true;
      if (filtroStatus === 'LUCRO') bateStatus = pos.lucroFinanceiro > 0;
      if (filtroStatus === 'PREJUIZO') bateStatus = pos.lucroFinanceiro < 0;
      return bateBusca && bateStatus;
    });
  }, [tabelaData, buscaTicker, filtroStatus]);

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

  const formatarBRL = (valor) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(valor);

  const thStyle = {
    padding: '15px', borderBottom: `2px solid ${theme.border}`, cursor: 'pointer',
    userSelect: 'none', transition: '0.2s', color: theme.textMuted,
    position: 'sticky', top: 0, backgroundColor: theme.bg, zIndex: 10
  };

  return (
    <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 3000 }}>
      <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.info}`, borderRadius: '12px', width: '95%', maxWidth: '1200px', height: '85vh', display: 'flex', flexDirection: 'column', overflow: 'hidden', boxShadow: `0 0 40px -10px rgba(59, 130, 246, 0.3)` }}>

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '20px 30px', borderBottom: `1px solid ${theme.border}`, backgroundColor: 'rgba(59, 130, 246, 0.05)' }}>
          <div>
            <h2 style={{ margin: 0, color: theme.textMain, fontSize: '1.5rem' }}>Raio-X de Custódia</h2>

            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '8px' }}>
              <span style={{ color: theme.info, fontWeight: 'bold' }}>Cliente:</span>
              <select
                value={activeUserId}
                onChange={(e) => setActiveUserId(Number(e.target.value))}
                style={{ padding: '4px 10px', borderRadius: '6px', backgroundColor: theme.bg, color: theme.info, border: `1px solid ${theme.info}`, outline: 'none', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.9rem' }}
              >
                {clientes && clientes.map(c => (
                  <option key={c.usuario_id} value={c.usuario_id}>
                    {c.nome} ({c.perfil})
                  </option>
                ))}
              </select>
            </div>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: `1px solid ${theme.border}`, backgroundColor: theme.bg, color: theme.textMuted, width: '40px', height: '40px', borderRadius: '50%', cursor: 'pointer', fontWeight: 'bold', fontSize: '1.1rem', transition: '0.2s' }} onMouseOver={(e) => e.target.style.color = theme.textMain} onMouseOut={(e) => e.target.style.color = theme.textMuted}>
            ✖
          </button>
        </div>

        {loading ? (
          <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', color: theme.info, fontWeight: 'bold', fontSize: '1.2rem' }}>
            ⏳ Carregando posições criptografadas...
          </div>
        ) : (
          <div style={{ flex: 1, padding: '30px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '30px' }}>

            <div style={{ display: 'flex', gap: '30px', flexWrap: 'wrap' }}>
              <div style={{ flex: 1, minWidth: '350px', backgroundColor: theme.bg, border: `1px solid ${theme.border}`, borderRadius: '8px', padding: '20px', display: 'flex', flexDirection: 'column' }}>
                <h3 style={{ margin: '0 0 5px 0', color: theme.textMain, textAlign: 'center' }}>Distribuição de Risco</h3>
                <p style={{ margin: '0 0 20px 0', color: theme.textMuted, fontSize: '0.8rem', textAlign: 'center' }}>Exposição de Capital por Ativo (Em R$)</p>

                <div style={{ flex: 1, minHeight: '300px' }}>
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie
                        data={pizzaData} dataKey="value" nameKey="name" cx="50%" cy="50%"
                        innerRadius={70} outerRadius={110} paddingAngle={2} labelLine={{ stroke: theme.textMuted }}
                        label={(props) => `${props?.name || ''} (${((props?.percent || 0) * 100).toFixed(1)}%)`}
                      >
                        {pizzaData.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={entry.tipo === 'CAIXA' ? theme.compra : CORES_PIZZA[index % CORES_PIZZA.length]} opacity={entry.tipo === 'CAIXA' ? 0.7 : 1} />
                        ))}
                      </Pie>
                      <Tooltip formatter={(value) => formatarBRL(value)} contentStyle={{ backgroundColor: theme.cardBg, borderColor: theme.border, borderRadius: '8px', color: theme.textMain, fontWeight: 'bold' }} />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
              </div>

              <div style={{ flex: 1, minWidth: '400px', backgroundColor: theme.bg, border: `1px solid ${theme.border}`, borderRadius: '8px', padding: '20px', display: 'flex', flexDirection: 'column' }}>
                <h3 style={{ margin: '0 0 5px 0', color: theme.textMain, textAlign: 'center' }}>Performance por Ativo (MtM)</h3>
                <p style={{ margin: '0 0 20px 0', color: theme.textMuted, fontSize: '0.8rem', textAlign: 'center' }}>Lucro ou Prejuízo Aberto (Em R$)</p>

                <div style={{ flex: 1, minHeight: '300px' }}>
                  {barraData.length > 0 ? (
                    <ResponsiveContainer width="100%" height="100%">
                      <BarChart data={barraData} layout="vertical" margin={{ top: 5, right: 30, left: 20, bottom: 5 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke={theme.border} horizontal={true} vertical={false} />
                        <XAxis type="number" stroke={theme.textMuted} tickFormatter={(val) => `R$ ${val}`} tick={{ fontSize: 12 }} />
                        <YAxis dataKey="ticker" type="category" stroke={theme.textMain} fontWeight="bold" width={60} tick={{ fontSize: 12 }} />
                        <Tooltip
                          cursor={{ fill: 'rgba(255,255,255,0.05)' }} contentStyle={{ backgroundColor: theme.cardBg, borderColor: theme.border, borderRadius: '8px' }}
                          itemStyle={{ color: theme.textMain, fontWeight: 'bold', fontSize: '14px' }} labelStyle={{ color: theme.info, fontWeight: 'bold', marginBottom: '5px', fontSize: '16px' }}
                          formatter={(value, name, props) => [`${formatarBRL(value)} (${(props?.payload?.rentabilidade_perc || 0).toFixed(2)}%)`, "P&L Aberto"]}
                        />
                        <ReferenceLine x={0} stroke={theme.textMuted} strokeWidth={2} />
                        <Bar dataKey="pnl" radius={[0, 4, 4, 0]}>
                          {barraData.map((entry, index) => (
                            <Cell key={`cell-${index}`} fill={entry.pnl >= 0 ? theme.compra : theme.venda} />
                          ))}
                        </Bar>
                      </BarChart>
                    </ResponsiveContainer>
                  ) : (
                    <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: theme.textMuted }}>O cliente não possui ações em custódia.</div>
                  )}
                </div>
              </div>
            </div>

            <div style={{ backgroundColor: theme.bg, borderRadius: '8px', border: `1px solid ${theme.border}`, padding: '20px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px', flexWrap: 'wrap', gap: '15px' }}>
                <h4 style={{ color: theme.textMain, margin: 0, fontSize: '1.2rem' }}>Custódia de Ativos</h4>
                <div style={{ display: 'flex', gap: '15px', alignItems: 'center', flexWrap: 'wrap' }}>
                  <input type="text" placeholder="🔍 Buscar ticker..." value={buscaTicker} onChange={(e) => setBuscaTicker(e.target.value)} style={{ padding: '8px 12px', borderRadius: '6px', backgroundColor: theme.cardBg, color: theme.textMain, border: `1px solid ${theme.border}`, outline: 'none' }} />
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
                <div style={{ maxHeight: '350px', overflowY: 'auto', overflowX: 'auto', borderRadius: '8px', border: `1px solid ${theme.border}`, backgroundColor: theme.cardBg }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.95rem', position: 'relative' }}>
                    <thead>
                      <tr style={{ color: theme.textMuted }}>
                        <th style={thStyle} onClick={() => requestSort('ticker')}>Ativo{getSortIcon('ticker')}</th>
                        <th style={thStyle} onClick={() => requestSort('quantidade')}>Qtd{getSortIcon('quantidade')}</th>
                        <th style={thStyle} onClick={() => requestSort('preco_medio')}>Custo de Aquisição{getSortIcon('preco_medio')}</th>
                        <th style={thStyle} onClick={() => requestSort('precoAtual')}>Marcação a Mercado{getSortIcon('precoAtual')}</th>
                        <th style={thStyle} onClick={() => requestSort('lucroFinanceiro')}>Lucro Líquido{getSortIcon('lucroFinanceiro')}</th>
                        <th style={thStyle} onClick={() => requestSort('rentabilidade_perc')}>Rentab. (%){getSortIcon('rentabilidade_perc')}</th>
                        <th style={{ ...thStyle, textAlign: 'center', cursor: 'default' }}>Operação</th>
                      </tr>
                    </thead>
                    <tbody>
                      {posicoesOrdenadas.map((pos, idx) => (
                        <tr key={idx} style={{ borderBottom: `1px solid ${theme.border}`, transition: 'background-color 0.2s' }} onMouseOver={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.02)'} onMouseOut={(e) => e.currentTarget.style.backgroundColor = 'transparent'}>
                          <td style={{ padding: '15px', fontWeight: 'bold', color: theme.info }}>
                            <button
                              onClick={() => setAtivoRaioX({ ativo: pos.ticker, preco_atual: pos.precoAtual, quantidade_carteira: pos.quantidade })}
                              style={{ background: 'none', border: 'none', color: theme.info, fontWeight: 'bold', cursor: 'pointer', textDecoration: 'underline', padding: 0 }}
                            >
                              {pos.ticker}
                            </button>
                          </td>
                          <td style={{ padding: '15px', color: theme.textMain }}>{pos.quantidade}</td>
                          
                          {/* 🌍 APLICAÇÃO DA MOEDA NATIVA AQUI */}
                          <td style={{ padding: '15px', color: theme.textMuted }}>{formatarMoedaNativa(pos.preco_medio, pos.ticker)}</td>
                          <td style={{ padding: '15px', color: theme.info, fontWeight: 'bold' }}>{formatarMoedaNativa(pos.precoAtual, pos.ticker)}</td>
                          <td style={{ padding: '15px', color: pos.lucroFinanceiro >= 0 ? theme.compra : theme.venda, fontWeight: 'bold' }}>{pos.lucroFinanceiro >= 0 ? '+' : ''}{formatarMoedaNativa(pos.lucroFinanceiro, pos.ticker)}</td>
                          
                          <td style={{ padding: '15px', color: pos.rentabilidade_perc >= 0 ? theme.compra : theme.venda, fontWeight: 'bold' }}>{pos.rentabilidade_perc >= 0 ? '+' : ''}{pos.rentabilidade_perc.toFixed(2)}%</td>
                          <td style={{ padding: '15px', textAlign: 'center' }}>
                            <button
                              onClick={() => setAtivoSelecionado({ ativo: pos.ticker, preco_atual: pos.precoAtual, quantidade_carteira: pos.quantidade })}
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
            </div>

          </div>
        )}
      </div>

      {ativoSelecionado && (
        <TradeModal 
            ativo={ativoSelecionado} 
            onClose={() => setAtivoSelecionado(null)} 
            usuarioId={activeUserId} 
        />
      )}

      {ativoRaioX && (
        <AssetDeepDive 
            ativoData={ativoRaioX} 
            onClose={() => setAtivoRaioX(null)}
            usuarioId={activeUserId}
        />
      )}

    </div>
  );
};

