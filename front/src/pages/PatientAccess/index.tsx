import React, { useState, useEffect, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import { 
  Calendar, 
  Clock, 
  User, 
  ShieldCheck, 
  RefreshCw, 
  ArrowRight,
  Info,
  MapPin,
  Facebook,
  Instagram,
  MessageCircle,
  Github,
  Maximize2
} from 'lucide-react';

export default function PatientAccess() {
  const { appointmentId } = useParams<{ appointmentId: string }>();

  useEffect(() => {
    console.log("[PatientAccess] Carregando acesso para o agendamento ID:", appointmentId);
  }, [appointmentId]);
  
  // Controle de fluxo
  const [unlocked, setUnlocked] = useState(false);
  
  // Digitos de validação secundária
  const [digits, setDigits] = useState<string[]>(['', '', '', '']);
  const inputRefs = [
    useRef<HTMLInputElement>(null),
    useRef<HTMLInputElement>(null),
    useRef<HTMLInputElement>(null),
    useRef<HTMLInputElement>(null)
  ];

  // Dados dos QR codes rotativos (Efêmeros)
  const [secondsLeft, setSecondsLeft] = useState(300);
  const [uuid1, setUuid1] = useState('ea544b1e-bf09-48b4-2e68-1a0fd2476899');
  const [uuid2, setUuid2] = useState('b61cf82b-734f-4545-b037-1caa13223c3d');

  // Controle de QR Code em tela cheia
  const [fullscreenCard, setFullscreenCard] = useState<'titular' | 'acompanhante' | null>(null);
  const modalRef = useRef<HTMLDivElement>(null);

  const openFullscreen = (cardType: 'titular' | 'acompanhante') => {
    setFullscreenCard(cardType);
    
    // Tenta usar API do navegador para tela cheia nativa
    setTimeout(() => {
      if (modalRef.current && modalRef.current.requestFullscreen) {
        modalRef.current.requestFullscreen().catch(() => {});
      }
    }, 50);
  };

  const closeFullscreen = () => {
    setFullscreenCard(null);
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => {});
    }
  };

  const getFullscreenData = () => {
    if (fullscreenCard === 'titular') {
      return { value: uuid1, title: "Acesso do Titular: Victor" };
    }
    if (fullscreenCard === 'acompanhante') {
      return { value: uuid2, title: "Acesso do Acompanhante: Teste" };
    }
    return null;
  };

  const fullscreenData = getFullscreenData();

  // Monitora saída da tela cheia nativa do browser para sincronizar o estado do React
  useEffect(() => {
    const handleFullscreenChange = () => {
      if (!document.fullscreenElement) {
        setFullscreenCard(null);
      }
    };

    document.addEventListener('fullscreenchange', handleFullscreenChange);
    return () => {
      document.removeEventListener('fullscreenchange', handleFullscreenChange);
    };
  }, []);

  // Controle do carrossel/slide
  const [activeCardIndex, setActiveCardIndex] = useState(0);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Efeito para decrementar o timer do QR Code rotativo
  useEffect(() => {
    if (!unlocked) return;
    
    const interval = setInterval(() => {
      setSecondsLeft((prev) => {
        if (prev <= 1) {
          // Quando expira, gera novos UUIDs mocks e reinicia o timer
          setUuid1(crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2, 15) + '-' + Math.random().toString(36).substring(2, 15));
          setUuid2(crypto.randomUUID ? crypto.randomUUID() : Math.random().toString(36).substring(2, 15) + '-' + Math.random().toString(36).substring(2, 15));
          return 300;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [unlocked]);

  // Formata o timer em MM:SS
  const formatTime = (secs: number) => {
    const m = Math.floor(secs / 60).toString().padStart(2, '0');
    const s = (secs % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  };

  // Manipulação de inputs dos dígitos do telefone
  const handleDigitChange = (index: number, val: string) => {
    const numericVal = val.replace(/\D/g, '');
    if (!numericVal) {
      const newDigits = [...digits];
      newDigits[index] = '';
      setDigits(newDigits);
      return;
    }
    const newDigits = [...digits];
    newDigits[index] = numericVal.substring(numericVal.length - 1);
    setDigits(newDigits);

    // Foca no próximo input se preenchido
    if (index < 3) {
      inputRefs[index + 1].current?.focus();
    }
  };

  const handleDigitKeyDown = (index: number, e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !digits[index] && index > 0) {
      const newDigits = [...digits];
      newDigits[index - 1] = '';
      setDigits(newDigits);
      inputRefs[index - 1].current?.focus();
    }
  };

  const handleValidate = (e: React.FormEvent) => {
    e.preventDefault();
    if (digits.every(d => d !== '')) {
      setUnlocked(true);
    }
  };

  // Atualiza bolinhas do carrossel ao rolar
  const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
    const container = e.currentTarget;
    const scrollLeft = container.scrollLeft;
    const width = container.clientWidth;
    // Calcula o index ativo com base na metade da rolagem do card
    const index = Math.round(scrollLeft / (width * 0.85));
    setActiveCardIndex(Math.min(Math.max(index, 0), 1));
  };

  const scrollToCard = (index: number) => {
    if (scrollRef.current) {
      const width = scrollRef.current.clientWidth;
      scrollRef.current.scrollTo({
        left: index * (width * 0.85),
        behavior: 'smooth'
      });
      setActiveCardIndex(index);
    }
  };

  const isFormComplete = digits.every(d => d !== '');

  // --- PASSO 1: TELA DE VALIDAÇÃO DE SEGURANÇA (LGPD) ---
  if (!unlocked) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-brand-secondary/35 via-slate-50 to-white flex items-center justify-center p-4 font-sans antialiased">
        <div className="w-full max-w-md bg-white/90 backdrop-blur-md rounded-3xl shadow-xl shadow-brand-primary/5 border border-white/60 p-8 flex flex-col justify-between min-h-[520px] transition-all">
          {/* Logo da Clínica */}
          <div className="text-center">
            <img 
              src="/Logo.png" 
              alt="Logo Inovare" 
              className="h-14 w-auto mx-auto mb-6 object-contain"
              onError={(e) => {
                // Fallback caso a imagem falhe ao carregar
                e.currentTarget.src = 'https://placehold.co/180x60/feb56c/ffffff?text=Inovare+TI';
              }}
            />
            
            <div className="inline-flex items-center gap-1.5 bg-brand-secondary/30 border border-brand-primary/10 rounded-full px-3 py-1 mb-4">
              <ShieldCheck className="w-4 h-4 text-brand-primary-dark" />
              <span className="text-xs text-brand-primary-dark font-semibold">Ambiente Seguro LGPD</span>
            </div>

            <h2 className="text-xl font-extrabold text-slate-800 tracking-tight">Validação de Acesso</h2>
            <p className="text-sm text-slate-500 mt-2.5 leading-relaxed max-w-[320px] mx-auto">
              Para sua privacidade e segurança, digite os <b>4 últimos dígitos</b> do número de telefone celular que recebeu este link.
            </p>
          </div>

          {/* Formulário de Código */}
          <form onSubmit={handleValidate} className="mt-8 flex-1 flex flex-col justify-between">
            <div className="space-y-4">
              <div className="flex justify-center gap-3.5">
                {digits.map((digit, index) => (
                  <input
                    key={index}
                    ref={inputRefs[index]}
                    type="text"
                    inputMode="numeric"
                    pattern="[0-9]*"
                    maxLength={1}
                    value={digit}
                    onChange={(e) => handleDigitChange(index, e.target.value)}
                    onKeyDown={(e) => handleDigitKeyDown(index, e)}
                    placeholder="•"
                    className="w-14 h-16 text-center text-2xl font-extrabold text-slate-800 border-2 border-slate-200 rounded-2xl focus:border-brand-primary focus:ring-4 focus:ring-brand-primary/10 bg-slate-50/50 transition-all font-mono placeholder:text-slate-300"
                  />
                ))}
              </div>
              <p className="text-xs text-slate-400 text-center flex items-center justify-center gap-1">
                <Info className="w-3.5 h-3.5 text-brand-primary" />
                Regra: digite qualquer valor de 4 dígitos para testar
              </p>
            </div>

            <button
              type="submit"
              disabled={!isFormComplete}
              className={`w-full py-4 px-6 rounded-2xl font-bold tracking-wide transition-all duration-300 mt-10 shadow-lg flex items-center justify-center gap-2 ${
                isFormComplete 
                  ? 'bg-gradient-to-r from-brand-primary to-brand-primary-dark hover:from-brand-primary hover:to-brand-primary-dark shadow-brand-primary/25 cursor-pointer active:scale-[0.98] text-white' 
                  : 'bg-slate-200 text-slate-400 shadow-none cursor-not-allowed'
              }`}
            >
              Confirmar Identidade
              <ArrowRight className="w-4 h-4" />
            </button>
          </form>
        </div>
      </div>
    );
  }

  // --- PASSO 2: EXIBIÇÃO DOS DADOS E CARROSSEL DE QR CODES ---
  return (
    <div className="min-h-screen bg-slate-100 flex flex-col justify-between font-sans antialiased">
      {/* Container Centralizado para Simular Mobile (Mobile-First Mockup) */}
      <div className="w-full max-w-md bg-white shadow-2xl shadow-brand-primary/5 border-x border-brand-secondary/35 flex flex-col min-h-screen mx-auto relative">
        
        {/* Header Superior */}
        <header className="sticky top-0 bg-white/95 backdrop-blur-md border-b border-brand-secondary/30 px-6 py-4 flex items-center justify-between z-10">
          <img 
            src="/Logo.png" 
            alt="Logo Inovare" 
            className="h-9 w-auto object-contain"
            onError={(e) => {
              e.currentTarget.src = 'https://placehold.co/120x40/feb56c/ffffff?text=Inovare+TI';
            }}
          />
          <div className="flex items-center gap-1.5 bg-emerald-50 text-emerald-700 border border-emerald-100 rounded-full px-3 py-1 text-xs font-bold">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
            Acesso Liberado
          </div>
        </header>

        {/* Conteúdo Principal */}
        <main className="flex-1 px-5 py-6 space-y-6 overflow-y-auto pb-12 bg-gradient-to-b from-white via-slate-50/50 to-slate-50">
          
          {/* Saudação Inicial */}
          <div className="space-y-1">
            <h1 className="text-xl font-extrabold text-slate-800 tracking-tight">Olá, Victor!</h1>
            <p className="text-xs text-slate-400 font-medium">Aqui estão seus cartões para liberação das catracas.</p>
          </div>

          {/* Card 1: Informações do Agendamento (Feegow Data) */}
          <div className="bg-gradient-to-br from-white via-brand-secondary/5 to-white border border-brand-primary/35 rounded-3xl p-5 space-y-4 shadow-sm shadow-brand-primary/5">
            <div className="flex items-center justify-between border-b border-brand-secondary/30 pb-3">
              <span className="text-xs text-brand-primary-dark font-extrabold tracking-wider uppercase">Detalhes da Consulta</span>
              <Calendar className="w-4 h-4 text-brand-primary" />
            </div>

            <div className="space-y-3.5">
              <div>
                <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block">Paciente Principal</span>
                <span className="text-sm font-extrabold text-slate-700">VICTOR GABRIEL DE OLIVEIRA HASS</span>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block">CPF</span>
                  <span className="text-xs font-semibold text-slate-600">***.916.171-**</span>
                </div>
                <div>
                  <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block">Nascimento</span>
                  <span className="text-xs font-semibold text-slate-600">26/10/2004</span>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4 border-t border-brand-secondary/30 pt-3">
                <div>
                  <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block">Data</span>
                  <span className="text-xs font-bold text-brand-primary-dark bg-brand-secondary/30 px-2 py-0.5 rounded-md inline-block mt-0.5 font-sans">
                    Hoje
                  </span>
                </div>
                <div>
                  <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block">Horário</span>
                  <span className="text-xs font-bold text-slate-700 flex items-center gap-1 mt-1">
                    <Clock className="w-3.5 h-3.5 text-brand-primary" />
                    14:00
                  </span>
                </div>
              </div>

              <div className="border-t border-brand-secondary/30 pt-3">
                <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block">Médico / Especialidade</span>
                <span className="text-xs font-semibold text-slate-700 flex items-center gap-1.5 mt-1">
                  <User className="w-3.5 h-3.5 text-brand-primary" />
                  Dr. Antonio Carlos Trevisan
                </span>
              </div>
            </div>
          </div>

          {/* Carrossel de QR Codes (Rolagem horizontal controlada) */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-slate-800">Cartões de Acesso (Catraca)</h3>
              <span className="text-[10px] bg-brand-secondary/40 text-brand-primary-dark rounded-full px-2.5 py-0.5 font-bold">
                Deslize para o lado
              </span>
            </div>

            {/* Container do Slide (Com snap) */}
            <div 
              ref={scrollRef}
              onScroll={handleScroll}
              className="flex gap-4 overflow-x-auto snap-x snap-mandatory scrollbar-none px-1 py-2"
              style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
            >
              {/* Card 1: Titular */}
              <div className="w-[88%] shrink-0 snap-center bg-white border border-brand-secondary/20 shadow-lg shadow-brand-primary/5 rounded-3xl p-5 flex flex-col items-center justify-between border-t-4 border-t-brand-primary">
                <div className="text-center w-full">
                  <span className="text-[10px] font-bold tracking-wider text-brand-primary uppercase block">Cartão 1</span>
                  <h4 className="text-sm font-bold text-slate-800 mt-0.5">Acesso do Titular: Victor</h4>
                  <p className="text-[11px] text-slate-400 mt-0.5">Passe na leitora ao entrar</p>
                </div>

                {/* QR Code Container */}
                <div className="my-5 p-5 border border-dashed border-brand-primary/40 rounded-2xl bg-brand-secondary/5 flex items-center justify-center relative">
                  {/* QR Code com alto contraste para facilitar leitura óptica */}
                  <QRCodeSVG 
                    value={uuid1} 
                    size={160} 
                    fgColor="#1e293b" // Escuro para facilidade na leitura óptica do leitor da catraca
                    bgColor="#f8fafc"
                  />
                  <div className="absolute top-2 right-2 flex items-center justify-center">
                    <span className="w-2.5 h-2.5 rounded-full bg-brand-primary animate-ping"></span>
                    <span className="absolute w-2.5 h-2.5 rounded-full bg-brand-primary"></span>
                  </div>
                </div>

                {/* Validade e Rotação */}
                <div className="w-full flex items-center justify-center gap-1.5 text-slate-500 bg-brand-secondary/15 rounded-xl py-2 px-3 border border-brand-primary/10 mb-3">
                  <RefreshCw className="w-3.5 h-3.5 animate-spin text-brand-primary-dark" style={{ animationDuration: '6s' }} />
                  <span className="text-[10px] font-medium tracking-wide">
                    Código expira em: <b className="font-bold text-slate-700 font-mono">{formatTime(secondsLeft)}</b>
                  </span>
                </div>

                {/* Botão de Tela Cheia */}
                <button 
                  onClick={() => openFullscreen('titular')}
                  className="w-full py-3 bg-slate-900 hover:bg-slate-800 active:scale-[0.98] text-white rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-1.5 shadow-sm cursor-pointer"
                >
                  <Maximize2 className="w-3.5 h-3.5 text-brand-primary" />
                  Ampliar QR Code
                </button>
              </div>

              {/* Card 2: Acompanhante */}
              <div className="w-[88%] shrink-0 snap-center bg-white border border-brand-secondary/20 shadow-lg shadow-brand-primary/5 rounded-3xl p-5 flex flex-col items-center justify-between border-t-4 border-t-brand-primary-dark">
                <div className="text-center w-full">
                  <span className="text-[10px] font-bold tracking-wider text-brand-primary-dark uppercase block">Cartão 2</span>
                  <h4 className="text-sm font-bold text-slate-800 mt-0.5">Acesso do Acompanhante: Teste</h4>
                  <p className="text-[11px] text-slate-400 mt-0.5">Passe na leitora em seguida</p>
                </div>

                {/* QR Code Container */}
                <div className="my-5 p-5 border border-dashed border-brand-primary-dark/40 rounded-2xl bg-brand-secondary/5 flex items-center justify-center relative">
                  <QRCodeSVG 
                    value={uuid2} 
                    size={160} 
                    fgColor="#1e293b"
                    bgColor="#f8fafc"
                  />
                  <div className="absolute top-2 right-2 flex items-center justify-center">
                    <span className="w-2.5 h-2.5 rounded-full bg-brand-primary-dark animate-ping"></span>
                    <span className="absolute w-2.5 h-2.5 rounded-full bg-brand-primary-dark"></span>
                  </div>
                </div>

                {/* Validade e Rotação */}
                <div className="w-full flex items-center justify-center gap-1.5 text-slate-500 bg-brand-secondary/15 rounded-xl py-2 px-3 border border-brand-primary-dark/10 mb-3">
                  <RefreshCw className="w-3.5 h-3.5 animate-spin text-brand-primary-dark" style={{ animationDuration: '6s' }} />
                  <span className="text-[10px] font-medium tracking-wide">
                    Código expira em: <b className="font-bold text-slate-700 font-mono">{formatTime(secondsLeft)}</b>
                  </span>
                </div>

                {/* Botão de Tela Cheia */}
                <button 
                  onClick={() => openFullscreen('acompanhante')}
                  className="w-full py-3 bg-slate-900 hover:bg-slate-800 active:scale-[0.98] text-white rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-1.5 shadow-sm cursor-pointer"
                >
                  <Maximize2 className="w-3.5 h-3.5 text-brand-primary" />
                  Ampliar QR Code
                </button>
              </div>
            </div>

            {/* Indicadores de Paginação (Dots) */}
            <div className="flex justify-center gap-1.5 mt-2">
              <button 
                onClick={() => scrollToCard(0)}
                className={`h-2 rounded-full transition-all duration-300 ${activeCardIndex === 0 ? 'w-6 bg-brand-primary' : 'w-2 bg-slate-200'}`}
                aria-label="Ir para cartão 1"
              />
              <button 
                onClick={() => scrollToCard(1)}
                className={`h-2 rounded-full transition-all duration-300 ${activeCardIndex === 1 ? 'w-6 bg-brand-primary' : 'w-2 bg-slate-200'}`}
                aria-label="Ir para cartão 2"
              />
            </div>
          </div>

          {/* Dica da Catraca */}
          <div className="bg-brand-secondary/15 border-l-4 border-brand-primary rounded-2xl p-4 flex gap-3 text-slate-700 shadow-sm">
            <Info className="w-5 h-5 text-brand-primary-dark shrink-0 mt-0.5" />
            <div className="space-y-1">
              <h5 className="text-xs font-bold text-brand-primary-dark uppercase tracking-wider">Instruções para Acesso</h5>
              <p className="text-[11px] leading-relaxed text-slate-650">
                Aproxime o QR Code na leitora da catraca. Se houver acompanhante, passe o primeiro cartão (Titular), aguarde a catraca girar e em seguida deslize para o lado e passe o segundo cartão.
              </p>
            </div>
          </div>

        </main>

        {/* Rodapé (SiteFooter Standard com Endereço e Link do Dev) */}
        <footer className="mt-auto border-t border-brand-secondary/30 bg-white py-8 px-6 text-center space-y-6">
          {/* Logo da Clínica no Rodapé */}
          <div className="flex flex-col items-center text-center gap-4">
            <div className="flex h-28 w-28 items-center justify-center rounded-3xl bg-brand-secondary/30 p-4 shadow-sm border border-brand-primary/10">
              <img
                src="/Logo.png"
                alt="Inovare – Serviços de Saúde"
                className="h-full w-full object-contain"
                onError={(e) => {
                  e.currentTarget.src = 'https://placehold.co/120x120/feb56c/ffffff?text=Inovare';
                }}
              />
            </div>

            {/* Bloco de Endereço da Clínica */}
            <div className="space-y-1.5 max-w-xs sm:max-w-md">
              <p className="text-sm font-extrabold uppercase tracking-wider text-brand-primary-dark">
                Inovare – Serviços de Saúde
              </p>
              <p className="text-[11px] text-slate-500 leading-relaxed font-semibold font-sans">
                R. Carlos Osternack, 111 - Vila Placidina, Ponta Grossa - PR, 84040-120
              </p>
              <p className="text-[10px] text-slate-400 font-bold">
                Atendimento: Segunda a sexta, 08h – 12h e 13h – 18h30
              </p>
            </div>
          </div>

          {/* Ações (Maps, WhatsApp, Redes Sociais) */}
          <div className="flex flex-wrap justify-center gap-2">
            <a 
              href="https://maps.app.goo.gl/ivTYbzpgdmX3XhUR7" 
              target="_blank" 
              rel="noopener noreferrer" 
              className="inline-flex items-center gap-1.5 px-3 py-2 bg-slate-50 hover:bg-brand-secondary/20 hover:text-brand-primary-dark border border-brand-primary/10 rounded-xl text-xs font-bold text-slate-650 transition-all shadow-sm"
            >
              <MapPin className="w-4 h-4 text-brand-primary" />
              Ver no Maps
            </a>
            <a 
              href="https://wa.me/554230262601" 
              target="_blank" 
              rel="noopener noreferrer" 
              className="inline-flex items-center gap-1.5 px-3 py-2 bg-slate-50 hover:bg-brand-secondary/20 hover:text-brand-primary-dark border border-brand-primary/10 rounded-xl text-xs font-bold text-slate-650 transition-all shadow-sm"
            >
              <MessageCircle className="w-4 h-4 text-emerald-500" />
              WhatsApp
            </a>
            <a 
              href="https://www.instagram.com/inovaress/" 
              target="_blank" 
              rel="noopener noreferrer" 
              className="inline-flex items-center gap-1.5 px-3 py-2 bg-slate-50 hover:bg-brand-secondary/20 hover:text-brand-primary-dark border border-brand-primary/10 rounded-xl text-xs font-bold text-slate-650 transition-all shadow-sm"
            >
              <Instagram className="w-4 h-4 text-pink-500" />
              Instagram
            </a>
            <a 
              href="https://www.facebook.com/inovarepg" 
              target="_blank" 
              rel="noopener noreferrer" 
              className="inline-flex items-center gap-1.5 px-3 py-2 bg-slate-50 hover:bg-brand-secondary/20 hover:text-brand-primary-dark border border-brand-primary/10 rounded-xl text-xs font-bold text-slate-650 transition-all shadow-sm"
            >
              <Facebook className="w-4 h-4 text-blue-600" />
              Facebook
            </a>
          </div>

          {/* Assinatura de Desenvolvimento com Ícone do Git */}
          <div className="border-t border-slate-100 pt-4 flex flex-col items-center gap-2">
            <p className="text-xs text-slate-400 flex items-center justify-center gap-1">
              Feito por
              <Github className="inline w-4 h-4 mx-1 text-slate-400" />
              <a
                href="https://github.com/VictorGHss"
                target="_blank"
                rel="noopener noreferrer"
                className="text-brand-primary-dark hover:text-brand-primary font-bold transition-colors underline underline-offset-2"
              >
                VictorGHss
              </a>
            </p>
          </div>
        </footer>

      </div>

      {/* Modal de Tela Cheia (Background Branco Puro para forçar brilho da tela do celular) */}
      {fullscreenData && (
        <div 
          ref={modalRef}
          className="fixed inset-0 z-50 bg-white flex flex-col items-center justify-between p-8"
        >
          {/* Header do Modal */}
          <div className="text-center mt-8">
            <span className="text-[10px] font-bold tracking-wider text-brand-primary uppercase block">Catraca de Acesso</span>
            <h4 className="text-lg font-bold text-slate-800 mt-1">{fullscreenData.title}</h4>
            <p className="text-xs text-slate-400 mt-1">Brilho da tela aumentado para leitura na catraca</p>
          </div>

          {/* QR Code centralizado ampliado */}
          <div className="flex flex-col items-center justify-center flex-1 my-6">
            <div className="p-6 bg-white border border-brand-secondary/35 rounded-3xl shadow-xl shadow-brand-primary/5">
              <QRCodeSVG 
                value={fullscreenData.value} 
                size={260} 
                fgColor="#0f172a" 
                bgColor="#ffffff"
              />
            </div>

            {/* Contador de expiração em tempo real */}
            <div className="flex items-center gap-1.5 text-slate-500 bg-brand-secondary/15 border border-brand-primary/10 rounded-full px-4 py-2 mt-8 text-xs font-semibold">
              <RefreshCw className="w-4 h-4 animate-spin text-brand-primary-dark" style={{ animationDuration: '6s' }} />
              <span>Expira em: <b className="font-bold text-slate-700 font-mono">{formatTime(secondsLeft)}</b></span>
            </div>
          </div>

          {/* Botão de Fechar */}
          <button 
            onClick={closeFullscreen}
            className="w-full max-w-sm py-4 bg-gradient-to-r from-brand-primary to-brand-primary-dark active:scale-[0.98] text-white rounded-2xl font-bold tracking-wide transition-all duration-300 shadow-lg shadow-brand-primary/20 cursor-pointer"
          >
            Fechar Tela Cheia
          </button>
        </div>
      )}
    </div>
  );
}
