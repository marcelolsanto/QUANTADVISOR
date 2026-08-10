import React, { useState, useEffect, useCallback } from 'react';
import { AreaChart, Area, LineChart, Line, Legend, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceLine } from 'recharts';
import { theme } from '../theme';
import { ClientDetailModal } from './ClientDetailModal'; 

const CORES_LINHAS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#14b8a6', '#f43f5e', '#84cc16'];

export default function PainelGestao({ usuarioId, isGestor }) {
  const [visaoMacro, setVisaoMacro] = useState(isGestor);
  const [resumoInd, setResumoInd] = useState({ caixa_livre: 0, custo_aquisicao: 0, patrimonio_total: 0 });
  const [historicoInd, setHistoricoInd] = useState([]);
  const [dadosMacro, setDadosMacro] = useState(null);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState('');
  const [historicoAtivosInd, setHistoricoAtivosInd] = useState([]);
  
  const [metricaGrafico, setMetricaGrafico] = useState('patrimonio');

  const [cotacaoDolar, setCotacaoDolar] = useState(5.00);

  const [modalClienteId, setModalClienteId] = useState(null);
  const [modalClienteNome, setModalClienteNome] = useState('');
  const [infoCliente, setInfoCliente] = useState({ nome: '', perfil: '' });
  const [todosClientes, setTodosClientes] = useState([]);

  useEffect(() => {
    fetch('https://economia.awesomeapi.com.br/last/USD-BRL')
      .then(r => r.json())
      .then(d => setCotacaoDolar(Number(d.USDBRL.ask)))
      .catch(() => console.error("Falha ao buscar Dólar. Usando fallback."));
  }, []);

  const carregarDados = useCallback(async (isSilent = false) => {
    if (!isSilent) setLoading(true);
    setErro('');
    
    try {
      const token = localStorage.getItem('@QuantAdvisor:token_web') || '';
      const headers = { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` };

      if (visaoMacro) {
        const respMacro = await fetch(`/api/dashboard/macro`, { headers });
        if (!respMacro.ok) throw new Error("Erro ao carregar dados Macro do fundo.");
        const dados = await respMacro.json();
        
        // 🛡️ CORREÇÃO DA ORDENAÇÃO: Usando a chave correta (patrimonio_global)
        if (dados.clientes) {
            dados.clientes.sort((a, b) => (b.patrimonio_global || 0) - (a.patrimonio_global || 0));
        }

        setDadosMacro(dados);
      } else {
        const [respResumo, respHistorico, respUsuarios, respHistAtivos] = await Promise.all([
          fetch(`/api/dashboard/resumo?usuario_id=${usuarioId}`, { headers }),
          fetch(`/api/dashboard/historico?usuario_id=${usuarioId}`, { headers }),
          fetch(`/api/usuarios`, { headers }),
          fetch(`/api/dashboard/historico-ativos?usuario_id=${usuarioId}`, { headers })
        ]);

        if (!respResumo.ok) throw new Error("Erro ao carregar dashboard individual.");
        const dadosResumo = await respResumo.json();
        setResumoInd(dadosResumo);
        
        const dadosHistorico = await respHistorico.json();
        setHistoricoInd(dadosHistorico || []);

        if (respHistAtivos.ok) {
            const dadosAtivos = await respHistAtivos.json();
            setHistoricoAtivosInd(dadosAtivos || []);
        }

        if (respUsuarios.ok) {
           const usuariosData = await respUsuarios.json();
           setTodosClientes(usuariosData.map(u => ({ usuario_id: u.id, nome: u.nome, perfil: u.perfil_risco })));
           
           const userMatched = usuariosData.find(u => u.id === usuarioId);
           if (userMatched) {
             setInfoCliente({ nome: userMatched.nome, perfil: userMatched.perfil_risco });
           }
        }
      }
    } catch (err) {
      if (!isSilent) setErro(err.message);
    } finally {
      if (!isSilent) setLoading(false);
    }
  }, [usuarioId, visaoMacro]);

  const formatarBRL = (valor) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(valor || 0);
  const formatarUSD = (valor) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(valor || 0);

  if (loading) return <div style={{ color: theme.info, textAlign: 'center', padding: '40px', fontWeight: 'bold' }}>⏳ Lendo o Razão Contábil...</div>;
  if (erro) return <div style={{ color: theme.venda, textAlign: 'center', padding: '40px', fontWeight: 'bold' }}>⚠️ Erro: {erro}</div>;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '30px' }}>
      
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '15px' }}>
        
        {/* LADO ESQUERDO: TÍTULO E SEMÁFORO DE REGIME */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
          <h2 style={{ color: theme.textMain, margin: 0, fontSize: '1.8rem' }}>
            {visaoMacro ? "Gestão Global do Fundo (AUM)" : "Visão Individual da Carteira"}
          </h2>
          
          {/* 👇 O SEMÁFORO MACROECONÔMICO 👇 */}
          {visaoMacro && dadosMacro?.regime_atual && (
            <div style={{ 
              padding: '6px 12px', borderRadius: '6px', fontWeight: 'bold', fontSize: '0.85rem', display: 'flex', alignItems: 'center', gap: '6px',
              border: `1px solid ${dadosMacro.regime_atual.includes('BULL') ? theme.compra : (dadosMacro.regime_atual.includes('BEAR') ? theme.venda : theme.alerta)}`,
              backgroundColor: dadosMacro.regime_atual.includes('BULL') ? 'rgba(16, 185, 129, 0.1)' : (dadosMacro.regime_atual.includes('BEAR') ? 'rgba(239, 68, 68, 0.1)' : 'rgba(245, 158, 11, 0.1)'),
              color: dadosMacro.regime_atual.includes('BULL') ? theme.compra : (dadosMacro.regime_atual.includes('BEAR') ? theme.venda : theme.alerta)
            }}>
              {dadosMacro.regime_atual.includes('BULL') ? '🐂' : (dadosMacro.regime_atual.includes('BEAR') ? '🐻' : '🦀')} 
              {dadosMacro.regime_atual}
            </div>
          )}
        </div>
        
        {/* LADO DIREITO: BOTÕES DE AÇÃO */}
        <div style={{ display: 'flex', gap: '15px' }}>
          {isGestor && (
            <button 
              onClick={() => {
                setVisaoMacro(!visaoMacro);
                if (!visaoMacro) setMetricaGrafico('patrimonio');
              }}
              style={{ backgroundColor: theme.cardBg, color: theme.info, border: `1px solid ${theme.info}`, padding: '10px 15px', borderRadius: '6px', fontWeight: 'bold', cursor: 'pointer' }}
            >
              {visaoMacro ? "👤 Ver Cliente Atual" : "🌍 Voltar para Visão Macro"}
            </button>
          )}
          <button 
            onClick={() => carregarDados(false)} 
            style={{ backgroundColor: theme.info, color: '#fff', border: 'none', padding: '10px 15px', borderRadius: '6px', fontWeight: 'bold', cursor: 'pointer' }}
          >
            🔄 Atualizar Dados
          </button>
        </div>
      </div>

      {visaoMacro && dadosMacro && (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '20px' }}>
            <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '8px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
              <h3 style={{ color: theme.textMuted, fontSize: '0.9rem', margin: '0 0 10px 0' }}>Total Sob Gestão (AUM)</h3>
              <div>
                <p style={{ color: theme.info, fontSize: '1.8rem', margin: 0, fontWeight: 'bold' }}>🇧🇷 {formatarBRL(dadosMacro.aum_total)}</p>
                <p style={{ color: theme.info, fontSize: '1.1rem', margin: '5px 0 0 0', fontWeight: 'bold', opacity: 0.8 }}>🇺🇸 {formatarUSD(dadosMacro.aum_total / cotacaoDolar)}</p>
              </div>
            </div>
            
            <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '8px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
              <h3 style={{ color: theme.textMuted, fontSize: '0.9rem', margin: '0 0 10px 0' }}>Total Alocado (MtM)</h3>
              <div>
                <p style={{ color: theme.compra, fontSize: '1.8rem', margin: 0, fontWeight: 'bold' }}>🇧🇷 {formatarBRL(dadosMacro.custodia_global)}</p>
                <p style={{ color: theme.compra, fontSize: '1.1rem', margin: '5px 0 0 0', fontWeight: 'bold', opacity: 0.8 }}>🇺🇸 {formatarUSD(dadosMacro.custodia_global / cotacaoDolar)}</p>
              </div>
            </div>
            
            <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '8px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
              <h3 style={{ color: theme.textMuted, fontSize: '0.9rem', margin: '0 0 10px 0' }}>Caixa Global (Dry Powder)</h3>
              <div>
                <p style={{ color: theme.textMain, fontSize: '1.8rem', margin: 0, fontWeight: 'bold' }}>🇧🇷 {formatarBRL(dadosMacro.caixa_global)}</p>
                <p style={{ color: theme.textMain, fontSize: '1.1rem', margin: '5px 0 0 0', fontWeight: 'bold', opacity: 0.8 }}>🇺🇸 {formatarUSD(dadosMacro.caixa_global / cotacaoDolar)}</p>
              </div>
            </div>
            
            <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '8px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
              <h3 style={{ color: theme.textMuted, fontSize: '0.9rem', margin: '0 0 5px 0' }}>Clientes Ativos</h3>
              <div style={{ display: 'flex', alignItems: 'center', height: '100%' }}>
                <p style={{ color: '#a855f7', fontSize: '2.5rem', margin: 0, fontWeight: 'bold' }}>{dadosMacro.total_clientes}</p>
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
            
            <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '25px', borderRadius: '8px' }}>
               <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
                 <h3 style={{ color: theme.textMain, margin: 0, fontSize: '1.2rem' }}>
                    {metricaGrafico === 'patrimonio' ? '📈 Evolução Patrimonial' : '💰 Evolução de Lucro / Prejuízo Diário'}
                 </h3>

                 <div style={{ display: 'flex', backgroundColor: theme.bg, borderRadius: '6px', border: `1px solid ${theme.border}`, overflow: 'hidden' }}>
                    <button
                      onClick={() => setMetricaGrafico('patrimonio')}
                      style={{ padding: '8px 16px', border: 'none', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.85rem', color: metricaGrafico === 'patrimonio' ? '#fff' : theme.textMuted, backgroundColor: metricaGrafico === 'patrimonio' ? theme.info : 'transparent' }}
                    >
                      Patrimônio (Total)
                    </button>
                    <button
                      onClick={() => setMetricaGrafico('lucro')}
                      style={{ padding: '8px 16px', border: 'none', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.85rem', color: metricaGrafico === 'lucro' ? '#fff' : theme.textMuted, backgroundColor: metricaGrafico === 'lucro' ? theme.compra : 'transparent', borderLeft: `1px solid ${theme.border}` }}
                    >
                      Lucro Líquido (P&L)
                    </button>
                  </div>
               </div>

               <div style={{ width: '100%', height: '350px' }}>
                  {dadosMacro.historico_clientes && dadosMacro.historico_clientes.length > 0 ? (
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={dadosMacro.historico_clientes} margin={{ top: 10, right: 30, left: 20, bottom: 0 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke={theme.border} vertical={false} />
                        <XAxis dataKey="data" stroke={theme.textMuted} tick={{ fill: theme.textMuted, fontSize: 12 }} axisLine={false} tickLine={false} />
                        
                        <YAxis 
                          stroke={theme.textMuted} 
                          tick={{ fill: theme.textMuted, fontSize: 12 }} 
                          tickFormatter={(val) => metricaGrafico === 'lucro' ? `R$ ${val.toFixed(0)}` : `R$ ${(val/1000).toFixed(1)}k`} 
                          domain={[
                            dataMin => (dataMin < 0 ? Math.floor(dataMin * 1.05) : Math.floor(dataMin * 0.95)), 
                            dataMax => (dataMax < 0 ? Math.ceil(dataMax * 1.05) : Math.ceil(dataMax * 1.05))
                          ]}
                          axisLine={false} 
                          tickLine={false} 
                        />
                        
                        <Tooltip 
                          contentStyle={{ backgroundColor: theme.bg, borderColor: theme.border, color: theme.textMain, borderRadius: '8px' }} 
                          formatter={(value) => [
                            formatarBRL(value), 
                            metricaGrafico === 'patrimonio' ? "Patrimônio" : "Lucro (P&L)"
                          ]} 
                        />
                        <Legend wrapperStyle={{ color: theme.textMain, fontSize: '12px', paddingTop: '10px' }} />
                        
                        {metricaGrafico === 'lucro' && <ReferenceLine y={0} stroke={theme.textMuted} strokeDasharray="3 3" />}

                        {dadosMacro.clientes && dadosMacro.clientes.map((c, index) => (
                          <Line 
                            key={c.nome} 
                            type="monotone" 
                            dataKey={metricaGrafico === 'patrimonio' ? c.nome : `${c.nome}_lucro`} 
                            name={c.nome} 
                            stroke={CORES_LINHAS[index % CORES_LINHAS.length]} 
                            strokeWidth={2.5} 
                            dot={true} 
                            activeDot={{ r: 6 }} 
                          />
                        ))}
                      </LineChart>
                    </ResponsiveContainer>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', justifyContent: 'center', alignItems: 'center', height: '100%', color: theme.textMuted }}>
                       <span style={{ fontSize: '2rem' }}>📭</span>
                       <span style={{ fontWeight: 'bold' }}>Banco de Dados Vazio</span>
                       <span style={{ fontSize: '0.85rem' }}>A evolução patrimonial começará a ser desenhada hoje após o fechamento do pregão (17h05).</span>
                    </div>
                  )}
               </div>
            </div>

            <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '25px', borderRadius: '8px', overflowX: 'auto' }}>
              <h3 style={{ color: theme.textMain, margin: '0 0 15px 0', fontSize: '1.2rem' }}>Desempenho de Custódia por Cliente</h3>
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', color: theme.textMain, fontSize: '0.9rem' }}>
                <thead>
                  <tr style={{ borderBottom: `2px solid ${theme.border}` }}>
                    <th style={{ padding: '12px', color: theme.textMuted }}>ID</th>
                    <th style={{ padding: '12px', color: theme.textMuted }}>Nome do Cliente</th>
                    <th style={{ padding: '12px', color: theme.textMuted }}>Perfil</th>
                    <th style={{ padding: '12px', color: theme.textMuted }}>Custo Inicial</th>
                    <th style={{ padding: '12px', color: theme.textMuted }}>Caixa Livre</th>
                    <th style={{ padding: '12px', color: theme.info }}>Patrimônio Atual</th>
                    <th style={{ padding: '12px', color: theme.textMuted, textAlign: 'right' }}>Lucro Aberto (P&L)</th>
                  </tr>
                </thead>
                <tbody>
                  {dadosMacro.clientes && dadosMacro.clientes.map((c) => {
                    // 🛡️ CORREÇÃO DO CÁLCULO DA TABELA (Usando as chaves que vêm da API Go agora)
                    const dolarAtual = dadosMacro.cotacao_dolar_ativa || 5.0;
                    const custoAquisicaoTotal = (c.custo_brl || 0) + ((c.custo_usd || 0) * dolarAtual);
                    const caixaLivreTotal = c.caixa_global || 0;
                    const patrimonioTotal = c.patrimonio_global || 0;
                    const lucroFinanceiro = c.lucro_global || 0;
                    
                    const rentabilidadePerc = custoAquisicaoTotal > 0 ? (lucroFinanceiro / custoAquisicaoTotal) * 100 : 0;
                    const isPositivo = lucroFinanceiro >= 0;
                    const corLucro = isPositivo ? theme.compra : theme.venda;
                    const sinalLucro = isPositivo ? '+' : '';

                    return (
                      <tr key={c.usuario_id} style={{ borderBottom: `1px solid ${theme.border}` }}>
                        <td style={{ padding: '12px', fontWeight: 'bold' }}>#{c.usuario_id}</td>
                        <td style={{ padding: '12px' }}>
                          <button 
                            onClick={() => {
                              setModalClienteId(c.usuario_id);
                              setModalClienteNome(c.nome);
                            }}
                            style={{
                              background: 'none', border: 'none', padding: 0, margin: 0,
                              color: theme.info, fontWeight: 'bold', cursor: 'pointer',
                              textDecoration: 'underline', fontSize: '0.9rem', textAlign: 'left'
                            }}
                          >
                            {c.nome}
                          </button>
                        </td>
                        <td style={{ padding: '12px' }}>
                          <span style={{ padding: '4px 8px', borderRadius: '4px', backgroundColor: 'rgba(255,255,255,0.1)', fontSize: '0.75rem', fontWeight: 'bold' }}>{c.perfil}</span>
                        </td>
                        <td style={{ padding: '12px', color: theme.textMuted }}>{formatarBRL(custoAquisicaoTotal)}</td>
                        <td style={{ padding: '12px' }}>{formatarBRL(caixaLivreTotal)}</td>
                        <td style={{ padding: '12px', fontWeight: 'bold', color: theme.info }}>{formatarBRL(patrimonioTotal)}</td>
                        
                        <td style={{ padding: '12px', textAlign: 'right' }}>
                          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end' }}>
                            <span style={{ fontWeight: 'bold', color: corLucro, fontSize: '1rem' }}>
                              {sinalLucro}{formatarBRL(lucroFinanceiro)}
                            </span>
                            <span style={{ fontSize: '0.75rem', color: corLucro, opacity: 0.8 }}>
                              ({sinalLucro}{rentabilidadePerc.toFixed(2)}%)
                            </span>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

          </div>
        </>
      )}

      {!visaoMacro && (() => {
        const custoAquisicao = resumoInd.custo_aquisicao || 0;
        const caixa = resumoInd.caixa_livre || 0;
        const livePoint = historicoInd.length > 0 ? historicoInd[historicoInd.length - 1] : null;
        const patrimonioAoVivo = livePoint && livePoint.data === 'Ao Vivo' ? livePoint.patrimonio : (caixa + custoAquisicao);
        
        const custodia = patrimonioAoVivo - caixa;
        const lucroFinanceiro = custodia - custoAquisicao;
        const rentabilidadePerc = custoAquisicao > 0 ? (lucroFinanceiro / custoAquisicao) * 100 : 0;
        
        const isPositivo = lucroFinanceiro >= 0;
        const corLucro = isPositivo ? theme.compra : theme.venda;
        const sinalLucro = isPositivo ? '+' : '';

        return (
         <>
           <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '20px', marginBottom: '20px' }}>
             
             <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '8px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
               <h3 style={{ color: theme.textMuted, fontSize: '0.9rem', margin: '0 0 10px 0' }}>Patrimônio Individual</h3>
               <div>
                 <p style={{ color: theme.info, fontSize: '1.8rem', margin: 0, fontWeight: 'bold' }}>🇧🇷 {formatarBRL(patrimonioAoVivo)}</p>
                 <p style={{ color: theme.info, fontSize: '1.1rem', margin: '5px 0 0 0', fontWeight: 'bold', opacity: 0.8 }}>🇺🇸 {formatarUSD(patrimonioAoVivo / cotacaoDolar)}</p>
               </div>
             </div>
             
             <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '8px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
               <h3 style={{ color: theme.textMuted, fontSize: '0.9rem', margin: '0 0 10px 0' }}>Alocado em Ativos</h3>
               <div>
                 <p style={{ color: theme.compra, fontSize: '1.8rem', margin: 0, fontWeight: 'bold' }}>🇧🇷 {formatarBRL(custoAquisicao)}</p>
                 <p style={{ color: theme.compra, fontSize: '1.1rem', margin: '5px 0 0 0', fontWeight: 'bold', opacity: 0.8 }}>🇺🇸 {formatarUSD(custoAquisicao / cotacaoDolar)}</p>
               </div>
             </div>

             <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '8px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
               <h3 style={{ color: theme.textMuted, fontSize: '0.9rem', margin: '0 0 10px 0' }}>Caixa Livre</h3>
               <div>
                 <p style={{ color: theme.textMain, fontSize: '1.8rem', margin: 0, fontWeight: 'bold' }}>🇧🇷 {formatarBRL(caixa)}</p>
                 <p style={{ color: theme.textMain, fontSize: '1.1rem', margin: '5px 0 0 0', fontWeight: 'bold', opacity: 0.8 }}>🇺🇸 {formatarUSD(caixa / cotacaoDolar)}</p>
               </div>
             </div>

             <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '8px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
               <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                 <h3 style={{ color: theme.textMuted, fontSize: '0.9rem', margin: 0 }}>Lucro Aberto (P&L)</h3>
                 <span style={{ fontSize: '0.9rem', color: corLucro, opacity: 0.8, fontWeight: 'bold' }}>
                   ({sinalLucro}{rentabilidadePerc.toFixed(2)}%)
                 </span>
               </div>
               <div>
                 <div style={{ color: corLucro, fontSize: '1.8rem', margin: 0, fontWeight: 'bold' }}>
                   🇧🇷 {sinalLucro}{formatarBRL(Math.abs(lucroFinanceiro))}
                 </div>
                 <div style={{ color: corLucro, fontSize: '1.1rem', margin: '5px 0 0 0', fontWeight: 'bold', opacity: 0.8 }}>
                   🇺🇸 {sinalLucro}{formatarUSD(Math.abs(lucroFinanceiro) / cotacaoDolar)}
                 </div>
               </div>
             </div>
             
           </div>

           <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
             
             <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '25px', borderRadius: '8px' }}>
               
               <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', flexWrap: 'wrap', gap: '15px' }}>
                 <h3 style={{ color: theme.textMain, margin: 0, fontSize: '1.2rem' }}>
                    {metricaGrafico === 'patrimonio' ? '📈 Evolução Patrimonial' : metricaGrafico === 'lucro' ? '💰 Evolução de Lucro / Prejuízo (P&L)' : '📊 Ativos em Custódia'}
                 </h3>

                 <div style={{ display: 'flex', backgroundColor: theme.bg, borderRadius: '6px', border: `1px solid ${theme.border}`, overflow: 'hidden' }}>
                    <button
                      onClick={() => setMetricaGrafico('patrimonio')}
                      style={{ padding: '8px 16px', border: 'none', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.85rem', color: metricaGrafico === 'patrimonio' ? '#fff' : theme.textMuted, backgroundColor: metricaGrafico === 'patrimonio' ? theme.info : 'transparent' }}
                    >
                      Patrimônio (Total)
                    </button>
                    <button
                      onClick={() => setMetricaGrafico('ativos')}
                      style={{ padding: '8px 16px', border: 'none', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.85rem', color: metricaGrafico === 'ativos' ? '#fff' : theme.textMuted, backgroundColor: metricaGrafico === 'ativos' ? '#8b5cf6' : 'transparent', borderLeft: `1px solid ${theme.border}` }}
                    >
                      Ativos (Isolados)
                    </button>
                    <button
                      onClick={() => setMetricaGrafico('lucro')}
                      style={{ padding: '8px 16px', border: 'none', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.85rem', color: metricaGrafico === 'lucro' ? '#fff' : theme.textMuted, backgroundColor: metricaGrafico === 'lucro' ? theme.compra : 'transparent', borderLeft: `1px solid ${theme.border}` }}
                    >
                      Lucro Líquido (P&L)
                    </button>
                  </div>
               </div>

               <div style={{ width: '100%', height: '350px' }}>
                 
                 {metricaGrafico === 'ativos' ? (
                   historicoAtivosInd.length > 0 ? (
                      <ResponsiveContainer width="100%" height="100%">
                        <LineChart data={historicoAtivosInd} margin={{ top: 10, right: 30, left: 20, bottom: 0 }}>
                          <CartesianGrid strokeDasharray="3 3" stroke={theme.border} vertical={false} />
                          <XAxis dataKey="data" stroke={theme.textMuted} tick={{ fill: theme.textMuted, fontSize: 12 }} axisLine={false} tickLine={false} />
                          <YAxis 
                              stroke={theme.textMuted} tick={{ fill: theme.textMuted, fontSize: 12 }} 
                              tickFormatter={(val) => `R$ ${val >= 1000 ? (val/1000).toFixed(1) + 'k' : val.toFixed(0)}`} 
                              axisLine={false} tickLine={false} 
                          />
                          <Tooltip 
                              contentStyle={{ backgroundColor: theme.bg, borderColor: theme.border, color: theme.textMain, borderRadius: '8px' }} 
                              formatter={(value, name) => [formatarBRL(value), name]}
                          />
                          <Legend wrapperStyle={{ color: theme.textMain, fontSize: '12px', paddingTop: '10px' }} />

                          {Object.keys(historicoAtivosInd[0] || {})
                              .filter(key => key !== 'data' && key !== 'Ao Vivo')
                              .map((ticker, index) => (
                                 <Line 
                                   key={ticker} 
                                   type="monotone" 
                                   dataKey={ticker} 
                                   name={ticker} 
                                   stroke={CORES_LINHAS[index % CORES_LINHAS.length]} 
                                   strokeWidth={2.5} 
                                   dot={true} 
                                   activeDot={{ r: 6 }} 
                                 />
                          ))}
                        </LineChart>
                      </ResponsiveContainer>
                   ) : (
                     <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', justifyContent: 'center', alignItems: 'center', height: '100%', color: theme.textMuted }}>
                        <span style={{ fontSize: '2rem' }}>📭</span>
                        <span style={{ fontWeight: 'bold' }}>Histórico de Ativos Vazio</span>
                        <span style={{ fontSize: '0.85rem' }}>Execute o script de Backfilling no Golang para ver o histórico reconstruído.</span>
                     </div>
                   )
                 ) : (
                  
                  historicoInd && historicoInd.length > 0 ? (() => {
                    
                    const capitalBase = custoAquisicao + caixa;

                    const historicoProcessado = historicoInd.map(ponto => {
                      const patrimonioDia = Number(ponto.patrimonio) || 0;
                      const lucroDinamico = patrimonioDia - capitalBase;
                      
                      return {
                        ...ponto,
                        valor_exibicao: metricaGrafico === 'patrimonio' ? patrimonioDia : lucroDinamico
                      };
                    });

                    return (
                      <ResponsiveContainer width="100%" height="100%">
                        <AreaChart data={historicoProcessado} margin={{ top: 10, right: 30, left: 20, bottom: 0 }}>
                          <defs>
                            <linearGradient id="colorPatrimonio" x1="0" y1="0" x2="0" y2="1">
                              <stop offset="5%" stopColor={theme.info} stopOpacity={0.4}/>
                              <stop offset="95%" stopColor={theme.info} stopOpacity={0}/>
                            </linearGradient>
                            <linearGradient id="colorLucro" x1="0" y1="0" x2="0" y2="1">
                              <stop offset="5%" stopColor={theme.compra} stopOpacity={0.4}/>
                              <stop offset="95%" stopColor={theme.compra} stopOpacity={0}/>
                            </linearGradient>
                          </defs>
                          <CartesianGrid strokeDasharray="3 3" stroke={theme.border} vertical={false} />
                          <XAxis dataKey="data" stroke={theme.textMuted} tick={{ fill: theme.textMuted, fontSize: 12 }} axisLine={false} tickLine={false} />
                          
                          <YAxis 
                            stroke={theme.textMuted} 
                            tick={{ fill: theme.textMuted, fontSize: 12 }} 
                            tickFormatter={(val) => metricaGrafico === 'lucro' ? `R$ ${val.toFixed(0)}` : `R$ ${(val/1000).toFixed(1)}k`} 
                            domain={[
                              dataMin => (dataMin < 0 ? Math.floor(dataMin * 1.05) : Math.floor(dataMin * 0.95)), 
                              dataMax => (dataMax < 0 ? Math.ceil(dataMax * 0.95) : Math.ceil(dataMax * 1.05))
                            ]} 
                            axisLine={false} 
                            tickLine={false} 
                          />
                          
                          <Tooltip 
                            contentStyle={{ backgroundColor: theme.bg, borderColor: theme.border, color: theme.textMain, borderRadius: '8px' }} 
                            itemStyle={{ color: metricaGrafico === 'patrimonio' ? theme.info : theme.compra, fontWeight: 'bold' }} 
                            formatter={(value) => [formatarBRL(value), metricaGrafico === 'patrimonio' ? "Patrimônio" : "Lucro (P&L)"]} 
                          />
                          
                          {metricaGrafico === 'lucro' && <ReferenceLine y={0} stroke={theme.textMuted} strokeDasharray="3 3" strokeWidth={2} />}

                          <Area 
                            type="monotone" 
                            dataKey="valor_exibicao"
                            stroke={metricaGrafico === 'patrimonio' ? theme.info : theme.compra} 
                            strokeWidth={3} 
                            fillOpacity={1} 
                            fill={metricaGrafico === 'patrimonio' ? "url(#colorPatrimonio)" : "url(#colorLucro)"} 
                            dot={true} 
                          />
                        </AreaChart>
                      </ResponsiveContainer>
                    );
                  })() : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', justifyContent: 'center', alignItems: 'center', height: '100%', color: theme.textMuted }}>
                        <span style={{ fontSize: '2rem' }}>📭</span>
                        <span style={{ fontWeight: 'bold' }}>Banco de Dados Vazio</span>
                        <span style={{ fontSize: '0.85rem' }}>A evolução começará a ser desenhada após o fechamento do pregão (17h05).</span>
                    </div>
                  ))}
               </div>
             </div>

             <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '25px', borderRadius: '8px', overflowX: 'auto' }}>
                <h3 style={{ color: theme.textMain, margin: '0 0 15px 0', fontSize: '1.2rem' }}>Desempenho de Custódia por Cliente</h3>
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', color: theme.textMain, fontSize: '0.9rem' }}>
                  <thead>
                    <tr style={{ borderBottom: `2px solid ${theme.border}` }}>
                      <th style={{ padding: '12px', color: theme.textMuted }}>ID</th>
                      <th style={{ padding: '12px', color: theme.textMuted }}>Nome do Cliente</th>
                      <th style={{ padding: '12px', color: theme.textMuted }}>Perfil</th>
                      <th style={{ padding: '12px', color: theme.textMuted }}>Custo Inicial</th>
                      <th style={{ padding: '12px', color: theme.textMuted }}>Caixa Livre</th>
                      <th style={{ padding: '12px', color: theme.info }}>Patrimônio Atual</th>
                      <th style={{ padding: '12px', color: theme.textMuted, textAlign: 'right' }}>Lucro Aberto (P&L)</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr style={{ borderBottom: `1px solid ${theme.border}` }}>
                      <td style={{ padding: '12px', fontWeight: 'bold' }}>#{usuarioId}</td>
                      <td style={{ padding: '12px' }}>
                        <button 
                          onClick={() => {
                            setModalClienteId(usuarioId);
                            setModalClienteNome(infoCliente.nome);
                          }}
                          style={{
                            background: 'none', border: 'none', padding: 0, margin: 0,
                            color: theme.info, fontWeight: 'bold', cursor: 'pointer',
                            textDecoration: 'underline', fontSize: '0.9rem', textAlign: 'left'
                          }}
                        >
                          {infoCliente.nome || 'Carregando...'}
                        </button>
                      </td>
                      <td style={{ padding: '12px' }}>
                        <span style={{ padding: '4px 8px', borderRadius: '4px', backgroundColor: 'rgba(255,255,255,0.1)', fontSize: '0.75rem', fontWeight: 'bold' }}>
                          {infoCliente.perfil || 'N/A'}
                        </span>
                      </td>
                      <td style={{ padding: '12px', color: theme.textMuted }}>{formatarBRL(custoAquisicao)}</td>
                      <td style={{ padding: '12px' }}>{formatarBRL(caixa)}</td>
                      <td style={{ padding: '12px', fontWeight: 'bold', color: theme.info }}>{formatarBRL(patrimonioAoVivo)}</td>
                      
                      <td style={{ padding: '12px', textAlign: 'right' }}>
                        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end' }}>
                          <span style={{ fontWeight: 'bold', color: corLucro, fontSize: '1rem' }}>
                            {sinalLucro}{formatarBRL(lucroFinanceiro)}
                          </span>
                          <span style={{ fontSize: '0.75rem', color: corLucro, opacity: 0.8 }}>
                            ({sinalLucro}{rentabilidadePerc.toFixed(2)}%)
                          </span>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
             </div>

           </div>
         </>
        );
      })()}

      {modalClienteId && (
        <ClientDetailModal 
          usuarioIdInicial={modalClienteId} 
          nomeClienteInicial={modalClienteNome}
          clientes={visaoMacro ? dadosMacro?.clientes : todosClientes} 
          onClose={() => {
            setModalClienteId(null);
            setModalClienteNome('');
          }} 
        />
      )}

    </div>
  );
}

