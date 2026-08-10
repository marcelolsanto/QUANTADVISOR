# ARQUIVO: motor_python/test_projecao.py
import pytest
import math
from projecao import converter_taxa_anual_para_mensal, calcular_imposto_regressivo

class TestMatematicaFinanceira:
    
    def test_conversao_taxa_juros_compostos(self):
        """
        Testa se a conversão de 10.5% ao ano resulta na taxa mensal correta (~0.83%).
        Garante que a projeção de Monte Carlo não inflará os rendimentos.
        """
        taxa_anual = 0.105
        taxa_mensal = converter_taxa_anual_para_mensal(taxa_anual)
        
        # Comparamos com tolerância para evitar erros de ponto flutuante na CPU
        assert math.isclose(taxa_mensal, 0.008355, rel_tol=1e-4), f"Taxa mensal calculada errada: {taxa_mensal}"

class TestMalhaFinaFiscal:

    def test_imposto_regressivo_curtissimo_prazo(self):
        """Até 180 dias (6 meses) = Alíquota máxima de 22.5%"""
        lucro = 1000.00
        imposto = calcular_imposto_regressivo(meses=5, lucro=lucro)
        assert imposto == 225.00

    def test_imposto_regressivo_medio_prazo(self):
        """De 181 a 360 dias (12 meses) = Alíquota de 20.0%"""
        lucro = 1000.00
        imposto = calcular_imposto_regressivo(meses=11, lucro=lucro)
        assert imposto == 200.00

    def test_imposto_regressivo_longo_prazo(self):
        """Mais de 720 dias (24 meses) = Alíquota mínima de 15.0%"""
        lucro = 1000.00
        imposto = calcular_imposto_regressivo(meses=36, lucro=lucro)
        assert imposto == 150.00

    def test_imposto_regressivo_sem_lucro(self):
        """O Leão não cobra sobre prejuízo. Lucro zero ou negativo deve gerar IR zero."""
        imposto = calcular_imposto_regressivo(meses=10, lucro=-500.00)
        assert imposto == 0.0

    def test_imposto_empate(self):
        """Zero a zero (breakeven) = Sem imposto."""
        imposto = calcular_imposto_regressivo(meses=10, lucro=0.0)
        assert imposto == 0.0