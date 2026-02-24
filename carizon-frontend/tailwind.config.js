/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50:  '#eff6ff',
          100: '#dbeafe',
          500: '#3b82f6',
          600: '#2563eb',
          700: '#1d4ed8',
        },
      },
      fontFamily: {
        sans: ['Pretendard', 'Noto Sans KR', 'sans-serif'],
      },
      animation: {
        'fade-in': 'fadeIn 0.35s ease both',
        'slide-up': 'slideUp 0.4s ease both',
        'slide-down': 'slideDown 0.35s ease both',
        'sheet-up': 'sheetUp 0.42s cubic-bezier(0.32,0.72,0,1) both',
        'dialog-in': 'dialogIn 0.3s cubic-bezier(0.34,1.2,0.64,1) both',
      },
      keyframes: {
        fadeIn:    { from: { opacity: 0 }, to: { opacity: 1 } },
        slideUp:   { from: { opacity: 0, transform: 'translateY(12px)' }, to: { opacity: 1, transform: 'translateY(0)' } },
        slideDown: { from: { opacity: 0, transform: 'translateY(-18px)' }, to: { opacity: 1, transform: 'translateY(0)' } },
        sheetUp:   { from: { transform: 'translateY(100%)' }, to: { transform: 'translateY(0)' } },
        dialogIn:  { from: { opacity: 0, transform: 'scale(0.96) translateY(8px)' }, to: { opacity: 1, transform: 'scale(1) translateY(0)' } },
      },
    },
  },
  plugins: [],
}
