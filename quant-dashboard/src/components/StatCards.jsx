
import { theme } from '../theme';
import { StatCardsSkeleton } from './SkeletonLoader';

export const StatCards = ({ data, perfilUsuario, loading = false }) => {
  if (loading || !data || data.length === 0) {
    return <StatCardsSkeleton />;
  }

  const totalOportunidades = data.filter(d => {
    const sinalAtual = d.sinais_perfil && perfilUsuario 
      ? d.sinais_perfil[perfilUsuario] 
      : d.sinal;
    return sinalAtual === 'COMPRA FORTE';
  }).length;

  const pechincha = [...data].sort((a, b) => a.z_score - b.z_score)[0];
  const maiorRisco = [...data].sort((a, b) => a.risco_var - b.risco_var)[0];
  const kellyMedio = ((data.reduce((acc, item) => acc + (item.kelly_recomendado || 0), 0) / data.length) * 100).toFixed(1);

  const cardStyle = {
    backgroundColor: theme.cardBg,
    padding: '20px',
    borderRadius: '10px',
    border: `1px solid ${theme.border}`,
    flex: 1,
    minWidth: '220px',
    boxShadow: '0 4px 12px rgba(0, 0, 0, 0.25)',
    backdropFilter: 'blur(12px)',
    transition: 'transform 0.3s ease, boxShadow 0.3s ease, borderColor 0.3s ease'
  };

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '20px', marginBottom: '30px' }}>
      <div className="glass-card" style={{ ...cardStyle, borderLeft: `4px solid ${theme.compra}` }}>
        <h4 style={{ margin: '0 0 8px 0', color: theme.textMuted, fontSize: '0.85rem', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Oportunidades Claras</h4>
        <div style={{ fontSize: '2rem', fontWeight: 'bold', color: theme.textMain, fontFamily: 'monospace' }}>{totalOportunidades}</div>
        <div style={{ fontSize: '0.85rem', color: theme.compra, fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '4px' }}>⚡ Compra Forte ({perfilUsuario || 'SOFISTICADO'})</div>
      </div>

      <div className="glass-card" style={{ ...cardStyle, borderLeft: `4px solid ${theme.info}` }}>
        <h4 style={{ margin: '0 0 8px 0', color: theme.textMuted, fontSize: '0.85rem', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Distorção Estatística</h4>
        <div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: theme.textMain }}>{pechincha ? pechincha.ativo : 'N/A'}</div>
        <div style={{ fontSize: '0.85rem', color: theme.info, fontWeight: 'bold', fontFamily: 'monospace' }}>Z-Score: {pechincha ? pechincha.z_score.toFixed(2) : '0.00'}</div>
      </div>

      <div className="glass-card" style={{ ...cardStyle, borderLeft: `4px solid ${theme.venda}` }}>
        <h4 style={{ margin: '0 0 8px 0', color: theme.textMuted, fontSize: '0.85rem', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Risco Máximo (VaR)</h4>
        <div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: theme.textMain }}>{maiorRisco ? maiorRisco.ativo : 'N/A'}</div>
        <div style={{ fontSize: '0.85rem', color: theme.venda, fontWeight: 'bold', fontFamily: 'monospace' }}>Risco de Cauda: {maiorRisco ? maiorRisco.risco_var.toFixed(2) : '0.00'}%</div>
      </div>

      <div className="glass-card" style={{ ...cardStyle, borderLeft: `4px solid #8b5cf6` }}>
        <h4 style={{ margin: '0 0 8px 0', color: theme.textMuted, fontSize: '0.85rem', textTransform: 'uppercase', letterSpacing: '0.5px' }}>Alocação Máx (Kelly Média)</h4>
        <div style={{ fontSize: '1.8rem', fontWeight: 'bold', color: '#8b5cf6', fontFamily: 'monospace' }}>{kellyMedio}%</div>
        <div style={{ fontSize: '0.85rem', color: theme.textMuted, fontWeight: 'bold' }}>📐 Fração Ótima de Risco</div>
      </div>
    </div>
  );
};

