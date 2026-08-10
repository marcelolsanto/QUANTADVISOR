
import { useState, useEffect } from 'react';
import { enviarOrdem, getMonteCarlo, adicionarAoCarrinho, getBacktest, getPrevisaoLSTM } from '../services/api';
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
import { theme } from '../theme';

// 🌍 FUNÇÃO AUXILIAR DE MOEDA NATIVA
const formatarMoedaNativa = (valor, ticker) => {
  const isEstrangeiro = !/\d/.test(ticker) && !ticker.endsWith('.SA');
  const moeda = isEstrangeiro ? 'USD' : 'BRL';
  return new Intl.NumberFormat(isEstrangeiro ? 'en-US' : 'pt-BR', {
    style: 'currency', currency: moeda, minimumFractionDigits: 2
  }).format(Number(valor) || 0);
};

export const TradeModal = ({ ativo, onClose, usuarioId }) => {
  const [quantidade, setQuantidade] = useState(100);
  const [tipoOrdem, setTipoOrdem] = useState('COMPRA');
  const [qtdCustodia, setQtdCustodia] = useState(ativo.quantidade_carteira || 0);
  const [status, setStatus] = useState({ loading: false, erro: '', sucesso: '' });

  const [abaAtiva, setAbaAtiva] = useState('BACKTEST');
  
  const [backtest, setBacktest] = useState(null);
  const [loadingBacktest, setLoadingBacktest] = useState(true);

  const [monteCarlo, setMonteCarlo] = useState(null);
  const [loadingMC, setLoadingMC] = useState(false);

  const [previsaoLSTM, setPrevisaoLSTM] = useState(null);
  const [loadingLSTM, setLoadingLSTM] = useState(false);

  useEffect(() => {
    if (ativo && ativo.quantidade_carteira !== undefined) {
      setQtdCustodia(ativo.quantidade_carteira);
    }
  }, [ativo, usuarioId]);

  // 1. CORREÇÃO: Dispara a busca do Backtest assim que a Boleta é aberta
  useEffect(() => {
    let isMounted = true;
    const carregarBacktest = async () => {
      setLoadingBacktest(true);
      try {
        const res = await getBacktest(ativo.ativo);
        if (isMounted) setBacktest(res.data);
      } catch (err) {
        const msg = err.response?.data?.detail || err.response?.data?.erro || "Falha na análise histórica.";
        if (isMounted) setBacktest({ sucesso: false, erro: msg });
      } finally {
        if (isMounted) setLoadingBacktest(false);
      }
    };

    if (ativo?.ativo) {
      carregarBacktest();
    }

    return () => { isMounted = false; };
  }, [ativo]);

  // 2. CORREÇÃO: Busca do Monte Carlo com tratamento de erro real
  const carregarMonteCarlo = async () => {
    setAbaAtiva('MONTE_CARLO');
    if (monteCarlo) return;

    setLoadingMC(true);
    try {
      const res = await getMonteCarlo(ativo.ativo);
      setMonteCarlo(res.data);
    } catch (err) {
      const msg = err.response?.data?.detail || err.response?.data?.erro || "Falha na simulação estocástica.";
      setMonteCarlo({ sucesso: false, erro: msg });
    } finally {
      setLoadingMC(false);
    }
  };

  // 3. CORREÇÃO: Busca da Rede Neural (LSTM) revelando o erro do Python
  const carregarLSTM = async () => {
    setAbaAtiva('LSTM');
    if (previsaoLSTM) return;
    
    setLoadingLSTM(true);
    try {
      const res = await getPrevisaoLSTM(ativo.ativo);
      setPrevisaoLSTM(res.data);
    } catch (err) {
      const msg = err.response?.data?.detail || err.response?.data?.erro || "Falha no processamento dos Tensores.";
      setPrevisaoLSTM({ sucesso: false, erro: msg });
    } finally {
      setLoadingLSTM(false);
    }
  };

  if (!ativo) return null;
  const valorTotal = quantidade * ativo.preco_atual;

  const executarOrdem = async () => {
    if (quantidade <= 0) return setStatus({ ...status, erro: 'Quantidade inválida.' });
    setStatus({ loading: true, erro: '', sucesso: '' });
    try {
      const payload = { usuario_id: usuarioId, ticker: ativo.ativo, tipo_ordem: tipoOrdem, quantidade: parseInt(quantidade), preco: ativo.preco_atual };
      const res = await enviarOrdem(payload);
      setStatus({ loading: false, erro: '', sucesso: res.data.mensagem });
      setTimeout(() => onClose(), 2000);
    } catch (err) {
      setStatus({ loading: false, erro: err.response?.data?.erro || 'Erro no servidor', sucesso: '' });
    }
  };

  const engatilharOrdem = async () => {
    if (quantidade <= 0) return setStatus({ ...status, erro: 'Quantidade inválida.' });
    setStatus({ loading: true, erro: '', sucesso: '' });
    try {
      const payload = { usuario_id: usuarioId, ticker: ativo.ativo, tipo_ordem: tipoOrdem, quantidade: parseInt(quantidade), preco: ativo.preco_atual };
      const res = await adicionarAoCarrinho(payload);
      setStatus({ loading: false, erro: '', sucesso: res.data.mensagem });
      setTimeout(() => {
        onClose();
        window.dispatchEvent(new Event('carrinhoAtualizado'));
      }, 1500);
    } catch (err) {
      setStatus({ loading: false, erro: err.response?.data?.erro || 'Erro ao engatilhar.', sucesso: '' });
    }
  };

  return (
    <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 400 }}>
      <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '30px', borderRadius: '12px', width: '95%', maxWidth: '600px', boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.5)', maxHeight: '95vh', overflowY: 'auto' }}>

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: `1px solid ${theme.border}`, paddingBottom: '10px', marginBottom: '15px' }}>
          <h2 style={{ margin: 0, color: theme.textMain }}>Boleta Institucional</h2>
          <button onClick={onClose} disabled={status.loading} style={{ background: 'none', border: 'none', fontSize: '1.2rem', cursor: 'pointer', color: theme.textMuted }}>✖</button>
        </div>

        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '15px' }}>
          <div>
            <div style={{ fontSize: '0.85rem', color: theme.textMuted }}>Ativo</div>
            <div style={{ fontSize: '1.8rem', fontWeight: 'bold', color: theme.textMain }}>{ativo.ativo}</div>
          </div>
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: '0.85rem', color: theme.textMuted }}>Preço de Mercado</div>
            {/* 🌍 APLICAÇÃO DA MOEDA NATIVA AQUI */}
            <div style={{ fontSize: '1.8rem', fontWeight: 'bold', color: theme.info }}>
              {formatarMoedaNativa(ativo.preco_atual, ativo.ativo)}
            </div>
          </div>
        </div>

        {/* NAVEGAÇÃO DAS ABAS DE INTELIGÊNCIA */}
        <div style={{ display: 'flex', borderBottom: `1px solid ${theme.border}`, marginBottom: '15px' }}>
          <button onClick={() => setAbaAtiva('BACKTEST')} style={{ flex: 1, padding: '10px', background: 'none', border: 'none', borderBottom: abaAtiva === 'BACKTEST' ? '3px solid #9b59b6' : '3px solid transparent', fontWeight: 'bold', color: abaAtiva === 'BACKTEST' ? '#9b59b6' : theme.textMuted, cursor: 'pointer' }}>🕰️ Backtest</button>
          <button onClick={carregarMonteCarlo} style={{ flex: 1, padding: '10px', background: 'none', border: 'none', borderBottom: abaAtiva === 'MONTE_CARLO' ? '3px solid #f39c12' : '3px solid transparent', fontWeight: 'bold', color: abaAtiva === 'MONTE_CARLO' ? '#f39c12' : theme.textMuted, cursor: 'pointer' }}>🔮 Monte Carlo</button>
          <button onClick={carregarLSTM} style={{ flex: 1, padding: '10px', background: 'none', border: 'none', borderBottom: abaAtiva === 'LSTM' ? `3px solid ${theme.compra}` : '3px solid transparent', fontWeight: 'bold', color: abaAtiva === 'LSTM' ? theme.compra : theme.textMuted, cursor: 'pointer' }}>🧠 IA (LSTM)</button>
        </div>

        {abaAtiva === 'LSTM' && (
          <div style={{ marginBottom: '20px', padding: '15px', borderRadius: '8px', backgroundColor: theme.bg, border: `1px solid ${theme.border}` }}>
            <h4 style={{ margin: '0 0 10px 0', color: theme.compra, fontSize: '0.9rem', display: 'flex', alignItems: 'center', gap: '5px' }}>
              🧠 Previsão de Machine Learning (T+1)
            </h4>

            {loadingLSTM ? (
              <p style={{ margin: 0, fontSize: '0.85rem', color: theme.compra, fontStyle: 'italic' }}>
                Treinando Rede Neural Recorrente (LSTM) ao vivo... (Pode levar de 5 a 10 segundos)
              </p>
            ) : previsaoLSTM && previsaoLSTM.sucesso ? (
              <div>
                <div style={{ display: 'flex', gap: '15px', marginBottom: '15px' }}>
                  <div style={{ flex: 1, backgroundColor: theme.cardBg, padding: '10px', borderRadius: '6px', border: `1px solid ${theme.border}`, textAlign: 'center' }}>
                    <div style={{ fontSize: '0.75rem', color: theme.textMuted, textTransform: 'uppercase' }}>Fechamento Base</div>
                    <div style={{ fontSize: '1.2rem', fontWeight: 'bold', color: theme.textMain }}>{formatarMoedaNativa(previsaoLSTM.preco_atual, ativo.ativo)}</div>
                  </div>
                  <div style={{ flex: 1, backgroundColor: theme.cardBg, padding: '10px', borderRadius: '6px', border: `1px solid ${theme.border}`, textAlign: 'center', borderBottom: previsaoLSTM.variacao_projetada_perc > 0 ? `3px solid ${theme.compra}` : `3px solid ${theme.venda}` }}>
                    <div style={{ fontSize: '0.75rem', color: theme.textMuted, textTransform: 'uppercase' }}>Alvo (Amanhã)</div>
                    <div style={{ fontSize: '1.2rem', fontWeight: 'bold', color: previsaoLSTM.variacao_projetada_perc > 0 ? theme.compra : theme.venda }}>
                      {formatarMoedaNativa(previsaoLSTM.previsao_t1, ativo.ativo)}
                    </div>
                  </div>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: theme.cardBg, padding: '10px 15px', borderRadius: '6px', border: `1px solid ${theme.border}` }}>
                  <div>
                    <div style={{ fontSize: '0.75rem', color: theme.textMuted }}>SINAL DA IA</div>
                    <div style={{ fontWeight: 'bold', color: previsaoLSTM.sinal_rede_neural.includes('COMPRAR') ? theme.compra : (previsaoLSTM.sinal_rede_neural.includes('VENDER') ? theme.venda : theme.info) }}>
                      {previsaoLSTM.sinal_rede_neural}
                    </div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: '0.75rem', color: theme.textMuted }}>VARIAÇÃO ESPERADA</div>
                    <div style={{ fontWeight: 'bold', fontSize: '1.1rem', color: previsaoLSTM.variacao_projetada_perc > 0 ? theme.compra : theme.venda }}>
                      {previsaoLSTM.variacao_projetada_perc > 0 ? '+' : ''}{previsaoLSTM.variacao_projetada_perc}%
                    </div>
                  </div>
                </div>

                <p style={{ fontSize: '0.7rem', color: theme.textMuted, margin: '10px 0 0 0', textAlign: 'center' }}>
                  * {previsaoLSTM.detalhes_modelo}
                </p>
              </div>
            ) : (
              <p style={{ margin: 0, fontSize: '0.85rem', color: theme.venda }}>⚠️ {previsaoLSTM?.erro}</p>
            )}
          </div>
        )}

        {abaAtiva === 'BACKTEST' && (
          <div style={{ marginBottom: '20px', padding: '15px', borderRadius: '8px', backgroundColor: theme.bg, border: `1px solid ${theme.border}` }}>
            <h4 style={{ margin: '0 0 10px 0', color: theme.textMain, fontSize: '0.9rem' }}>Estatística da Estratégia Z-Score</h4>
            {loadingBacktest ? (
              <p style={{ margin: 0, fontSize: '0.85rem', color: theme.textMuted, fontStyle: 'italic' }}>Analisando histórico de operações...</p>
            ) : backtest && backtest.sucesso ? (
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <div><span style={{ color: theme.textMuted, display: 'block', fontSize: '0.8rem' }}>Taxa de Acerto</span><span style={{ fontWeight: 'bold', color: backtest.win_rate > 50 ? theme.compra : '#f39c12' }}>{backtest.win_rate}%</span></div>
                <div><span style={{ color: theme.textMuted, display: 'block', fontSize: '0.8rem' }}>Drawdown Máx.</span><span style={{ fontWeight: 'bold', color: backtest.max_drawdown < -20 ? theme.venda : '#f39c12' }}>{backtest.max_drawdown}%</span></div>
                <div style={{ textAlign: 'right' }}><span style={{ color: theme.textMuted, display: 'block', fontSize: '0.8rem' }}>Amostra</span><span style={{ fontWeight: 'bold', color: theme.textMain }}>{backtest.total_trades} trades</span></div>
              </div>
            ) : (
              <p style={{ margin: 0, fontSize: '0.85rem', color: theme.venda }}>⚠️ {backtest?.erro || 'Dados insuficientes.'}</p>
            )}
          </div>
        )}

        {abaAtiva === 'MONTE_CARLO' && (
          <div style={{ marginBottom: '20px', padding: '15px', borderRadius: '8px', backgroundColor: theme.bg, border: `1px solid ${theme.border}` }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '10px' }}>
              <h4 style={{ margin: '0', color: '#f39c12', fontSize: '0.9rem' }}>Projeção Estocástica Avançada (12 Meses)</h4>
              {monteCarlo && monteCarlo.sucesso && (
                <span style={{ fontSize: '0.75rem', backgroundColor: 'rgba(243, 156, 18, 0.1)', color: '#f39c12', padding: '2px 8px', borderRadius: '4px', border: '1px solid #f39c12' }}>
                  {monteCarlo.modelo_utilizado}
                </span>
              )}
            </div>

            {loadingMC ? (
              <p style={{ margin: 0, fontSize: '0.85rem', color: '#f39c12', fontStyle: 'italic' }}>Calculando 2.000 realidades paralelas incluindo Gaps Sistêmicos...</p>
            ) : monteCarlo && monteCarlo.sucesso ? (
              <>
                <div style={{ height: '180px', width: '100%', marginBottom: '15px' }}>
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={monteCarlo.grafico}>
                      <XAxis dataKey="dia" hide />
                      <YAxis domain={['auto', 'auto']} width={40} tick={{ fontSize: 10, fill: theme.textMuted }} stroke={theme.border} />
                      <Tooltip
                        formatter={(val) => `R$ ${val}`}
                        labelFormatter={(l) => `Dia ${l}`}
                        contentStyle={{ backgroundColor: theme.cardBg, borderColor: theme.border, borderRadius: '8px', color: theme.textMain }}
                        itemStyle={{ fontWeight: 'bold' }}
                      />
                      <Area type="monotone" dataKey="otimista" stroke={theme.compra} fill={theme.compra} fillOpacity={0.1} />
                      <Area type="monotone" dataKey="pessimista" stroke={theme.venda} fill={theme.venda} fillOpacity={0.1} />
                      <Area type="monotone" dataKey="provavel" stroke={theme.info} fill="none" strokeWidth={2} />
                    </AreaChart>
                  </ResponsiveContainer>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem', marginBottom: '15px', borderBottom: `1px dashed ${theme.border}`, paddingBottom: '10px' }}>
                  <div style={{ textAlign: 'center' }}><span style={{ color: theme.venda, display: 'block' }}>Pior Cenário (5%)</span><span style={{ fontWeight: 'bold', fontSize: '1.1rem', color: theme.textMain }}>{formatarMoedaNativa(monteCarlo.projecao_1_ano.pessimista, ativo.ativo)}</span></div>
                  <div style={{ textAlign: 'center' }}><span style={{ color: theme.info, display: 'block' }}>Alvo Provável (50%)</span><span style={{ fontWeight: 'bold', fontSize: '1.1rem', color: theme.textMain }}>{formatarMoedaNativa(monteCarlo.projecao_1_ano.provavel, ativo.ativo)}</span></div>
                  <div style={{ textAlign: 'center' }}><span style={{ color: theme.compra, display: 'block' }}>Melhor Cenário (95%)</span><span style={{ fontWeight: 'bold', fontSize: '1.1rem', color: theme.textMain }}>{formatarMoedaNativa(monteCarlo.projecao_1_ano.otimista, ativo.ativo)}</span></div>
                </div>

                <div style={{ display: 'flex', gap: '15px', alignItems: 'center' }}>
                  <div style={{ flex: 1 }}>
                    <span style={{ color: theme.textMuted, display: 'block', fontSize: '0.75rem', textTransform: 'uppercase' }}>Frequência de Gaps</span>
                    <span style={{ fontWeight: 'bold', color: '#f39c12', fontSize: '0.9rem' }}>~{monteCarlo.risco_estrutural.frequencia_gaps_ano} saltos/ano</span>
                  </div>
                  <div style={{ flex: 1, borderLeft: `1px solid ${theme.border}`, paddingLeft: '15px' }}>
                    <span style={{ color: theme.textMuted, display: 'block', fontSize: '0.75rem', textTransform: 'uppercase' }}>Impacto Médio</span>
                    <span style={{ fontWeight: 'bold', color: monteCarlo.risco_estrutural.impacto_medio_gap.includes('-') ? theme.venda : theme.compra, fontSize: '0.9rem' }}>
                      {monteCarlo.risco_estrutural.impacto_medio_gap}
                    </span>
                  </div>
                </div>
              </>
            ) : (
              <p style={{ margin: 0, fontSize: '0.85rem', color: theme.venda }}>⚠️ {monteCarlo?.erro}</p>
            )}
          </div>
        )}

        <div style={{ display: 'flex', gap: '10px', marginBottom: '15px' }}>
          <button onClick={() => setTipoOrdem('COMPRA')} style={{ flex: 1, padding: '12px', borderRadius: '6px', border: `1px solid ${tipoOrdem === 'COMPRA' ? theme.compra : theme.border}`, fontWeight: 'bold', cursor: 'pointer', backgroundColor: tipoOrdem === 'COMPRA' ? 'rgba(16, 185, 129, 0.1)' : theme.bg, color: tipoOrdem === 'COMPRA' ? theme.compra : theme.textMuted, transition: '0.2s' }}>COMPRAR</button>
          <button
            onClick={() => {
              setTipoOrdem('VENDA');
              if (qtdCustodia > 0) {
                setQuantidade(qtdCustodia);
              }
            }}
            style={{ flex: 1, padding: '12px', borderRadius: '6px', border: `1px solid ${tipoOrdem === 'VENDA' ? theme.venda : theme.border}`, fontWeight: 'bold', cursor: 'pointer', backgroundColor: tipoOrdem === 'VENDA' ? 'rgba(239, 68, 68, 0.1)' : theme.bg, color: tipoOrdem === 'VENDA' ? theme.venda : theme.textMuted, transition: '0.2s' }}
          >
            VENDER
          </button>
        </div>

        <div style={{ marginBottom: '15px' }}>
          <label style={{ display: 'block', fontSize: '0.9rem', color: theme.textMuted, marginBottom: '5px' }}>
            Quantidade (Cotas):
            <span style={{ marginLeft: '10px', color: qtdCustodia > 0 ? theme.compra : theme.textMuted, fontWeight: 'bold' }}>
              (Em Custódia: {qtdCustodia})
            </span>
            {backtest && backtest.sucesso && backtest.kelly_recomendado_perc > 0 && (
              <span style={{ color: theme.info, fontWeight: 'bold', marginLeft: '10px' }}>
                (🎯 Lote Seguro: Máx {backtest.kelly_recomendado_perc}% do Caixa Livre)
              </span>
            )}
          </label>
          <input type="number" value={quantidade} onChange={(e) => setQuantidade(e.target.value)} style={{ width: '100%', padding: '12px', fontSize: '1.2rem', borderRadius: '6px', border: `1px solid ${theme.border}`, backgroundColor: theme.bg, color: theme.textMain, boxSizing: 'border-box' }} />
        </div>

        <div style={{ backgroundColor: theme.bg, padding: '15px', borderRadius: '6px', border: `1px solid ${theme.border}`, marginBottom: '20px', textAlign: 'center' }}>
          <div style={{ fontSize: '0.9rem', color: theme.textMuted }}>Total Financeiro da Operação</div>
          {/* 🌍 APLICAÇÃO DA MOEDA NATIVA AQUI */}
          <div style={{ fontSize: '1.8rem', fontWeight: 'bold', color: theme.textMain }}>
            {formatarMoedaNativa(valorTotal, ativo.ativo)}
          </div>
        </div>

        {status.erro && <div style={{ color: theme.venda, backgroundColor: 'rgba(239, 68, 68, 0.1)', border: `1px solid ${theme.venda}`, padding: '10px', borderRadius: '6px', marginBottom: '15px', textAlign: 'center' }}>{status.erro}</div>}
        {status.sucesso && <div style={{ color: theme.compra, backgroundColor: 'rgba(16, 185, 129, 0.1)', border: `1px solid ${theme.compra}`, padding: '10px', borderRadius: '6px', marginBottom: '15px', textAlign: 'center' }}>{status.sucesso}</div>}

        <div style={{ display: 'flex', gap: '10px' }}>
          <button onClick={onClose} disabled={status.loading} style={{ flex: 1, padding: '15px', borderRadius: '6px', border: `1px solid ${theme.border}`, fontWeight: 'bold', cursor: status.loading ? 'wait' : 'pointer', backgroundColor: theme.bg, color: theme.textMuted }}>
            CANCELAR
          </button>

          <button onClick={engatilharOrdem} disabled={status.loading} style={{ flex: 1, padding: '15px', borderRadius: '6px', border: `1px solid ${theme.alerta}`, fontWeight: 'bold', cursor: status.loading ? 'wait' : 'pointer', backgroundColor: 'rgba(245, 158, 11, 0.1)', color: theme.alerta }}>
            🛒 ENGATILHAR
          </button>

          <button onClick={executarOrdem} disabled={status.loading} style={{ flex: 1, padding: '15px', borderRadius: '6px', border: 'none', fontWeight: 'bold', cursor: status.loading ? 'wait' : 'pointer', backgroundColor: tipoOrdem === 'COMPRA' ? theme.compra : theme.venda, color: '#fff' }}>
            EXECUTAR AGORA
          </button>
        </div>
      </div>
    </div>
  );
};

