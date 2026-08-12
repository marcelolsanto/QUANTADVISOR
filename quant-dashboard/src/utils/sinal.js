/**
 * Utilitário centralizado para resolução robusta do sinal visual de IA por Perfil de Usuário
 * Garante fallback seguro se o perfil for 'Agressivo', 'Arrojado', 'Moderado' ou 'Conservador'
 */
export const getSinalVisual = (item, perfilUsuario = 'Agressivo') => {
  if (!item) return 'NEUTRO';

  // Se o item tem um mapa sinais_perfil
  if (item.sinais_perfil && typeof item.sinais_perfil === 'object') {
    // 1. Tenta exato
    if (item.sinais_perfil[perfilUsuario]) {
      return item.sinais_perfil[perfilUsuario];
    }
    // 2. Mapeamento Agressivo <-> Arrojado
    if (perfilUsuario === 'Agressivo' && item.sinais_perfil['Arrojado']) {
      return item.sinais_perfil['Arrojado'];
    }
    if (perfilUsuario === 'Arrojado' && item.sinais_perfil['Agressivo']) {
      return item.sinais_perfil['Agressivo'];
    }
    // 3. Fallback para qualquer perfil cadastrado
    const perfisValidos = Object.values(item.sinais_perfil).filter(Boolean);
    if (perfisValidos.length > 0) {
      return perfisValidos[0];
    }
  }

  // Fallback para sinal direto ou decisao_ia
  return item.sinal || item.sinal_exibicao || item.decisao_ia || 'NEUTRO';
};
