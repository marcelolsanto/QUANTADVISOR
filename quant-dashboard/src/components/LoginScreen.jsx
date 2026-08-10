
import { useState, useEffect } from 'react';
import { realizarLoginWeb, solicitarCadastro, validarCadastro, realizarLogoutWeb } from '../services/api';
import { theme } from '../theme';
import { toast } from 'sonner';

export const LoginScreen = ({ onLoginSuccess }) => {
  // Estado da Tela: 'LOGIN' | 'CADASTRO' | 'VALIDACAO'
  const [view, setView] = useState('LOGIN');

  // Estados dos Formulários
  const [login, setLogin] = useState('');
  const [senha, setSenha] = useState('');
  const [erro, setErro] = useState('');
  const [sucesso, setSucesso] = useState('');
  const [loading, setLoading] = useState(false);

  const [novoUsuario, setNovoUsuario] = useState({
    nome_cliente: '',
    email: '',
    whatsapp: '',
    login: '',
    senha: '',
    perfil_risco: 'Conservador',
    saldo_caixa: ''
  });

  const [codigoOTP, setCodigoOTP] = useState('');
  // 1. Estado adicionado para guardar o código que vem do backend
  const [codigoDebug, setCodigoDebug] = useState('');

  // ==============================================================
  // 🛡️ REGRA DE SEGURANÇA: Sempre inicia o sistema deslogado
  // ==============================================================
  useEffect(() => {
    realizarLogoutWeb(); // Limpa tokens e perfis do localStorage
  }, []);

  const handleLogin = async (e) => {
    e.preventDefault();
    setErro('');
    setLoading(true);

    const res = await realizarLoginWeb(login, senha);
    if (res.sucesso) {
      toast.success(`Bem-vindo, ${res.dados?.nome || login}!`, {
        description: `Sessão iniciada como ${res.dados?.role || 'CLIENTE'}`,
        duration: 4000
      });
      onLoginSuccess(res.dados);
    } else {
      toast.error("Falha no Acesso", {
        description: res.erro || "Login ou senha incorretos."
      });
      setErro(res.erro);
      setLoading(false);
    }
  };

  const handleSolicitarCadastro = async (e) => {
    e.preventDefault();
    setErro('');
    setLoading(true);
    try {
      const payloadCadastro = {
        ...novoUsuario,
        saldo_caixa: Number(novoUsuario.saldo_caixa)
      };
      const res = await solicitarCadastro(payloadCadastro);

      // 2. Salva o código recebido do backend para exibir na tela
      if (res.data && res.data.codigo_teste) {
        setCodigoDebug(res.data.codigo_teste);
      }

      setSucesso(res.data.mensagem);
      setView('VALIDACAO');
    } catch (err) {
      setErro(err.response?.data?.erro || 'Erro ao conectar com o servidor.');
    } finally {
      setLoading(false);
    }
  };

  const handleValidarCodigo = async (e) => {
    e.preventDefault();
    setErro('');
    setLoading(true);
    try {
      const res = await validarCadastro({ email: novoUsuario.email, codigo: codigoOTP });
      setSucesso(res.data.mensagem);
      // Reseta tudo e volta para o login para o utilizador entrar
      setTimeout(() => {
        setView('LOGIN');
        setLogin(novoUsuario.login);
        setSucesso('');
        setCodigoDebug(''); // Limpa o código da tela
      }, 2000);
    } catch (err) {
      setErro(err.response?.data?.erro || 'Código incorreto.');
    } finally {
      setLoading(false);
    }
  };

  const inputStyle = {
    width: '100%', padding: '15px', borderRadius: '8px',
    backgroundColor: theme.bg, color: theme.textMain,
    border: `1px solid ${theme.border}`, outline: 'none',
    fontSize: '1rem', marginBottom: '20px'
  };

  const buttonStyle = {
    width: '100%', padding: '15px', borderRadius: '8px', border: 'none',
    backgroundColor: loading ? theme.border : theme.info, color: '#fff',
    fontWeight: 'bold', fontSize: '1rem', cursor: loading ? 'wait' : 'pointer',
    transition: '0.2s', letterSpacing: '1px'
  };

  return (
    <div style={{ display: 'flex', height: '100vh', width: '100vw', backgroundColor: theme.bg, alignItems: 'center', justifyContent: 'center' }}>
      <div style={{ backgroundColor: theme.cardBg, padding: '40px', borderRadius: '12px', border: `1px solid ${theme.border}`, width: '100%', maxWidth: '420px', boxShadow: `0 0 30px -10px rgba(59, 130, 246, 0.2)` }}>

        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', width: '100%', textAlign: 'center', margin: '0 auto' }} className="mb-6">
          <img
            src="/public/logo-completa.png"
            alt="Logo QuantAdvisor"
            style={{ display: 'block', margin: '0 auto 8px auto', maxWidth: '220px', width: '100%', height: 'auto' }}
            className="object-contain mix-blend-screen"
          />
          <p style={{ margin: '0 auto', textAlign: 'center' }} className="text-gray-400 text-sm">
            Gestão Patrimonial e IA Institucional
          </p><br></br>
        </div>

        {erro && <div style={{ padding: '10px', backgroundColor: 'rgba(239, 68, 68, 0.1)', color: theme.venda, border: `1px solid ${theme.venda}`, borderRadius: '6px', textAlign: 'center', marginBottom: '20px', fontWeight: 'bold' }}>⚠️ {erro}</div>}
        {sucesso && <div style={{ padding: '10px', backgroundColor: 'rgba(16, 185, 129, 0.1)', color: theme.compra, border: `1px solid ${theme.compra}`, borderRadius: '6px', textAlign: 'center', marginBottom: '20px', fontWeight: 'bold' }}>✅ {sucesso}</div>}

        {/* ===================== VIEW DE LOGIN ===================== */}
        {view === 'LOGIN' && (
          <form onSubmit={handleLogin}>
            <label style={{ display: 'block', color: theme.textMuted, marginBottom: '8px', fontWeight: 'bold', fontSize: '0.85rem' }}>Login</label>
            <input type="text" value={login} onChange={(e) => setLogin(e.target.value)} style={inputStyle} placeholder="Seu utilizador" required autoComplete="username" />

            <label style={{ display: 'block', color: theme.textMuted, marginBottom: '8px', fontWeight: 'bold', fontSize: '0.85rem' }}>Senha</label>
            <input type="password" value={senha} onChange={(e) => setSenha(e.target.value)} style={inputStyle} placeholder="••••••••" required autoComplete="current-password" />

            <button type="submit" disabled={loading} style={buttonStyle}>
              {loading ? 'AUTENTICANDO...' : 'ENTRAR NO SISTEMA'}
            </button>

            <p style={{ textAlign: 'center', marginTop: '20px', fontSize: '0.9rem', color: theme.textMuted }}>
              Novo por aqui? <span onClick={() => { setView('CADASTRO'); setErro(''); }} style={{ color: theme.info, cursor: 'pointer', fontWeight: 'bold', textDecoration: 'underline' }}>Abra a sua conta.</span>
            </p>
          </form>
        )}

        {/* ===================== VIEW DE CADASTRO ===================== */}
        {view === 'CADASTRO' && (
          <form onSubmit={handleSolicitarCadastro}>
            <input type="text" placeholder="Nome Completo" value={novoUsuario.nome_cliente} onChange={(e) => setNovoUsuario({ ...novoUsuario, nome_cliente: e.target.value })} style={inputStyle} required />
            <input type="email" placeholder="E-mail" value={novoUsuario.email} onChange={(e) => setNovoUsuario({ ...novoUsuario, email: e.target.value })} style={inputStyle} required />
            <input type="tel" placeholder="Telemóvel (WhatsApp com indicativo)" value={novoUsuario.whatsapp} onChange={(e) => setNovoUsuario({ ...novoUsuario, whatsapp: e.target.value })} style={inputStyle} required />
            <input type="text" placeholder="Nome de Utilizador (Login)" value={novoUsuario.login} onChange={(e) => setNovoUsuario({ ...novoUsuario, login: e.target.value })} style={inputStyle} required />
            <input type="password" placeholder="Crie uma Senha" value={novoUsuario.senha} onChange={(e) => setNovoUsuario({ ...novoUsuario, senha: e.target.value })} style={inputStyle} required />

            <div style={{ display: 'flex', gap: '15px' }}>
              <div style={{ flex: 1 }}>
                <select
                  value={novoUsuario.perfil_risco}
                  onChange={(e) => setNovoUsuario({ ...novoUsuario, perfil_risco: e.target.value })}
                  style={{ ...inputStyle, cursor: 'pointer' }}
                  required
                >
                  <option value="Conservador">Conservador</option>
                  <option value="Moderado">Moderado</option>
                  <option value="Arrojado">Arrojado</option>
                  <option value="Agressivo">Agressivo</option>
                </select>
              </div>

              <div style={{ flex: 1 }}>
                <input
                  type="number"
                  placeholder="Capital (R$)"
                  value={novoUsuario.saldo_caixa}
                  onChange={(e) => setNovoUsuario({ ...novoUsuario, saldo_caixa: e.target.value })}
                  style={inputStyle}
                  min="0"
                  step="0.01"
                  required
                />
              </div>
            </div>

            <button type="submit" disabled={loading} style={buttonStyle}>
              {loading ? 'ENVIANDO CÓDIGO...' : 'RECEBER CÓDIGO NO WHATSAPP'}
            </button>

            <p style={{ textAlign: 'center', marginTop: '20px', fontSize: '0.9rem', color: theme.textMuted }}>
              Já tem conta? <span onClick={() => { setView('LOGIN'); setErro(''); }} style={{ color: theme.info, cursor: 'pointer', fontWeight: 'bold', textDecoration: 'underline' }}>Fazer Login.</span>
            </p>
          </form>
        )}

        {/* ===================== VIEW DE VALIDAÇÃO (OTP) ===================== */}
        {view === 'VALIDACAO' && (
          <form onSubmit={handleValidarCodigo}>
            <div style={{ textAlign: 'center', marginBottom: '20px', color: theme.textMuted, fontSize: '0.9rem' }}>
              Enviamos um código de 6 dígitos para o WhatsApp<br /><strong style={{ color: theme.textMain }}>{novoUsuario.whatsapp}</strong>.
            </div>

            {/* 3. CAIXA DE MODO DESENVOLVEDOR: Apresenta o código OTP na tela */}
            {codigoDebug && (
              <div style={{ backgroundColor: 'rgba(245, 158, 11, 0.1)', border: `1px dashed ${theme.alerta}`, color: theme.alerta, padding: '15px', borderRadius: '8px', textAlign: 'center', marginBottom: '25px' }}>
                <div style={{ fontSize: '0.8rem', textTransform: 'uppercase', fontWeight: 'bold', marginBottom: '5px' }}>🛠️ Modo Desenvolvedor</div>
                O código de verificação é: <span style={{ fontSize: '1.5rem', fontWeight: 'bold', letterSpacing: '4px', marginLeft: '8px' }}>{codigoDebug}</span>
              </div>
            )}

            <input
              type="text" placeholder="Digite o Código OTP"
              value={codigoOTP} onChange={(e) => setCodigoOTP(e.target.value)}
              style={{ ...inputStyle, textAlign: 'center', fontSize: '1.5rem', letterSpacing: '10px', fontWeight: 'bold' }}
              maxLength="6" required
            />

            <button type="submit" disabled={loading} style={{ ...buttonStyle, backgroundColor: theme.compra }}>
              {loading ? 'VERIFICANDO...' : 'ATIVAR CONTA'}
            </button>

            <p style={{ textAlign: 'center', marginTop: '20px', fontSize: '0.9rem', color: theme.textMuted }}>
              <span onClick={() => { setView('CADASTRO'); setErro(''); setCodigoDebug(''); }} style={{ color: theme.info, cursor: 'pointer', fontWeight: 'bold', textDecoration: 'underline' }}>Voltar e corrigir telemóvel</span>
            </p>
          </form>
        )}

      </div>
    </div>
  );
};

