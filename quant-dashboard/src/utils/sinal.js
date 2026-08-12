/**
 * Utilitário centralizado para resolução robusta do sinal visual de IA por Perfil de Usuário
 * Garante fallback seguro se o perfil for 'Agressivo', 'Arrojado', 'Moderado' ou 'Conservador'
 * Suporta objetos e strings JSON tratadas defensivamente.
 */
export const getSinalVisual = (item, perfilUsuario = 'Agressivo') => {
  if (!item) return 'NEUTRO';

  let mapa = item.sinais_perfil;

  if (typeof mapa === 'string') {
    try {
      mapa = JSON.parse(mapa);
    } catch (e) {
      mapa = null;
    }
  }

  if (mapa && typeof mapa === 'object' && mapa !== null) {
    const perfil = perfilUsuario || 'Agressivo';
    if (mapa[perfil]) return mapa[perfil];
    if (perfil === 'Agressivo' && mapa['Arrojado']) return mapa['Arrojado'];
    if (perfil === 'Arrojado' && mapa['Agressivo']) return mapa['Agressivo'];
    
    const perfisValidos = Object.values(mapa).filter(Boolean);
    if (perfisValidos.length > 0) {
      return perfisValidos[0];
    }
  }

  return item.sinal || item.sinal_exibicao || item.decisao_ia || 'NEUTRO';
};
