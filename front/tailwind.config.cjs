/**
 * Configuração Tailwind personalizada para o front-end.
 * Adiciona a cor `inovare` mapeada para o HEX `#ffa751`.
 */
module.exports = {
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx,vue}'
  ],
  theme: {
    extend: {
      colors: {
        inovare: '#ffa751'
      }
    }
  },
  plugins: []
};
