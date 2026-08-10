
import React, { createContext, useState, useContext } from 'react';

// Cria o contexto
const MarketContext = createContext();

// Provedor que vai abraçar o nosso App
export const MarketProvider = ({ children }) => {
    // Começa sempre na B3 (BRL). A outra opção será 'USD' (Wall Street)
    const [market, setMarket] = useState('BRL');

    return (
        <MarketContext.Provider value={{ market, setMarket }}>
            {children}
        </MarketContext.Provider>
    );
};

// Hook customizado para facilitar o uso nas outras telas
export const useMarket = () => useContext(MarketContext);

