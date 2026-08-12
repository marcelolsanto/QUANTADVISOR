# Plano de Integração da API na Central de Gestão

Este plano descreve as alterações necessárias para integrar a tela `CentralGestaoScreen.kt` com a API QuantAdvisor, permitindo a listagem real de clientes, o controle do piloto automático (IA) e a exclusão de contas.

## Alterações Propostas

### [Componente de Rede]

#### [MODIFY] [Network.kt](file:///C:/Users/G15/AndroidStudioProjects/QuantAdvisor/app/src/main/java/quantadvisor/com/br/Network.kt)
- Adicionar os modelos de dados baseados no Swagger: `UsuarioResumo`, `TogglePilotoReq`, `DeletarContaRequest`.
- Atualizar a interface `QuantApiService` com os novos endpoints:
    - `GET /api/usuarios`: Listar todos os clientes.
    - `POST /api/piloto/toggle`: Ativar/Desativar IA.
    - `POST /api/usuarios/deletar`: Excluir conta.
- Adicionar um Interceptor ao `RetrofitClient` para incluir o token JWT no cabeçalho `Authorization` de forma automática.

### [Componente de UI]

#### [MODIFY] [CentralGestaoScreen.kt](file:///C:/Users/G15/AndroidStudioProjects/QuantAdvisor/app/src/main/java/quantadvisor/com/br/CentralGestaoScreen.kt)
- Substituir a lista de clientes "mockada" por um estado que consome a API.
- Implementar o carregamento inicial de dados usando `LaunchedEffect`.
- Vincular o `Switch` de piloto automático à chamada `/api/piloto/toggle`.
- Vincular o botão de excluir à chamada `/api/usuarios/deletar`.
- Adicionar estados de carregamento e erro na interface.

## Plano de Verificação

### Testes Manuais
- Verificar se a lista de clientes é carregada corretamente ao abrir a tela.
- Testar o filtro de busca local na lista retornada pela API.
- Confirmar se o `Switch` reflete o estado real e persiste a alteração no backend.
- Validar se a exclusão de um cliente remove o item da lista e executa a chamada na API.
- Verificar se as cores dos badges e P&L estão mapeadas corretamente de acordo com os dados da API.
