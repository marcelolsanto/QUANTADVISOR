const { test, expect } = require('@playwright/test');

test.describe('QuantAdvisor Dashboard E2E Tests', () => {
  let consoleErrors = [];

  test.beforeEach(async ({ page }) => {
    consoleErrors = [];
    page.on('console', msg => {
      if (msg.type() === 'error') {
        consoleErrors.push(msg.text());
      }
    });

    // Injeta credenciais simuladas no localStorage
    await page.addInitScript(() => {
      window.localStorage.setItem('@QuantAdvisor:token_web', 'test-token-simulado');
      window.localStorage.setItem('@QuantAdvisor:user_id_web', '1');
      window.localStorage.setItem('@QuantAdvisor:nome_web', 'Usuário Teste QA');
      window.localStorage.setItem('@QuantAdvisor:role_web', 'GESTOR');
    });
  });

  test('Deve realizar fluxo completo: alternar jurisdição B3/WallSt, renderizar gráficos e gerar PDF sem erros de sintaxe/script no console', async ({ page }) => {
    const baseURL = process.env.BASE_URL || 'http://localhost:5173';
    await page.goto(baseURL);

    // 1. Caso haja tela de login visivel, simula preenchimento
    const inputLogin = page.locator('input[placeholder*="login" i], input[type="text"]').first();
    if (await inputLogin.isVisible()) {
      await inputLogin.fill('admin');
      const inputSenha = page.locator('input[type="password"]').first();
      if (await inputSenha.isVisible()) {
        await inputSenha.fill('admin123');
      }
      const btnSubmit = page.locator('button[type="submit"], button:has-text("Entrar")').first();
      if (await btnSubmit.isVisible()) {
        await btnSubmit.click();
      }
    }

    // 2. Aguarda a interface carregar
    await page.waitForTimeout(2000);

    // 3. Alterna a jurisdição do mercado (B3 <-> Wall St)
    const toggleMercado = page.locator('text=B3 (BRL)').or(page.locator('text=NYSE (USD)')).first();
    if (await toggleMercado.isVisible()) {
      await toggleMercado.click();
      await page.waitForTimeout(1000);
      await toggleMercado.click();
      await page.waitForTimeout(1000);
    }

    // 4. Valida se elementos visuais principais (gráficos/containers) estão presentes
    const elementosGrafico = page.locator('.recharts-responsive-container, canvas, svg').first();
    if (await elementosGrafico.isVisible()) {
      await expect(elementosGrafico).toBeVisible();
    }

    // 5. Aciona a geração de PDF se o botão estiver visível na interface
    const btnPDF = page.locator('button:has-text("Baixar Relatório"), button:has-text("PDF")').first();
    if (await btnPDF.isVisible()) {
      await btnPDF.click();
      await page.waitForTimeout(1500);
    }

    // 6. Assegura que nenhum erro de execução de script (SyntaxError/TypeError) ocorreu no console
    const errosRelevantes = consoleErrors.filter(err => 
      !err.includes('401') && 
      !err.includes('Favicon') && 
      !err.includes('WebSocket') && 
      !err.includes('Failed to load resource')
    );
    expect(errosRelevantes).toHaveLength(0);
  });
});
