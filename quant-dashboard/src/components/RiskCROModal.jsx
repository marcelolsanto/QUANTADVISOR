
import { useState, useEffect, useRef } from 'react';
import { consultarCROSintetico } from '../services/api';
import { theme } from '../theme';

// Imports necessários para renderizar Matemática e Markdown
import ReactMarkdown from 'react-markdown';
import remarkMath from 'remark-math';
import rehypeKatex from 'rehype-katex';
import 'katex/dist/katex.min.css';

export const RiskCROModal = ({ onClose }) => {
  const [mensagens, setMensagens] = useState([
    {
      autor: 'cro',
      conteudo: 'Olá! Sou o CRO Sintético e Arquiteto do QuantAdvisor. Como posso ajudar você hoje com análises do mercado, estatísticas ou com a engenharia do nosso sistema?'
    }
  ]);
  const [inputTexto, setInputTexto] = useState('');
  const [loading, setLoading] = useState(false);
  
  const fimDoChatRef = useRef(null);

  useEffect(() => {
    fimDoChatRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [mensagens]);

  const enviarMensagem = async () => {
    if (!inputTexto.trim()) return;

    const novaMensagemUsuario = { autor: 'usuario', conteudo: inputTexto };
    setMensagens((prev) => [...prev, novaMensagemUsuario]);
    setInputTexto('');
    setLoading(true);

    try {
      const res = await consultarCROSintetico({ cenario: inputTexto });
      
      if (res.data.sucesso) {
        const dados = res.data.dados;
        
        // Renderização com suporte a LaTeX embutido
        const respostaFormatada = (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div>
              <strong style={{ color: theme.textMain }}>🧠 Diagnóstico / Conceito:</strong>
              <div style={{ marginTop: '4px', lineHeight: '1.5', color: theme.textMain }}>
                <ReactMarkdown remarkPlugins={[remarkMath]} rehypePlugins={[rehypeKatex]}>
                  {dados.diagnostico_carteira}
                </ReactMarkdown>
              </div>
            </div>
            <div style={{ borderLeft: `2px solid ${theme.border}`, paddingLeft: '10px' }}>
              <strong style={{ color: theme.textMuted }}>⚙️ Engenharia / Impacto Causal:</strong>
              <div style={{ marginTop: '4px', lineHeight: '1.5', color: theme.textMuted }}>
                <ReactMarkdown remarkPlugins={[remarkMath]} rehypePlugins={[rehypeKatex]}>
                  {dados.impacto_causal}
                </ReactMarkdown>
              </div>
            </div>
            <div>
              <strong style={{ color: theme.compra }}>🎯 Plano de Ação:</strong>
              <div style={{ marginTop: '4px', lineHeight: '1.5', color: theme.compra, fontWeight: '500' }}>
                <ReactMarkdown remarkPlugins={[remarkMath]} rehypePlugins={[rehypeKatex]}>
                  {dados.sugestao_ajuste}
                </ReactMarkdown>
              </div>
            </div>
          </div>
        );

        setMensagens((prev) => [...prev, { autor: 'cro', conteudo: respostaFormatada }]);
      } else {
        setMensagens((prev) => [...prev, { autor: 'sistema', conteudo: `⚠️ Erro: ${res.data.erro}` }]);
      }
    } catch (err) {
      setMensagens((prev) => [...prev, { autor: 'sistema', conteudo: `⚠️ Falha de comunicação com a IA: ${err.response?.data?.detail || err.message}` }]);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      enviarMensagem();
    }
  };

  return (
    <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1200 }}>
      <div style={{ backgroundColor: theme.cardBg, border: `1px solid ${theme.info}`, borderRadius: '12px', width: '90%', maxWidth: '850px', height: '85vh', display: 'flex', flexDirection: 'column', overflow: 'hidden', boxShadow: `0 0 40px -10px ${theme.info}` }}>
        
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '20px', borderBottom: `1px solid ${theme.border}`, backgroundColor: 'rgba(59, 130, 246, 0.05)' }}>
          <div>
            <h2 style={{ margin: 0, color: theme.info, display: 'flex', alignItems: 'center', gap: '10px', fontSize: '1.3rem' }}>
              🧠 Chief Risk Officer (CRO) Copilot
            </h2>
            <p style={{ margin: '5px 0 0 0', color: theme.textMuted, fontSize: '0.85rem' }}>
              Llama 3.3 Engine • Contexto Técnico e Econômico Integrado
            </p>
          </div>
          <button onClick={onClose} style={{ background: 'none', border: 'none', fontSize: '1.5rem', cursor: 'pointer', color: theme.textMuted, transition: '0.2s' }} onMouseOver={(e) => e.target.style.color = theme.textMain} onMouseOut={(e) => e.target.style.color = theme.textMuted}>
            ✖
          </button>
        </div>

        <div style={{ flex: 1, padding: '20px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '20px', backgroundColor: '#0b1120' }}>
          {mensagens.map((msg, index) => {
            const isUser = msg.autor === 'usuario';
            const isSystem = msg.autor === 'sistema';

            return (
              <div key={index} style={{ display: 'flex', justifyContent: isUser ? 'flex-end' : 'flex-start' }}>
                <div style={{
                  maxWidth: '85%',
                  padding: '15px 20px',
                  borderRadius: isUser ? '16px 16px 0 16px' : '16px 16px 16px 0',
                  backgroundColor: isUser ? theme.info : (isSystem ? 'rgba(239, 68, 68, 0.1)' : theme.cardBg),
                  color: isSystem ? theme.venda : '#fff',
                  border: isSystem ? `1px solid ${theme.venda}` : (isUser ? 'none' : `1px solid ${theme.border}`),
                  boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
                  fontSize: '0.95rem'
                }}>
                  {msg.conteudo}
                </div>
              </div>
            );
          })}
          {loading && (
            <div style={{ display: 'flex', justifyContent: 'flex-start' }}>
              <div style={{ padding: '15px 20px', borderRadius: '16px 16px 16px 0', backgroundColor: theme.cardBg, border: `1px solid ${theme.border}`, color: theme.textMuted, fontStyle: 'italic', display: 'flex', gap: '8px', alignItems: 'center' }}>
                <span className="animate-pulse">●</span>
                <span className="animate-pulse" style={{ animationDelay: '0.2s' }}>●</span>
                <span className="animate-pulse" style={{ animationDelay: '0.4s' }}>●</span>
                <span style={{ marginLeft: '5px' }}>Processando equações e lendo logs...</span>
              </div>
            </div>
          )}
          <div ref={fimDoChatRef} />
        </div>

        <div style={{ padding: '20px', borderTop: `1px solid ${theme.border}`, backgroundColor: theme.cardBg }}>
          <div className="mobile-stack" style={{ display: 'flex', gap: '15px', alignItems: 'flex-end' }}>
            <textarea
              rows="2"
              value={inputTexto}
              onChange={(e) => setInputTexto(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Pergunte sobre a arquitetura do Go, métricas de risco ou dicas de mercado... (Enter para enviar)"
              style={{
                flex: 1, padding: '15px', borderRadius: '8px', backgroundColor: theme.bg, color: theme.textMain,
                border: `1px solid ${theme.border}`, outline: 'none', resize: 'none', fontFamily: 'inherit',
                lineHeight: '1.4'
              }}
            />
            <button
              onClick={enviarMensagem}
              disabled={loading || !inputTexto.trim()}
              style={{
                height: '54px', padding: '0 25px', borderRadius: '8px',
                backgroundColor: (loading || !inputTexto.trim()) ? theme.border : theme.info,
                color: '#fff', fontWeight: 'bold', border: 'none', cursor: (loading || !inputTexto.trim()) ? 'not-allowed' : 'pointer',
                transition: '0.2s'
              }}
            >
              ENVIAR
            </button>
          </div>
        </div>

      </div>
    </div>
  );
};

