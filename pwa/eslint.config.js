import js from '@eslint/js';
import globals from 'globals';
import hooks from 'eslint-plugin-react-hooks';

export default [
  {ignores: ['dist/**', 'node_modules/**']},
  js.configs.recommended,
  {
    files: ['**/*.{js,jsx}'],
    languageOptions: {
      ecmaVersion: 2023,
      sourceType: 'module',
      parserOptions: {ecmaFeatures: {jsx: true}},
      globals: {...globals.browser, BarcodeDetector: 'readonly'},
    },
    plugins: {'react-hooks': hooks},
    rules: {
      ...hooks.configs.recommended.rules,
      'no-unused-vars': ['error', {argsIgnorePattern: '^_'}],
    },
  },
  {
    // Node tooling, not browser code. Previously linted with browser globals only, so
    // process/console/navigator all reported as undefined.
    files: ['*.mjs', 'vite.config.js', 'eslint.config.js'],
    languageOptions: {
      ecmaVersion: 2023,
      sourceType: 'module',
      globals: {...globals.node, ...globals.browser},
    },
  },
];
