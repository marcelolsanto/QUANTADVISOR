import os

# ==========================================
# CONFIGURAÇÕES DO EXTRATOR
# ==========================================

# 1. Pastas para IGNORAR (Não entraremos nelas de jeito nenhum)
IGNORE_FOLDERS = {
    '.git', 'node_modules', '__pycache__', 'venv', 'env', 
    '.vscode', 'dist', 'build', '.idea', 'postgres_data', 'raw_data'
}

# 2. Extensões de arquivos que queremos LER (O seu código de verdade)
ALLOWED_EXTENSIONS = {
    '.go', '.py', '.jsx', '.js', '.sql', '.json'
}

# 3. Arquivos específicos para IGNORAR (ex: o próprio script, logs)
IGNORE_FILES = {
    'extrair_codigo.py', 'gerar_arvore.py', 'mapa_do_projeto.txt', 
    'package-lock.json', 'go.sum', 'selic.json'
}

def consolidar_codigo(diretorio_raiz, arquivo_saida):
    with open(arquivo_saida, 'w', encoding='utf-8') as outfile:
        outfile.write("========================================================\n")
        outfile.write(" CÓDIGO FONTE COMPLETO - QUANTADVISOR\n")
        outfile.write("========================================================\n\n")

        # os.walk varre a pasta raiz e todas as subpastas
        for root, dirs, files in os.walk(diretorio_raiz):
            
            # Filtra a lista de pastas para o script não entrar no node_modules ou venv
            dirs[:] = [d for d in dirs if d not in IGNORE_FOLDERS]

            for file in files:
                extensao = os.path.splitext(file)[1].lower()
                
                # Só processa se for um arquivo de código e não estiver na lista negra
                if extensao in ALLOWED_EXTENSIONS and file not in IGNORE_FILES:
                    caminho_completo = os.path.join(root, file)
                    
                    # Cria um caminho relativo (ex: frontend_react/src/App.jsx) para ficar bonito
                    caminho_relativo = os.path.relpath(caminho_completo, diretorio_raiz)

                    try:
                        with open(caminho_completo, 'r', encoding='utf-8') as infile:
                            conteudo = infile.read()
                            
                            # Escreve o cabeçalho do arquivo
                            outfile.write(f"\n{'='*80}\n")
                            outfile.write(f"ARQUIVO: {caminho_relativo}\n")
                            outfile.write(f"{'='*80}\n\n")
                            
                            # Escreve o código
                            outfile.write(conteudo)
                            outfile.write("\n")
                            
                            print(f"Lido e extraído: {caminho_relativo}")
                    except Exception as e:
                        print(f"Erro ao ler {caminho_relativo}: {e}")

if __name__ == "__main__":
    diretorio_atual = os.getcwd()
    saida = os.path.join(diretorio_atual, "codigo_completo.txt")
    
    print("Iniciando extração de código...")
    consolidar_codigo(diretorio_atual, saida)
    print(f"\n✅ SUCESSO! Todo o seu código foi agrupado no arquivo: codigo_completo.txt")