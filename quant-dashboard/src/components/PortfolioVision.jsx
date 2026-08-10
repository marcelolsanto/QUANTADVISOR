
import { useState, useEffect } from 'react';
import { getProjecaoPortfolio } from '../services/api';
import { ComposedChart, Bar, Area, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { theme } from '../theme';

export const PortfolioVision = ({ usuarioId }) => {
  const [dados, setDados] = useState(null);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState('');

  // Controles do Gráfico
  const [visaoMensal, setVisaoMensal] = useState(false);
  const [visaoReal, setVisaoReal] = useState(false);

  useEffect(() => {
    const carregarProjecao = async () => {
      setLoading(true);
      setErro('');
      try {
        const res = await getProjecaoPortfolio(usuarioId);
        if (res.data.sucesso) {
          setDados(res.data);
        } else {
          setErro(res.data.erro || 'Erro ao processar dados.');
        }
      } catch (err) {
        setErro(err.response?.data?.erro || 'Módulo indisponível.');
      } finally {
        setLoading(false);
      }
    };
    if (usuarioId) carregarProjecao();
  }, [usuarioId]);

  if (loading) {
    return (
      <div style={{ padding: '40px', textAlign: 'center', backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, borderRadius: '12px' }}>
        <p style={{ color: theme.info, fontWeight: 'bold', fontSize: '1.2rem' }}>⏳ Calculando Juros Líquidos e Simulando Monte Carlo...</p>
      </div>
    );
  }

  if (erro || !dados) {
    return (
      <div style={{ padding: '20px', backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, borderRadius: '12px', textAlign: 'center' }}>
        <h3 style={{ color: theme.venda, margin: '0 0 10px 0' }}>📊 Projeção Patrimonial</h3>
        <p style={{ color: theme.textMuted, margin: 0 }}>{erro}</p>
      </div>
    );
  }

  const { acoes_custodia, caixa_livre, capital_simulado, usou_caixa_ficticio } = dados.composicao_atual;
  const { acoes_ano, cdb_ano, ipca_ano, pre_ano } = dados.taxas_aplicadas;

  const processarRangeEstocastico = (dadosArray) => {
    return dadosArray.map(d => ({
      ...d,
      range_acoes_nominal: [d.alocacao_acoes_pessimista, d.alocacao_acoes_otimista],
      range_acoes_real: [d.alocacao_acoes_pessimista_real, d.alocacao_acoes_otimista_real]
    }));
  };

  const dadosGrafico = processarRangeEstocastico(visaoMensal ? dados.projecao_mensal : dados.projecao_anual);
  const keySuffix = visaoReal ? "_real" : "";

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '30px' }}>

      {/* CARDS SUPERIORES: SEGREGAÇÃO INSTITUCIONAL */}
      <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap' }}>
        <div style={{ flex: 1, minWidth: '220px', backgroundColor: theme.cardBg, padding: '20px', borderRadius: '8px', borderLeft: `4px solid ${theme.info}`, border: `1px solid ${theme.border}` }}>
          <h4 style={{ margin: '0 0 10px 0', color: theme.textMuted, fontSize: '0.85rem', textTransform: 'uppercase' }}>🏦 Ações em Custódia (Base do Cálculo)</h4>
          <div style={{ fontSize: '1.6rem', fontWeight: 'bold', color: theme.textMain }}>R$ {acoes_custodia.toLocaleString('pt-BR', { minimumFractionDigits: 2 })}</div>
          <span style={{ fontSize: '0.8rem', color: theme.info, fontWeight: 'bold' }}>Sua Média Bruta: {acoes_ano}% a.a.</span>
          {acoes_custodia <= 0 && !usou_caixa_ficticio && (
             <div style={{ fontSize: '0.75rem', color: theme.textMuted, marginTop: '5px' }}>* Sem ações. Simulando com o Caixa Livre.</div>
          )}
        </div>

        <div style={{ flex: 1, minWidth: '220px', backgroundColor: theme.cardBg, padding: '20px', borderRadius: '8px', borderLeft: `4px solid ${theme.compra}`, border: `1px solid ${theme.border}` }}>
          <h4 style={{ margin: '0 0 10px 0', color: theme.textMuted, fontSize: '0.85rem', textTransform: 'uppercase' }}>💵 Caixa Livre (Pronto p/ Alocar)</h4>
          <div style={{ fontSize: '1.6rem', fontWeight: 'bold', color: theme.compra }}>R$ {caixa_livre.toLocaleString('pt-BR', { minimumFractionDigits: 2 })}</div>
          {usou_caixa_ficticio && (
            <span style={{ fontSize: '0.8rem', color: theme.alerta }}>* Conta zerada. Usando R$ 1.000 fictícios.</span>
          )}
        </div>

        <div style={{ flex: 1, minWidth: '220px', backgroundColor: theme.cardBg, padding: '20px', borderRadius: '8px', borderLeft: `4px solid #f39c12`, border: `1px solid ${theme.border}` }}>
          <h4 style={{ margin: '0 0 10px 0', color: theme.textMuted, fontSize: '0.85rem', textTransform: 'uppercase' }}>🏆 Maior Taxa Renda Fixa</h4>
          <div style={{ fontSize: '1.6rem', fontWeight: 'bold', color: '#f39c12' }}>{Math.max(cdb_ano, ipca_ano, pre_ano)}% a.a.</div>
          <span style={{ fontSize: '0.8rem', color: theme.textMuted }}>CDB Bancário / Tesouro IPCA+ (Bruto)</span>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '20px', flexWrap: 'wrap' }}>

        {/* GRÁFICO PRINCIPAL: Composto */}
        <div style={{ flex: 2, minWidth: '500px', backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '25px', borderRadius: '12px', boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)' }}>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '20px', flexWrap: 'wrap', gap: '15px' }}>
            <div>
              <h3 style={{ margin: '0 0 5px 0', color: theme.textMain }}>
                🎯 Evolução do Capital Alocado (Horizonte {visaoMensal ? '1 Ano' : '10 Anos'})
              </h3>
              <p style={{ margin: 0, color: theme.textMuted, fontSize: '0.85rem' }}>
                Projeção de crescimento da sua custódia atual de <b>R$ {capital_simulado.toLocaleString('pt-BR', { minimumFractionDigits: 2 })}</b>.
              </p>
            </div>

            <div style={{ display: 'flex', gap: '10px' }}>
              <div style={{ display: 'flex', backgroundColor: theme.bg, borderRadius: '6px', border: `1px solid ${theme.alerta}`, overflow: 'hidden' }}>
                <button
                  onClick={() => setVisaoReal(false)}
                  style={{ padding: '8px 12px', border: 'none', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.75rem', color: !visaoReal ? '#000' : theme.textMuted, backgroundColor: !visaoReal ? theme.alerta : 'transparent' }}
                  title="Dinheiro final na conta"
                >
                  Líquido Nominal
                </button>
                <button
                  onClick={() => setVisaoReal(true)}
                  style={{ padding: '8px 12px', border: 'none', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.75rem', color: visaoReal ? '#000' : theme.textMuted, backgroundColor: visaoReal ? theme.alerta : 'transparent', borderLeft: `1px solid ${theme.alerta}` }}
                  title="Equação de Fisher: Desconta a perda de poder de compra"
                >
                  Poder de Compra Real
                </button>
              </div>

              <div style={{ display: 'flex', backgroundColor: theme.bg, borderRadius: '6px', border: `1px solid ${theme.border}`, overflow: 'hidden' }}>
                <button
                  onClick={() => setVisaoMensal(false)}
                  style={{ padding: '8px 16px', border: 'none', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.85rem', color: !visaoMensal ? '#fff' : theme.textMuted, backgroundColor: !visaoMensal ? theme.info : 'transparent' }}
                >
                  Anual
                </button>
                <button
                  onClick={() => setVisaoMensal(true)}
                  style={{ padding: '8px 16px', border: 'none', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.85rem', color: visaoMensal ? '#fff' : theme.textMuted, backgroundColor: visaoMensal ? theme.compra : 'transparent', borderLeft: `1px solid ${theme.border}` }}
                >
                  Mensal
                </button>
              </div>
            </div>
          </div>

          <div style={{ width: '100%', height: '420px', minHeight: '400px' }}>
            <ResponsiveContainer width="100%" height="100%" minWidth={1} minHeight={1}>
              <ComposedChart data={dadosGrafico} margin={{ top: 20, right: 20, left: 10, bottom: 5 }}>
                <CartesianGrid strokeDasharray="3 3" stroke={theme.border} vertical={false} />

                <XAxis 
                  dataKey={visaoMensal ? "mes_absoluto" : "ano"} 
                  tickFormatter={(v) => visaoMensal ? `Mês ${v}` : `Ano ${v}`} 
                  stroke={theme.textMuted} 
                  tick={{ fontSize: 11 }} 
                />
                
                <YAxis tickFormatter={(v) => `R$ ${(v / 1000).toFixed(0)}k`} stroke={theme.textMuted} tick={{ fontSize: 11 }} width={65} />
                
                <Tooltip
                  formatter={(val, name, props) => {
                    const p = props.payload;
                    if (name === "alocacao_acoes_provavel" || name === "alocacao_acoes_provavel_real") {
                        const pess = visaoReal ? p.alocacao_acoes_pessimista_real : p.alocacao_acoes_pessimista;
                        const otim = visaoReal ? p.alocacao_acoes_otimista_real : p.alocacao_acoes_otimista;
                        return [
                          `R$ ${val.toLocaleString('pt-BR', { minimumFractionDigits: 0 })} (Pior C. R$ ${pess.toLocaleString('pt-BR', { minimumFractionDigits: 0 })} | Melhor C. R$ ${otim.toLocaleString('pt-BR', { minimumFractionDigits: 0 })})`, 
                          "📈 Resgate Ações (Alvo)"
                        ];
                    }
                    
                    const cleanName = name.replace('_real', '');
                    const dict = { 
                      alocacao_selic: `Resgate Selic`, 
                      alocacao_cdb: `Resgate CDB 110%`, 
                      alocacao_lci: `Resgate LCI 95%`, 
                      alocacao_ipca: `Resgate IPCA+`,
                      alocacao_pre: `Resgate Prefixado`
                    };
                    
                    if (name === "range_acoes_nominal" || name === "range_acoes_real") return [null, null];
                    
                    return [`R$ ${val.toLocaleString('pt-BR', { minimumFractionDigits: 2 })} ${visaoReal ? 'Real' : 'Nominal'}`, dict[cleanName]];
                  }}
                  labelFormatter={(l) => visaoMensal ? `Se sacar no Mês ${l}` : `Se sacar no Ano ${l}`}
                  contentStyle={{ backgroundColor: theme.bg, borderColor: theme.border, borderRadius: '8px', color: theme.textMain }}
                  itemStyle={{ fontWeight: 'bold' }}
                />
                
                <Legend wrapperStyle={{ color: theme.textMain, paddingTop: '10px', fontSize: '12px' }} formatter={(v) => {
                   const cleanV = v.replace('_real', '');
                   const d = { 
                     alocacao_acoes_provavel: "📈 Ações (Provável)", 
                     range_acoes_nominal: "☁️ Probabilidade (5%~95%)",
                     range_acoes_real: "☁️ Probabilidade (5%~95%)",
                     alocacao_selic: "🟢 Tesouro Selic", 
                     alocacao_cdb: "🟠 CDB 110%", 
                     alocacao_lci: "🟩 LCI/LCA", 
                     alocacao_ipca: "🟣 Tesouro IPCA+", 
                     alocacao_pre: "🔴 Prefixado" 
                   };
                   return d[cleanV] || v;
                }}/>
                
                <Area 
                  type="monotone" 
                  dataKey={visaoReal ? "range_acoes_real" : "range_acoes_nominal"} 
                  stroke="none" 
                  fill={theme.info} 
                  fillOpacity={0.12} 
                  activeDot={false}
                  tooltipType="none" 
                />

                <Bar 
                  dataKey={`alocacao_acoes_provavel${keySuffix}`} 
                  name={`alocacao_acoes_provavel${keySuffix}`} 
                  fill={theme.info} 
                  radius={[4, 4, 0, 0]} 
                  barSize={visaoMensal ? 35 : 20} 
                  opacity={0.9} 
                />

                <Line type="monotone" dataKey={`alocacao_ipca${keySuffix}`} stroke="#9b59b6" strokeWidth={3} dot={visaoMensal} activeDot={{ r: 6 }} />
                <Line type="monotone" dataKey={`alocacao_cdb${keySuffix}`} stroke="#f39c12" strokeWidth={3} dot={visaoMensal} activeDot={{ r: 6 }} />
                <Line type="monotone" dataKey={`alocacao_pre${keySuffix}`} stroke="#e74c3c" strokeWidth={2} dot={visaoMensal} strokeOpacity={0.7} />
                <Line type="monotone" dataKey={`alocacao_selic${keySuffix}`} stroke={theme.compra} strokeWidth={2} dot={visaoMensal} strokeOpacity={0.7} />
                <Line type="monotone" dataKey={`alocacao_lci${keySuffix}`} stroke="#2ecc71" strokeWidth={2} strokeDasharray="4 4" dot={visaoMensal} />
                
              </ComposedChart> 
            </ResponsiveContainer>
          </div>

        </div>
      </div>
    </div>
  );
};

