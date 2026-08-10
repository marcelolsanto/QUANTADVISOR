FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV PATH=$PATH:/usr/local/go/bin

# 1. Instalar dependências modernas e Python 3.10
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl wget git build-essential \
    python3 python3-pip python3-dev \
    r-base r-base-dev \
    pkg-config libfreetype6-dev libpng-dev libtiff5-dev libjpeg-dev libwebp-dev libxml2-dev \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# 2. Instalar o Motor Golang
RUN curl -O https://dl.google.com/go/go1.22.2.linux-amd64.tar.gz \
    && tar -C /usr/local -xzf go1.22.2.linux-amd64.tar.gz \
    && rm go1.22.2.linux-amd64.tar.gz

# 3. Pacotes de Estatística R (Mantido para retrocompatibilidade)
RUN Rscript -e "install.packages(c('plumber', 'dplyr', 'readr', 'jsonlite', 'shiny'), repos='https://cloud.r-project.org')"

WORKDIR /workspace
COPY . /workspace

# 4. A MÁGICA DE MLOPS: Pré-instala as IAs e compila o Go direto na imagem!
# O servidor vai ligar instantaneamente daqui pra frente.

# Checkpoint A: Instala a IA (Versão CPU) e SALVA na memória do Docker
RUN cd motor_python && pip3 install --default-timeout=100 --retries=10 --no-cache-dir torch==2.1.0 --index-url https://download.pytorch.org/whl/cpu

# Checkpoint B: Instala o resto das bibliotecas (se a internet cair aqui, o PyTorch já não repete!)
RUN cd motor_python && pip3 install --no-cache-dir --default-timeout=1000 --retries=20 -r requirements.txt --extra-index-url https://download.pytorch.org/whl/cpu

# Checkpoint C: Compila o Executor Go forçando a versão local
RUN rm -rf /usr/local/go \
    && curl -O https://dl.google.com/go/go1.26.1.linux-amd64.tar.gz \
    && tar -C /usr/local -xzf go1.26.1.linux-amd64.tar.gz \
    && rm go1.26.1.linux-amd64.tar.gz \
    && cd coletor_go \
    && export GOTOOLCHAIN=local \
    && export GOPROXY=https://proxy.golang.org,direct \
    && go mod download \
    && go build -o quant_engine .