
import { useState } from 'react';
import { realizarCambio } from '../services/api';
import { theme } from '../theme';

export const CambioModal = ({ onClose, usuarioId, saldoBRL, saldoUSD, cotacaoDolar }) => {
  const [direcao, setDirecao] = useState('BRL_PARA_USD');
  const [valorOrigem, setValorOrigem] = useState('');
  const [status, setStatus] = useState({ loading: false, erro: '', sucesso: '' });

  const valorNum = parseFloat(valorOrigem) || 0;

  // Parâmetros comerciais
  const spread = 0.015; // 1.5%
  const iof = 0.0038;   // 0.38%

  // Cálculos dinâmicos em tempo real
  let cotacaoEfetiva;
  let valorIof;
  let valorDestino;

  if (direcao === 'BRL_PARA_USD') {
    valorIof = valorNum * iof;
    const valorBaseBRL = valorNum - valorIof;
    cotacaoEfetiva = cotacaoDolar * (1 + spread);
    valorDestino = valorBaseBRL / cotacaoEfetiva;
  } else {
    cotacaoEfetiva = cotacaoDolar * (1 - spread);
    const valorBrutoBRL = valorNum * cotacaoEfetiva;
    valorIof = valorBrutoBRL * iof;
    valorDestino = valorBrutoBRL - valorIof;
  }

  const formatarBRL = (v) => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v);
  const formatarUSD = (v) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(v);

  const executarRemessa = async () => {
    if (valorNum <= 0) return setStatus({ ...status, erro: 'Digite um valor válido.' });
    if (direcao === 'BRL_PARA_USD' && valorNum > saldoBRL) return setStatus({ ...status, erro: 'Saldo em Reais insuficiente.' });
    if (direcao === 'USD_PARA_BRL' && valorNum > saldoUSD) return setStatus({ ...status, erro: 'Saldo em Dólares insuficiente.' });

    setStatus({ loading: true, erro: '', sucesso: '' });
    try {
      const res = await realizarCambio({
        usuario_id: usuarioId,
        direcao: direcao,
        valor_origem: valorNum
      });
      setStatus({ loading: false, erro: '', sucesso: res.data.mensagem });
      setTimeout(() => {
        onClose();
        window.dispatchEvent(new Event('carrinhoAtualizado')); // Pode aproveitar o mesmo listener para atualizar a tela
      }, 2000);
    } catch (err) {
      setStatus({ loading: false, erro: err.response?.data?.erro || 'Erro no servidor', sucesso: '' });
    }
  };

  return (
    <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 5000 }}>
      <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '30px', borderRadius: '12px', width: '95%', maxWidth: '500px' }}>
        
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
          <h2 style={{ margin: 0, color: theme.textMain }}>💱 Câmbio Global</h2>
          <button onClick={onClose} disabled={status.loading} style={{ background: 'none', border: 'none', fontSize: '1.2rem', cursor: 'pointer', color: theme.textMuted }}>✖</button>
        </div>

        <div style={{ display: 'flex', gap: '10px', marginBottom: '20px' }}>
          <button onClick={() => setDirecao('BRL_PARA_USD')} style={{ flex: 1, padding: '10px', borderRadius: '6px', border: `1px solid ${direcao === 'BRL_PARA_USD' ? theme.compra : theme.border}`, backgroundColor: direcao === 'BRL_PARA_USD' ? 'rgba(16, 185, 129, 0.1)' : theme.bg, color: direcao === 'BRL_PARA_USD' ? theme.compra : theme.textMuted, fontWeight: 'bold', cursor: 'pointer' }}>
            🇧🇷 Enviar para EUA
          </button>
          <button onClick={() => setDirecao('USD_PARA_BRL')} style={{ flex: 1, padding: '10px', borderRadius: '6px', border: `1px solid ${direcao === 'USD_PARA_BRL' ? theme.info : theme.border}`, backgroundColor: direcao === 'USD_PARA_BRL' ? 'rgba(59, 130, 246, 0.1)' : theme.bg, color: direcao === 'USD_PARA_BRL' ? theme.info : theme.textMuted, fontWeight: 'bold', cursor: 'pointer' }}>
            🇺🇸 Repatriar p/ BR
          </button>
        </div>

        <div style={{ marginBottom: '15px' }}>
          <label style={{ display: 'block', color: theme.textMuted, marginBottom: '5px', fontSize: '0.9rem' }}>
            Valor da Remessa ({direcao === 'BRL_PARA_USD' ? 'BRL' : 'USD'}):
            <span style={{ marginLeft: '10px', color: theme.textMain, fontWeight: 'bold' }}>
              (Disponível: {direcao === 'BRL_PARA_USD' ? formatarBRL(saldoBRL) : formatarUSD(saldoUSD)})
            </span>
          </label>
          <input type="number" value={valorOrigem} onChange={(e) => setValorOrigem(e.target.value)} placeholder="0.00" style={{ width: '100%', padding: '12px', fontSize: '1.2rem', borderRadius: '6px', border: `1px solid ${theme.border}`, backgroundColor: theme.bg, color: theme.textMain }} />
        </div>

        <div style={{ backgroundColor: theme.bg, padding: '15px', borderRadius: '8px', border: `1px solid ${theme.border}`, marginBottom: '20px', fontSize: '0.9rem' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
            <span style={{ color: theme.textMuted }}>Dólar Comercial:</span>
            <span style={{ color: theme.textMain }}>{formatarBRL(cotacaoDolar)}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
            <span style={{ color: theme.textMuted }}>Spread da Corretora (1.5%):</span>
            <span style={{ color: theme.venda }}> Inclusivo no VET</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
            <span style={{ color: theme.textMuted }}>IOF (0.38%):</span>
            <span style={{ color: theme.venda }}>-{formatarBRL(valorIof)}</span>
          </div>
          <div style={{ display: 'flex', justifyContent: 'space-between', borderTop: `1px dashed ${theme.border}`, paddingTop: '8px', marginTop: '8px', fontWeight: 'bold' }}>
            <span style={{ color: theme.info }}>VET (Valor Efetivo Total):</span>
            <span style={{ color: theme.info }}>{formatarBRL(cotacaoEfetiva)}</span>
          </div>
        </div>

        <div style={{ textAlign: 'center', marginBottom: '20px' }}>
          <div style={{ color: theme.textMuted, fontSize: '0.9rem', marginBottom: '5px' }}>Você receberá na conta {direcao === 'BRL_PARA_USD' ? 'americana' : 'brasileira'}</div>
          <div style={{ fontSize: '2rem', fontWeight: 'bold', color: direcao === 'BRL_PARA_USD' ? theme.compra : theme.info }}>
            {direcao === 'BRL_PARA_USD' ? formatarUSD(valorDestino) : formatarBRL(valorDestino)}
          </div>
        </div>

        {status.erro && <div style={{ color: theme.venda, textAlign: 'center', marginBottom: '15px' }}>⚠️ {status.erro}</div>}
        {status.sucesso && <div style={{ color: theme.compra, textAlign: 'center', marginBottom: '15px' }}>✅ {status.sucesso}</div>}

        <button onClick={executarRemessa} disabled={status.loading || valorNum <= 0} style={{ width: '100%', padding: '15px', borderRadius: '6px', border: 'none', backgroundColor: (status.loading || valorNum <= 0) ? theme.border : theme.info, color: '#fff', fontWeight: 'bold', cursor: (status.loading || valorNum <= 0) ? 'not-allowed' : 'pointer' }}>
          {status.loading ? 'PROCESSANDO...' : 'CONFIRMAR CÂMBIO'}
        </button>

      </div>
    </div>
  );
};

