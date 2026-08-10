
import { useState, useEffect } from 'react';
import { getRiscoSistemico } from '../services/api';
import { theme } from '../theme'; // Importação do tema

export const RiskModal = ({ onClose }) => {
  const [dadosRisco, setDadosRisco] = useState(null);
  const [loading, setLoading] = useState(true);
  const [erro, setErro] = useState('');

  useEffect(() => {
    const fetchRisco = async () => {
      try {
        const res = await getRiscoSistemico();
        if (res.data.sucesso) {
          setDadosRisco(res.data);
        } else {
          setErro(res.data.erro);
        }
      } catch {
        setErro('Erro de comunicação com o motor quantitativo.');
      } finally {
        setLoading(false);
      }
    };
    fetchRisco();
  }, []);

  return (
    <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1100 }}>
      <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '30px', borderRadius: '12px', width: '90%', maxWidth: '1000px', maxHeight: '90vh', overflowY: 'auto', boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.5)' }}>

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', borderBottom: `1px solid ${theme.border}`, paddingBottom: '15px' }}>
          <div>
            <h2 style={{ margin: 0, color: theme.textMain, display: 'flex', alignItems: 'center', gap: '10px' }}>
              🛡️ Inteligência de Risco Sistêmico
            </h2>
            <p style={{ margin: '5px 0 0 0', color: theme.textMuted, fontSize: '0.9rem' }}>
              Matriz de Correlação de Pearson {dadosRisco && `(${dadosRisco.ativos_analisados} ativos cruzados)`}
            </p>
          </div>
          <button
            onClick={onClose}
            style={{ background: theme.bg, border: `1px solid ${theme.border}`, width: '40px', height: '40px', borderRadius: '50%', fontSize: '1.2rem', cursor: 'pointer', color: theme.textMain, fontWeight: 'bold', transition: 'background-color 0.2s' }}
            onMouseOver={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.1)'}
            onMouseOut={(e) => e.currentTarget.style.backgroundColor = theme.bg}
          >
            ✖
          </button>
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: '50px 0', color: theme.textMuted }}>
            <div style={{ fontSize: '2rem', marginBottom: '15px' }}>⏳</div>
            <h3 style={{ color: theme.textMain }}>Processando Álgebra Linear Quantitativa...</h3>
            <p>Calculando a matriz de correlação cruzada do mercado. Isso pode levar alguns segundos.</p>
          </div>
        ) : erro ? (
          <div style={{ textAlign: 'center', padding: '50px 0', color: theme.venda }}>
            <h3>⚠️ Atenção</h3>
            <p>{erro}</p>
          </div>
        ) : dadosRisco && (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '30px' }}>

            {/* Coluna Esquerda: Insights */}
            <div style={{ flex: '1', minWidth: '300px' }}>

              {/* Alertas de Concentração */}
              <div style={{ marginBottom: '30px' }}>
                <h4 style={{ color: theme.venda, borderBottom: `1px solid ${theme.venda}`, paddingBottom: '5px', opacity: 0.9 }}>⚠️ Alerta de Concentração</h4>
                <p style={{ fontSize: '0.85rem', color: theme.textMuted, marginBottom: '15px' }}>Ativos altamente correlacionados. Comprá-los juntos duplica o risco direcional.</p>

                {dadosRisco.alertas_concentracao.map((alerta, idx) => (
                  <div key={idx} style={{ display: 'flex', justifyContent: 'space-between', padding: '10px', backgroundColor: 'rgba(239, 68, 68, 0.08)', borderLeft: `4px solid ${theme.venda}`, marginBottom: '8px', borderRadius: '4px' }}>
                    <span style={{ fontWeight: 'bold', color: theme.textMain }}>{alerta.ativo1} ↔ {alerta.ativo2}</span>
                    <span style={{ color: theme.venda, fontWeight: 'bold' }}>+{alerta.correlacao.toFixed(2)}</span>
                  </div>
                ))}
              </div>

              {/* Oportunidades de Hedge */}
              <div>
                <h4 style={{ color: theme.compra, borderBottom: `1px solid ${theme.compra}`, paddingBottom: '5px', opacity: 0.9 }}>✅ Oportunidades de Hedge</h4>
                <p style={{ fontSize: '0.85rem', color: theme.textMuted, marginBottom: '15px' }}>Ativos descorrelacionados. Ideais para balanceamento e proteção da carteira.</p>

                {dadosRisco.oportunidades_hedge.map((hedge, idx) => (
                  <div key={idx} style={{ display: 'flex', justifyContent: 'space-between', padding: '10px', backgroundColor: 'rgba(16, 185, 129, 0.08)', borderLeft: `4px solid ${theme.compra}`, marginBottom: '8px', borderRadius: '4px' }}>
                    <span style={{ fontWeight: 'bold', color: theme.textMain }}>{hedge.ativo1} ↔ {hedge.ativo2}</span>
                    <span style={{ color: theme.compra, fontWeight: 'bold' }}>{hedge.correlacao > 0 ? '+' : ''}{hedge.correlacao.toFixed(2)}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Coluna Direita: Heatmap */}
            <div style={{ flex: '2', minWidth: '400px', backgroundColor: theme.bg, padding: '20px', borderRadius: '8px', border: `1px solid ${theme.border}`, textAlign: 'center' }}>
              <h4 style={{ color: theme.textMain, margin: '0 0 15px 0' }}>Mapa de Calor (Riscos Estruturais)</h4>
              {/* O React agora renderiza o Base64 direto da memória, sem bater na URL */}
              <img
                src={`data:image/png;base64,${dadosRisco.heatmap_base64}`}
                alt="Matriz de Correlação"
                style={{ width: '100%', height: 'auto', borderRadius: '4px', border: `1px solid ${theme.border}`, opacity: 0.9 }}
              alt="Matriz de Correlação"
              style={{ width: '100%', height: 'auto', borderRadius: '4px', border: `1px solid ${theme.border}`, opacity: 0.9 }} // Leve transparência para mesclar melhor no dark mode
              />
              <p style={{ fontSize: '0.8rem', color: theme.textMuted, marginTop: '10px' }}>* Manchas vermelhas representam setores inteiros se movendo em bloco. Manchas verdes são portos seguros (Hedges).</p>
            </div>

          </div>
        )}
      </div>
    </div>
  );
};

