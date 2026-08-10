
import React, { useState, useEffect, useMemo } from 'react';
import { ComposedChart, Area, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { Activity, BarChart2, Building2, Users, Briefcase } from 'lucide-react';
import { getDetalhesAtivo } from '../services/api';
import { theme } from '../theme';
import { TradeModal } from './TradeModal';
import ReactMarkdown from 'react-markdown'; // 👈 IMPORTADO AQUI

// 🌍 FUNÇÃO AUXILIAR DE MOEDA NATIVA
const formatarMoedaNativa = (valor, ticker) => {
  const isEstrangeiro = !/\d/.test(ticker) && !ticker.endsWith('.SA');
  const moeda = isEstrangeiro ? 'USD' : 'BRL';
  return new Intl.NumberFormat(isEstrangeiro ? 'en-US' : 'pt-BR', {
    style: 'currency', currency: moeda, minimumFractionDigits: 2
  }).format(Number(valor) || 0);
};

const gerarDatas = (tamanho) => {
  const dates = [];
  let d = new Date();
  for (let i = 0; i < tamanho; i++) {
    while (d.getDay() === 0 || d.getDay() === 6) d.setDate(d.getDate() - 1);
    dates.unshift(d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }));
    d.setDate(d.getDate() - 1);
  }
  return dates;
};

const calcularEMA = (dados, janela) => {
  if (!dados || dados.length === 0) return [];
  const k = 2 / (janela + 1);
  let emaArray = [];
  let emaAnterior = dados[0];

  for (let i = 0; i < dados.length; i++) {
    if (i === 0) {
      emaArray.push(emaAnterior);
    } else {
      let emaAtual = (dados[i] * k) + (emaAnterior * (1 - k));
      emaArray.push(emaAtual);
      emaAnterior = emaAtual;
    }
  }
  return emaArray;
};

export function AssetDeepDive({ ativoData, onClose, usuarioId }) {
  const [dadosBase, setDadosBase] = useState(null);
  const [loading, setLoading] = useState(true);
  const [timeframe, setTimeframe] = useState('3M');
  const [abrirBoleta, setAbrirBoleta] = useState(false);

  const ticker = ativoData?.ativo;
  const ia = ativoData;

  useEffect(() => {
    if (!ticker) return;
    getDetalhesAtivo(ticker).then(res => {
      setDadosBase(res.data);
      setLoading(false);
    }).catch(() => setLoading(false));
  }, [ticker]);

  const dadosGrafico = useMemo(() => {
    if (!dadosBase || !dadosBase.historico || !Array.isArray(dadosBase.historico) || dadosBase.historico.length === 0) return [];

    const precos = dadosBase.historico;
    const datas = gerarDatas(precos.length);
    const ema20 = calcularEMA(precos, 20);
    const ema200 = calcularEMA(precos, 200);

    const fullData = precos.map((p, i) => ({
      data: datas[i],
      preco: p,
      ema20: ema20[i],
      ema200: ema200[i]
    }));

    const cortes = { '1M': 21, '3M': 63, '6M': 126, '1A': 252 };
    const limite = cortes[timeframe] || 63;

    return fullData.slice(-limite);
  }, [dadosBase, timeframe]);

  if (!ativoData) return null;

  if (loading) return <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.85)', zIndex: 2000, display: 'flex', justifyContent: 'center', alignItems: 'center', color: theme.info, fontSize: '1.2rem', fontWeight: 'bold' }}>Extraindo matriz de dados de {ticker}...</div>;

  // Extração segura do Perfil Institucional da Empresa
  const quoteType = dadosBase?.fundamentos?.quoteSummary?.result?.[0]?.quoteType || {};
  const profile = dadosBase?.fundamentos?.quoteSummary?.result?.[0]?.assetProfile || {};
  
  const companyName = quoteType.longName || quoteType.shortName || ticker;
  const sector = profile.sector || 'Setor Institucional Não Classificado';
  const industry = profile.industry || 'Indústria Global';
  const employees = profile.fullTimeEmployees ? profile.fullTimeEmployees.toLocaleString('pt-BR') : 'N/A';
  const summary = profile.longBusinessSummary || 'Resumo executivo indisponível no momento. O motor de ingestão está atualizando os fundamentos na RAM.';

  const fund = dadosBase?.fundamentos?.quoteSummary?.result?.[0]?.financialData || {};
  const ebitda = fund.ebitda?.raw || 1;
  const divida = fund.totalDebt?.raw || 0;
  const dividaEbitda = ebitda !== 0 ? (divida / ebitda).toFixed(2) : 'N/A';
  const margemLiquida = fund.profitMargins?.raw ? (fund.profitMargins.raw * 100).toFixed(2) : 'N/A';

  const sinalFinal = ia.sinalFormatado || 'NEUTRO';

  return (
    <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.85)', zIndex: 4000, display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
      <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.info}`, width: '90%', maxWidth: '1000px', borderRadius: '12px', padding: '25px', overflowY: 'auto', maxHeight: '90vh' }}>

        {/* CABEÇALHO COM NOME COMPLETO DA EMPRESA */}
        <div style={{ display: 'flex', justifyContent: 'space-between', borderBottom: `1px solid ${theme.border}`, paddingBottom: '15px', marginBottom: '20px' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: '12px', flexWrap: 'wrap' }}>
              <h2 style={{ margin: 0, color: theme.textMain, fontSize: '1.8rem' }}>{ticker}</h2>
              <span style={{ fontSize: '1.2rem', color: theme.info, fontWeight: 'bold' }}>{companyName}</span>
            </div>
            <p style={{ margin: '5px 0 0 0', color: theme.textMuted }}>Raio-X Quantitativo & Fundamentalista</p>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', color: theme.textMuted, fontSize: '1.5rem', cursor: 'pointer' }}>✖</button>
        </div>

        {/* 🏢 APRESENTAÇÃO INSTITUCIONAL DA EMPRESA */}
        <div style={{ backgroundColor: theme.bg, borderRadius: '8px', padding: '20px', border: `1px solid ${theme.border}`, marginBottom: '20px' }}>
          <div style={{ display: 'flex', gap: '12px', marginBottom: '12px', flexWrap: 'wrap' }}>
            <span style={{ backgroundColor: 'rgba(59, 130, 246, 0.1)', color: theme.info, padding: '4px 10px', borderRadius: '4px', fontSize: '0.8rem', fontWeight: 'bold', border: `1px solid ${theme.info}`, display: 'flex', alignItems: 'center', gap: '5px' }}>
              <Building2 size={14} /> {sector}
            </span>
            <span style={{ backgroundColor: 'rgba(255, 255, 255, 0.05)', color: theme.textMuted, padding: '4px 10px', borderRadius: '4px', fontSize: '0.8rem', border: `1px solid ${theme.border}`, display: 'flex', alignItems: 'center', gap: '5px' }}>
              <Briefcase size={14} /> {industry}
            </span>
            <span style={{ backgroundColor: 'rgba(255, 255, 255, 0.05)', color: theme.textMuted, padding: '4px 10px', borderRadius: '4px', fontSize: '0.8rem', border: `1px solid ${theme.border}`, display: 'flex', alignItems: 'center', gap: '5px' }}>
              <Users size={14} /> {employees} Colaboradores
            </span>
          </div>
          
          {/* 👇 INJEÇÃO DO TEXTO INTELIGENTE (MARKDOWN) AQUI 👇 */}
          <style>{`
            .markdown-dossie ul { padding-left: 20px; margin-top: 10px; margin-bottom: 10px; }
            .markdown-dossie li { margin-bottom: 6px; }
            .markdown-dossie p { margin-top: 0; margin-bottom: 10px; }
            .markdown-dossie strong { color: ${theme.textMain}; }
          `}</style>
          
          <div className="markdown-dossie" style={{ margin: 0, color: theme.textMuted, fontSize: '0.9rem', lineHeight: '1.6', textAlign: 'justify' }}>
            <ReactMarkdown>{summary}</ReactMarkdown>
          </div>
          {/* 👆 FIM DA INJEÇÃO 👆 */}

        </div>

        <div style={{ display: 'flex', gap: '10px', marginBottom: '15px' }}>
          {['1M', '3M', '6M', '1A'].map(tf => (
            <button key={tf} onClick={() => setTimeframe(tf)} style={{ padding: '6px 12px', borderRadius: '6px', border: `1px solid ${timeframe === tf ? theme.info : theme.border}`, backgroundColor: timeframe === tf ? theme.info : 'transparent', color: timeframe === tf ? '#fff' : theme.textMuted, cursor: 'pointer', fontWeight: 'bold' }}>
              {tf}
            </button>
          ))}
        </div>

        <div style={{ height: '350px', backgroundColor: theme.bg, borderRadius: '8px', padding: '15px', border: `1px solid ${theme.border}` }}>
          {dadosGrafico.length > 0 ? (
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart data={dadosGrafico} margin={{ top: 10, right: 0, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={theme.border} vertical={false} />
                <XAxis dataKey="data" stroke={theme.textMuted} tick={{ fontSize: 10 }} />
                <YAxis domain={['auto', 'auto']} stroke={theme.textMuted} tick={{ fontSize: 12 }} />
                <Tooltip contentStyle={{ backgroundColor: theme.cardBg, borderColor: theme.border, color: theme.textMain }} />

                <Area type="monotone" dataKey="preco" name="Fechamento" stroke={theme.textMain} fill={theme.info} fillOpacity={0.1} strokeWidth={2} />
                <Line type="monotone" dataKey="ema20" name="EMA 20 (Curto)" stroke={theme.compra} dot={false} strokeWidth={1.5} />
                <Line type="monotone" dataKey="ema200" name="EMA 200 (Tendência)" stroke={theme.venda} dot={false} strokeWidth={2} strokeDasharray="5 5" />
              </ComposedChart>
            </ResponsiveContainer>
          ) : (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%', color: theme.textMuted }}>
              O gráfico está a ser gerado pela IA. Volte em 1 minuto...
            </div>
          )}
        </div>

        <div style={{ display: 'flex', gap: '20px', marginTop: '20px', flexWrap: 'wrap' }}>
          <div className="mobile-card" style={{ flex: 1, minWidth: '300px', backgroundColor: theme.bg, padding: '20px', borderRadius: '8px', borderLeft: `4px solid ${theme.info}` }}>
            <h4 style={{ margin: '0 0 15px 0', color: theme.textMain, display: 'flex', alignItems: 'center', gap: '8px' }}><Activity size={18} color={theme.info} /> Decisão do Agente PPO</h4>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px', borderBottom: `1px solid rgba(255,255,255,0.1)`, paddingBottom: '10px' }}>
              <div>
                <div style={{ color: theme.textMuted, fontSize: '0.85rem' }}>Veredicto Final:</div>
                <div style={{ fontWeight: 'bold', fontSize: '1.2rem', color: sinalFinal.includes('COMPRA') ? theme.compra : (sinalFinal.includes('VENDA') ? theme.venda : theme.alerta) }}>{sinalFinal}</div>
              </div>
              <button
                onClick={() => setAbrirBoleta(true)}
                style={{ padding: '8px 16px', backgroundColor: theme.info, color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.9rem' }}
              >
                Negociar
              </button>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '10px' }}>
              <span style={{ color: theme.textMuted }}>Z-Score (Estatística):</span>
              <span style={{ fontWeight: 'bold', color: theme.textMain }}>{ia.z_score?.toFixed(2) || '0.00'}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: theme.textMuted }}>Risco Diário (VaR 99%):</span>
              <span style={{ fontWeight: 'bold', color: ia.risco_var < -5 ? theme.venda : theme.compra }}>{ia.risco_var?.toFixed(2) || '0.00'}%</span>
            </div>
          </div>

          <div className="mobile-card" style={{ flex: 1, minWidth: '300px', backgroundColor: theme.bg, padding: '20px', borderRadius: '8px', borderLeft: `4px solid ${theme.compra}` }}>
            <h4 style={{ margin: '0 0 15px 0', color: theme.textMain, display: 'flex', alignItems: 'center', gap: '8px' }}><BarChart2 size={18} color={theme.compra} /> Saúde Estrutural (Yahoo)</h4>

            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '10px' }}>
              <span style={{ color: theme.textMuted }}>Dívida / EBITDA:</span>
              <span style={{ fontWeight: 'bold', color: dividaEbitda > 3.5 ? theme.venda : theme.compra }}>{dividaEbitda}x</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '10px' }}>
              <span style={{ color: theme.textMuted }}>Margem Líquida:</span>
              <span style={{ fontWeight: 'bold', color: margemLiquida < 0 ? theme.venda : theme.textMain }}>{margemLiquida !== 'N/A' ? `${margemLiquida}%` : 'N/A'}</span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ color: theme.textMuted }}>Preço Atual (MtM):</span>
              <span style={{ fontWeight: 'bold', color: theme.info }}>{formatarMoedaNativa(ia.preco_atual, ticker)}</span>
            </div>
          </div>
        </div>
        
        {abrirBoleta && (
          <TradeModal 
            ativo={{ 
              ativo: ticker, 
              preco_atual: ia.preco_atual || (dadosGrafico.length > 0 ? dadosGrafico[dadosGrafico.length - 1]?.preco : 0),
              quantidade_carteira: ia.quantidade_carteira 
            }} 
            onClose={() => setAbrirBoleta(false)} 
            usuarioId={usuarioId || Number(localStorage.getItem('@QuantAdvisor:user_id_web'))} 
          />
        )}
      </div>
    </div>
  );
}

export default AssetDeepDive;

