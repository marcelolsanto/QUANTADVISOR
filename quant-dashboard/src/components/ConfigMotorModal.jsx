
import React, { useState, useEffect } from 'react';
import { getParametros, updateParametros, togglePiloto } from '../services/api';
import { theme } from '../theme';

export const ConfigMotorModal = ({ usuarioIdInicial, nomeClienteInicial, clientes, onClose }) => {
  const [activeUserId, setActiveUserId] = useState(usuarioIdInicial);
  const [activeUserName, setActiveUserName] = useState(nomeClienteInicial);

  const perfilClienteAtivo = clientes?.find(c => (c.usuario_id || c.id) === activeUserId)?.perfil_risco || 'Moderado';

  const [parametros, setParametros] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [statusMsg, setStatusMsg] = useState({ tipo: '', texto: '' });
  const [setupInicial, setSetupInicial] = useState(false);
  const [pilotoStatus, setPilotoStatus] = useState(false);
  const [loadingPiloto, setLoadingPiloto] = useState(false);

  // 🧠 A NOVA FÁBRICA DE PERFIS (Sincronizada com o Horizonte de Risco do Python)
  const getParametrosPorPerfil = (perfil, id) => {
    const perfis = {
      'Conservador': { 
        multiplicador_kelly: 0.05,        
        limite_concentracao_ativo: 0.05,  
        gatilho_rebalanceamento: 0.06,    
        piso_max_drawdown: -0.01,         // 🛡️ NOVO: Trava de 1% na carteira
        multiplicador_stop_var: 1.1,      
        z_score_compra_forte: -2.5,       
        z_score_venda_lucro: 1.0,         
        z_score_stop_loss: -2.0           
      },
      'Moderado': { 
        multiplicador_kelly: 0.10,        
        limite_concentracao_ativo: 0.10,  
        gatilho_rebalanceamento: 0.12,    
        piso_max_drawdown: -0.02,         // 🛡️ NOVO: Trava de 2% na carteira
        multiplicador_stop_var: 1.3,      
        z_score_compra_forte: -2.0,       
        z_score_venda_lucro: 1.5,         
        z_score_stop_loss: -2.5           
      },
      'Arrojado': { 
        multiplicador_kelly: 0.20,        
        limite_concentracao_ativo: 0.15,  
        gatilho_rebalanceamento: 0.20,    
        piso_max_drawdown: -0.03,         // 🛡️ NOVO: Trava de 3%
        multiplicador_stop_var: 1.6,      
        z_score_compra_forte: -1.5,       
        z_score_venda_lucro: 2.5,         
        z_score_stop_loss: -3.5           
      },
      'Agressivo': { 
        multiplicador_kelly: 0.35,        
        limite_concentracao_ativo: 0.25,  
        gatilho_rebalanceamento: 0.03,    
        piso_max_drawdown: -0.04,         // 🛡️ NOVO: Teto rígido
        multiplicador_stop_var: 1.8,      
        z_score_compra_forte: -1.2,       
        z_score_venda_lucro: 0.5,         
        z_score_stop_loss: -3.0           
      },
    };
    
    const config = perfis[perfil] || perfis['Moderado']; 
    
    return {
      usuario_id: id,
      bloqueio_sentimento_negativo: true, 
      custo_friccao_padrao: 0.0003, 
      modo_isencao_fiscal_estrita: true, 
      ...config
    };
  };

  useEffect(() => {
    const carregarConfiguracoes = async () => {
      setLoading(true);
      setStatusMsg({ tipo: '', texto: '' });
      try {
        const res = await getParametros(activeUserId);
        setParametros(res.data);
        setSetupInicial(false);
      } catch (err) {
        if (err.response && err.response.status === 404) {
          setSetupInicial(true);
          setParametros(getParametrosPorPerfil(perfilClienteAtivo, activeUserId));
          setStatusMsg({ tipo: 'alerta', texto: `Aplicando Setup de Tolerância a Risco: ${perfilClienteAtivo.toUpperCase()}` });
        } else {
          setStatusMsg({ tipo: 'erro', texto: 'Não foi possível carregar as configurações do motor.' });
        }
      } finally {
        setLoading(false);
      }
    };
    carregarConfiguracoes();
  }, [activeUserId, perfilClienteAtivo]);

  useEffect(() => {
    const cl = clientes?.find(c => (c.usuario_id || c.id) === activeUserId);
    if (cl) {
      setPilotoStatus(cl.piloto_automatico === true);
    }
  }, [activeUserId, clientes]);

  const handleTogglePiloto = async () => {
    setLoadingPiloto(true);
    try {
      await togglePiloto({ usuario_id: activeUserId, estado: !pilotoStatus });
      setPilotoStatus(!pilotoStatus);
      setStatusMsg({ tipo: 'sucesso', texto: `Piloto automático ${!pilotoStatus ? 'LIGADO' : 'DESLIGADO'} com sucesso!` });
    } catch (err) {
      setStatusMsg({ tipo: 'erro', texto: 'Erro ao alterar a permissão do Piloto Automático.' });
    } finally {
      setLoadingPiloto(false);
    }
  };

  const handleChange = (campo, valor) => {
    setParametros({ ...parametros, [campo]: valor });
  };

  const handleSalvar = async () => {
    setSaving(true);
    setStatusMsg({ tipo: '', texto: '' });
    try {
      await updateParametros(parametros);
      setStatusMsg({ tipo: 'sucesso', texto: '⚙️ Calibragem salva! O robô já obedecerá as novas regras na próxima varredura.' });
      setSetupInicial(false);
    } catch (err) {
      setStatusMsg({ tipo: 'erro', texto: 'Erro ao salvar calibração no banco de dados.' });
    } finally {
      setSaving(false);
    }
  };

  if (loading) return (
    <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 4000 }}>
      <div style={{ color: theme.info, fontSize: '1.2rem', fontWeight: 'bold' }}>⏳ Lendo parâmetros do Motor...</div>
    </div>
  );

  const secaoStyle = { backgroundColor: theme.bg, padding: '20px', borderRadius: '8px', border: `1px solid ${theme.border}`, marginBottom: '20px' };
  const tituloSecaoStyle = { margin: '0 0 15px 0', color: theme.textMain, fontSize: '1.1rem', borderBottom: `1px solid ${theme.border}`, paddingBottom: '10px' };
  const labelStyle = { fontWeight: 'bold', color: theme.textMain, display: 'flex', justifyContent: 'space-between', marginBottom: '5px' };
  const descStyle = { fontSize: '0.8rem', color: theme.textMuted, marginBottom: '10px', lineHeight: '1.4' };
  const rangeStyle = { width: '100%', cursor: 'pointer', accentColor: theme.info };

  return (
    <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 4000 }}>
      <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.info}`, borderRadius: '12px', width: '95%', maxWidth: '800px', height: '90vh', display: 'flex', flexDirection: 'column', boxShadow: `0 0 40px -10px ${theme.info}` }}>

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '20px 30px', borderBottom: `1px solid ${theme.border}`, backgroundColor: 'rgba(59, 130, 246, 0.05)', flexWrap: 'wrap', gap: '15px' }}>
          <div>
            <h2 style={{ margin: 0, color: theme.info, fontSize: '1.5rem', display: 'flex', alignItems: 'center', gap: '8px' }}>
              ⚙️ Calibragem Quantitativa
            </h2>

            {clientes && clientes.length > 0 ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginTop: '10px' }}>
                <span style={{ color: theme.textMuted, fontSize: '0.9rem' }}>Ajustando robô de:</span>
                <select
                  value={activeUserId}
                  onChange={(e) => {
                    const newId = Number(e.target.value);
                    setActiveUserId(newId);
                    const cl = clientes.find(c => (c.usuario_id || c.id) === newId);
                    if (cl) setActiveUserName(cl.nome_cliente || cl.nome);
                  }}
                  style={{
                    padding: '4px 10px', borderRadius: '6px', backgroundColor: 'rgba(59, 130, 246, 0.1)',
                    color: theme.info, border: `1px solid ${theme.info}`, outline: 'none', cursor: 'pointer',
                    fontWeight: 'bold', fontSize: '0.9rem'
                  }}
                >
                  {clientes.map(c => (
                    <option key={c.usuario_id || c.id} value={c.usuario_id || c.id}>
                      {c.nome_cliente || c.nome} ({c.perfil_risco || c.perfil || 'N/A'})
                    </option>
                  ))}
                </select>
              </div>
            ) : (
              <p style={{ margin: '5px 0 0 0', color: theme.textMuted, fontSize: '0.9rem' }}>Ajustando proteção para: <strong style={{ color: theme.textMain }}>{activeUserName}</strong></p>
            )}
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', color: theme.textMuted, fontSize: '1.5rem', cursor: 'pointer' }}>✖</button>
        </div>

        <div style={{ flex: 1, padding: '30px', overflowY: 'auto' }}>

          {statusMsg.texto && (
            <div style={{
              padding: '15px', borderRadius: '6px', marginBottom: '20px', fontWeight: 'bold', textAlign: 'center',
              backgroundColor: statusMsg.tipo === 'sucesso' ? 'rgba(16, 185, 129, 0.1)' : (statusMsg.tipo === 'alerta' ? 'rgba(245, 158, 11, 0.1)' : 'rgba(239, 68, 68, 0.1)'),
              color: statusMsg.tipo === 'sucesso' ? theme.compra : (statusMsg.tipo === 'alerta' ? theme.alerta : theme.venda),
              border: `1px solid ${statusMsg.tipo === 'sucesso' ? theme.compra : (statusMsg.tipo === 'alerta' ? theme.alerta : theme.venda)}`
            }}>
              {statusMsg.texto}
            </div>
          )}

          {parametros ? (
            <>
              {/* MÓDULO 0: CONTROLE MESTRE (PILOTO IA) */}
              <div style={{ ...secaoStyle, border: `1px solid ${pilotoStatus ? theme.compra : theme.venda}` }}>
                <h3 style={{ ...tituloSecaoStyle, color: pilotoStatus ? theme.compra : theme.textMain }}>🤖 Piloto Automático (Execução Direta)</h3>

                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px', backgroundColor: 'rgba(255,255,255,0.02)', borderRadius: '6px' }}>
                  <div style={{ flex: 1, paddingRight: '20px' }}>
                    <div style={{ fontWeight: 'bold', color: theme.textMain }}>Permissão de Negociação Ativa</div>
                    <div style={{ fontSize: '0.8rem', color: theme.textMuted }}>Se ativado, o robô irá rotear compras e vendas automaticamente para a corretora/B3. Se desativado, a IA apenas enviará as sugestões para o Carrinho Noturno.</div>
                  </div>
                  <button
                    onClick={handleTogglePiloto}
                    disabled={loadingPiloto}
                    style={{
                      padding: '8px 16px', borderRadius: '20px', fontWeight: 'bold',
                      cursor: loadingPiloto ? 'wait' : 'pointer',
                      backgroundColor: pilotoStatus ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)',
                      color: pilotoStatus ? theme.compra : theme.venda,
                      border: `1px solid ${pilotoStatus ? theme.compra : theme.venda}`,
                      transition: '0.3s'
                    }}
                  >
                    {loadingPiloto ? '⏳...' : (pilotoStatus ? '🤖 LIGADO' : '⏸️ DESLIGADO')}
                  </button>
                </div>
              </div>

              {/* MÓDULO 3: PROTEÇÃO DE CAUDA (REESTRUTURADO PARA O TOPO E MAIS RÍGIDO) */}
              <div style={{ ...secaoStyle, border: `1px solid ${theme.venda}`, backgroundColor: 'rgba(239, 68, 68, 0.05)' }}>
                <h3 style={{ ...tituloSecaoStyle, color: theme.venda, borderBottomColor: theme.venda }}>🛡️ Travas de Risco de Cauda (Limites de Perda)</h3>

                <div style={{ marginBottom: '20px' }}>
                  <div style={{ ...labelStyle, color: theme.venda }}>
                    <span>Trava de Sobrevivência (Max Drawdown Global)</span>
                    <span style={{ color: theme.venda, fontSize: '1.2rem' }}>{(parametros.piso_max_drawdown * 100).toFixed(1)}% do Patrimônio</span>
                  </div>
                  <p style={{ ...descStyle, color: '#fca5a5' }}>
                    <strong>BOTÃO DE PÂNICO:</strong> Se a soma de TODAS as operações do fundo cair esse percentual exato, o robô liquida as posições a mercado e congela a conta instantaneamente para evitar a falência.
                  </p>
                  <input type="range" min="-0.10" max="-0.005" step="0.005" value={parametros.piso_max_drawdown} onChange={(e) => handleChange('piso_max_drawdown', parseFloat(e.target.value))} style={{ ...rangeStyle, accentColor: theme.venda }} />
                </div>

                <div style={{ marginBottom: '20px' }}>
                  <div style={labelStyle}>
                    <span>Stop Loss Estatístico (Tese Quebrada por Ativo)</span>
                    <span style={{ color: theme.alerta }}>Pânico em: {parametros.z_score_stop_loss.toFixed(1)} Z-Score</span>
                  </div>
                  <p style={descStyle}>Se o ativo desabar além da matemática esperada, a tese falhou. O robô vende a mercado e corta o prejuízo.</p>
                  <input type="range" min="-6.0" max="-1.5" step="0.1" value={parametros.z_score_stop_loss} onChange={(e) => handleChange('z_score_stop_loss', parseFloat(e.target.value))} style={rangeStyle} />
                </div>
                
                <div>
                  <div style={labelStyle}>
                    <span>Stop Loss Dinâmico (Múltiplo de Volatilidade)</span>
                    <span style={{ color: theme.alerta }}>{parametros.multiplicador_stop_var.toFixed(1)}x a Volatilidade (VaR)</span>
                  </div>
                  <p style={descStyle}>Proteção para dias de escândalos e quedas em abismo. Corta a posição se o solavanco de hoje for X vezes mais forte que a média de oscilação do ativo.</p>
                  <input type="range" min="1.0" max="4.0" step="0.1" value={parametros.multiplicador_stop_var} onChange={(e) => handleChange('multiplicador_stop_var', parseFloat(e.target.value))} style={rangeStyle} />
                </div>
              </div>


              {/* MÓDULO 1: SIZING */}
              <div style={secaoStyle}>
                <h3 style={tituloSecaoStyle}>📏 Dimensionamento de Posição e Lucros</h3>

                <div style={{ marginBottom: '20px' }}>
                  <div style={labelStyle}>
                    <span>Agressividade de Entrada (Fração de Kelly)</span>
                    <span style={{ color: theme.info }}>{(parametros.multiplicador_kelly * 100).toFixed(0)}% da Recomendação Ideal</span>
                  </div>
                  <p style={descStyle}>Determina o quanto o robô confia na IA. Valores pequenos (ex: 5%) geram um giro rápido sem expor o grosso do patrimônio (Scalp), limitando riscos.</p>
                  <input type="range" min="0.01" max="1.0" step="0.01" value={parametros.multiplicador_kelly} onChange={(e) => handleChange('multiplicador_kelly', parseFloat(e.target.value))} style={rangeStyle} />
                </div>

                <div style={{ marginBottom: '20px' }}>
                  <div style={labelStyle}>
                    <span>Concentração Máxima por Ativo</span>
                    <span style={{ color: theme.alerta }}>{(parametros.limite_concentracao_ativo * 100).toFixed(0)}% do Patrimônio</span>
                  </div>
                  <p style={descStyle}>Teto absoluto de exposição. O robô é proibido de alocar mais do que esse percentual em uma única empresa, evitando o Risco de Ruína.</p>
                  <input type="range" min="0.01" max="0.5" step="0.01" value={parametros.limite_concentracao_ativo} onChange={(e) => handleChange('limite_concentracao_ativo', parseFloat(e.target.value))} style={rangeStyle} />
                </div>

                <div>
                  <div style={labelStyle}>
                    <span>Take Profit Fixo (Garantia de Lucro)</span>
                    <span style={{ color: theme.compra }}>+{(parametros.gatilho_rebalanceamento * 100).toFixed(0)}% de lucro</span>
                  </div>
                  <p style={descStyle}>Força a venda da posição para realizar o lucro no DRE e jogar o dinheiro de volta para a segurança do caixa livre.</p>
                  <input type="range" min="0.01" max="0.5" step="0.01" value={parametros.gatilho_rebalanceamento} onChange={(e) => handleChange('gatilho_rebalanceamento', parseFloat(e.target.value))} style={rangeStyle} />
                </div>
              </div>

              {/* MÓDULO 2: Z-SCORE (COMPRA E VENDA) */}
              <div style={secaoStyle}>
                <h3 style={tituloSecaoStyle}>🧠 Gatilhos Estatísticos da Inteligência Artificial</h3>

                <div style={{ marginBottom: '20px' }}>
                  <div style={labelStyle}>
                    <span>Entrada Segura (Oportunidade Z-Score)</span>
                    <span style={{ color: theme.compra }}>Comprar se abaixo de: {parametros.z_score_compra_forte.toFixed(1)}</span>
                  </div>
                  <p style={descStyle}>Nível de "pânico irracional" do mercado. Valores extremos (-2.5) obrigam o robô a ter extrema paciência, entrando apenas em distorções garantidas.</p>
                  <input type="range" min="-4.0" max="-0.5" step="0.1" value={parametros.z_score_compra_forte} onChange={(e) => handleChange('z_score_compra_forte', parseFloat(e.target.value))} style={rangeStyle} />
                </div>

                <div>
                  <div style={labelStyle}>
                    <span>Take Profit Dinâmico (Saída Z-Score)</span>
                    <span style={{ color: theme.info }}>Vender se acima de: {parametros.z_score_venda_lucro > 0 ? '+' : ''}{parametros.z_score_venda_lucro.toFixed(1)}</span>
                  </div>
                  <p style={descStyle}>Vende o ativo quando a tese de reversão à média for cumprida. 0.0 vende assim que voltar à média histórica. +2.0 segura a ação até o topo da euforia de mercado.</p>
                  <input type="range" min="0.0" max="3.0" step="0.1" value={parametros.z_score_venda_lucro} onChange={(e) => handleChange('z_score_venda_lucro', parseFloat(e.target.value))} style={rangeStyle} />
                </div>
              </div>

              {/* MÓDULO 4: FISCAL */}
              <div style={secaoStyle}>
                <h3 style={tituloSecaoStyle}>🏛️ Tesouraria e Compliance</h3>

                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px', backgroundColor: 'rgba(255,255,255,0.02)', borderRadius: '6px', marginBottom: '10px' }}>
                  <div style={{ flex: 1, paddingRight: '20px' }}>
                    <div style={{ fontWeight: 'bold', color: theme.textMain }}>Guardião Fiscal de Isenção (R$ 20k/35k)</div>
                    <div style={{ fontSize: '0.8rem', color: theme.textMuted }}>Se ativado, o robô bloqueia vendas no verde para evitar o pagamento de DARF de Ganho de Capital, focando em eficiência tributária.</div>
                  </div>
                  <button onClick={() => handleChange('modo_isencao_fiscal_estrita', !parametros.modo_isencao_fiscal_estrita)} style={{ padding: '8px 16px', borderRadius: '20px', border: 'none', fontWeight: 'bold', cursor: 'pointer', backgroundColor: parametros.modo_isencao_fiscal_estrita ? theme.compra : theme.border, color: '#fff' }}>
                    {parametros.modo_isencao_fiscal_estrita ? 'ESTRITO (PROTEGIDO)' : 'MODO LUCRO (GERA DARF)'}
                  </button>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px', backgroundColor: 'rgba(255,255,255,0.02)', borderRadius: '6px' }}>
                  <div style={{ flex: 1, paddingRight: '20px' }}>
                    <div style={{ fontWeight: 'bold', color: theme.textMain }}>Proteção de Escândalos (NLP FinBERT)</div>
                    <div style={{ fontSize: '0.8rem', color: theme.textMuted }}>Veta entradas se as manchetes das últimas 24h apontarem escândalos de corrupção ou quebra (Ignora o Z-Score nesses casos).</div>
                  </div>
                  <button onClick={() => handleChange('bloqueio_sentimento_negativo', !parametros.bloqueio_sentimento_negativo)} style={{ padding: '8px 16px', borderRadius: '20px', border: 'none', fontWeight: 'bold', cursor: 'pointer', backgroundColor: parametros.bloqueio_sentimento_negativo ? theme.compra : theme.border, color: '#fff' }}>
                    {parametros.bloqueio_sentimento_negativo ? 'ATIVADO' : 'DESLIGADO'}
                  </button>
                </div>
              </div>
            </>
          ) : (
            <div style={{ textAlign: 'center', padding: '40px', color: theme.textMuted }}>
              A comunicação com o Banco de Dados falhou criticamente.
            </div>
          )}

        </div>

        <div style={{ padding: '20px 30px', borderTop: `1px solid ${theme.border}`, backgroundColor: theme.cardBg, display: 'flex', gap: '15px' }}>
          <button onClick={onClose} disabled={saving} style={{ flex: 1, padding: '12px', background: 'none', border: `1px solid ${theme.border}`, color: theme.textMuted, borderRadius: '6px', fontWeight: 'bold', cursor: 'pointer' }}>
            CANCELAR
          </button>
          <button
            onClick={handleSalvar}
            disabled={saving || !parametros}
            style={{ flex: 2, padding: '12px', background: setupInicial ? theme.compra : theme.info, color: '#fff', border: 'none', borderRadius: '6px', fontWeight: 'bold', cursor: saving ? 'wait' : 'pointer', transition: '0.2s' }}>
            {saving ? '⏳ ATIVANDO PROTOCOLO...' : (setupInicial ? '🚀 INICIAR CALIBRAGEM' : '💾 APLICAR CALIBRAGEM DE RISCO')}
          </button>
        </div>

      </div>
    </div>
  );
};

