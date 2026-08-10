import torch

# Substitua 'nome_do_arquivo.pt' pelo nome real do seu arquivo
caminho_arquivo = '/home/marcelosantos_dev/Documentos/meu-ambiente-dev/QUANTADVISOR/motor_python/modelos_salvos/lstm_PETR3.pth'

# Carrega os dados (geralmente um dicionário de pesos conhecido como state_dict)
modelo_dados = torch.load(caminho_arquivo)

# Exibe as chaves (camadas do modelo)
print("Camadas do modelo:")
for chave in modelo_dados.keys():
    print(chave)

# Para ver os valores matemáticos (tensores) de uma camada específica:
print("\nPesos da camada linear:")
print(modelo_dados['linear.weight'])

caminho_arquivo = '/home/marcelosantos_dev/Documentos/meu-ambiente-dev/QUANTADVISOR/motor_python/modelos_salvos/lstm_PETR3.pth' # Ajuste o nome correto do arquivo aqui
state_dict = torch.load(caminho_arquivo, weights_only=True)

print("=== ENGENHARIA REVERSA DA ARQUITETURA LSTM ===")

# Analisando a primeira camada (Layer 0)
if 'lstm.weight_ih_l0' in state_dict:
    # O shape no PyTorch é (4 * hidden_size, input_size)
    shape_ih = state_dict['lstm.weight_ih_l0'].shape
    
    input_size = shape_ih[1]
    hidden_size = shape_ih[0] // 4 # Dividimos por 4 devido aos 4 gates
    
    print(f"\n[+] Dados de Entrada (Features): {input_size}")
    print(f"[+] Neurônios Ocultos (Hidden Size): {hidden_size}")

# Contando o total de camadas LSTM
num_layers = sum(1 for key in state_dict.keys() if 'weight_ih' in key)
print(f"[+] Quantidade de camadas LSTM empilhadas: {num_layers}")

# Analisando a camada de saída
if 'linear.weight' in state_dict:
    shape_linear = state_dict['linear.weight'].shape
    print(f"[+] Saída final (Output Size): {shape_linear[0]}")

print("\n=== DISTRIBUIÇÃO DOS PESOS (Primeiros valores de Input) ===")
# Mostra os 5 primeiros pesos brutos que multiplicam a entrada
print(state_dict['lstm.weight_ih_l0'][0][:5].numpy())