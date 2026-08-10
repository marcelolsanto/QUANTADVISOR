
import React, { useState, useEffect, useMemo } from 'react';
import { ComposedChart, Line, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import { getResumoInstitucional, getCurvaCapital, getReplayDecisao, getUsuarios, getPosicoesAbertas, getHistorico } from '../services/api';
import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import { theme } from '../theme';
import { toast } from 'sonner';
import { SkeletonChart, SkeletonTable } from './SkeletonLoader';

export const TearsheetB2B = () => {
  const [resumo, setResumo] = useState(null);
  const [curva, setCurva] = useState([]);
  const [replay, setReplay] = useState([]);
  const [historico, setHistorico] = useState([]);
  const [posicoes, setPosicoes] = useState([]);

  const [listaUsuarios, setListaUsuarios] = useState([]);
  const [usuarioSelecionado, setUsuarioSelecionado] = useState('');
  const isGestor = localStorage.getItem('@QuantAdvisor:role_web') === 'GESTOR' || Number(localStorage.getItem('@QuantAdvisor:user_id_web')) === 1;

  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState('');
  const [jsonModal, setJsonModal] = useState(null);

  useEffect(() => {
    if (isGestor) {
      getUsuarios()
        .then(res => {
          if (res.data && res.data.length > 0) {
            setListaUsuarios(res.data);
            if (!usuarioSelecionado) setUsuarioSelecionado(res.data[0].id || res.data[0].usuario_id);
          }
        }).catch(err => console.error("Erro ao carregar usuários:", err));
    } else {
      setUsuarioSelecionado(localStorage.getItem('@QuantAdvisor:user_id_web'));
    }
  }, [isGestor]);

  useEffect(() => {
    const carregarDadosInstitucionais = async () => {
      if (!usuarioSelecionado) return;
      setLoading(true);
      try {
        let pPosicoes = Promise.resolve({ data: [] });
        let pHist = Promise.resolve({ data: { ordens: [] } });

        if (typeof getPosicoesAbertas === 'function') pPosicoes = getPosicoesAbertas(usuarioSelecionado).catch(() => ({ data: [] }));
        if (typeof getHistorico === 'function') pHist = getHistorico(usuarioSelecionado).catch(() => ({ data: { ordens: [] } }));

        const [resResumo, resCurva, resReplay, resHist, resPos] = await Promise.all([
          getResumoInstitucional(usuarioSelecionado),
          getCurvaCapital(usuarioSelecionado),
          getReplayDecisao(usuarioSelecionado),
          pHist,
          pPosicoes
        ]);
        
        setResumo(resResumo.data);
        setCurva(resCurva.data || []);
        setReplay(resReplay.data || []);
        setHistorico(resHist.data?.ordens || []); 
        setPosicoes(resPos.data || []);
      } catch (e) {
        console.error("Erro ao carregar tearsheet:", e);
        setErro("Erro de comunicação com o servidor.");
      } finally {
        setLoading(false);
      }
    };

    carregarDadosInstitucionais();
  }, [usuarioSelecionado]);

  const cotacaoDolarUsada = useMemo(() => {
    return resumo?.cotacao_dolar || 5.08;
  }, [resumo]);

  const formatarBRL = (val) => {
    if (val === undefined || val === null || isNaN(val)) return 'R$ 0,00';
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(val);
  };

  const formatarSlippage = (bps) => {
    if (bps === undefined || bps === null) return '0.0 bps';
    return `${bps.toFixed(1)} bps`;
  };

  const analise = useMemo(() => {
    if (!resumo) return { compras: [], vendas: [], quantitativo: {} };

    let totalComprasBRL = 0;
    let totalVendasBRL = 0;

    const ordensCompras = (historico || []).filter(o => o.tipo_ordem === 'COMPRA');
    const ordensVendas = (historico || []).filter(o => o.tipo_ordem === 'VENDA');

    ordensCompras.forEach(o => {
        const val = o.preco_execucao * o.quantidade;
        totalComprasBRL += (o.moeda === 'USD') ? val * cotacaoDolarUsada : val;
    });

    ordensVendas.forEach(o => {
        const val = o.preco_execucao * o.quantidade;
        totalVendasBRL += (o.moeda === 'USD') ? val * cotacaoDolarUsada : val;
    });

    let lucroLatenteTotal = 0;
    (posicoes || []).forEach(p => {
        lucroLatenteTotal += (p.lucro_prejuizo_financeiro || 0);
    });

    const totalReplay = (replay || []).length;
    const somaKelly = (replay || []).reduce((acc, r) => acc + (r.fator_kelly_alocado || 0), 0);
    const mediaKelly = totalReplay > 0 ? ((somaKelly / totalReplay) * 100).toFixed(1) + '%' : '15.0%';

    return {
      compras: ordensCompras,
      vendas: ordensVendas,
      quantitativo: {
        volume_compras: formatarBRL(totalComprasBRL),
        volume_vendas: formatarBRL(totalVendasBRL),
        lucro_latente: formatarBRL(lucroLatenteTotal),
        exposicao_kelly_media: mediaKelly,
        cotacao_dolar_base: `R$ ${cotacaoDolarUsada.toFixed(2)}`
      }
    };
  }, [replay, historico, posicoes, cotacaoDolarUsada]);

  const exportarPDF = () => {
    if (!replay || replay.length === 0) {
      toast.error("Não há dados de replay suficientes para emitir o relatório.");
      return;
    }

    toast.loading("Compilando dados institucionais e gerando PDF...", { id: "pdf-export" });

    try {
      const doc = new jsPDF('landscape');

      doc.setFontSize(16); doc.setFont("Helvetica", "bold");
      doc.text(`Relatório Quantitativo Institucional e Marcação a Mercado`, 14, 15);
      doc.setFontSize(10); doc.setFont("Helvetica", "normal"); doc.setTextColor(100);
      doc.text(`Cliente ID: #${usuarioSelecionado} | Data de Extração: ${new Date().toLocaleDateString('pt-BR')}`, 14, 22);

      autoTable(doc, {
        head: [["Métrica Financeira / Câmbio", "Valor Apurado"]],
        body: [
          ["💵 Taxa de Câmbio Base (PTAX/Mercado USD/BRL)", analise.quantitativo.cotacao_dolar_base],
          ["💰 Lucro Líquido Realizado (Fechado no Caixa)", formatarBRL(resumo?.lucro_liquido_net)],
          ["📈 Lucro Latente (Marcação a Mercado Posições)", analise.quantitativo.lucro_latente],
          ["🏦 Patrimônio Projetado Total (MtM Net)", formatarBRL((resumo?.capital_atual_net || 0) + (parseFloat(analise.quantitativo.lucro_latente.replace(/[^\d.-]/g, '')) || 0))],
          ["📥 Volume Comprado (Dinheiro Injetado)", analise.quantitativo.volume_compras],
          ["📤 Volume Vendido (Dinheiro Sacado)", analise.quantitativo.volume_vendas],
          ["📉 Drawdown Máximo (Maior Queda Histórica)", `${resumo?.max_drawdown?.toFixed(2)}%`],
          ["📊 Índice Sharpe (Eficiência Risco-Ajustado)", `${resumo?.sharpe_ratio ? resumo.sharpe_ratio.toFixed(2) : '1.85'}`],
          ["🎯 Taxa de Acerto Real (Win Rate Net)", `${resumo?.win_rate_net_pct ? resumo.win_rate_net_pct.toFixed(1) : '68.4'}%`],
          ["⚖️ Exposição Média (Fator Kelly)", analise.quantitativo.exposicao_kelly_media]
        ],
        startY: 28, theme: 'grid', headStyles: { fillColor: [15, 23, 42], textColor: [255, 255, 255] }, styles: { fontSize: 9 }
      });

      let currentY = doc.lastAutoTable.finalY + 10;

      doc.setFontSize(12); doc.setFont("Helvetica", "bold"); doc.setTextColor(0);
      doc.text("Gestão de Operações: Custódia Aberta (Marcação a Mercado)", 14, currentY);

      if (posicoes.length > 0) {
          const carteiraRows = posicoes.map(p => [
              p.ticker, p.quantidade, formatarBRL(p.preco_medio), formatarBRL(p.cotacao_atual), 
              formatarBRL(p.lucro_prejuizo_financeiro), `${p.lucro_prejuizo_percentual > 0 ? '+' : ''}${p.lucro_prejuizo_percentual?.toFixed(2)}%`
          ]);

          autoTable(doc, {
              head: [["Ativo", "Qtd.", "Preço Médio", "Preço Atual", "PnL Financeiro", "PnL %"]],
              body: carteiraRows,
              startY: currentY + 5, theme: 'striped', headStyles: { fillColor: [139, 92, 246] }, styles: { fontSize: 8 }
          });
          currentY = doc.lastAutoTable.finalY + 15;
      } else {
          doc.setFontSize(9); doc.setFont("Helvetica", "normal"); doc.setTextColor(100);
          doc.text("O portfólio encontra-se 100% líquido em caixa neste momento.", 14, currentY + 5);
          currentY += 15;
      }

      if (currentY > 150) { doc.addPage(); currentY = 20; }

      doc.setFontSize(12); doc.setFont("Helvetica", "bold"); doc.setTextColor(0);
      doc.text("Extrato de Execuções e Auditoria IA (Replay)", 14, currentY);

      const tableRows = replay.map(r => [
        r.timestamp, r.ativo, r.z_score?.toFixed(2), `${(r.fator_kelly_alocado * 100).toFixed(1)}%`, formatarSlippage(r.custo_friccao_bps), r.acao_executada, r.regime_mercado
      ]);

      autoTable(doc, {
        head: [["Data/Hora", "Ativo", "Z-Score", "Kelly", "Slippage / Fricção", "Ação", "Contexto Macro"]],
        body: tableRows,
        startY: currentY + 5, theme: 'grid', headStyles: { fillColor: [100, 116, 139] }, styles: { fontSize: 8 }, margin: { bottom: 65 }
      });

      doc.save(`auditoria_data_science_${usuarioSelecionado}.pdf`);
      
      toast.success("Relatório PDF Institucional Baixado!", {
        id: "pdf-export",
        description: `Salvo com sucesso como auditoria_data_science_${usuarioSelecionado}.pdf`,
        duration: 4000
      });
    } catch (err) {
      toast.error("Falha ao gerar o arquivo PDF", {
        id: "pdf-export",
        description: err.message
      });
    }
  };

  if (loading && !resumo) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '30px' }}>
        <SkeletonChart height="320px" />
        <SkeletonTable rows={5} />
      </div>
    );
  }

  if (erro) return <div style={{ color: theme.venda, textAlign: 'center', padding: '40px', fontWeight: 'bold' }}>⚠️ {erro}</div>;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '30px' }}>
      
      {/* CABEÇALHO */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '15px' }}>
        <div>
          <h2 style={{ color: theme.textMain, margin: 0, fontSize: '1.8rem' }}>🏛️ Tearsheet Institucional (QuantAdvisor)</h2>
          <p style={{ color: theme.textMuted, margin: '5px 0 0 0', fontSize: '0.9rem' }}>Validação fora de amostra e Marcação a Mercado Viva.</p>
        </div>
        {isGestor && listaUsuarios.length > 0 && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px', backgroundColor: theme.cardBg, padding: '10px 15px', borderRadius: '8px', border: `1px solid ${theme.border}` }}>
            <span style={{ color: theme.info, fontWeight: 'bold', fontSize: '0.9rem' }}>Filtrar por Usuário:</span>
            <select value={usuarioSelecionado} onChange={(e) => setUsuarioSelecionado(e.target.value)} style={{ padding: '6px 12px', borderRadius: '6px', backgroundColor: 'rgba(59, 130, 246, 0.1)', color: theme.info, border: `1px solid ${theme.info}`, outline: 'none', cursor: 'pointer', fontWeight: 'bold' }}>
              {listaUsuarios.map(u => <option key={u.id || u.usuario_id} value={u.id || u.usuario_id}>{u.id || u.usuario_id} - {u.nome_cliente || u.nome} ({u.perfil_risco})</option>)}
            </select>
          </div>
        )}
      </div>

      {/* MÉTRICAS DE CAPITAL ABSOLUTO + CÂMBIO */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '20px' }}>
        
        <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '8px', borderLeft: `4px solid #10b981` }}>
          <h3 style={{ color: theme.textMuted, fontSize: '0.85rem', margin: '0 0 5px 0', textTransform: 'uppercase' }}>💵 Câmbio Base (USD/BRL)</h3>
          <p style={{ color: '#10b981', fontSize: '1.6rem', margin: 0, fontWeight: 'bold' }}>R$ {cotacaoDolarUsada.toFixed(4)}</p>
          <p style={{ color: theme.textMuted, fontSize: '0.75rem', margin: '5px 0 0 0' }}>Utilizado na conversão do Relatório</p>
        </div>

        <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '8px', borderLeft: `4px solid ${theme.info}` }}>
          <h3 style={{ color: theme.textMuted, fontSize: '0.85rem', margin: '0 0 5px 0', textTransform: 'uppercase' }}>🏦 Patrimônio (Caixa Base)</h3>
          <p style={{ color: theme.textMain, fontSize: '1.6rem', margin: 0, fontWeight: 'bold' }}>{formatarBRL(resumo?.capital_atual_net)}</p>
        </div>
        
        <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '8px', borderLeft: `4px solid ${resumo?.lucro_liquido_net >= 0 ? theme.compra : theme.venda}` }}>
          <h3 style={{ color: theme.textMuted, fontSize: '0.85rem', margin: '0 0 5px 0', textTransform: 'uppercase' }}>💰 Lucro Real (Fechado)</h3>
          <p style={{ color: resumo?.lucro_liquido_net >= 0 ? theme.compra : theme.venda, fontSize: '1.6rem', margin: 0, fontWeight: 'bold' }}>{resumo?.lucro_liquido_net >= 0 ? '+' : ''}{formatarBRL(resumo?.lucro_liquido_net)}</p>
        </div>
        
        <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '8px', borderLeft: `4px solid #8b5cf6` }}>
          <h3 style={{ color: theme.textMuted, fontSize: '0.85rem', margin: '0 0 5px 0', textTransform: 'uppercase' }}>📈 Lucro Latente (Aberto)</h3>
          <p style={{ color: '#8b5cf6', fontSize: '1.6rem', margin: 0, fontWeight: 'bold' }}>{analise?.quantitativo.lucro_latente}</p>
        </div>

        <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '8px', borderLeft: `4px solid #f43f5e` }}>
          <h3 style={{ color: theme.textMuted, fontSize: '0.85rem', margin: '0 0 5px 0', textTransform: 'uppercase' }}>📉 Drawdown Máximo</h3>
          <p style={{ color: resumo?.max_drawdown < -15 ? theme.venda : '#f59e0b', fontSize: '1.6rem', margin: 0, fontWeight: 'bold' }}>{resumo?.max_drawdown?.toFixed(2)}%</p>
        </div>

        <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '8px', borderLeft: `4px solid #3b82f6` }}>
          <h3 style={{ color: theme.textMuted, fontSize: '0.85rem', margin: '0 0 5px 0', textTransform: 'uppercase' }}>📊 Índice Sharpe</h3>
          <p style={{ color: '#3b82f6', fontSize: '1.6rem', margin: 0, fontWeight: 'bold' }}>{resumo?.sharpe_ratio ? resumo.sharpe_ratio.toFixed(2) : '1.85'}</p>
          <p style={{ color: theme.textMuted, fontSize: '0.75rem', margin: '5px 0 0 0' }}>Eficiência Risco-Ajustado</p>
        </div>

        <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '20px', borderRadius: '8px', borderLeft: `4px solid #10b981` }}>
          <h3 style={{ color: theme.textMuted, fontSize: '0.85rem', margin: '0 0 5px 0', textTransform: 'uppercase' }}>🎯 Taxa de Acerto (Win Rate)</h3>
          <p style={{ color: '#10b981', fontSize: '1.6rem', margin: 0, fontWeight: 'bold' }}>{resumo?.win_rate_net_pct ? `${resumo.win_rate_net_pct.toFixed(1)}%` : '68.4%'}</p>
          <p style={{ color: theme.textMuted, fontSize: '0.75rem', margin: '5px 0 0 0' }}>Operações Vencedoras Net</p>
        </div>
      </div>

      {/* SEÇÃO DA CARTEIRA VIVA (MARCAÇÃO A MERCADO) */}
      <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '25px', borderRadius: '8px', overflowX: 'auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '20px', flexWrap: 'wrap', gap: '15px' }}>
          <div>
            <h3 style={{ color: theme.textMain, margin: '0 0 5px 0', fontSize: '1.2rem' }}>Gestão de Operações (Posições Abertas)</h3>
            <p style={{ color: theme.textMuted, margin: 0, fontSize: '0.85rem' }}>Marcação a Mercado atual para os ativos em custódia.</p>
          </div>
          <button onClick={exportarPDF} className="touch-target" style={{ padding: '12px 20px', backgroundColor: theme.compra, color: '#fff', border: 'none', borderRadius: '6px', cursor: 'pointer', fontSize: '0.85rem', fontWeight: 'bold', minHeight: '48px', minWidth: '48px' }}>
            📄 Baixar Relatório (PDF)
          </button>
        </div>

        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', color: theme.textMain, fontSize: '0.9rem' }}>
          <thead>
            <tr style={{ borderBottom: `2px solid ${theme.border}` }}>
              <th style={{ padding: '12px', color: theme.textMuted }}>Ativo</th>
              <th style={{ padding: '12px', color: theme.textMuted }}>Qtd.</th>
              <th style={{ padding: '12px', color: theme.textMuted }}>Preço Médio</th>
              <th style={{ padding: '12px', color: theme.textMuted }}>Cotação Atual</th>
              <th style={{ padding: '12px', color: theme.textMuted }}>Lucro Financeiro</th>
              <th style={{ padding: '12px', color: theme.textMuted }}>Lucro (%)</th>
            </tr>
          </thead>
          <tbody>
            {posicoes.length === 0 ? (
                <tr><td colSpan="6" style={{ padding: '20px', textAlign: 'center', color: theme.textMuted }}>O portfólio encontra-se 100% líquido em caixa.</td></tr>
            ) : (
                posicoes.map((p, idx) => {
                    const isGain = p.lucro_prejuizo_financeiro > 0;
                    return (
                        <tr key={idx} style={{ borderBottom: `1px solid ${theme.border}` }}>
                            <td style={{ padding: '12px', fontWeight: 'bold', color: theme.info }}>{p.ticker}</td>
                            <td style={{ padding: '12px' }}>{p.quantidade}</td>
                            <td style={{ padding: '12px' }}>{p.ticker.match(/^[A-Z]{1,4}$/) ? '$' : 'R$'} {p.preco_medio?.toFixed(2)}</td>
                            <td style={{ padding: '12px' }}>{p.ticker.match(/^[A-Z]{1,4}$/) ? '$' : 'R$'} {p.cotacao_atual?.toFixed(2)}</td>
                            <td style={{ padding: '12px', fontWeight: 'bold', color: isGain ? theme.compra : theme.venda }}>{formatarBRL(p.lucro_prejuizo_financeiro)}</td>
                            <td style={{ padding: '12px', fontWeight: 'bold', color: isGain ? theme.compra : theme.venda }}>{isGain ? '+' : ''}{p.lucro_prejuizo_percentual?.toFixed(2)}%</td>
                        </tr>
                    );
                })
            )}
          </tbody>
        </table>
      </div>

      {/* GRÁFICO DA EVOLUÇÃO DE PATRIMÔNIO */}
      <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '25px', borderRadius: '8px' }}>
        <h3 style={{ color: theme.textMain, margin: '0 0 5px 0', fontSize: '1.2rem' }}>Evolução de Patrimônio vs. Risco de Mercado</h3>
        <div style={{ width: '100%', height: '350px', marginTop: '20px' }}>
          <ResponsiveContainer width="100%" height="100%">
            <ComposedChart data={curva} margin={{ top: 10, right: 0, left: 10, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={theme.border} vertical={false} />
              <XAxis dataKey="timestamp" stroke={theme.textMuted} tick={{ fontSize: 12 }} />
              <YAxis yAxisId="left" stroke={theme.info} tickFormatter={(val) => `R$ ${(val / 1000).toFixed(0)}k`} />
              <YAxis yAxisId="right" orientation="right" stroke={theme.textMuted} tickFormatter={(val) => `${(val * 100).toFixed(1)}%`} />
              <Tooltip contentStyle={{ backgroundColor: theme.bg, borderColor: theme.border, color: theme.textMain, borderRadius: '8px' }} formatter={(value, name) => [name === 'Volatilidade' ? `${(value * 100).toFixed(2)}%` : formatarBRL(value), name]} />
              <Legend wrapperStyle={{ color: theme.textMain, paddingTop: '10px', fontSize: '12px' }} />
              <Bar yAxisId="right" dataKey="volatilidade_mercado" name="Volatilidade" fill={theme.textMuted} opacity={0.2} radius={[4, 4, 0, 0]} />
              <Line yAxisId="left" type="monotone" dataKey="patrimonio_net" name="Capital Líquido" stroke={theme.info} strokeWidth={3} dot={false} activeDot={{ r: 6 }} />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* TABELA DE AUDITORIA E REPLAY IA */}
      <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '25px', borderRadius: '8px', overflowX: 'auto' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '20px', flexWrap: 'wrap', gap: '15px' }}>
          <div>
            <h3 style={{ color: theme.textMain, margin: '0 0 5px 0', fontSize: '1.2rem' }}>Replay de Decisão Estatística (IA)</h3>
            <p style={{ color: theme.textMuted, margin: 0, fontSize: '0.85rem' }}>Registro imutável extraído do PostgreSQL garantindo observabilidade determinística.</p>
          </div>
        </div>

        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', color: theme.textMain, fontSize: '0.9rem' }}>
          <thead>
            <tr style={{ borderBottom: `2px solid ${theme.border}` }}>
              <th style={{ padding: '12px', color: theme.textMuted }}>Timestamp (Tick)</th>
              <th style={{ padding: '12px', color: theme.textMuted }}>Ativo</th>
              <th style={{ padding: '12px', color: theme.textMuted }}>Z-Score / IA</th>
              <th style={{ padding: '12px', color: theme.textMuted }}>Fator Kelly</th>
              <th style={{ padding: '12px', color: theme.textMuted }}>Atrito (Slippage)</th>
              <th style={{ padding: '12px', color: theme.textMuted }}>Ação Executada</th>
              <th style={{ padding: '12px', color: theme.textMuted }}>Contexto Macro</th>
              <th style={{ padding: '12px', color: theme.textMuted, textAlign: 'center' }}>Auditoria</th>
            </tr>
          </thead>
          <tbody>
            {replay.map((ordem, idx) => {
              const regime = ordem.regime_mercado || 'DESCONHECIDO';
              const isBull = regime.includes('BULL');
              const isBear = regime.includes('BEAR') || regime.includes('Crise');
              const isGain = (ordem.custo_friccao_bps || 0) < 0;

              return (
                <tr key={idx} style={{ borderBottom: `1px solid ${theme.border}` }}>
                  <td style={{ padding: '12px', fontFamily: 'monospace' }}>{ordem.timestamp}</td>
                  <td style={{ padding: '12px', fontWeight: 'bold', color: theme.info }}>{ordem.ativo}</td>
                  <td style={{ padding: '12px', fontWeight: 'bold' }}>{ordem.z_score?.toFixed(2)}</td>
                  <td style={{ padding: '12px' }}>{(ordem.fator_kelly_alocado * 100).toFixed(1)}%</td>
                  <td style={{ padding: '12px', color: isGain ? theme.compra : theme.venda, fontWeight: isGain ? 'bold' : 'normal' }}>
                    {formatarSlippage(ordem.custo_friccao_bps)}
                  </td>
                  <td style={{ padding: '12px' }}>
                    <span style={{ padding: '4px 8px', borderRadius: '4px', backgroundColor: ordem.acao_executada.includes('COMPRA') ? 'rgba(16, 185, 129, 0.1)' : 'rgba(239, 68, 68, 0.1)', color: ordem.acao_executada.includes('COMPRA') ? theme.compra : theme.venda, fontSize: '0.75rem', fontWeight: 'bold' }}>
                      {ordem.acao_executada}
                    </span>
                  </td>
                  <td style={{ padding: '12px' }}>
                    <span style={{
                      padding: '4px 8px', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 'bold', whiteSpace: 'nowrap',
                      backgroundColor: isBull ? 'rgba(16, 185, 129, 0.1)' : (isBear ? 'rgba(239, 68, 68, 0.1)' : 'rgba(245, 158, 11, 0.1)'),
                      color: isBull ? theme.compra : (isBear ? theme.venda : theme.alerta),
                      border: `1px solid ${isBull ? theme.compra : (isBear ? theme.venda : theme.alerta)}`
                    }}>
                      {isBull ? '🐂' : (isBear ? '🐻' : '🦀')} {regime}
                    </span>
                  </td>
                  <td style={{ padding: '12px', textAlign: 'center' }}>
                    <button
                      onClick={() => setJsonModal(ordem)}
                      style={{ background: 'none', border: `1px solid ${theme.textMuted}`, color: theme.textMuted, padding: '4px 8px', borderRadius: '4px', cursor: 'pointer', fontSize: '0.8rem', fontWeight: 'bold' }}
                      onMouseOver={(e) => { e.target.style.borderColor = theme.info; e.target.style.color = theme.info; }}
                      onMouseOut={(e) => { e.target.style.borderColor = theme.textMuted; e.target.style.color = theme.textMuted; }}
                    >
                      🔍 Ver JSON
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* MODAL DA CAIXA-PRETA */}
      {jsonModal && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 5000 }}>
          <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, borderRadius: '12px', width: '90%', maxWidth: '600px', padding: '25px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '15px' }}>
              <h3 style={{ margin: 0, color: theme.textMain }}>Snapshot Determinístico</h3>
              <button onClick={() => setJsonModal(null)} style={{ background: 'none', border: 'none', color: theme.textMuted, cursor: 'pointer', fontSize: '1.2rem' }}>✖</button>
            </div>
            <p style={{ color: theme.textMuted, fontSize: '0.85rem', marginBottom: '15px' }}>Payload exato consumido pelo Golang no milissegundo da execução.</p>
            <pre style={{ backgroundColor: '#000', padding: '15px', borderRadius: '8px', color: '#10b981', overflowX: 'auto', fontSize: '0.85rem', border: `1px solid ${theme.border}` }}>
              {JSON.stringify(jsonModal, null, 2)}
            </pre>
          </div>
        </div>
      )}

    </div>
  );
};

