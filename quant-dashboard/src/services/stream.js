
// ARQUIVO: src/services/stream.js

const listeners = new Set();
const connectionListeners = new Set();
let sse = null;
let isConnected = false;

// Função para sintonizar nos dados
export const subscribeToMarket = (callback) => {
  listeners.add(callback);

  // Se for o primeiro componente a pedir dados, abre a conexão!
  if (!sse) {
    sse = new EventSource('/api/stream/mercado');

    sse.onopen = () => {
      isConnected = true;
      connectionListeners.forEach(cb => cb(true));
    };

    sse.onerror = () => {
      isConnected = false;
      connectionListeners.forEach(cb => cb(false));
    };

    sse.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        listeners.forEach(cb => cb(data));
      } catch {
        // ignora JSON de streaming malformado
      }
    };
  }

  // Retorna a função de limpeza (quando o componente é desmontado)
  return () => {
    listeners.delete(callback);
    // Se ninguém mais estiver ouvindo, desliga o túnel de rede
    if (listeners.size === 0 && sse) {
      sse.close();
      sse = null;
      isConnected = false;
    }
  };
};

// Função para sintonizar no status da conexão (Online/Offline)
export const subscribeToConnectionStatus = (callback) => {
  connectionListeners.add(callback);
  callback(isConnected); // Envia o status atual imediatamente
  return () => connectionListeners.delete(callback);
};

