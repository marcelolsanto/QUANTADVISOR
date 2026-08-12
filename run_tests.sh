#!/usr/bin/env bash
# ==============================================================================
# QuantAdvisor Test Suite Automation Runner
# ==============================================================================
set -e

echo "🚀 [QUANTADVISOR] Iniciando a Execução da Suíte Completa de Testes..."
echo ""

echo "----------------------------------------------------------------------"
echo "1. 🐍 Executando Testes Unitários e Econométricos da Engine Python..."
echo "----------------------------------------------------------------------"
docker compose exec -T quant_motor_python python3 -m pytest tests/

echo ""
echo "----------------------------------------------------------------------"
echo "2. 🐹 Executando Testes Unitários e Integrados do Motor Golang HFT..."
echo "----------------------------------------------------------------------"
docker compose exec -T quant_coletor_go go test -v ./...

echo ""
echo "----------------------------------------------------------------------"
echo "3. 📦 Executando Testes de Carga e Estresse (1.500 Conexões / SSE / IA)..."
echo "----------------------------------------------------------------------"
docker compose exec -T quant_motor_python python3 /workspace/tests/load/stress_test.py

echo ""
echo "✅ [QUANTADVISOR] Suíte completa de testes executada com sucesso!"
