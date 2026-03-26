/**
 * Configuração Tailwind personalizada para o front-end.
 * Define as cores oficiais da marca Inovare sob o namespace `brand`.
 * Permite classes como `bg-brand-primary` e `bg-brand-secondary`.
 */
module.exports = {
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx,vue}'
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          primary: '#feb56c',
          'primary-dark': '#f1a154',
          secondary: '#fed8b0'
        }
      }
    }
  },
  plugins: []
};
