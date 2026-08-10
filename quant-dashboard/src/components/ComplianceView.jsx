
import React, { useState, useEffect } from 'react';
import { getLancamentosContabeis, getResumoFiscal, getUsuarioInfo, getUsuarios, getPosicoesAbertas, getLotesFiscais } from '../services/api';
import { theme } from '../theme';
import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import { toast } from 'sonner';
import { SkeletonTable } from './SkeletonLoader';

export const ComplianceView = ({ usuarioId }) => {
    // ==========================================
    // ESTADOS DE SELEÇÃO E PERMISSÕES
    // ==========================================
    const [activeUserId, setActiveUserId] = useState(usuarioId);
    const [listaUsuarios, setListaUsuarios] = useState([]);
    const isGestor = localStorage.getItem('@QuantAdvisor:role_web') === 'GESTOR' || Number(localStorage.getItem('@QuantAdvisor:user_id_web')) === 1;

    const [lancamentos, setLancamentos] = useState([]);
    const [lotes, setLotes] = useState([]); 
    const [resumoFiscal, setResumoFiscal] = useState(null);
    const [lucroAberto, setLucroAberto] = useState(0); 
    const [dadosCliente, setDadosCliente] = useState({ nome: 'Investidor Padrão', email: 'N/A', celular: 'N/A' });
    const [mesSelecionado, setMesSelecionado] = useState(() => new Date().toISOString().substring(0, 7)); 
    const [loading, setLoading] = useState(true);
    
    // Estado para rastrear o patrimônio base vs atual dinamicamente
    const [patrimonioGlobal, setPatrimonioGlobal] = useState({ custoInicial: 0, patrimonioAtual: 0 });

    useEffect(() => {
        setActiveUserId(usuarioId);
    }, [usuarioId]);

    useEffect(() => {
        if (isGestor) {
            getUsuarios()
                .then(res => {
                    if (res.data) setListaUsuarios(res.data);
                })
                .catch(err => console.error("Erro ao buscar usuários no Compliance:", err));
        }
    }, [isGestor]);

    const carregarMundoContabil = async () => {
        setLoading(true);
        try {
            const pLan = getLancamentosContabeis(activeUserId).catch(() => ({ data: [] }));
            const pFiscal = getResumoFiscal(activeUserId, mesSelecionado).catch(() => ({ data: null }));
            const pPosicoes = getPosicoesAbertas(activeUserId).catch(() => ({ data: [] }));
            const pLotes = getLotesFiscais(activeUserId).catch(() => ({ data: [] })); 

            let pUser = Promise.resolve({ data: { nome: 'Investidor Padrão', email: 'N/A', celular: 'N/A' } });
            try {
                if (typeof getUsuarioInfo === 'function') {
                    pUser = getUsuarioInfo(activeUserId).catch(() => ({ data: { nome: 'Investidor Padrão', email: 'N/A', celular: 'N/A' } }));
                }
            } catch (error) {
                console.warn("⚠️ [Compliance] Aviso: getUsuarioInfo não encontrado. Usando fallback no PDF.");
            }

            const [resLan, resFiscal, resPos, resLotes, resUser] = await Promise.all([pLan, pFiscal, pPosicoes, pLotes, pUser]);

            setLancamentos(resLan.data);
            setResumoFiscal(resFiscal.data);
            setLotes(resLotes.data);
            setDadosCliente(resUser.data);

            const lucroLatenteCalc = resPos.data.reduce((acc, pos) => acc + (pos.lucro_prejuizo_financeiro || 0), 0);
            setLucroAberto(lucroLatenteCalc);

            // 🌟 CÁLCULO PATRIMONIAL GLOBAL DINÂMICO
            getUsuarios().then(res => {
                const userAtual = res.data.find(u => u.id === activeUserId || u.usuario_id === activeUserId);
                if (userAtual) {
                    const saldoBRL = userAtual.saldo_brl !== undefined ? userAtual.saldo_brl : (userAtual.saldo_disponivel || 0);
                    const saldoUSD = userAtual.saldo_usd || 0;
                    const cotacaoDolarAtiva = 5.0819; // Taxa de Câmbio da Auditoria

                    // Calcula o Custo Inicial de Aquisição (Preço Médio)
                    const custoAquisicao = resPos.data.reduce((acc, pos) => {
                        const multiplicador = pos.moeda === 'USD' ? cotacaoDolarAtiva : 1;
                        return acc + (pos.quantidade * (pos.preco_medio || 0) * multiplicador);
                    }, 0);

                    const caixaLivre = saldoBRL + (saldoUSD * cotacaoDolarAtiva);
                    const investimentoBase = caixaLivre + custoAquisicao;

                    // Calcula o valor total em custódia convertendo USD para BRL (Preço Atual)
                    const valorCustodiaAtual = resPos.data.reduce((acc, pos) => {
                        const preco = pos.preco_atual || pos.preco_medio || 0;
                        const multiplicador = pos.moeda === 'USD' ? cotacaoDolarAtiva : 1;
                        return acc + (pos.quantidade * preco * multiplicador);
                    }, 0);

                    setPatrimonioGlobal({
                        custoInicial: investimentoBase, // Aporte Base Dinâmico
                        patrimonioAtual: caixaLivre + valorCustodiaAtual
                    });
                }
            });

        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (activeUserId) carregarMundoContabil();
    }, [activeUserId, mesSelecionado]);

    const verificarLiquidacao = (dataLiquidacaoStr) => {
        const dataLiq = new Date(dataLiquidacaoStr);
        const hoje = new Date();
        return dataLiq <= hoje;
    };

    const formatarBRL = (v) => Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

    // VARIÁVEIS FISCAIS
    const isentoSwing = resumoFiscal?.isento_swing ?? true;
    const isentoExterior = resumoFiscal?.isento_exterior ?? true;
    
    const darfFinal = resumoFiscal?.darf_a_pagar || 0;
    const impostoSwing = resumoFiscal?.imposto_swing || 0;
    const impostoExterior = resumoFiscal?.imposto_exterior || 0;
    const impostoDayTrade = resumoFiscal?.imposto_dt || 0;
    const impostoBrutoTotal = impostoSwing + impostoExterior + impostoDayTrade;
    const irrfRetido = resumoFiscal?.irrf_dedo_duro_retido || 0;

    const lancamentosDoMes = lancamentos.filter(l => {
        if (!l.data_lancamento) return false;
        const dataL = new Date(l.data_lancamento);
        const ano = dataL.getFullYear();
        const mes = String(dataL.getMonth() + 1).padStart(2, '0');
        return `${ano}-${mes}` === mesSelecionado;
    });

    // 1. Lucro Bruto Total
    const lucroBrutoB3 = resumoFiscal?.lucro_realizado_swing || 0;
    const lucroBrutoEUA = resumoFiscal?.lucro_realizado_exterior || 0;
    const lucroBrutoDT = resumoFiscal?.lucro_realizado_daytrade || 0;
    
    const receitaTotal = Math.max(0, lucroBrutoB3) + Math.max(0, lucroBrutoEUA) + Math.max(0, lucroBrutoDT);
    const perdasTotais = Math.min(0, lucroBrutoB3) + Math.min(0, lucroBrutoEUA) + Math.min(0, lucroBrutoDT);

    // 2. CUSTOS OPERACIONAIS REAIS
    const custosOperacionais = lotes.reduce((acc, lote) => {
        const dataLote = new Date(lote.data_entrada);
        const ano = dataLote.getFullYear();
        const mes = String(dataLote.getMonth() + 1).padStart(2, '0');
        if (`${ano}-${mes}` === mesSelecionado) {
            return acc + (lote.custos_b3 || 0);
        }
        return acc;
    }, 0);

    // 3. Resultados Líquidos
    const lucroLiquidoReal = receitaTotal + perdasTotais - darfFinal - irrfRetido - custosOperacionais;
    const margemLiquida = receitaTotal > 0 ? (lucroLiquidoReal / receitaTotal) * 100 : 0;
    const resultadoEconGlobal = lucroLiquidoReal + lucroAberto;

    // 4. RECONCILIAÇÃO PATRIMONIAL DINÂMICA
    const perdaPatrimonialAbsoluta = patrimonioGlobal.patrimonioAtual - patrimonioGlobal.custoInicial;
    const impactoCambialInvisivel = perdaPatrimonialAbsoluta - resultadoEconGlobal;

    // =========================================================================
    // GERADOR DE RELATÓRIO PDF INSTITUCIONAL (DARF + DRE + PATRIMÔNIO)
    // =========================================================================
    const exportarRelatorioPDF = () => {
        toast.loading("Compilando Reconciliação Fiscal e gerando DARF...", { id: "darf-pdf" });
        try {
            const doc = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });

        doc.setFont("Helvetica", "bold");
        doc.setFontSize(16);
        doc.text("QUANTADVISOR WEALTHTECH LTDA.", 14, 20);

        doc.setFont("Helvetica", "normal");
        doc.setFontSize(9);
        doc.setTextColor(100, 100, 100);
        doc.text("CNPJ: 45.123.456/0001-99", 14, 25);
        doc.text("Av. Brigadeiro Faria Lima, 3477 - Itaim Bibi, São Paulo - SP", 14, 30);
        doc.text("contato@quantadvisor.com.br | www.quantadvisor.com.br", 14, 35);

        doc.setLineWidth(0.2);
        doc.setDrawColor(200, 200, 200);
        doc.line(14, 39, 196, 39);

        doc.setFont("Helvetica", "bold");
        doc.setFontSize(12);
        doc.setTextColor(0, 0, 0);
        doc.text("RELATÓRIO DE RECONCILIAÇÃO PATRIMONIAL E FISCAL", 14, 47);

        doc.setFont("Helvetica", "normal");
        doc.setFontSize(10);
        doc.text(`Competência Fiscal: ${mesSelecionado}`, 14, 54);
        doc.text(`Data de Emissão: ${new Date().toLocaleDateString('pt-BR')}`, 14, 59);

        doc.setFillColor(245, 245, 245);
        doc.rect(14, 63, 182, 18, 'F');
        doc.setFont("Helvetica", "bold");
        doc.text("DADOS DO INVESTIDOR", 18, 69);
        doc.setFont("Helvetica", "normal");
        doc.setFontSize(9);
        doc.text(`Nome: ${dadosCliente.nome}`, 18, 75);
        doc.text(`Contato: ${dadosCliente.email} | Celular: ${dadosCliente.celular}`, 18, 79);

        // --- SEÇÃO 1: RECONCILIAÇÃO PATRIMONIAL E DRE GERENCIAL ---
        doc.setFont("Helvetica", "bold");
        doc.setFontSize(11);
        doc.text("1. Reconciliação Patrimonial e DRE Gerencial", 14, 90);

        const dadosPatrimonio = [
            ["Investimento Inicial (Custo + Caixa Base)", formatarBRL(patrimonioGlobal.custoInicial)],
            ["Patrimônio Atual (Marcado a Mercado)", formatarBRL(patrimonioGlobal.patrimonioAtual)],
            ["Resultado Patrimonial Real (Déficit/Superávit)", formatarBRL(perdaPatrimonialAbsoluta)],
            ["Impacto da Variação Cambial no Período", formatarBRL(impactoCambialInvisivel)]
        ];

        autoTable(doc, {
            startY: 94,
            body: dadosPatrimonio,
            theme: 'grid',
            styles: { fontSize: 8 },
            columnStyles: { 0: { cellWidth: 130, fontStyle: 'bold' }, 1: { halign: 'right', fontStyle: 'bold' } },
            margin: { left: 14, right: 14 }
        });

        const yDRE = doc.lastAutoTable.finalY + 5;

        const dadosDRE = [
            ["(+) Receita Bruta de Operações Vencedoras", formatarBRL(receitaTotal)],
            ["(-) Perdas Realizadas (Stop Loss)", perdasTotais < 0 ? formatarBRL(perdasTotais) : "R$ 0,00"],
            ["(-) Custos Operacionais (B3, Emolumentos e Fricção)", custosOperacionais > 0 ? "-" + formatarBRL(custosOperacionais) : "R$ 0,00"],
            ["(-) Impostos (DARF + IRRF Retido)", "-" + formatarBRL(darfFinal + irrfRetido)],
            ["(+) Lucro Latente (Custódia Aberta)", formatarBRL(lucroAberto)],
            ["(=) Resultado Econômico Global (Ações)", formatarBRL(resultadoEconGlobal)]
        ];

        autoTable(doc, {
            startY: yDRE,
            body: dadosDRE,
            theme: 'striped',
            styles: { fontSize: 8 },
            columnStyles: { 0: { cellWidth: 130 }, 1: { halign: 'right', fontStyle: 'bold' } },
            margin: { left: 14, right: 14 }
        });

        // --- SEÇÃO 2: APURAÇÃO JURISDICIONAL (DARF) ---
        let currentY = doc.lastAutoTable.finalY + 12;
        
        if (currentY > 250) {
            doc.addPage();
            currentY = 20;
        }

        doc.setFont("Helvetica", "bold");
        doc.setFontSize(11);
        doc.text("2. Resumo de Apuração Jurisdicional (B3 & Exterior)", 14, currentY);

        const dadosResumo = [
            ["Modalidade", "Vol. Vendas Bruto", "Lucro/Prejuízo Líq.", "Abate Prej. Ant.", "Alíquota", "IR Devido"],
            ["B3 - Ações (Swing Trade)", formatarBRL(resumoFiscal?.volume_vendas_swing), formatarBRL(resumoFiscal?.lucro_realizado_swing), formatarBRL(resumoFiscal?.prejuizo_anterior_swing), "15%", formatarBRL(impostoSwing)],
            ["EUA - Wall St. (Exterior)", formatarBRL(resumoFiscal?.volume_vendas_exterior), formatarBRL(resumoFiscal?.lucro_realizado_exterior), formatarBRL(resumoFiscal?.prejuizo_anterior_exterior), "15%", formatarBRL(impostoExterior)],
            ["B3 - Operações (Day Trade)", "N/A", formatarBRL(resumoFiscal?.lucro_realizado_daytrade), formatarBRL(resumoFiscal?.prejuizo_anterior_dt), "20%", formatarBRL(impostoDayTrade)],
            ["(-) Retenção na Fonte B3 (IRRF)", "-", "-", "-", "-", formatarBRL(resumoFiscal?.irrf_dedo_duro_retido)],
            ["(=) TOTAL A RECOLHER (DARF)", "-", "-", "-", "-", formatarBRL(darfFinal)]
        ];

        autoTable(doc, {
            startY: currentY + 4,
            head: [dadosResumo[0]],
            body: dadosResumo.slice(1),
            theme: 'striped',
            headStyles: { fillColor: [31, 41, 55], textColor: [255, 255, 255], fontStyle: 'bold' },
            footStyles: { fillColor: [243, 244, 246], fontStyle: 'bold' },
            margin: { left: 14, right: 14 },
            styles: { fontSize: 8 }
        });

        // --- SEÇÃO 3: EXTRATO DO LIVRO DIÁRIO ---
        currentY = doc.lastAutoTable.finalY + 12;

        if (currentY > 250) {
            doc.addPage();
            currentY = 20;
        }

        doc.setFont("Helvetica", "bold");
        doc.setFontSize(11);
        doc.text("3. Escrituração Analítica (Livro Diário)", 14, currentY);

        const colunasLancamentos = ["Data Lançamento", "Histórico Operacional", "Conta Débito", "Conta Crédito", "Valor (R$)"];
        const linhasLancamentos = lancamentosDoMes.map(l => [
            new Date(l.data_lancamento).toLocaleDateString('pt-BR'),
            l.historico,
            l.conta_debito,
            l.conta_credito,
            formatarBRL(l.valor)
        ]);

        autoTable(doc, {
            startY: currentY + 4,
            head: [colunasLancamentos],
            body: linhasLancamentos,
            theme: 'grid',
            headStyles: { fillColor: [75, 85, 99], textColor: [255, 255, 255] },
            columnStyles: { 0: { cellWidth: 20 }, 1: { cellWidth: 70 }, 2: { cellWidth: 35 }, 3: { cellWidth: 35 }, 4: { cellWidth: 22, halign: 'right' } },
            margin: { left: 14, right: 14 },
            styles: { fontSize: 7 }
        });

        const pageCount = doc.internal.getNumberOfPages();
        for (let i = 1; i <= pageCount; i++) {
            doc.setPage(i);
            doc.setFontSize(7);
            doc.setTextColor(150, 150, 150);
            const disclaimer = "Este relatório é gerado automaticamente e serve apenas como documento auxiliar. A responsabilidade pela declaração e recolhimento de impostos recai exclusivamente sobre o contribuinte, conforme normativas da Receita Federal do Brasil.";
            doc.text(disclaimer, 14, 285, { maxWidth: 182, align: "justify" });
        }

        const nomeArquivoSeguro = (dadosCliente.nome || "Investidor").replace(/\s+/g, '_');
        doc.save(`Reconciliacao_${mesSelecionado}_${nomeArquivoSeguro}.pdf`);
        toast.success("Relatório Fiscal & DARF Gerado!", {
            id: "darf-pdf",
            description: `Salvo com sucesso: Reconciliacao_${mesSelecionado}_${nomeArquivoSeguro}.pdf`,
            duration: 4000
        });
        } catch (err) {
            toast.error("Falha ao gerar o PDF de Reconciliação", {
                id: "darf-pdf",
                description: err.message
            });
        }
    };

    const thStyle = { padding: '12px', borderBottom: `2px solid ${theme.border}`, color: theme.textMuted, fontSize: '0.85rem', textTransform: 'uppercase', fontWeight: 'bold' };
    const tdStyle = { padding: '12px', borderBottom: `1px solid ${theme.border}`, color: theme.textMain, fontSize: '0.9rem' };

    if (loading) return <SkeletonTable rows={6} />;

    return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '30px' }}>
            
            {/* ========================================================= */}
            {/* 🌟 PAINEL: RECONCILIAÇÃO PATRIMONIAL E DRE GERENCIAL */}
            {/* ========================================================= */}
            <div style={{ backgroundColor: theme.cardBg, borderRadius: '12px', border: `1px solid ${theme.border}`, padding: '25px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', borderBottom: `1px solid ${theme.border}`, paddingBottom: '15px', flexWrap: 'wrap', gap: '15px' }}>
                    <div>
                        <h3 style={{ margin: '0 0 5px 0', color: theme.textMain }}>📊 Reconciliação Patrimonial e DRE</h3>
                        <p style={{ margin: '0', fontSize: '0.85rem', color: theme.textMuted }}>Onde o desempenho operacional do robô encontra a realidade do saldo global.</p>
                    </div>

                    {isGestor && listaUsuarios.length > 0 ? (
                        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                            <span style={{ color: theme.info, fontWeight: 'bold', fontSize: '0.9rem' }}>Visualizando Cliente:</span>
                            <select
                                value={activeUserId}
                                onChange={(e) => setActiveUserId(Number(e.target.value))}
                                style={{
                                    padding: '6px 12px', borderRadius: '6px', backgroundColor: 'rgba(59, 130, 246, 0.1)',
                                    color: theme.info, border: `1px solid ${theme.info}`, outline: 'none', cursor: 'pointer',
                                    fontWeight: 'bold', fontSize: '0.9rem'
                                }}
                            >
                                {listaUsuarios.map(u => (
                                    <option key={u.id} value={u.id}>{u.nome} ({u.perfil_risco})</option>
                                ))}
                            </select>
                        </div>
                    ) : (
                        <div style={{ fontSize: '0.9rem', color: theme.info, fontWeight: 'bold' }}>
                            Cliente: {dadosCliente.nome}
                        </div>
                    )}
                </div>

                {/* 🌟 BLOCO 1: A VERDADE PATRIMONIAL */}
                <div style={{ backgroundColor: perdaPatrimonialAbsoluta < 0 ? 'rgba(239, 68, 68, 0.05)' : 'rgba(16, 185, 129, 0.05)', borderRadius: '8px', border: `1px solid ${perdaPatrimonialAbsoluta < 0 ? theme.venda : theme.compra}`, padding: '20px', marginBottom: '20px' }}>
                    <h4 style={{ color: perdaPatrimonialAbsoluta < 0 ? theme.venda : theme.compra, margin: '0 0 15px 0' }}>{perdaPatrimonialAbsoluta < 0 ? '⚠️' : '📈'} Evolução do Patrimônio Base</h4>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0' }}>
                        <span style={{ color: theme.textMuted }}>Investimento Inicial (Custo + Caixa Livre base):</span>
                        <span style={{ color: theme.textMain, fontWeight: 'bold' }}>{formatarBRL(patrimonioGlobal.custoInicial)}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0' }}>
                        <span style={{ color: theme.textMuted }}>Patrimônio Atual (Marcado a Mercado):</span>
                        <span style={{ color: theme.info, fontWeight: 'bold' }}>{formatarBRL(patrimonioGlobal.patrimonioAtual)}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderTop: `1px dashed ${perdaPatrimonialAbsoluta < 0 ? theme.venda : theme.compra}`, marginTop: '10px' }}>
                        <span style={{ color: perdaPatrimonialAbsoluta < 0 ? theme.venda : theme.compra, fontWeight: 'bold' }}>{perdaPatrimonialAbsoluta < 0 ? 'Déficit' : 'Superávit'} Patrimonial Real:</span>
                        <span style={{ color: perdaPatrimonialAbsoluta < 0 ? theme.venda : theme.compra, fontWeight: 'bold', fontSize: '1.2rem' }}>
                            {perdaPatrimonialAbsoluta > 0 ? '+' : ''}{formatarBRL(perdaPatrimonialAbsoluta)}
                        </span>
                    </div>
                </div>

                {/* BLOCO 2: O DRE DO ROBÔ */}
                <div style={{ backgroundColor: theme.bg, borderRadius: '8px', border: `1px solid ${theme.border}`, padding: '20px' }}>
                    <h4 style={{ color: theme.info, margin: '0 0 15px 0', borderBottom: `1px solid rgba(255,255,255,0.05)`, paddingBottom: '10px' }}>I. Desempenho Operacional do Robô (DRE)</h4>
                    
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: `1px solid rgba(255,255,255,0.05)` }}>
                        <span style={{ color: theme.textMuted }}>(+) Receita Bruta de Operações</span>
                        <span style={{ color: theme.compra, fontWeight: 'bold' }}>{formatarBRL(receitaTotal)}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: `1px solid rgba(255,255,255,0.05)` }}>
                        <span style={{ color: theme.textMuted }}>(–) Perdas (Stop Loss)</span>
                        <span style={{ color: theme.venda, fontWeight: 'bold' }}>{perdasTotais < 0 ? formatarBRL(perdasTotais) : 'R$ 0,00'}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: `1px solid rgba(255,255,255,0.05)` }}>
                        <span style={{ color: theme.textMuted }}>(–) Custos B3, Emolumentos e Fricção</span>
                        <span style={{ color: theme.alerta, fontWeight: 'bold' }}>{custosOperacionais > 0 ? '-' + formatarBRL(custosOperacionais) : 'R$ 0,00'}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: `1px solid rgba(255,255,255,0.05)` }}>
                        <span style={{ color: theme.textMuted }}>(–) Impostos (DARF + IRRF)</span>
                        <span style={{ color: theme.venda, fontWeight: 'bold' }}>{formatarBRL(darfFinal + irrfRetido)}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: `1px solid rgba(255,255,255,0.05)` }}>
                        <span style={{ color: theme.textMuted }}>(+) Lucro Latente (Custódia Aberta)</span>
                        <span style={{ color: theme.compra, fontWeight: 'bold' }}>{formatarBRL(lucroAberto)}</span>
                    </div>
                    
                    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '15px 0 5px 0', marginTop: '10px', borderTop: `2px solid ${theme.info}` }}>
                        <span style={{ color: theme.info, fontWeight: 'bold', fontSize: '1.1rem' }}>(=) Resultado Econômico das Ações</span>
                        <span style={{ color: resultadoEconGlobal >= 0 ? theme.info : theme.venda, fontWeight: 'bold', fontSize: '1.2rem' }}>
                            {resultadoEconGlobal >= 0 ? '+' : ''}{formatarBRL(resultadoEconGlobal)}
                        </span>
                    </div>
                </div>

                {/* 🌟 BLOCO 3: ONDE O DINHEIRO SUMIU (Câmbio) */}
                <div style={{ backgroundColor: theme.bg, borderRadius: '8px', border: `1px solid ${theme.alerta}`, padding: '20px', marginTop: '20px' }}>
                    <h4 style={{ color: theme.alerta, margin: '0 0 15px 0' }}>II. Reconciliação Macroeconômica</h4>
                    <p style={{ fontSize: '0.85rem', color: theme.textMuted, marginBottom: '15px' }}>
                        A diferença entre o rendimento operacional da carteira e a evolução real do saldo é diretamente influenciada pela exposição cambial (Dólar vs. Real) ao longo do tempo.
                    </p>
                    <div style={{ display: 'flex', justifyContent: 'space-between', backgroundColor: 'rgba(245, 158, 11, 0.1)', borderRadius: '6px', padding: '15px' }}>
                        <span style={{ color: theme.alerta, fontWeight: 'bold' }}>(=) Impacto de Desvalorização Cambial no Dólar:</span>
                        <span style={{ color: impactoCambialInvisivel < 0 ? theme.venda : theme.compra, fontWeight: 'bold', fontSize: '1.1rem' }}>
                            {impactoCambialInvisivel > 0 ? '+' : ''}{formatarBRL(impactoCambialInvisivel)}
                        </span>
                    </div>
                </div>

            </div>

            {/* SEÇÃO 1: APURAÇÃO FISCAL (DARF) */}
            <div style={{ backgroundColor: theme.cardBg, borderRadius: '12px', border: `1px solid ${theme.border}`, padding: '25px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', borderBottom: `1px solid ${theme.border}`, paddingBottom: '15px', flexWrap: 'wrap', gap: '15px' }}>
                    <div>
                        <h3 style={{ margin: 0, color: theme.textMain }}>🦁 Apuração Mensal de Imposto de Renda (DARF)</h3>
                        <p style={{ margin: '5px 0 0 0', fontSize: '0.85rem', color: theme.textMuted }}>Separação estrita entre B3 (Brasil) e Wall Street (EUA)</p>
                    </div>

                    <div style={{ display: 'flex', gap: '15px', alignItems: 'center', flexWrap: 'wrap' }}>
                        <input
                            type="month"
                            value={mesSelecionado}
                            onChange={(e) => setMesSelecionado(e.target.value)}
                            style={{ padding: '10px 15px', borderRadius: '6px', backgroundColor: theme.bg, color: theme.textMain, border: `1px solid ${theme.border}`, outline: 'none', cursor: 'pointer', fontWeight: 'bold', fontSize: '1rem' }}
                        />
                        <button
                            onClick={exportarRelatorioPDF}
                            className="touch-target"
                            style={{ padding: '12px 20px', borderRadius: '6px', backgroundColor: theme.compra, color: '#FFF', border: 'none', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.9rem', display: 'flex', alignItems: 'center', gap: '8px', minHeight: '48px', minWidth: '48px' }}
                        >
                            📥 Exportar DARF / Relatório (PDF)
                        </button>
                    </div>
                </div>

                {resumoFiscal ? (
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '20px' }}>
                        <div style={{ backgroundColor: theme.bg, padding: '20px', borderRadius: '8px', borderTop: `4px solid ${theme.info}`, border: `1px solid ${theme.border}` }}>
                            <h4 style={{ margin: '0 0 15px 0', color: theme.info }}>🇧🇷 Brasil (B3 Swing Trade)</h4>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                                <span style={{ color: theme.textMuted, fontSize: '0.85rem' }}>Vol. de Vendas:</span>
                                <span style={{ fontWeight: 'bold', color: isentoSwing ? theme.compra : theme.venda }}>{formatarBRL(resumoFiscal.volume_vendas_swing)}</span>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                                <span style={{ color: theme.textMuted, fontSize: '0.85rem' }}>Lucro Bruto:</span>
                                <span style={{ fontWeight: 'bold', color: resumoFiscal.lucro_realizado_swing >= 0 ? theme.compra : theme.venda }}>{formatarBRL(resumoFiscal.lucro_realizado_swing)}</span>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '15px' }}>
                                <span style={{ color: theme.textMuted, fontSize: '0.85rem' }}>(-) Prejuízo Ant.:</span>
                                <span style={{ fontWeight: 'bold', color: theme.textMain }}>{formatarBRL(resumoFiscal.prejuizo_anterior_swing)}</span>
                            </div>
                            <div style={{ padding: '10px', borderRadius: '4px', backgroundColor: isentoSwing ? 'rgba(16, 185, 129, 0.1)' : 'rgba(239, 68, 68, 0.1)', textAlign: 'center', marginBottom: '10px' }}>
                                <span style={{ fontSize: '0.8rem', fontWeight: 'bold', color: isentoSwing ? theme.compra : theme.venda }}>
                                    {isentoSwing ? '✅ ISENTO (Abaixo R$ 20k)' : '⚠️ TRIBUTADO (15%)'}
                                </span>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', borderTop: `1px dashed ${theme.border}`, paddingTop: '10px' }}>
                                <span style={{ color: theme.textMain, fontWeight: 'bold' }}>IR Devido:</span>
                                <span style={{ fontWeight: 'bold', color: impostoSwing > 0 ? theme.venda : theme.textMain }}>{formatarBRL(impostoSwing)}</span>
                            </div>
                        </div>

                        <div style={{ backgroundColor: theme.bg, padding: '20px', borderRadius: '8px', borderTop: `4px solid #8b5cf6`, border: `1px solid ${theme.border}` }}>
                            <h4 style={{ margin: '0 0 15px 0', color: '#8b5cf6' }}>🇺🇸 Exterior (Wall St.)</h4>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                                <span style={{ color: theme.textMuted, fontSize: '0.85rem' }}>Vol. de Vendas:</span>
                                <span style={{ fontWeight: 'bold', color: isentoExterior ? theme.compra : theme.venda }}>{formatarBRL(resumoFiscal.volume_vendas_exterior)}</span>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                                <span style={{ color: theme.textMuted, fontSize: '0.85rem' }}>Lucro Bruto:</span>
                                <span style={{ fontWeight: 'bold', color: resumoFiscal.lucro_realizado_exterior >= 0 ? theme.compra : theme.venda }}>{formatarBRL(resumoFiscal.lucro_realizado_exterior)}</span>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '15px' }}>
                                <span style={{ color: theme.textMuted, fontSize: '0.85rem' }}>(-) Prejuízo Ant.:</span>
                                <span style={{ fontWeight: 'bold', color: theme.textMain }}>{formatarBRL(resumoFiscal.prejuizo_anterior_exterior)}</span>
                            </div>
                            <div style={{ padding: '10px', borderRadius: '4px', backgroundColor: isentoExterior ? 'rgba(16, 185, 129, 0.1)' : 'rgba(239, 68, 68, 0.1)', textAlign: 'center', marginBottom: '10px' }}>
                                <span style={{ fontSize: '0.8rem', fontWeight: 'bold', color: isentoExterior ? theme.compra : theme.venda }}>
                                    {isentoExterior ? '✅ ISENTO (Abaixo R$ 35k)' : '⚠️ TRIBUTADO (15%)'}
                                </span>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', borderTop: `1px dashed ${theme.border}`, paddingTop: '10px' }}>
                                <span style={{ color: theme.textMain, fontWeight: 'bold' }}>IR Devido:</span>
                                <span style={{ fontWeight: 'bold', color: impostoExterior > 0 ? theme.venda : theme.textMain }}>{formatarBRL(impostoExterior)}</span>
                            </div>
                        </div>

                        <div style={{ backgroundColor: theme.bg, padding: '20px', borderRadius: '8px', borderTop: `4px solid ${theme.alerta}`, border: `1px solid ${theme.border}` }}>
                            <h4 style={{ margin: '0 0 15px 0', color: theme.alerta }}>⚡ Day Trade (Brasil)</h4>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                                <span style={{ color: theme.textMuted, fontSize: '0.85rem' }}>Lucro Bruto:</span>
                                <span style={{ fontWeight: 'bold', color: resumoFiscal.lucro_realizado_daytrade >= 0 ? theme.compra : theme.venda }}>{formatarBRL(resumoFiscal.lucro_realizado_daytrade)}</span>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '15px' }}>
                                <span style={{ color: theme.textMuted, fontSize: '0.85rem' }}>(-) Prejuízo Ant.:</span>
                                <span style={{ fontWeight: 'bold', color: theme.textMain }}>{formatarBRL(resumoFiscal.prejuizo_anterior_dt)}</span>
                            </div>
                            <div style={{ padding: '10px', borderRadius: '4px', backgroundColor: 'rgba(245, 158, 11, 0.1)', textAlign: 'center', marginBottom: '40px' }}>
                                <span style={{ fontSize: '0.8rem', fontWeight: 'bold', color: theme.alerta }}>❌ SEM ISENÇÃO (20%)</span>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', borderTop: `1px dashed ${theme.border}`, paddingTop: '10px' }}>
                                <span style={{ color: theme.textMain, fontWeight: 'bold' }}>IR Devido:</span>
                                <span style={{ fontWeight: 'bold', color: impostoDayTrade > 0 ? theme.venda : theme.textMain }}>{formatarBRL(impostoDayTrade)}</span>
                            </div>
                        </div>

                        <div style={{ backgroundColor: theme.bg, padding: '20px', borderRadius: '8px', borderTop: `4px solid ${theme.textMain}`, border: `1px solid ${theme.border}` }}>
                            <h4 style={{ margin: '0 0 15px 0', color: theme.textMain }}>🧾 DARF Consolidado</h4>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                                <span style={{ color: theme.textMuted, fontSize: '0.85rem' }}>Imposto Bruto Total:</span>
                                <span style={{ fontWeight: 'bold', color: theme.textMain }}>{formatarBRL(impostoBrutoTotal)}</span>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '15px' }}>
                                <span style={{ color: theme.textMuted, fontSize: '0.85rem' }}>(-) IRRF Retido B3:</span>
                                <span style={{ fontWeight: 'bold', color: theme.compra }}>{formatarBRL(resumoFiscal.irrf_dedo_duro_retido)}</span>
                            </div>
                            <div style={{ backgroundColor: theme.cardBg, padding: '15px', borderRadius: '6px', textAlign: 'center', border: `1px solid ${darfFinal > 0 ? theme.venda : theme.border}`, marginTop: '20px' }}>
                                <div style={{ fontSize: '0.85rem', color: theme.textMuted, textTransform: 'uppercase', marginBottom: '5px' }}>Total a Recolher (R$)</div>
                                <div style={{ fontSize: '2rem', fontWeight: 'bold', color: darfFinal > 0 ? theme.venda : theme.compra }}>
                                    {formatarBRL(darfFinal)}
                                </div>
                            </div>
                        </div>

                    </div>
                ) : (
                    <div style={{ padding: '40px', textAlign: 'center', color: theme.textMuted, fontStyle: 'italic' }}>
                        <div style={{ fontSize: '2rem', marginBottom: '10px' }}>📭</div>
                        Nenhuma operação liquidada com incidência fiscal neste mês.
                    </div>
                )}
            </div>

            {/* SEÇÃO 2: CONTROLADOR DE FLUXO DE CAIXA D+2 (PARTIDAS DOBRADAS) */}
            <div style={{ backgroundColor: theme.cardBg, borderRadius: '12px', border: `1px solid ${theme.border}`, padding: '25px' }}>
                <h3 style={{ margin: '0 0 15px 0', color: theme.textMain }}>📓 Extrato do Livro Diário ({mesSelecionado})</h3>
                <p style={{ margin: '0 0 20px 0', fontSize: '0.85rem', color: theme.textMuted }}>Monitoramento do fluxo de caixa e partidas dobradas referente ao mês selecionado.</p>

                <div style={{ borderRadius: '8px', border: `1px solid ${theme.border}`, overflowX: 'auto', backgroundColor: theme.bg, maxHeight: '400px' }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                        <thead style={{ position: 'sticky', top: 0, backgroundColor: theme.cardBg, zIndex: 1 }}>
                            <tr>
                                <th style={thStyle}>Lançamento / Histórico</th>
                                <th style={thStyle}>Conta Débito</th>
                                <th style={thStyle}>Conta Crédito</th>
                                <th style={thStyle}>Valor (R$)</th>
                                <th style={thStyle}>Data Liquidação</th>
                                <th style={{ ...thStyle, textAlign: 'center' }}>Status B3</th>
                            </tr>
                        </thead>
                        <tbody>
                            {lancamentosDoMes.length === 0 ? (
                                <tr><td colSpan="6" style={{ padding: '20px', textAlign: 'center', color: theme.textMuted }}>Nenhuma movimentação escriturada neste mês.</td></tr>
                            ) : (
                                lancamentosDoMes.map((l) => {
                                    const liquidado = verificarLiquidacao(l.data_liquidacao);
                                    return (
                                        <tr key={l.id} style={{ borderBottom: `1px solid ${theme.border}`, transition: '0.2s' }} onMouseOver={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.02)'} onMouseOut={(e) => e.currentTarget.style.backgroundColor = 'transparent'}>
                                            <td style={tdStyle}>
                                                <div style={{ fontWeight: 'bold' }}>{l.historico}</div>
                                                <div style={{ fontSize: '0.75rem', color: theme.textMuted }}>{new Date(l.data_lancamento).toLocaleString('pt-BR')}</div>
                                            </td>
                                            <td style={{ ...tdStyle, color: theme.info, fontSize: '0.8rem', fontWeight: 'bold' }}>{l.conta_debito}</td>
                                            <td style={{ ...tdStyle, color: theme.venda, fontSize: '0.8rem', fontWeight: 'bold' }}>{l.conta_credito}</td>
                                            <td style={{ ...tdStyle, fontWeight: 'bold', fontFamily: 'monospace' }}>{formatarBRL(l.valor)}</td>
                                            <td style={tdStyle}>{new Date(l.data_liquidacao).toLocaleDateString('pt-BR')}</td>
                                            <td style={{ ...tdStyle, textAlign: 'center' }}>
                                                <span style={{
                                                    padding: '4px 8px', borderRadius: '4px', fontSize: '0.75rem', fontWeight: 'bold',
                                                    backgroundColor: liquidado ? 'rgba(16, 185, 129, 0.12)' : 'rgba(245, 158, 11, 0.12)',
                                                    color: liquidado ? theme.compra : theme.alerta,
                                                    border: `1px solid ${liquidado ? theme.compra : theme.alerta}`
                                                }}>
                                                    {liquidado ? '🟢 LIQUIDADO' : '🟡 D+2 PENDENTE'}
                                                </span>
                                            </td>
                                        </tr>
                                    );
                                })
                            )}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
};

