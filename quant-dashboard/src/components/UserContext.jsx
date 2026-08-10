
import { createContext, useContext, useState } from 'react';

const UserContext = createContext();

export const UserProvider = ({ children }) => {
  const [contaAtiva, setContaAtiva] = useState(null); // ID do usuário selecionado

  return (
    <UserContext.Provider value={{ contaAtiva, setContaAtiva }}>
      {children}
    </UserContext.Provider>
  );
};

export const useUser = () => useContext(UserContext);

