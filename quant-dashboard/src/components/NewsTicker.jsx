
import React, { useState, useEffect } from 'react';
import api, { getAuditoria, getHistorico } from '../services/api';
import { theme } from '../theme';
import { subscribeToMarket } from "../services/stream";
import { getSinalVisual } from '../utils/sinal';

// 🌍 FUNÇÃO AUXILIAR DE MOEDA
const formatarMoedaNativa = (valor, ticker = '') => {
  const isEstrangeiro = ticker && !/\d/.test(ticker) && !ticker.endsWith('.SA');
  const moeda = isEstrangeiro ? 'USD' : 'BRL';
  return new Intl.NumberFormat(isEstrangeiro ? 'en-US' : 'pt-BR', {
    style: 'currency', currency: moeda, minimumFractionDigits: 2
  }).format(Number(valor) || 0);
};

export const NewsTicker = ({ usuarioId, perfilUsuario = 'Agressivo' }) => {
  const [manchetes, setManchetes] = useState([]);
  const [indiceAtual, setIndiceAtual] = useState(0);

  useEffect(() => {
    let isMounted = true;

    const compilarNoticias = async () => {
      try {
        const novasManchetes = [];
        
        let cotacaoDolar = 5.00;
        try {
          const reqDolar = await fetch('https://economia.awesomeapi.com.br/last/USD-BRL');
          const dadosDolar = await reqDolar.json();
          cotacaoDolar = Number(dadosDolar.USDBRL.ask);
        } catch (e) { console.warn("Aviso: Falha ao buscar Dólar", e); }

        // ==============================================================
        // 1. [SISTEMA] Regime Macro
        // ==============================================================
        try {
          const resAuditoria = await getAuditoria();
          if (resAuditoria.data && resAuditoria.data.regime) {
            const regime = resAuditoria.data.regime;
            novasManchetes.push({
              id: 'regime',
              tag: 'SISTEMA',
              texto: `Regime Macroeconômico Atual: ${regime} - Algoritmos calibrados.`,
              cor: regime.includes('BULL') ? theme.compra : (regime.includes('BEAR') ? theme.venda : theme.alerta)
            });
          }
        } catch (e) { console.warn("Aviso: Falha no Regime Macro", e); }

        // ==============================================================
        // 2. [👑 TOP INVESTIDOR] Desempenho por Clientes (Leaderboard)
        // ==============================================================
        try {
          const resUsuarios = await api.get('/api/usuarios');
          if (resUsuarios.data && Array.isArray(resUsuarios.data) && resUsuarios.data.length > 0) {
            let melhorCliente = null;
            let maiorLucro = 0;

            resUsuarios.data.forEach(cliente => {
              const lucroAtual = Number(cliente.lucro_acumulado || 0);
              if (lucroAtual > maiorLucro) {
                maiorLucro = lucroAtual;
                melhorCliente = cliente;
              }
            });

            if (melhorCliente && maiorLucro > 0) {
              const lucroFmt = formatarMoedaNativa(maiorLucro, 'BRL');
              novasManchetes.push({
                id: 'destaque_cliente',
                tag: '👑 TOP INVESTIDOR',
                texto: `O cliente ${melhorCliente.nome || melhorCliente.login} lidera o ranking do fundo com ${lucroFmt} de lucro acumulado!`,
                cor: theme.compra
              });
            }
          }
        } catch (e) { console.warn("Aviso: Falha no Leaderboard de Clientes. A rota pode estar restrita.", e); }

        // ==============================================================
        // 3. [🎯 SEU DESTAQUE] O Melhor Ativo da Custódia Selecionada
        // ==============================================================
        try {
          const resCarteira = await api.get(`/api/carteira?usuario_id=${usuarioId}`);
          if (resCarteira.data && resCarteira.data.posicoes && resCarteira.data.posicoes.length > 0) {
            let melhorAtivo = null;
            let maiorLucroFinanceiroBRL = -Infinity;

            resCarteira.data.posicoes.forEach(pos => {
              const precoAtual = pos.preco_atual !== null ? pos.preco_atual : pos.preco_medio;
              const isEstrangeiro = !/\d/.test(pos.ticker) && !pos.ticker.endsWith('.SA');
              const taxaCambio = isEstrangeiro ? cotacaoDolar : 1;
              const lucroBRL = (pos.quantidade * precoAtual * taxaCambio) - (pos.quantidade * pos.preco_medio * taxaCambio);
              const rentabilidadePerc = pos.preco_medio > 0 ? ((precoAtual - pos.preco_medio) / pos.preco_medio) * 100 : 0;

              if (lucroBRL > maiorLucroFinanceiroBRL && lucroBRL > 0) {
                maiorLucroFinanceiroBRL = lucroBRL;
                melhorAtivo = { ...pos, lucroBRL, rentabilidadePerc };
              }
            });

            if (melhorAtivo) {
              const lucroFmt = formatarMoedaNativa(melhorAtivo.lucroBRL, 'BRL');
              novasManchetes.push({
                id: 'destaque_pnl',
                tag: '🎯 SEU DESTAQUE',
                texto: `O ativo ${melhorAtivo.ticker} lidera a carteira atual com +${melhorAtivo.rentabilidadePerc.toFixed(2)}% de lucro aberto (${lucroFmt}).`,
                cor: theme.info
              });
            }
          }
        } catch (e) { console.warn("Aviso: Falha no P&L da Carteira", e); }

        // ==============================================================
        // 4. [⚡ TAPE READING] & [🏦 LIVRO DIÁRIO]
        // ==============================================================
        try {
          const resHist = await getHistorico(usuarioId);
          if (resHist.data && resHist.data.ordens && resHist.data.ordens.length > 0) {
            const ultimosEventos = resHist.data.ordens.slice(0, 1);
            
            ultimosEventos.forEach((evento, index) => {
              const tipo = evento.tipo_ordem ? evento.tipo_ordem.toUpperCase() : 'LANÇAMENTO';
              
              if (tipo === 'COMPRA' || tipo === 'VENDA') {
                const precoFmt = formatarMoedaNativa(evento.preco_execucao, evento.ticker);
                novasManchetes.push({
                  id: `tape_${index}_${evento.id || Date.now()}`,
                  tag: '⚡ REPLAY IA',
                  texto: `Execução no Mercado: O robô realizou ${tipo} de ${evento.quantidade} cotas de ${evento.ticker} a ${precoFmt}.`,
                  cor: tipo === 'COMPRA' ? theme.compra : theme.venda
                });
              } else {
                const valorRef = evento.valor || evento.financeiro || evento.preco_execucao || 0;
                const valorFmt = formatarMoedaNativa(valorRef, 'BRL');
                novasManchetes.push({
                  id: `ledger_${index}_${evento.id || Date.now()}`,
                  tag: '🏦 LIVRO DIÁRIO',
                  texto: `Movimentação de Caixa: Foi registrado um ${tipo} no valor de ${valorFmt}.`,
                  cor: theme.textMuted
                });
              }
            });
          }
        } catch (e) { console.warn("Aviso: Falha ao carregar Extrato/Histórico", e); }

        // ==============================================================
        // 5. [MERCADO] Notícias em Tempo Real (Google + Yahoo)
        // ==============================================================
        try {
          // Puxa o feed oficial da aba global "Negócios/Economia" do Google News/Finance
          const googleRss = 'https://news.google.com/news/rss/headlines/section/topic/BUSINESS?hl=pt-BR&gl=BR';
          // Puxa exclusivamente artigos do Yahoo Finanças Brasil
          const yahooRss = "https://news.google.com/rss/search?q=site:br.financas.yahoo.com+when:1d&hl=pt-BR&gl=BR&ceid=BR:pt-419";
          const yahooUrl = `https://api.rss2json.com/v1/api.json?rss_url=${encodeURIComponent(yahooRss)}`;

          const [googleRes, yahooRes] = await Promise.all([
            fetch(`https://api.rss2json.com/v1/api.json?rss_url=${encodeURIComponent(googleRss)}`).then(res => res.json()).catch(() => ({ items: [] })),
            fetch(`https://api.rss2json.com/v1/api.json?rss_url=${encodeURIComponent(yahooRss)}`).then(res => res.json()).catch(() => ({ items: [] }))
          ]);

          const noticiasUnificadas = [];

          // Processa Google News (Tag cinza)
          if (googleRes.status === 'ok' && googleRes.items) {
            googleRes.items.forEach((n, idx) => {
              noticiasUnificadas.push({
                id: `google_${idx}_${Date.now()}`,
                tag: 'GOOGLE NEWS',
                texto: n.title.split(' - ')[0],
                link: n.link,
                cor: theme.textMuted,
                hora: new Date(n.pubDate).getTime()
              });
            });
          }

          // Processa Yahoo Finance (Tag laranja/alerta)
          if (yahooRes.status === 'ok' && yahooRes.items) {
            yahooRes.items.forEach((n, idx) => {
              noticiasUnificadas.push({
                id: `yahoo_${idx}_${Date.now()}`,
                tag: 'YAHOO FINANCE',
                texto: n.title.split(' - ')[0],
                link: n.link,
                cor: theme.alerta,
                hora: new Date(n.pubDate).getTime()
              });
            });
          }

          // Filtra duplicadas (pelo título exato)
          const noticiasUnicas = noticiasUnificadas.filter((v, i, a) => a.findIndex(t => (t.texto === v.texto)) === i);

          // Ordena das mais recentes para as mais antigas
          noticiasUnicas.sort((a, b) => b.hora - a.hora);

          // Adiciona as 10 notícias mais quentes do dia na fita do rodapé
          noticiasUnicas.slice(0, 10).forEach(n => {
            novasManchetes.push(n);
          });

        } catch (e) { console.warn("Aviso: Falha ao carregar Notícias Unificadas", e); }

        // Protege e faz o "Merge" com os alertas que a IA já enviou
        if (isMounted) {
          setManchetes(prev => {
            const alertasIA = prev.filter(m => m.id.startsWith('gatilho_'));
            return [...alertasIA, ...novasManchetes];
          });
        }

      } catch (err) {
        console.error("Erro crítico geral no Ticker:", err);
      }
    };

    compilarNoticias();
    const interval = setInterval(compilarNoticias, 15 * 60 * 1000); 

    // ==============================================================
    // 6. [IA GATILHO] Stream SSE (Torre Central)
    // ==============================================================
    const unsubMarket = subscribeToMarket((pacote) => {
      const sinalDinamico = getSinalVisual(pacote, perfilUsuario);

      if (sinalDinamico === 'COMPRA FORTE' || sinalDinamico === 'ALERTA DE VENDA') {
        if (isMounted) {
          setManchetes(prev => {
            const uniqueId = `gatilho_${Date.now()}_${Math.random().toString(36).substring(7)}`;
            const novaManchete = {
              id: uniqueId,
              tag: '🤖 RADAR IA',
              texto: `${sinalDinamico} detectado em ${pacote.ativo} | Z-Score: ${pacote.z_score?.toFixed(2)} | VaR: ${pacote.risco_var?.toFixed(1)}%`,
              cor: sinalDinamico === 'COMPRA FORTE' ? theme.compra : theme.venda
            };
            
            const newState = [...prev];
            newState.splice(2, 0, novaManchete); 
            return newState.slice(0, 15); 
          });
        }
      }
    });

    return () => {
      isMounted = false;
      clearInterval(interval);
      unsubMarket();
    };
  }, [usuarioId, perfilUsuario]);

  if (manchetes.length === 0) return null;

  return (
    <div style={{
      position: 'fixed', bottom: 0, left: 0, right: 0,
      backgroundColor: '#030712', borderTop: `1px solid ${theme.border}`,
      height: '38px', display: 'flex', alignItems: 'center',
      zIndex: 5000, boxShadow: '0 -4px 20px rgba(0,0,0,0.8)'
    }}>
      
      <div style={{
        backgroundColor: theme.info, color: '#fff',
        padding: '0 20px', fontWeight: 'bold', fontSize: '0.8rem',
        letterSpacing: '1px', height: '100%', display: 'flex', alignItems: 'center',
        zIndex: 2, boxShadow: '4px 0 15px rgba(0,0,0,0.8)',
        whiteSpace: 'nowrap', borderRight: `2px solid ${theme.border}`
      }}>
        QUANTADVISOR LIVE
      </div>
      
      <div className="ticker-wrapper" style={{ flex: 1, overflow: 'hidden', whiteSpace: 'nowrap', display: 'flex', alignItems: 'center' }}>
        <div className="ticker-content" style={{ display: 'inline-block', animation: 'ticker 100s linear infinite', paddingLeft: '100%' }}>
          {manchetes.map((m) => (
            <span key={m.id} style={{ marginRight: '70px', fontSize: '0.85rem', display: 'inline-flex', alignItems: 'center' }}>
              
              <span style={{ 
                backgroundColor: `${m.cor}15`, color: m.cor, padding: '2px 8px', borderRadius: '4px', 
                fontSize: '0.7rem', fontWeight: 'bold', marginRight: '12px', border: `1px solid ${m.cor}50` 
              }}>
                {m.tag}
              </span>
              
              {m.link ? (
                <a href={m.link} target="_blank" rel="noreferrer" 
                  style={{ color: '#e2e8f0', textDecoration: 'none', transition: 'color 0.2s', fontWeight: '500' }} 
                  onMouseOver={(e) => e.target.style.color = theme.info} onMouseOut={(e) => e.target.style.color = '#e2e8f0'}>
                  {m.texto}
                </a>
              ) : (
                <span style={{ color: '#f8fafc', fontWeight: 'bold', letterSpacing: '0.5px' }}>
                  {m.texto}
                </span>
              )}
              
            </span>
          ))}
        </div>
      </div>

      <style>{`
        @keyframes ticker { 0% { transform: translateX(0); } 100% { transform: translateX(-100%); } }
        .ticker-wrapper:hover .ticker-content { animation-play-state: paused; }
      `}</style>
    </div>
  );
};

