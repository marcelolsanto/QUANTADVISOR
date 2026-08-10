import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tsEslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  ...tsEslint.config(
    js.configs.recommended,
    ...tsEslint.configs.recommended,
    {
      files: ['**/*.{js,jsx,ts,tsx}'],
      extends: [
        reactHooks.configs.flat.recommended,
        reactRefresh.configs.vite,
      ],
      languageOptions: {
        globals: globals.browser,
        parserOptions: { ecmaFeatures: { jsx: true } },
      },
      rules: {
        'no-unused-vars': 'warn',
        '@typescript-eslint/no-unused-vars': 'warn',
        'react-refresh/only-export-components': 'off',
        'react-hooks/set-state-in-effect': 'warn',
        'react-hooks/preserve-manual-memoization': 'warn',
      },
    }
  )
])
