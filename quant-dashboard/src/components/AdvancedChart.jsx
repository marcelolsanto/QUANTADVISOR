
import React, { useState } from 'react';
import { ComposedChart, Bar, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts';
import { theme } from '../theme';

export const AdvancedChart = ({ data, showVolume = true }) => {
  const [chartType, setChartType] = useState('candle'); // 'candle' ou 'line'

  // Formatação para velas (Cálculo de cor e corpo)
  const renderData = data.map(item => ({
    ...item,
    bodyOpen: Math.min(item.open, item.close),
    bodyClose: Math.max(item.open, item.close),
    color: item.close >= item.open ? theme.compra : theme.venda
  }));

  return (
    <div style={{ height: '400px', width: '100%' }}>
      {/* Controles */}
      <div style={{ marginBottom: '10px', display: 'flex', gap: '10px' }}>
        <button onClick={() => setChartType('candle')} style={{ background: chartType === 'candle' ? theme.info : theme.border, color: '#fff', border: 'none', padding: '5px 10px', borderRadius: '4px', cursor: 'pointer' }}>Velas</button>
        <button onClick={() => setChartType('line')} style={{ background: chartType === 'line' ? theme.info : theme.border, color: '#fff', border: 'none', padding: '5px 10px', borderRadius: '4px', cursor: 'pointer' }}>Linha</button>
      </div>

      <ResponsiveContainer width="100%" height="90%">
        <ComposedChart data={renderData}>
          <CartesianGrid strokeDasharray="3 3" stroke={theme.border} vertical={false} />
          <XAxis dataKey="tempo" stroke={theme.textMuted} tick={{ fontSize: 10 }} />
          <YAxis domain={['auto', 'auto']} stroke={theme.textMuted} tick={{ fontSize: 12 }} />
          <Tooltip contentStyle={{ backgroundColor: theme.cardBg, borderColor: theme.border }} />

          {chartType === 'candle' ? (
            <>
              {/* Pavio (Wick) */}
              <Line dataKey="high" stroke={theme.textMain} dot={false} strokeWidth={1} />
              <Line dataKey="low" stroke={theme.textMain} dot={false} strokeWidth={1} />
              {/* Corpo (Body) */}
              <Bar dataKey="bodyClose" fill={theme.textMain}>
                {renderData.map((entry, index) => (
                  <Cell key={`cell-${index}`} fill={entry.color} />
                ))}
              </Bar>
            </>
          ) : (
            <Line type="monotone" dataKey="close" stroke={theme.info} dot={false} />
          )}

          {showVolume && (
            <Bar dataKey="volume" yAxisId="right" fill={theme.textMuted} fillOpacity={0.3} />
          )}
          <YAxis yAxisId="right" orientation="right" hide />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
};

