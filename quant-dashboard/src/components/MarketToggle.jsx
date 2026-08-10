
import React from 'react';
import { useMarket } from '../contexts/MarketContext';
import { theme } from '../theme';
import { toast } from 'sonner';

export const MarketToggle = () => {
    const { market, setMarket } = useMarket();
    const isBRL = market === 'BRL';

    const handleToggle = () => {
        const novoMercado = isBRL ? 'USD' : 'BRL';
        setMarket(novoMercado);

        if (novoMercado === 'BRL') {
            toast.success("🇧🇷 Jurisdição B3 (Brasil) Ativada", {
                description: "Visão consolidada em BRL | Regulado por CVM & B3",
                duration: 3000
            });
        } else {
            toast.info("🇺🇸 Jurisdição NYSE / Wall St. Ativada", {
                description: "Visão em USD (Dólar) | Regulado por SEC & FINRA",
                duration: 3000
            });
        }
    };

    return (
        <div style={{
            display: 'flex',
            alignItems: 'center',
            backgroundColor: 'rgba(0,0,0,0.3)',
            borderRadius: '30px',
            padding: '4px',
            border: `1px solid ${theme.border}`,
            cursor: 'pointer',
            width: '240px',
            minHeight: '48px', // Touch target seguro para mobile
            position: 'relative',
            backdropFilter: 'blur(10px)',
            boxShadow: '0 4px 12px rgba(0, 0, 0, 0.2)'
        }}
        onClick={handleToggle}
        className="touch-target"
        >
            {/* Pílula Deslizante com Transição Suave */}
            <div style={{
                position: 'absolute',
                top: '4px',
                left: isBRL ? '4px' : '120px',
                width: '116px',
                height: 'calc(100% - 8px)',
                backgroundColor: isBRL ? theme.compra : '#3b82f6', // Verde B3, Azul EUA
                borderRadius: '26px',
                transition: 'left 0.35s cubic-bezier(0.4, 0, 0.2, 1), background-color 0.35s ease',
                zIndex: 1,
                boxShadow: isBRL ? '0 0 12px rgba(16, 185, 129, 0.4)' : '0 0 12px rgba(59, 130, 246, 0.4)'
            }} />

            {/* Opção Brasil */}
            <div style={{
                flex: 1,
                textAlign: 'center',
                padding: '6px 0',
                fontSize: '0.85rem',
                fontWeight: 'bold',
                color: isBRL ? '#fff' : theme.textMuted,
                zIndex: 2,
                transition: 'color 0.3s ease'
            }}>
                🇧🇷 B3 (BRL)
            </div>

            {/* Opção EUA */}
            <div style={{
                flex: 1,
                textAlign: 'center',
                padding: '6px 0',
                fontSize: '0.85rem',
                fontWeight: 'bold',
                color: !isBRL ? '#fff' : theme.textMuted,
                zIndex: 2,
                transition: 'color 0.3s ease'
            }}>
                🇺🇸 NYSE (USD)
            </div>
        </div>
    );
};

