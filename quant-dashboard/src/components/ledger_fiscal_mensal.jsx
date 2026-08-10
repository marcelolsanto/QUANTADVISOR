
import { useState, useEffect } from 'react';
import { getAuditoriaFiscal } from '../services/api'; // Você precisará criar essa rota no Go
import { theme } from '../theme';

export const AccountingEngine = ({ usuarioId }) => {
  const [ledger, setLedger] = useState([]);
  
  useEffect(() => {
    // Busca os dados das tabelas que criamos (ledger_fiscal_mensal e lancamentos_contabeis)
    const carregarContabilidade = async () => {
      const res = await getAuditoriaFiscal(usuarioId);
      setLedger(res.data);
    };
    carregarContabilidade();
  }, [usuarioId]);

  return (
    <div style={{ backgroundColor: theme.cardBg, padding: '20px', borderRadius: '12px' }}>
      <p style={{ color: theme.textMuted }}>Registros carregados: {ledger.length}</p>
      <h3>📈 Apuração Mensal (DARF)</h3>
      {/* Tabela consumindo a tabela ledger_fiscal_mensal */}
    </div>
  );
};

