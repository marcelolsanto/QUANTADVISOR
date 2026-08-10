
import React, { useState, useMemo } from 'react';
import { ResponsiveContainer, ComposedChart, Line, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, RadarChart, PolarGrid, PolarAngleAxis, PolarRadiusAxis, Radar } from 'recharts';
import { theme } from '../theme';

export const AnalyticsBenchmark = ({ usuarioId }) => {
  const [periodo, setPeriodo] = useState('1Y');
  const [benchmarkComparativo, setBenchmarkComparativo] = useState('ALL');

  // Dados simulados realistas de performance histórica e econometria
  const historicoPerformance = useMemo(() => [
    { mes: 'Jan', carteira: 2.1, ibov: 1.2, cdi: 1.05, sp500: 2.4, alpha: 0.9 },
    { mes: 'Fev', carteira: 4.8, ibov: 2.5, cdi: 2.10, sp500: 5.1, alpha: 2.3 },
    { mes: 'Mar', carteira: 3.9, ibov: -0.8, cdi: 3.15, sp500: 3.8, alpha: 4.7 },
    { mes: 'Abr', carteira: 7.2, ibov: 1.9, cdi: 4.20, sp500: 6.9, alpha: 5.3 },
    { mes: 'Mai', carteira: 9.8, ibov: 3.4, cdi: 5.25, sp500: 8.5, alpha: 6.4 },
    { mes: 'Jun', carteira: 12.4, ibov: 4.1, cdi: 6.30, sp500: 11.2, alpha: 8.3 },
    { mes: 'Jul', carteira: 15.6, ibov: 5.8, cdi: 7.35, sp500: 14.1, alpha: 9.8 },
    { mes: 'Ago', carteira: 18.2, ibov: 7.0, cdi: 8.40, sp500: 16.5, alpha: 11.2 },
    { mes: 'Set', carteira: 21.0, ibov: 8.2, cdi: 9.45, sp500: 17.8, alpha: 12.8 },
    { mes: 'Out', carteira: 24.5, ibov: 10.1, cdi: 10.50, sp500: 19.4, alpha: 14.4 },
    { mes: 'Nov', carteira: 26.8, ibov: 12.3, cdi: 11.55, sp500: 21.0, alpha: 14.5 },
    { mes: 'Dez', carteira: 29.4, ibov: 14.2, cdi: 12.60, sp500: 22.5, alpha: 15.2 }
  ], []);

  const dadosRadar = useMemo(() => [
    { metrica: 'Retorno %', Carteira: 92, IBOV: 60, SP500: 78, CDI: 45 },
    { metrica: 'Sharpe', Carteira: 88, IBOV: 42, SP500: 75, CDI: 95 },
    { metrica: 'Proteção VaR', Carteira: 85, IBOV: 40, SP500: 70, CDI: 100 },
    { metrica: 'Liquidez', Carteira: 95, IBOV: 90, SP500: 95, CDI: 100 },
    { metrica: 'Sortino', Carteira: 90, IBOV: 45, SP500: 72, CDI: 98 },
    { metrica: 'Alpha Jensen', Carteira: 94, IBOV: 35, SP500: 65, CDI: 10 }
  ], []);

  const metricasChave = {
    retornoAcumulado: '+29.4%',
    alphaJensen: '+15.2%',
    betaIbov: '0.78',
    volatilidadeAnualizada: '11.4%',
    sharpeRatio: '2.14',
    sortinoRatio: '3.08',
    maxDrawdown: '-6.2%'
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '30px' }}>
      
      {/* CABEÇALHO DA ABA ANALYTICS */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '15px' }}>
        <div>
          <h2 style={{ color: theme.textMain, margin: 0, fontSize: '1.8rem', display: 'flex', alignItems: 'center', gap: '10px' }}>
            <span>📈</span> Analytics Quantitativo & Benchmarking
          </h2>
          <p style={{ color: theme.textMuted, margin: '5px 0 0 0', fontSize: '0.9rem' }}>
            Comparativo de Eficiência Risco-Retorno contra CDI, IBOV e S&P 500.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '10px' }}>
          {['1M', '6M', '1Y', 'YTD', 'ALL'].map(p => (
            <button
              key={p}
              onClick={() => setPeriodo(p)}
              style={{
                padding: '6px 14px', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.8rem',
                backgroundColor: periodo === p ? theme.info : theme.cardBg,
                color: periodo === p ? '#fff' : theme.textMuted,
                border: `1px solid ${periodo === p ? theme.info : theme.border}`,
                transition: '0.2s'
              }}
            >
              {p}
            </button>
          ))}
        </div>
      </div>

      {/* KPI GRID - MÉTRICAS QUANTITATIVAS AVANÇADAS */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: '20px' }}>
        <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '10px', borderLeft: `4px solid ${theme.compra}` }}>
          <h4 style={{ color: theme.textMuted, fontSize: '0.8rem', margin: '0 0 5px 0', textTransform: 'uppercase' }}>🚀 Alpha de Jensen</h4>
          <p style={{ color: theme.compra, fontSize: '1.7rem', margin: 0, fontWeight: 'bold', fontFamily: 'monospace' }}>{metricasChave.alphaJensen}</p>
          <p style={{ color: theme.textMuted, fontSize: '0.75rem', margin: '5px 0 0 0' }}>Excesso de retorno vs Mercado</p>
        </div>

        <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '10px', borderLeft: `4px solid ${theme.info}` }}>
          <h4 style={{ color: theme.textMuted, fontSize: '0.8rem', margin: '0 0 5px 0', textTransform: 'uppercase' }}>📊 Índice Sharpe</h4>
          <p style={{ color: theme.info, fontSize: '1.7rem', margin: 0, fontWeight: 'bold', fontFamily: 'monospace' }}>{metricasChave.sharpeRatio}</p>
          <p style={{ color: theme.textMuted, fontSize: '0.75rem', margin: '5px 0 0 0' }}>Eficiência Risco-Ajustado</p>
        </div>

        <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '10px', borderLeft: `4px solid #8b5cf6` }}>
          <h4 style={{ color: theme.textMuted, fontSize: '0.8rem', margin: '0 0 5px 0', textTransform: 'uppercase' }}>🛡️ Índice Sortino</h4>
          <p style={{ color: '#8b5cf6', fontSize: '1.7rem', margin: 0, fontWeight: 'bold', fontFamily: 'monospace' }}>{metricasChave.sortinoRatio}</p>
          <p style={{ color: theme.textMuted, fontSize: '0.75rem', margin: '5px 0 0 0' }}>Proteção a Downside Volatility</p>
        </div>

        <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '10px', borderLeft: `4px solid #f39c12` }}>
          <h4 style={{ color: theme.textMuted, fontSize: '0.8rem', margin: '0 0 5px 0', textTransform: 'uppercase' }}>⚖️ Beta do Portfólio</h4>
          <p style={{ color: '#f39c12', fontSize: '1.7rem', margin: 0, fontWeight: 'bold', fontFamily: 'monospace' }}>{metricasChave.betaIbov}</p>
          <p style={{ color: theme.textMuted, fontSize: '0.75rem', margin: '5px 0 0 0' }}>Sensibilidade vs IBOV</p>
        </div>

        <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '10px', borderLeft: `4px solid #ec4899` }}>
          <h4 style={{ color: theme.textMuted, fontSize: '0.8rem', margin: '0 0 5px 0', textTransform: 'uppercase' }}>📉 Max Drawdown</h4>
          <p style={{ color: '#ec4899', fontSize: '1.7rem', margin: 0, fontWeight: 'bold', fontFamily: 'monospace' }}>{metricasChave.maxDrawdown}</p>
          <p style={{ color: theme.textMuted, fontSize: '0.75rem', margin: '5px 0 0 0' }}>Maior queda de pico a vale</p>
        </div>
      </div>

      {/* GRÁFICO COMPARATIVO DE RENTABILIDADE ACUMULADA */}
      <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '25px', borderRadius: '12px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', flexWrap: 'wrap', gap: '15px' }}>
          <div>
            <h3 style={{ color: theme.textMain, margin: '0 0 5px 0', fontSize: '1.2rem' }}>Evolução de Rentabilidade Acumulada (%)</h3>
            <p style={{ color: theme.textMuted, margin: 0, fontSize: '0.85rem' }}>QuantAdvisor vs Benchmarks Globais</p>
          </div>

          <div style={{ display: 'flex', gap: '8px' }}>
            {[
              { id: 'ALL', label: 'Todos os Benchmarks' },
              { id: 'IBOV', label: 'IBOV' },
              { id: 'CDI', label: 'CDI' },
              { id: 'SP500', label: 'S&P 500' }
            ].map(b => (
              <button
                key={b.id}
                onClick={() => setBenchmarkComparativo(b.id)}
                style={{
                  padding: '6px 12px', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.75rem',
                  backgroundColor: benchmarkComparativo === b.id ? 'rgba(59, 130, 246, 0.15)' : 'transparent',
                  color: benchmarkComparativo === b.id ? theme.info : theme.textMuted,
                  border: `1px solid ${benchmarkComparativo === b.id ? theme.info : theme.border}`
                }}
              >
                {b.label}
              </button>
            ))}
          </div>
        </div>

        <div style={{ width: '100%', height: '380px' }}>
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={historicoPerformance} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={theme.border} vertical={false} />
              <XAxis dataKey="mes" stroke={theme.textMuted} tick={{ fontSize: 12 }} />
              <YAxis stroke={theme.textMuted} tickFormatter={(val) => `${val}%`} />
              <Tooltip
                contentStyle={{ backgroundColor: theme.bg, borderColor: theme.border, borderRadius: '8px', color: theme.textMain }}
                formatter={(val, name) => [`${val.toFixed(1)}%`, name]}
              />
              <Legend wrapperStyle={{ paddingTop: '10px', fontSize: '12px', color: theme.textMain }} />
              
              <Bar dataKey="alpha" name="Alpha Gerado (%)" fill="rgba(16, 185, 129, 0.2)" radius={[4, 4, 0, 0]} />
              <Line type="monotone" dataKey="carteira" name="Sua Carteira (QuantAdvisor)" stroke={theme.compra} strokeWidth={3} dot={{ r: 4 }} activeDot={{ r: 7 }} />

              {(benchmarkComparativo === 'ALL' || benchmarkComparativo === 'IBOV') && (
                <Line type="monotone" dataKey="ibov" name="IBOVESPA" stroke="#f39c12" strokeWidth={2} dot={false} strokeDasharray="4 4" />
              )}
              {(benchmarkComparativo === 'ALL' || benchmarkComparativo === 'CDI') && (
                <Line type="monotone" dataKey="cdi" name="CDI Taxa Selic" stroke="#3b82f6" strokeWidth={2} dot={false} />
              )}
              {(benchmarkComparativo === 'ALL' || benchmarkComparativo === 'SP500') && (
                <Line type="monotone" dataKey="sp500" name="S&P 500 (USD)" stroke="#8b5cf6" strokeWidth={2} dot={false} />
              )}
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* RADAR DE EFICIÊNCIA RISCO-RETORNO MULTI-EIXO */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '20px' }}>
        
        <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '25px', borderRadius: '12px' }}>
          <h3 style={{ color: theme.textMain, margin: '0 0 5px 0', fontSize: '1.1rem' }}>🕸️ Perfil Radar de Eficiência Risco-Retorno</h3>
          <p style={{ color: theme.textMuted, margin: '0 0 20px 0', fontSize: '0.8rem' }}>Pontuação Relativa de Métricas Quantitativas (0 a 100)</p>
          
          <div style={{ width: '100%', height: '300px' }}>
            <ResponsiveContainer width="100%" height="100%">
              <RadarChart data={dadosRadar}>
                <PolarGrid stroke={theme.border} />
                <PolarAngleAxis dataKey="metrica" stroke={theme.textMuted} tick={{ fontSize: 11 }} />
                <PolarRadiusAxis stroke={theme.textMuted} angle={30} domain={[0, 100]} />
                <Radar name="QuantAdvisor" dataKey="Carteira" stroke={theme.compra} fill={theme.compra} fillOpacity={0.4} />
                <Radar name="IBOV" dataKey="IBOV" stroke="#f39c12" fill="#f39c12" fillOpacity={0.15} />
                <Radar name="S&P 500" dataKey="SP500" stroke="#8b5cf6" fill="#8b5cf6" fillOpacity={0.15} />
                <Legend wrapperStyle={{ fontSize: '11px', color: theme.textMain }} />
              </RadarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* ANÁLISE DE ATRIBUIÇÃO DE DESEMPENHO */}
        <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '25px', borderRadius: '12px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <h3 style={{ color: theme.textMain, margin: '0 0 5px 0', fontSize: '1.1rem' }}>📋 Relatório de Atribuição (Performance Attribution)</h3>
            <p style={{ color: theme.textMuted, margin: '0 0 15px 0', fontSize: '0.8rem' }}>Decomposição das fontes de retorno geradas pelo algoritmo.</p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <div style={{ padding: '12px', backgroundColor: 'rgba(16, 185, 129, 0.1)', borderRadius: '8px', border: `1px solid ${theme.compra}` }}>
                <div style={{ fontWeight: 'bold', color: theme.compra, fontSize: '0.85rem' }}>🎯 Stock Picking (Seleção Quantitativa Z-Score)</div>
                <div style={{ color: theme.textMuted, fontSize: '0.8rem', marginTop: '3px' }}>Contribuição de <b>+11.8%</b> impulsionada pela rotação estatística em ativos subavaliados.</div>
              </div>

              <div style={{ padding: '12px', backgroundColor: 'rgba(59, 130, 246, 0.1)', borderRadius: '8px', border: `1px solid ${theme.info}` }}>
                <div style={{ fontWeight: 'bold', color: theme.info, fontSize: '0.85rem' }}>📐 Asset Allocation (Dimensionamento de Kelly)</div>
                <div style={{ color: theme.textMuted, fontSize: '0.8rem', marginTop: '3px' }}>Gerou <b>+5.4%</b> controlando atritos e reduzindo alocação em regimes de incerteza.</div>
              </div>

              <div style={{ padding: '12px', backgroundColor: 'rgba(139, 92, 246, 0.1)', borderRadius: '8px', border: `1px solid #8b5cf6` }}>
                <div style={{ fontWeight: 'bold', color: '#8b5cf6', fontSize: '0.85rem' }}>🌍 Diversificação Internacional (Hedge USD/BRL)</div>
                <div style={{ color: theme.textMuted, fontSize: '0.8rem', marginTop: '3px' }}>Proteção Cambial adicionou <b>+2.2%</b> mitigando riscos de cauda do mercado doméstico.</div>
              </div>
            </div>
          </div>

          <div style={{ marginTop: '20px', paddingTop: '15px', borderTop: `1px solid ${theme.border}`, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.8rem', color: theme.textMuted }}>Veredito Econométrico: <b>Outperform Elevado</b></span>
            <span style={{ fontSize: '0.8rem', color: theme.compra, fontWeight: 'bold' }}>Confiança Modelo: 96.8%</span>
          </div>
        </div>

      </div>

    </div>
  );
};


