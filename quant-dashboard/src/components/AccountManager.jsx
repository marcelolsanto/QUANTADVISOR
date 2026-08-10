
import { useState, useEffect } from 'react';
import { getUsuarios, criarUsuario, deletarUsuario, editarUsuario, togglePiloto } from '../services/api';
import { theme } from '../theme';
import { ConfigMotorModal } from './ConfigMotorModal';

const PERFIS_PADRAO = ['Conservador', 'Moderado', 'Arrojado', 'Agressivo'];

export const AccountManager = ({ onSelecionarConta, isGestor }) => {
  const [contas, setContas] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadingToggle, setLoadingToggle] = useState(null);

  const [busca, setBusca] = useState('');
  const [filtroPerfil, setFiltroPerfil] = useState('TODOS');

  const formInicial = { nome_cliente: '', email: '', whatsapp: '', login: '', senha: '', perfil_risco: 'Conservador', saldo_inicial: 100000, role: 'CLIENTE', piloto_automatico: false };
  const [novaConta, setNovaConta] = useState(formInicial);
  const [statusMsg, setStatusMsg] = useState({ tipo: '', texto: '' });

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modoEdicao, setModoEdicao] = useState(false);
  const [idEdicao, setIdEdicao] = useState(null);

  const [configMotorId, setConfigMotorId] = useState(null);
  const [configMotorNome, setConfigMotorNome] = useState('');

  const carregarDados = async () => {
    setLoading(true);
    try {
      const resContas = await getUsuarios().catch(() => ({ data: [] }));
      // 🛡️ PROTEÇÃO CONTRA NULL
      setContas(resContas.data || []);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { carregarDados(); }, []);

  const handleTogglePiloto = async (id, estadoAtual) => {
    if (!isGestor) return alert("Apenas Gestores podem alterar a permissão da IA.");
    setLoadingToggle(id);
    try {
      await togglePiloto({ usuario_id: id, estado: !estadoAtual });
      await carregarDados(); 
    } catch (err) {
      alert("Erro ao alterar o Piloto Automático.");
    } finally {
      setLoadingToggle(null);
    }
  };

  const abrirModalCadastro = () => {
    setModoEdicao(false);
    setIdEdicao(null);
    setNovaConta({ ...formInicial });
    setStatusMsg({ tipo: '', texto: '' });
    setIsModalOpen(true);
  };

  const iniciarEdicao = (conta) => {
    setModoEdicao(true);
    setIdEdicao(conta.usuario_id || conta.id);
    setNovaConta({
      nome_cliente: conta.nome_cliente || conta.nome || '',
      email: conta.email || '',
      whatsapp: conta.whatsapp || '',
      login: conta.login || '',
      senha: '', 
      perfil_risco: conta.perfil_risco || 'Conservador',
      saldo_inicial: conta.saldo_brl !== undefined ? conta.saldo_brl : (conta.saldo_disponivel || 0),
      role: conta.role || 'CLIENTE',
      piloto_automatico: conta.piloto_automatico === true 
    });
    setStatusMsg({ tipo: '', texto: '' });
    setIsModalOpen(true);
  };

  const fecharModal = () => setIsModalOpen(false);

  const handleSalvarConta = async (e) => {
    e.preventDefault();
    if (!novaConta.nome_cliente || !novaConta.login) return setStatusMsg({ tipo: 'erro', texto: 'Nome e Login são obrigatórios.' });
    if (!modoEdicao && !novaConta.senha) return setStatusMsg({ tipo: 'erro', texto: 'A Senha é obrigatória.' });

    try {
      if (modoEdicao) {
        await editarUsuario({ id: idEdicao, ...novaConta });
        setStatusMsg({ tipo: 'sucesso', texto: 'Dados atualizados com sucesso!' });
      } else {
        await criarUsuario({ ...novaConta, saldo_inicial: parseFloat(novaConta.saldo_inicial) });
        setStatusMsg({ tipo: 'sucesso', texto: 'Conta criada com sucesso!' });
      }
      carregarDados();
      setTimeout(() => fecharModal(), 1500);
    } catch (err) {
      setStatusMsg({ tipo: 'erro', texto: err.response?.data?.erro || 'Erro ao processar a requisição.' });
    }
  };

  const handleDelete = async (id, nome) => {
    if (!window.confirm(`ATENÇÃO: Deseja realmente excluir o usuário ${nome}?`)) return;
    try {
      await deletarUsuario({ id });
      carregarDados();
    } catch (err) { alert("Erro ao excluir usuário"); }
  };

  // 🛡️ PROTEÇÃO NA FILTRAGEM
  const contasFiltradas = (Array.isArray(contas) ? contas : []).filter(c => {
    const nomeBase = c.nome_cliente || c.nome || '';
    const matchBusca = nomeBase.toLowerCase().includes(busca.toLowerCase()) || (c.login && c.login.toLowerCase().includes(busca.toLowerCase()));
    const matchPerfil = filtroPerfil === 'TODOS' || c.perfil_risco === filtroPerfil;
    return matchBusca && matchPerfil;
  });

  const formatarData = (dataString) => {
    if (!dataString) return '--/--/----';
    return new Date(dataString).toLocaleDateString('pt-BR');
  };

  const inputStyle = { width: '100%', padding: '10px', borderRadius: '4px', backgroundColor: theme.bg, color: theme.textMain, border: `1px solid ${theme.border}`, outline: 'none' };
  const labelStyle = { display: 'block', marginBottom: '5px', fontWeight: 'bold', color: theme.textMuted, fontSize: '0.85rem' };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '20px', position: 'relative' }}>
      
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={{ color: theme.textMain, margin: 0, fontSize: '1.8rem' }}>🏢 Central de Gestão (CRM)</h2>
        {isGestor && (
          <button onClick={abrirModalCadastro} style={{ padding: '10px 20px', borderRadius: '6px', backgroundColor: theme.info, color: '#fff', border: 'none', cursor: 'pointer', fontWeight: 'bold', display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.9rem', transition: '0.2s' }}>
            ➕ Cadastrar Usuário
          </button>
        )}
      </div>

      <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, padding: '25px', borderRadius: '8px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
          <h3 style={{ margin: 0, color: theme.textMain }}>📋 Carteira de Usuários</h3>
          <div style={{ display: 'flex', gap: '10px' }}>
            <input type="text" placeholder="Buscar nome ou login..." value={busca} onChange={(e) => setBusca(e.target.value)} style={{ ...inputStyle, width: '250px', padding: '8px 12px', borderRadius: '6px' }} />
            <select value={filtroPerfil} onChange={(e) => setFiltroPerfil(e.target.value)} style={{ ...inputStyle, width: '180px', padding: '8px 12px', borderRadius: '6px', cursor: 'pointer' }}>
              <option value="TODOS">Todos os Perfis</option>
              {PERFIS_PADRAO.map(p => <option key={p} value={p}>{p}</option>)}
            </select>
          </div>
        </div>

        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', color: theme.textMain, fontSize: '0.9rem' }}>
            <thead>
              <tr style={{ borderBottom: `2px solid ${theme.border}` }}>
                <th style={{ padding: '12px', color: theme.textMuted }}>Nome / Login</th>
                <th style={{ padding: '12px', color: theme.textMuted }}>Perfil</th>
                <th style={{ padding: '12px', color: theme.textMuted }}>Contato</th>
                <th style={{ padding: '12px', color: theme.textMuted }}>Cadastro</th>
                <th style={{ padding: '12px', color: theme.textMuted, textAlign: 'right' }}>Ações</th>
              </tr>
            </thead>
            <tbody>
              {contasFiltradas.length === 0 ? (
                <tr><td colSpan="6" style={{ padding: '30px', textAlign: 'center', color: theme.textMuted }}>Nenhum usuário encontrado.</td></tr>
              ) : (
                contasFiltradas.map(conta => {
                  const idExibicao = conta.usuario_id || conta.id;
                  const nomeExibicao = conta.nome_cliente || conta.nome;

                  return (
                    <tr key={idExibicao} style={{ borderBottom: `1px solid ${theme.border}` }} onMouseOver={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.02)'} onMouseOut={(e) => e.currentTarget.style.backgroundColor = 'transparent'}>
                      <td style={{ padding: '15px 12px' }}>
                        <div style={{ fontWeight: 'bold', display: 'flex', alignItems: 'center' }}>
                          {nomeExibicao}
                          {conta.role === 'GESTOR' && <span style={{ marginLeft: '8px', fontSize: '0.65rem', backgroundColor: theme.alerta, color: '#fff', padding: '2px 6px', borderRadius: '4px' }}>GESTOR</span>}
                        </div>
                        <div style={{ fontSize: '0.8rem', color: theme.textMuted }}>@{conta.login || `user_${idExibicao}`}</div>
                      </td>
                      <td style={{ padding: '15px 12px' }}><span style={{ padding: '4px 8px', borderRadius: '4px', backgroundColor: 'rgba(255,255,255,0.05)', border: `1px solid ${theme.border}`, fontSize: '0.75rem' }}>{conta.perfil_risco}</span></td>
                      <td style={{ padding: '15px 12px', fontSize: '0.85rem' }}>
                        {conta.email && <div style={{ marginBottom: '4px' }}>📧 {conta.email}</div>}
                        {conta.whatsapp && <div style={{ color: theme.textMuted }}>📱 {conta.whatsapp}</div>}
                      </td>
                      <td style={{ padding: '15px 12px', fontSize: '0.85rem', color: theme.textMuted }}>📅 {formatarData(conta.data_cadastro)}</td>
                      
                      <td style={{ padding: '15px 12px', textAlign: 'right' }}>
                        <button onClick={() => onSelecionarConta(idExibicao, conta.perfil_risco)} style={{ padding: '8px 12px', backgroundColor: theme.info, color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.8rem', marginRight: '8px' }} title="Acessar Terminal">➔</button>
                        {isGestor && (
                          <>
                            <button 
                              onClick={() => { setConfigMotorId(idExibicao); setConfigMotorNome(nomeExibicao); }} 
                              style={{ background: 'none', border: `1px solid ${theme.info}`, color: theme.info, padding: '7px 12px', borderRadius: '4px', cursor: 'pointer', marginRight: '8px' }}
                              title="Calibrar Motor Quantitativo"
                            >
                              ⚙️
                            </button>
                            <button onClick={() => iniciarEdicao(conta)} style={{ background: 'none', border: `1px solid ${theme.alerta}`, color: theme.alerta, padding: '7px 12px', borderRadius: '4px', cursor: 'pointer', marginRight: '8px' }} title="Editar">✏️</button>
                            <button onClick={() => handleDelete(idExibicao, nomeExibicao)} style={{ background: 'none', border: `1px solid ${theme.venda}`, color: theme.venda, padding: '7px 12px', borderRadius: '4px', cursor: 'pointer' }} title="Deletar">🗑️</button>
                          </>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {isModalOpen && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 3000 }}>
          <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.info}`, borderRadius: '12px', width: '90%', maxWidth: '500px', padding: '30px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '25px' }}>
              <h3 style={{ margin: '0', color: theme.textMain }}>{modoEdicao ? '✏️ Editar Usuário' : '➕ Cadastrar Novo Usuário'}</h3>
              <button onClick={fecharModal} style={{ background: 'none', border: 'none', color: theme.textMuted, cursor: 'pointer', fontSize: '1.2rem' }}>✖</button>
            </div>

            <form onSubmit={handleSalvarConta}>
              <label style={labelStyle}>Nome Completo:</label>
              <input type="text" value={novaConta.nome_cliente} onChange={e => setNovaConta({...novaConta, nome_cliente: e.target.value})} style={{...inputStyle, marginBottom: '15px'}} required />

              <div style={{ display: 'flex', gap: '15px', marginBottom: '15px' }}>
                <div style={{ flex: 1 }}><label style={labelStyle}>E-mail:</label><input type="email" value={novaConta.email} onChange={e => setNovaConta({...novaConta, email: e.target.value})} style={inputStyle} /></div>
                <div style={{ flex: 1 }}><label style={labelStyle}>WhatsApp:</label><input type="tel" value={novaConta.whatsapp} onChange={e => setNovaConta({...novaConta, whatsapp: e.target.value})} style={inputStyle} /></div>
              </div>

              <label style={labelStyle}>Login (Usuário):</label>
              <input type="text" value={novaConta.login} onChange={e => setNovaConta({...novaConta, login: e.target.value})} style={{...inputStyle, marginBottom: '15px'}} disabled={modoEdicao} required />

              <div style={{ display: 'flex', gap: '15px', marginBottom: '15px' }}>
                <div style={{ flex: 1 }}><label style={labelStyle}>{modoEdicao ? 'Nova Senha (opcional):' : 'Senha:'}</label><input type="password" value={novaConta.senha} onChange={e => setNovaConta({...novaConta, senha: e.target.value})} style={inputStyle} required={!modoEdicao} /></div>
                <div style={{ flex: 1 }}><label style={labelStyle}>Nível de Acesso:</label><select value={novaConta.role} onChange={e => setNovaConta({...novaConta, role: e.target.value})} style={inputStyle}><option value="CLIENTE">👤 Cliente</option><option value="GESTOR">💼 Gestor</option></select></div>
              </div>

              <div style={{ display: 'flex', gap: '15px', marginBottom: '20px' }}>
                <div style={{ flex: 2 }}><label style={labelStyle}>Perfil de Risco:</label><select value={novaConta.perfil_risco} onChange={e => setNovaConta({...novaConta, perfil_risco: e.target.value})} style={inputStyle}>{PERFIS_PADRAO.map(p => <option key={p} value={p}>{p}</option>)}</select></div>
                {!modoEdicao && <div style={{ flex: 1 }}><label style={labelStyle}>Capital (R$):</label><input type="number" value={novaConta.saldo_inicial} onChange={e => setNovaConta({...novaConta, saldo_inicial: e.target.value})} style={inputStyle} min="0" step="0.01" /></div>}
              </div>

              {statusMsg.texto && <div style={{ padding: '10px', borderRadius: '4px', backgroundColor: statusMsg.tipo === 'sucesso' ? 'rgba(16, 185, 129, 0.1)' : 'rgba(239, 68, 68, 0.1)', color: statusMsg.tipo === 'sucesso' ? theme.compra : theme.venda, textAlign: 'center', border: `1px solid ${statusMsg.tipo === 'sucesso' ? theme.compra : theme.venda}`, marginBottom: '15px', fontWeight: 'bold' }}>{statusMsg.texto}</div>}

              <button type="submit" style={{ width: '100%', padding: '15px', borderRadius: '6px', backgroundColor: modoEdicao ? theme.info : theme.compra, color: '#fff', border: 'none', fontWeight: 'bold', cursor: 'pointer' }}>{modoEdicao ? 'SALVAR ALTERAÇÕES' : 'SALVAR NOVO USUÁRIO'}</button>
            </form>
          </div>
        </div>
      )}

      {/* 👇 CÓDIGO NO FINAL DO AccountManager.jsx 👇 */}
      {configMotorId && (
        <ConfigMotorModal 
          usuarioIdInicial={configMotorId}
          nomeClienteInicial={configMotorNome}
          clientes={contas} 
          onClose={() => {
            setConfigMotorId(null);
            setConfigMotorNome('');
          }}
        />
      )}

    </div>
  );
};

