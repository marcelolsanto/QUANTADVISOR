import os

# ==============================================================================
# A BARREIRA ANTI-LIXO (Filtros Quantitativos)
# ==============================================================================

IGNORE_DIRS = {
    '.git', 'node_modules', '__pycache__', 'venv', 'env', '.vscode',
    'dist', 'build', '.idea', 'postgres_data', 'raw_data', 'tmp'
}

ALLOWED_EXTENSIONS = {
    '.go', '.py', '.js', '.jsx', '.css', '.html', '.sql'
}

IGNORE_FILES = {
    'package-lock.json', 'go.sum', 'selic.json',
    'extrair_modulos.py', 'auditoria_matematica.log'
}

WHITELIST_FILES = {
    'Dockerfile', 'docker-compose.yml', 'package.json', 'vite.config.js'
}

# ==============================================================================
# MAPEAMENTO DOS MÓDULOS (Pasta de Origem -> Arquivo Gerado)
# ==============================================================================
MODULOS = [
    ("coletor_go", "codigo_coletor.txt"),
    ("motor_python", "codigo_motor.txt"),
    # Pega tudo do React, incluindo o quant-dashboard
    ("quant-dashboard", "codigo_dashboard.txt"),
    ("quant-mobile", "codigo_mobile.txt"),
    ("terminal-quantitativo-&-risco-de-cauda", "terminal-quantitativo-&-risco-de-cauda.txt")
]


def consolidar_modulos():
    diretorio_raiz = os.getcwd()

    print("Iniciando extração modular limpa...\n")

    for pasta_alvo, nome_arquivo_saida in MODULOS:
        caminho_pasta_alvo = os.path.join(diretorio_raiz, pasta_alvo)
        caminho_txt_saida = os.path.join(diretorio_raiz, nome_arquivo_saida)

        if not os.path.exists(caminho_pasta_alvo):
            print(
                f"⚠️ Aviso: A pasta '{pasta_alvo}' não foi encontrada na raiz.")
            continue

        contador_arquivos = 0

        with open(caminho_txt_saida, 'w', encoding='utf-8') as outfile:
            outfile.write(
                "========================================================\n")
            outfile.write(
                f" CÓDIGO FONTE LIMPO - MÓDULO: {pasta_alvo.upper()}\n")
            outfile.write(
                "========================================================\n\n")

            # Faz o walk APENAS dentro da pasta alvo específica
            for root, dirs, files in os.walk(caminho_pasta_alvo):
                # Remove o lixo (node_modules, etc)
                dirs[:] = [d for d in dirs if d not in IGNORE_DIRS]

                for file in files:
                    extensao = os.path.splitext(file)[1].lower()

                    is_valid_code = extensao in ALLOWED_EXTENSIONS
                    is_whitelist = file in WHITELIST_FILES
                    is_not_ignored = file not in IGNORE_FILES
                    # Evita ler os próprios txts gerados
                    is_not_txt = not file.endswith('.txt')

                    if (is_valid_code or is_whitelist) and is_not_ignored and is_not_txt:
                        caminho_completo = os.path.join(root, file)
                        caminho_relativo = os.path.relpath(
                            caminho_completo, diretorio_raiz)

                        try:
                            with open(caminho_completo, 'r', encoding='utf-8') as infile:
                                conteudo = infile.read()

                                outfile.write(f"\n{'='*80}\n")
                                outfile.write(f"ARQUIVO: {caminho_relativo}\n")
                                outfile.write(f"{'='*80}\n\n")
                                outfile.write(conteudo)
                                outfile.write("\n")

                                contador_arquivos += 1
                        except Exception as e:
                            print(f"⚠️ [AVISO] Falha ao ler o arquivo {caminho_relativo}: {str(e)}")

    print(f"✅ [{pasta_alvo}] -> Gerou '{nome_arquivo_saida}' ({contador_arquivos} arquivos extraídos).")


if __name__ == "__main__":
    consolidar_modulos()
    print("\n🚀 SUCESSO! Seus 3 arquivos de texto foram gerados na raiz do projeto.")
