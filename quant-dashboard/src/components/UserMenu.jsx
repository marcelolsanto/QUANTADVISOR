
import { useState, useRef, useEffect } from 'react';
import { theme } from '../theme';

export const UserMenu = ({ nome, role, onLogout, setTelaAtiva, mercadoAtivo, setMercadoAtivo }) => {
  const [isOpen, setIsOpen] = useState(false);
  const menuRef = useRef(null);

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <div style={{ position: 'relative' }} ref={menuRef}>
      <div 
        onClick={() => setIsOpen(!isOpen)}
        style={{
          width: '42px', height: '42px', borderRadius: '50%',
          backgroundColor: theme.info, color: '#fff',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontWeight: 'bold', cursor: 'pointer', border: `2px solid ${theme.border}`,
          fontSize: '1.1rem', transition: 'transform 0.2s'
        }}
        onMouseOver={(e) => e.currentTarget.style.transform = 'scale(1.05)'}
        onMouseOut={(e) => e.currentTarget.style.transform = 'scale(1)'}
      >
        {nome ? nome.charAt(0).toUpperCase() : 'U'}
      </div>

      {isOpen && (
        <div style={{
          position: 'absolute', top: '55px', right: 0,
          backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`,
          borderRadius: '8px', padding: '10px', width: '240px',
          boxShadow: '0 10px 25px -5px rgba(0,0,0,0.5)', zIndex: 2000
        }}>
          <div style={{ padding: '8px', borderBottom: `1px solid ${theme.border}`, marginBottom: '5px' }}>
            <div style={{ fontSize: '0.9rem', fontWeight: 'bold', color: theme.textMain }}>{nome}</div>
            <div style={{ fontSize: '0.75rem', color: theme.info, marginTop: '2px' }}>{role}</div>
          </div>
          
          {mercadoAtivo && setMercadoAtivo && (
            <div style={{ padding: '10px 5px', borderBottom: `1px solid ${theme.border}`, marginBottom: '5px' }}>
              <div style={{ fontSize: '0.75rem', color: theme.textMuted, marginBottom: '8px', fontWeight: 'bold', textTransform: 'uppercase' }}>
                Jurisdição de Operação:
              </div>
              <div style={{ display: 'flex', gap: '5px' }}>
                <button
                  onClick={() => { setMercadoAtivo('BRL'); setIsOpen(false); }}
                  style={{
                    flex: 1, padding: '8px 5px', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.8rem',
                    backgroundColor: mercadoAtivo === 'BRL' ? 'rgba(16, 185, 129, 0.15)' : 'transparent',
                    color: mercadoAtivo === 'BRL' ? theme.compra : theme.textMuted,
                    border: `1px solid ${mercadoAtivo === 'BRL' ? theme.compra : theme.border}`,
                    transition: '0.2s'
                  }}
                >
                  🇧🇷 B3
                </button>
                <button
                  onClick={() => { setMercadoAtivo('USD'); setIsOpen(false); }}
                  style={{
                    flex: 1, padding: '8px 5px', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.8rem',
                    backgroundColor: mercadoAtivo === 'USD' ? 'rgba(59, 130, 246, 0.15)' : 'transparent',
                    color: mercadoAtivo === 'USD' ? theme.info : theme.textMuted,
                    border: `1px solid ${mercadoAtivo === 'USD' ? theme.info : theme.border}`,
                    transition: '0.2s'
                  }}
                >
                  🇺🇸 Wall St.
                </button>
              </div>
            </div>
          )}
          
          <button 
            onClick={() => { 
                setTelaAtiva('GESTAO'); 
                setIsOpen(false); 
            }}
            style={{
              width: '100%', background: 'none', border: 'none', padding: '10px',
              color: theme.textMain, cursor: 'pointer', textAlign: 'left', borderRadius: '4px',
              fontWeight: '500'
            }}
            onMouseOver={(e) => e.target.style.backgroundColor = 'rgba(255,255,255,0.05)'}
            onMouseOut={(e) => e.target.style.backgroundColor = 'transparent'}
          >
            👤 Editar Perfil
          </button>

          <button
            onClick={() => {
              window.dispatchEvent(new CustomEvent('abrirConfigMotor', {
                detail: {
                  id: Number(localStorage.getItem('@QuantAdvisor:user_id_web')),
                  nome: nome
                }
              }));
              setIsOpen(false);
            }}
            style={{
              width: '100%', background: 'none', border: 'none', padding: '10px',
              color: theme.info, cursor: 'pointer', textAlign: 'left', borderRadius: '4px',
              fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '8px'
            }}
            onMouseOver={(e) => e.target.style.backgroundColor = 'rgba(59, 130, 246, 0.1)'}
            onMouseOut={(e) => e.target.style.backgroundColor = 'transparent'}
          >
            ⚙️ Calibrar Meu Robô
          </button>

          <a
            href="/download/QuantAdvisor.apk"
            download="QuantAdvisor.apk"
            onClick={() => setIsOpen(false)}
            style={{
              width: '100%', background: 'rgba(16, 185, 129, 0.1)', border: `1px solid ${theme.compra}`, padding: '10px',
              color: theme.compra, cursor: 'pointer', textAlign: 'left', borderRadius: '4px',
              fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '8px', textDecoration: 'none',
              boxSizing: 'border-box', marginTop: '4px', marginBottom: '4px'
            }}
          >
            📱 Instalar App Android (.APK)
          </a>

          <button 
            onClick={onLogout}
            style={{
              width: '100%', background: 'none', border: 'none', padding: '10px',
              color: theme.venda, cursor: 'pointer', fontWeight: 'bold', textAlign: 'left', borderRadius: '4px'
            }}
            onMouseOver={(e) => e.target.style.backgroundColor = 'rgba(239, 68, 68, 0.1)'}
            onMouseOut={(e) => e.target.style.backgroundColor = 'transparent'}
          >
            Sair 🚪
          </button>
        </div>
      )}
    </div>
  );
};

