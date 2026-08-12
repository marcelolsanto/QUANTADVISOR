import os
import re

def unpack_android():
    txt_path = "projeto_completo_arquitetura_v7.txt"
    target_dir = "android_app"

    if not os.path.exists(txt_path):
        print(f"Erro: Arquivo {txt_path} não encontrado.")
        return

    with open(txt_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Match sections defined as "FILE: path\to\file"
    pattern = r"===============================================================================\n\nFILE:\s+(.*?)\n-------------------------------------------------------------------------------\n(.*?)(?=\n===============================================================================|\Z)"
    matches = re.findall(pattern, content, re.DOTALL)

    extracted = 0
    for file_rel_path, file_code in matches:
        file_rel_path = file_rel_path.strip().replace("\\", "/")
        out_file_path = os.path.join(target_dir, file_rel_path)

        os.makedirs(os.path.dirname(out_file_path), exist_ok=True)
        with open(out_file_path, "w", encoding="utf-8") as out:
            out.write(file_code)
        extracted += 1

    print(f"✅ Sucesso! Extraídos {extracted} arquivos para a pasta '{target_dir}'.")

if __name__ == "__main__":
    unpack_android()
