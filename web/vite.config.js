import { defineConfig } from 'vite';
import { readdirSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';

// Páginas: el inicio y una por bug (las genera scripts/generar.mjs desde datos/matriz.json).
const raiz = import.meta.dirname;
const entradas = { inicio: resolve(raiz, 'index.html'), noEncontrada: resolve(raiz, '404.html') };
if (existsSync(resolve(raiz, 'bug'))) {
  for (const slug of readdirSync(resolve(raiz, 'bug'))) entradas['bug-' + slug] = resolve(raiz, 'bug', slug, 'index.html');
}

export default defineConfig(({ command }) => ({
  base: command === 'build' ? '/cuadre/' : '/',
  publicDir: 'publico',
  server: { host: '127.0.0.1' },
  build: { outDir: 'dist', emptyOutDir: true, rollupOptions: { input: entradas } },
}));
