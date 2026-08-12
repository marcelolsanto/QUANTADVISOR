
import React, { useState, useMemo, memo } from 'react';
import { ComposedChart, Line, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, ReferenceLine } from 'recharts';
import { theme } from '../theme';
import { getSinalVisual } from '../utils/sinal';

export const DashboardChart = memo(({ data, perfilUsuario = 'Agressivo' }) => {
  
  // 1. Estado que controla o filtro selecionado
  const [filtroSinal, setFiltroSinal] = useState('TODOS');

  // 2. Filtro e Formatação unificados no useMemo para máxima performance
  const dadosProcessados = useMemo(() => {
    if (!data) return [];
    
    // 1. Filtra pelo sinal do perfil usando getSinalVisual
    const filtrados = filtroSinal === 'TODOS' 
      ? data 
      : data.filter(item => {
          const sinal = getSinalVisual(item, perfilUsuario);
          return sinal === filtroSinal;
        });

    // 2. 👇 TRAVA DE PERFORMANCE: Renderizar no máximo os 50 primeiros para 60 FPS no DOM SVG!
    const limiteRenderizacao = filtrados.slice(0, 50);

    // 3. Formata apenas os que vão pra tela
    return limiteRenderizacao.map(item => ({
      ...item,
      risco_var_invertido: item.risco_var * -1
    }));
  }, [data, filtroSinal, perfilUsuario]);

  if (!data || data.length === 0) {
    return (
      <div style={{ height: '460px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: theme.textMuted }}>
        Aguardando processamento do motor econométrico...
      </div>
    );
  }

  return (
    <div style={{
      width: '100%',
      height: '520px', // Altura ligeiramente maior para acomodar a barra de botões
      backgroundColor: theme.cardBg,
      border: `1px solid ${theme.border}`,
      padding: '25px 20px 20px 20px',
      borderRadius: '12px',
      boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)',
      boxSizing: 'border-box',
      marginBottom: '24px',
      display: 'flex',
      flexDirection: 'column'
    }}>
      
      {/* 3. CABEÇALHO E BARRA DE FILTROS */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '20px', flexWrap: 'wrap', gap: '10px' }}>
        <h4 style={{ margin: '0 0 0 5px', color: theme.textMain, fontSize: '1.1rem', fontWeight: '600', letterSpacing: '0.5px' }}>
          📊 Matriz Avançada (Exibindo: {dadosProcessados.length} ativos)
        </h4>
        
        <div style={{ display: 'flex', gap: '8px' }}>
          {['TODOS', 'COMPRA FORTE', 'NEUTRO', 'ALERTA DE VENDA'].map(sinal => {
            const isActive = filtroSinal === sinal;
            
            // Define as cores dos botões dinamicamente
            let btnColor = theme.textMuted;
            if (isActive) {
              if (sinal === 'COMPRA FORTE') btnColor = theme.compra;
              else if (sinal === 'ALERTA DE VENDA') btnColor = theme.venda;
              else if (sinal === 'NEUTRO') btnColor = theme.alerta;
              else btnColor = theme.info;
            }

            return (
              <button 
                key={sinal}
                onClick={() => setFiltroSinal(sinal)}
                style={{
                  padding: '6px 12px',
                  borderRadius: '6px',
                  border: `1px solid ${isActive ? btnColor : theme.border}`,
                  backgroundColor: isActive ? `${btnColor}20` : 'transparent', // 20 adiciona opacidade ao hex
                  color: isActive ? btnColor : theme.textMuted,
                  cursor: 'pointer',
                  fontWeight: 'bold',
                  fontSize: '0.8rem',
                  transition: '0.2s'
                }}
              >
                {sinal === 'TODOS' ? '🧾 TODOS' : sinal}
              </button>
            )
          })}
        </div>
      </div>

      {/* 4. GRÁFICO RECHARTS */}
      <ResponsiveContainer width="99%" height="100%" minWidth={1} minHeight={1}>
        <ComposedChart data={dadosProcessados} margin={{ top: 10, right: 0, left: -20, bottom: 10 }}>
          <CartesianGrid strokeDasharray="3 3" stroke={theme.border} vertical={false} />

          <XAxis
            dataKey="ativo"
            stroke={theme.textMuted}
            tick={{ fill: theme.textMuted, fontSize: 11, fontWeight: 'bold' }}
            angle={-45}
            textAnchor="end"
            height={60}
            minTickGap={15}
          />

          <YAxis
            yAxisId="left"
            orientation="left"
            stroke={theme.info}
            // TRAVA DE ESCALA: Garante que o eixo abranja sempre no mínimo do -2 ao +2
            domain={[(dataMin) => Math.min(dataMin, -2), (dataMax) => Math.max(dataMax, 2)]}
            tick={{ fill: theme.textMuted, fontSize: 12 }}
            label={{
              value: 'Z-Score (Desvios)',
              angle: -90,
              position: 'insideLeft',
              fill: theme.info,
              offset: -10,
              style: { fontWeight: 'bold', fontSize: '11px' }
            }}
          />

          <YAxis
            yAxisId="right"
            orientation="right"
            stroke={theme.venda}
            tick={{ fill: theme.textMuted, fontSize: 12 }}
            tickFormatter={(v) => `${Math.abs(v)}%`}
            label={{
              value: 'Risco de Cauda (VaR 99% Diário)',
              angle: 90,
              position: 'insideRight',
              fill: theme.venda,
              offset: -5,
              style: { fontWeight: 'bold', fontSize: '11px' }
            }}
          />

          <Tooltip
            contentStyle={{
              backgroundColor: theme.bg,
              borderColor: theme.border,
              borderRadius: '8px',
              color: theme.textMain,
              boxShadow: '0 10px 15px -3px rgba(0,0,0,0.5)'
            }}
            formatter={(value, name) => {
              if (name === "VaR 99% (Risco Máximo Esperado)") {
                return [`${Math.abs(value)}%`, name];
              }
              return [value, name];
            }}
            itemStyle={{ color: theme.textMain, fontWeight: 'bold', fontSize: '12px' }}
            labelStyle={{ color: theme.info, fontWeight: 'bold', marginBottom: '5px' }}
          />

          <Legend wrapperStyle={{ color: theme.textMain, fontSize: '12px', paddingTop: '15px' }} />

          <ReferenceLine
            yAxisId="left"
            y={-1.5}
            // FORÇA A EXIBIÇÃO: Obriga o Recharts a esticar o gráfico para exibir a linha
            ifOverflow="extendDomain"
            label={{ position: 'insideTopLeft', value: 'Gatilho de Compra', fill: theme.compra, fontSize: 11, fontWeight: '700' }}
            stroke={theme.compra}
            strokeDasharray="4 4"
            strokeWidth={1.5}
          />

          <Line
            yAxisId="left"
            type="monotone"
            dataKey="z_score"
            stroke={theme.info}
            name="Z-Score (Oportunidade)"
            strokeWidth={3}
            dot={{ fill: theme.bg, stroke: theme.info, strokeWidth: 2, r: 3 }}
            activeDot={{ r: 5, fill: theme.info, stroke: theme.bg }}
            isAnimationActive={false}
          />

          <Bar
            yAxisId="right"
            dataKey="risco_var_invertido"
            name="VaR 99% (Risco Máximo Esperado)"
            fill={theme.venda}
            opacity={0.15}
            radius={[0, 0, 10, 10]}
            isAnimationActive={false}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
});

