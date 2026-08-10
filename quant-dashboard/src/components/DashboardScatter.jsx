
import { ScatterChart, Scatter, XAxis, YAxis, ZAxis, CartesianGrid, Tooltip, ReferenceLine, ResponsiveContainer, Cell } from 'recharts';
import { theme } from '../theme';

export const DashboardScatter = ({ data }) => {
  return (
    <div style={{ backgroundColor: theme.cardBg, padding: '20px', borderRadius: '12px', border: `1px solid ${theme.border}`, height: '500px' }}>
      <h4 style={{ color: theme.textMain, margin: '0 0 20px 0' }}>Matriz de Oportunidade vs. Risco</h4>
      <ResponsiveContainer width="100%" height="90%">
        <ScatterChart margin={{ top: 20, right: 20, bottom: 20, left: 20 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={theme.border} />
          <XAxis type="number" dataKey="z_score" name="Z-Score" unit="" stroke={theme.textMuted} label={{ value: 'Z-Score (Oportunidade)', position: 'bottom', fill: theme.textMuted }} />
          <YAxis type="number" dataKey="risco_var" name="VaR 99%" unit="%" stroke={theme.textMuted} label={{ value: 'Risco de Cauda (VaR)', angle: -90, position: 'left', fill: theme.textMuted }} />
          
          {/* Linhas de quadrante */}
          <ReferenceLine x={0} stroke={theme.textMuted} />
          <ReferenceLine y={0} stroke={theme.textMuted} />

          <Tooltip cursor={{ strokeDasharray: '3 3' }} />
          
          <Scatter name="Ativos" data={data} fill={theme.info}>
            {data.map((entry, index) => (
              <Cell key={`cell-${index}`} fill={entry.z_score > 0 && entry.risco_var < 0 ? theme.compra : theme.info} />
            ))}
          </Scatter>
        </ScatterChart>
      </ResponsiveContainer>
    </div>
  );
};

