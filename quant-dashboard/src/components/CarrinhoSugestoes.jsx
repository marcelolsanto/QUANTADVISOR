
import { useState, useEffect, useMemo, useCallback } from 'react';
import { getCarrinho, enviarOrdem, limparCarrinho } from '../services/api';
import { theme } from '../theme';

// 🌍 FUNÇÃO AUXILIAR DE MOEDA NATIVA
const formatarMoedaNativa = (valor, ticker) => {
  const isEstrangeiro = !/\d/.test(ticker) && !ticker.endsWith('.SA');
  const moeda = isEstrangeiro ? 'USD' : 'BRL';
  return new Intl.NumberFormat(isEstrangeiro ? 'en-US' : 'pt-BR', {
    style: 'currency', currency: moeda, minimumFractionDigits: 2
  }).format(Number(valor) || 0);
};

const CarrinhoSugestoes = ({ usuarioId }) => {
  const [sugestoes, setSugestoes] = useState([]);
  const [loadingItem, setLoadingItem] = useState(null);

  const [sortConfig, setSortConfig] = useState({ key: 'ticker', direction: 'ascending' });

  const carregarCarrinho = useCallback(async () => {
    if (!usuarioId) return;
    try {
      const res = await getCarrinho(usuarioId);
      setSugestoes(res.data || []);
    } catch (err) {
      console.error("Erro ao carregar carrinho:", err);
    }
  }, [usuarioId]);

  useEffect(() => {
    let isMounted = true;
    let timerId = null;

    const loopCarrinho = async () => {
      if (!isMounted) return;
      if (!document.hidden) await carregarCarrinho();
      if (isMounted) timerId = setTimeout(loopCarrinho, 15000);
    };

    loopCarrinho();
    window.addEventListener('carrinhoAtualizado', carregarCarrinho);

    return () => {
      isMounted = false;
      if (timerId) clearTimeout(timerId);
      window.removeEventListener('carrinhoAtualizado', carregarCarrinho);
    };
  }, [usuarioId, carregarCarrinho]);

  const sugestoesAgrupadas = Object.values(sugestoes.reduce((acc, item) => {
    const chave = `${item.ticker}_${item.tipo}`;
    if (!acc[chave]) {
      acc[chave] = { ...item, quantidadeTotal: 0, ocorrencias: 0, idsPendentes: [] };
    }
    acc[chave].quantidadeTotal += item.quantidade;
    acc[chave].ocorrencias += 1;
    acc[chave].idsPendentes.push(item.id);
    acc[chave].preco = item.preco;
    return acc;
  }, {}));

  const sugestoesOrdenadas = useMemo(() => {
    let sortable = [...sugestoesAgrupadas];
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
  }, [sugestoesAgrupadas, sortConfig]);

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

  const limparTodoOCarrinho = async () => {
    if (!window.confirm("🧹 Atenção: Isso vai apagar TODAS as sugestões antigas da IA. Deseja prosseguir?")) return;
    const todosIds = sugestoes.map(item => item.id);
    if (todosIds.length === 0) return;
    try {
      await limparCarrinho({ ids: todosIds });
      carregarCarrinho();
    } catch (err) {
      alert("⚠️ Erro ao esvaziar o carrinho.");
    }
  };

  const recusarOrdemAgrupada = async (grupo) => {
    setLoadingItem(grupo.ticker);
    try {
      await limparCarrinho({ ids: grupo.idsPendentes });
      carregarCarrinho();
    } catch (err) {
      alert("⚠️ Erro ao excluir a sugestão.");
    } finally {
      setLoadingItem(null);
    }
  };

  const aprovarOrdemAgrupada = async (grupo) => {
    const precoFormatado = formatarMoedaNativa(grupo.preco, grupo.ticker);
    const inputQtd = window.prompt(`Confirme ou Edite o lote para ${grupo.ticker}\nSinal da IA: ${grupo.tipo} a ${precoFormatado}`, grupo.quantidadeTotal);
    if (inputQtd === null) return; 

    const qtdFinal = parseInt(inputQtd, 10);
    if (isNaN(qtdFinal) || qtdFinal <= 0) return alert("⚠️ Quantidade inválida. A operação foi cancelada.");

    setLoadingItem(grupo.ticker);
    try {
      await enviarOrdem({ usuario_id: usuarioId, ticker: grupo.ticker, tipo_ordem: grupo.tipo, quantidade: qtdFinal, preco: grupo.preco });
      await limparCarrinho({ ids: grupo.idsPendentes });
      alert(`✅ Lote de ${qtdFinal} cotas de ${grupo.ticker} executado com sucesso!`);
      carregarCarrinho();
    } catch (err) {
      alert("⚠️ Erro na aprovação: " + (err.response?.data?.erro || "Falha na comunicação."));
    } finally {
      setLoadingItem(null);
    }
  };

  const aprovarTodoOCarrinho = async () => {
    if (!window.confirm("🚀 Atenção: O sistema executará primeiro as VENDAS para fazer caixa, e depois as COMPRAS. Deseja prosseguir?")) return;

    setLoadingItem('ALL');
    try {
      const filaDeExecucao = [...sugestoesAgrupadas].sort((a, b) => {
        if (a.tipo === 'VENDA' && b.tipo !== 'VENDA') return -1;
        if (a.tipo !== 'VENDA' && b.tipo === 'VENDA') return 1;
        return 0;
      });

      for (const grupo of filaDeExecucao) {
        await enviarOrdem({
          usuario_id: usuarioId,
          ticker: grupo.ticker,
          tipo_ordem: grupo.tipo,
          quantidade: grupo.quantidadeTotal,
          preco: grupo.preco
        });
        await limparCarrinho({ ids: grupo.idsPendentes });
      }
      
      alert("✅ Lote executado com sucesso (Vendas priorizadas e Caixa protegido)!");
      carregarCarrinho();
    } catch (err) {
      alert("⚠️ Erro na execução em lote: " + (err.response?.data?.erro || "Falha na comunicação."));
    } finally {
      setLoadingItem(null);
    }
  };

  const thStyle = { padding: '12px 10px', borderBottom: `2px solid ${theme.border}`, color: theme.textMuted, fontWeight: 'bold', cursor: 'pointer', userSelect: 'none', transition: '0.2s', position: 'sticky', top: 0, backgroundColor: theme.cardBg, zIndex: 10, boxShadow: `0 2px 4px rgba(0,0,0,0.1)` };
  const tdStyle = { padding: '12px 10px', borderBottom: `1px solid ${theme.border}` };

  return (
    <div style={{ backgroundColor: theme.cardBg, borderRadius: '12px', border: `1px solid ${theme.border}`, padding: '20px', boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)', height: '100%', minHeight: '400px' }}>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: `1px solid ${theme.border}`, paddingBottom: '15px', marginBottom: '15px', flexWrap: 'wrap', gap: '10px' }}>
        <h3 style={{ color: theme.textMain, margin: 0, display: 'flex', alignItems: 'center', gap: '8px' }}>
          🛒 Carrinho Noturno
          <span style={{ backgroundColor: theme.bg, color: theme.info, padding: '4px 10px', borderRadius: '12px', fontSize: '0.8rem', fontWeight: 'bold', border: `1px solid ${theme.border}` }}>
            {sugestoesAgrupadas.length} Lotes
          </span>
        </h3>

        {sugestoesAgrupadas.length > 0 && (
          <div style={{ display: 'flex', gap: '10px' }}>
            <button onClick={limparTodoOCarrinho} disabled={loadingItem === 'ALL'} style={{ padding: '6px 12px', backgroundColor: 'rgba(239, 68, 68, 0.1)', color: theme.venda, border: `1px solid ${theme.venda}`, borderRadius: '6px', cursor: loadingItem === 'ALL' ? 'wait' : 'pointer', fontSize: '0.8rem', fontWeight: 'bold', transition: '0.2s', opacity: loadingItem === 'ALL' ? 0.5 : 1 }}>
              🧹 Esvaziar Tudo
            </button>
            <button onClick={aprovarTodoOCarrinho} disabled={loadingItem === 'ALL'} style={{ padding: '6px 12px', backgroundColor: theme.compra, color: '#fff', border: 'none', borderRadius: '6px', cursor: loadingItem === 'ALL' ? 'wait' : 'pointer', fontSize: '0.8rem', fontWeight: 'bold', transition: '0.2s', opacity: loadingItem === 'ALL' ? 0.5 : 1 }}>
              {loadingItem === 'ALL' ? '⏳ Processando...' : '✅ Executar Tudo'}
            </button>
          </div>
        )}
      </div>

      {sugestoesAgrupadas.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '40px 0', color: theme.textMuted, fontStyle: 'italic' }}>
          <div style={{ fontSize: '2rem', marginBottom: '10px', opacity: 0.5 }}>💤</div>
          Nenhuma sugestão pendente.<br />A IA está aguardando oportunidades.
        </div>
      ) : (
        <div style={{ 
          maxHeight: '350px',
          overflowY: 'auto', 
          overflowX: 'auto',
          borderRadius: '8px',
          border: `1px solid ${theme.border}`,
          backgroundColor: theme.bg
        }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.9rem', position: 'relative' }}>
            <thead>
              <tr>
                <th style={thStyle} onClick={() => requestSort('ticker')}>Ativo{getSortIcon('ticker')}</th>
                <th style={thStyle} onClick={() => requestSort('tipo')}>Ação{getSortIcon('tipo')}</th>
                <th style={thStyle} onClick={() => requestSort('quantidadeTotal')}>Qtd Total{getSortIcon('quantidadeTotal')}</th>
                <th style={thStyle} onClick={() => requestSort('preco')}>Preço Base{getSortIcon('preco')}</th>
                <th style={{ ...thStyle, textAlign: 'center', cursor: 'default' }}>Aprovação</th>
              </tr>
            </thead>
            <tbody>
              {sugestoesOrdenadas.map((grupo, idx) => {
                const isCompra = grupo.tipo === 'COMPRA';
                return (
                  <tr key={idx} style={{ transition: '0.2s' }} onMouseOver={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.02)'} onMouseOut={(e) => e.currentTarget.style.backgroundColor = 'transparent'}>
                    <td style={{ ...tdStyle, fontWeight: 'bold', color: theme.textMain }}>
                      {grupo.ticker}
                      {grupo.ocorrencias > 1 && (
                        <div style={{ fontSize: '0.65rem', color: theme.textMuted, marginTop: '2px' }}>Recomendado {grupo.ocorrencias}x</div>
                      )}
                    </td>
                    <td style={tdStyle}>
                      <span style={{ padding: '4px 8px', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 'bold', backgroundColor: isCompra ? 'rgba(16, 185, 129, 0.15)' : 'rgba(239, 68, 68, 0.15)', color: isCompra ? theme.compra : theme.venda, border: `1px solid ${isCompra ? theme.compra : theme.venda}` }}>
                        {grupo.tipo}
                      </span>
                    </td>
                    <td style={{ ...tdStyle, color: theme.textMain, fontWeight: 'bold' }}>{grupo.quantidadeTotal}</td>
                    
                    {/* 🌍 APLICAÇÃO DA MOEDA NATIVA AQUI */}
                    <td style={{ ...tdStyle, fontFamily: 'monospace', color: theme.textMuted }}>
                      {formatarMoedaNativa(grupo.preco, grupo.ticker)}
                    </td>

                    <td style={{ ...tdStyle, textAlign: 'center' }}>
                      <div style={{ display: 'flex', gap: '5px', justifyContent: 'center' }}>
                        <button onClick={() => aprovarOrdemAgrupada(grupo)} disabled={loadingItem === grupo.ticker} style={{ padding: '6px 12px', borderRadius: '4px', border: 'none', fontWeight: 'bold', fontSize: '0.8rem', cursor: loadingItem === grupo.ticker ? 'wait' : 'pointer', transition: '0.2s', backgroundColor: loadingItem === grupo.ticker ? theme.border : theme.compra, color: '#fff', opacity: loadingItem === grupo.ticker ? 0.7 : 1 }}>
                          {loadingItem === grupo.ticker ? '⏳...' : 'APROVAR'}
                        </button>
                        <button onClick={() => recusarOrdemAgrupada(grupo)} disabled={loadingItem === grupo.ticker} style={{ padding: '6px', borderRadius: '4px', border: `1px solid ${theme.border}`, fontWeight: 'bold', fontSize: '0.8rem', cursor: loadingItem === grupo.ticker ? 'wait' : 'pointer', transition: '0.2s', backgroundColor: theme.bg, color: theme.venda, opacity: loadingItem === grupo.ticker ? 0.7 : 1 }} title="Recusar e Excluir">
                          ❌
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default CarrinhoSugestoes;

