
import React, { useState, useEffect } from 'react';
import { subscribeToMarket, subscribeToConnectionStatus } from '../services/stream.js';
import { theme } from '../theme';
import { getSinalVisual } from '../utils/sinal';

export const LiveTerminal = ({ perfilUsuario = 'Agressivo' }) => {
  const [logs, setLogs] = useState([]);
  const [conectado, setConectado] = useState(false);
  
  const [ultimoAlvo, setUltimoAlvo] = useState(() => {
    try {
      const salvo = localStorage.getItem('@QuantAdvisor:ultimo_alvo');
      return salvo ? JSON.parse(salvo) : null;
    } catch (e) { return null; }
  });

  useEffect(() => {
    const unsubStatus = subscribeToConnectionStatus(setConectado);

    const unsubMarket = subscribeToMarket((pacote) => {
      const timestamp = new Date().toLocaleTimeString('pt-BR', { hour12: false });
      
      const sinalDinamico = getSinalVisual(pacote, perfilUsuario);

      const novoLog = {
        id: Date.now() + Math.random(),
        hora: timestamp,
        ...pacote,
        sinal_exibicao: sinalDinamico
      };
      
      setLogs(prevLogs => [novoLog, ...prevLogs].slice(0, 60));

      if (novoLog.sinal_exibicao === 'COMPRA FORTE' || novoLog.sinal_exibicao === 'ALERTA DE VENDA') {
        setUltimoAlvo(novoLog);
      }
    });

    return () => {
      unsubStatus();
      unsubMarket();
    };
  }, [perfilUsuario]); 

  useEffect(() => {
    if (ultimoAlvo) {
      setTimeout(() => {
        localStorage.setItem('@QuantAdvisor:ultimo_alvo', JSON.stringify(ultimoAlvo));
      }, 0);
    }
  }, [ultimoAlvo]);

  const getCorSinal = (sinal) => {
    if (!sinal) return theme.textMuted;
    if (sinal.includes('COMPRA')) return theme.compra; 
    if (sinal.includes('VENDA')) return theme.venda;   
    return theme.info;                                 
  };

  const totalLogs = logs.length;
  const buys = logs.filter(l => l.sinal_exibicao === 'COMPRA FORTE').length;
  const sells = logs.filter(l => l.sinal_exibicao === 'ALERTA DE VENDA').length;
  const neutrals = totalLogs - buys - sells;

  const buyPct = totalLogs ? (buys / totalLogs) * 100 : 0;
  const sellPct = totalLogs ? (sells / totalLogs) * 100 : 0;
  const neuPct = totalLogs ? (neutrals / totalLogs) * 100 : 0;

  const avgZ = totalLogs ? (logs.reduce((acc, l) => acc + (l.z_score || 0), 0) / totalLogs).toFixed(2) : '0.00';
  const avgVar = totalLogs ? (logs.reduce((acc, l) => acc + (l.risco_var || 0), 0) / totalLogs).toFixed(1) : '0.0';

  return (
    <div style={{ backgroundColor: '#050a15', border: `1px solid ${theme.border}`, borderRadius: '8px', overflow: 'hidden', display: 'flex', flexDirection: 'column', height: '380px', boxShadow: 'inset 0 0 20px rgba(0,0,0,0.8)' }}>
      
      <div style={{ backgroundColor: '#0a101d', padding: '10px 15px', borderBottom: `1px solid ${theme.border}`, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span style={{ color: theme.textMuted, fontFamily: 'monospace', fontSize: '0.85rem', fontWeight: 'bold' }}>&gt;_ QUANT_ENGINE_LIVE_FLOW</span>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.75rem', fontWeight: 'bold', fontFamily: 'monospace' }}>
          <div style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: conectado ? theme.compra : theme.venda, boxShadow: `0 0 8px ${conectado ? theme.compra : theme.venda}` }} />
          <span style={{ color: conectado ? theme.compra : theme.venda }}>{conectado ? 'STREAMING ATIVO' : 'CONECTANDO...'}</span>
        </div>
      </div>

      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        <div style={{ flex: '0 0 70%', padding: '15px', overflowY: 'auto', overflowX: 'auto', display: 'flex', flexDirection: 'column', gap: '6px', fontFamily: '"Roboto Mono", monospace', maxHeight: '320px' }}>
          {logs.length === 0 ? (
            <div style={{ color: theme.textMuted, fontStyle: 'italic', opacity: 0.5, fontSize: '0.8rem' }}>Aguardando a próxima varredura do motor em Golang...</div>
          ) : (
            logs.map((log) => {
              const corSinal = getCorSinal(log.sinal_exibicao);
              return (
                <div key={log.id} style={{ display: 'flex', gap: '10px', alignItems: 'center', borderBottom: '1px solid rgba(255,255,255,0.02)', paddingBottom: '4px', fontSize: '0.70rem', whiteSpace: 'nowrap' }}>
                  <span style={{ color: theme.textMuted }}>[{log.hora}]</span>
                  <span style={{ color: '#8b5cf6', fontWeight: 'bold' }}>{log.fonte || 'SYS'}</span>
                  <span style={{ color: '#fff', fontWeight: 'bold', width: '50px' }}>{log.ativo}</span>
                  <span style={{ color: theme.textMuted }}>|</span>
                  <span style={{ color: theme.textMuted }}>MtM: <span style={{ color: theme.textMain }}>R$ {log.preco_atual?.toFixed(2)}</span></span>
                  <span style={{ color: theme.textMuted }}>|</span>
                  <span style={{ color: theme.textMuted }}>Z: <span style={{ color: theme.textMain }}>{log.z_score?.toFixed(2)}</span></span>
                  <span style={{ color: theme.textMuted }}>|</span>
                  <span style={{ color: theme.textMuted }}>VaR: <span style={{ color: theme.textMain }}>{log.risco_var?.toFixed(1)}%</span></span>
                  
                  {/* 👇 NOVAS MÉTRICAS DE FLUXO (MICROESTRUTURA) 👇 */}
                  <span style={{ color: theme.textMuted }}>|</span>
                  <span style={{ color: theme.textMuted }}>Δ VWAP: <span style={{ color: log.distancia_vwap_perc < 0 ? theme.compra : (log.distancia_vwap_perc > 0 ? theme.venda : theme.textMain) }}>{log.distancia_vwap_perc > 0 ? '+' : ''}{log.distancia_vwap_perc?.toFixed(2)}%</span></span>
                  <span style={{ color: theme.textMuted }}>|</span>
                  <span style={{ color: theme.textMuted }}>Vol_Z: <span style={{ color: log.volume_zscore > 1.5 ? theme.alerta : theme.textMain }}>{log.volume_zscore > 0 ? '+' : ''}{log.volume_zscore?.toFixed(2)}</span></span>
                  
                  <span style={{ color: theme.textMuted }}>|</span>
                  <span style={{ color: corSinal, fontWeight: 'bold', textShadow: `0 0 5px ${corSinal}40` }}>
                    {log.sinal_exibicao === 'COMPRA FORTE' ? '🎯 COMPRA FORTE' : log.sinal_exibicao === 'ALERTA DE VENDA' ? '🚨 ALERTA VENDA' : '⏸️ NEUTRO'}
                  </span>
                </div>
              );
            })
          )}
        </div>

        <div style={{ flex: '0 0 30%', backgroundColor: '#030712', borderLeft: `1px solid ${theme.border}`, padding: '20px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
          <div>
            <h4 style={{ color: theme.textMuted, fontSize: '0.75rem', textTransform: 'uppercase', margin: '0 0 10px 0', letterSpacing: '1px' }}>🎯 Último Gatilho Detectado</h4>
            {ultimoAlvo ? (
              <div style={{ backgroundColor: 'rgba(255,255,255,0.05)', border: `1px solid ${getCorSinal(ultimoAlvo.sinal_exibicao)}`, borderRadius: '8px', padding: '15px', animation: 'pulseGlow 2s infinite' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
                  <span style={{ fontSize: '1.4rem', fontWeight: 'bold', color: theme.textMain }}>{ultimoAlvo.ativo}</span>
                  <span style={{ backgroundColor: `${getCorSinal(ultimoAlvo.sinal_exibicao)}20`, color: getCorSinal(ultimoAlvo.sinal_exibicao), padding: '4px 8px', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 'bold' }}>
                    {ultimoAlvo.sinal_exibicao}
                  </span>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem' }}>
                  <div><span style={{ color: theme.textMuted }}>Preço:</span> <strong style={{ color: theme.info }}>R$ {ultimoAlvo.preco_atual?.toFixed(2)}</strong></div>
                  <div><span style={{ color: theme.textMuted }}>Z-Score:</span> <strong style={{ color: theme.textMain }}>{ultimoAlvo.z_score?.toFixed(2)}</strong></div>
                </div>
                {/* 👇 NOVAS MÉTRICAS NO CARD 👇 */}
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem', marginTop: '4px' }}>
                  <div><span style={{ color: theme.textMuted }}>Δ VWAP:</span> <strong style={{ color: ultimoAlvo.distancia_vwap_perc < 0 ? theme.compra : (ultimoAlvo.distancia_vwap_perc > 0 ? theme.venda : theme.textMain) }}>{ultimoAlvo.distancia_vwap_perc > 0 ? '+' : ''}{ultimoAlvo.distancia_vwap_perc?.toFixed(2)}%</strong></div>
                  <div><span style={{ color: theme.textMuted }}>Vol_Z:</span> <strong style={{ color: ultimoAlvo.volume_zscore > 1.5 ? theme.alerta : theme.textMain }}>{ultimoAlvo.volume_zscore > 0 ? '+' : ''}{ultimoAlvo.volume_zscore?.toFixed(2)}</strong></div>
                </div>
                <div style={{ color: theme.textMuted, fontSize: '0.7rem', marginTop: '10px', textAlign: 'right' }}>Visto às {ultimoAlvo.hora}</div>
              </div>
            ) : (
              <div style={{ padding: '20px', textAlign: 'center', color: theme.textMuted, fontStyle: 'italic', border: `1px dashed ${theme.border}`, borderRadius: '8px' }}>
                Aguardando a inteligência artificial encontrar um ativo descontado...
              </div>
            )}
          </div>

          <div>
            <h4 style={{ color: theme.textMuted, fontSize: '0.75rem', textTransform: 'uppercase', margin: '0 0 10px 0', letterSpacing: '1px' }}>📊 Termômetro do Lote Atual</h4>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '15px' }}>
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: '1.2rem', fontWeight: 'bold', color: theme.textMain }}>{avgZ}</div>
                <div style={{ fontSize: '0.7rem', color: theme.textMuted }}>Z-Score Médio</div>
              </div>
              <div style={{ width: '1px', backgroundColor: theme.border }}></div>
              <div style={{ textAlign: 'center' }}>
                <div style={{ fontSize: '1.2rem', fontWeight: 'bold', color: theme.venda }}>{avgVar}%</div>
                <div style={{ fontSize: '0.7rem', color: theme.textMuted }}>VaR Médio</div>
              </div>
            </div>
            <div style={{ width: '100%', height: '8px', backgroundColor: theme.border, borderRadius: '4px', display: 'flex', overflow: 'hidden', marginBottom: '8px' }}>
              <div style={{ width: `${buyPct}%`, backgroundColor: theme.compra, transition: 'width 0.3s' }}></div>
              <div style={{ width: `${neuPct}%`, backgroundColor: theme.info, transition: 'width 0.3s' }}></div>
              <div style={{ width: `${sellPct}%`, backgroundColor: theme.venda, transition: 'width 0.3s' }}></div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.7rem', color: theme.textMuted, fontWeight: 'bold' }}>
              <span style={{ color: theme.compra }}>{buys} C</span>
              <span style={{ color: theme.info }}>{neutrals} N</span>
              <span style={{ color: theme.venda }}>{sells} V</span>
            </div>
          </div>
        </div>
      </div>
      <style>{`@keyframes fadeIn { from { opacity: 0; transform: translateY(-5px); } to { opacity: 1; transform: translateY(0); } } @keyframes pulseGlow { 0% { box-shadow: 0 0 0 0 rgba(139, 92, 246, 0.4); } 50% { box-shadow: 0 0 10px 0 rgba(139, 92, 246, 0.1); } 100% { box-shadow: 0 0 0 0 rgba(139, 92, 246, 0.4); } }`}</style>
    </div>
  );
};

