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
  ExternalLink,
  MapPin
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
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4 font-sans">
        <div className="w-full max-w-md bg-white rounded-3xl shadow-xl shadow-slate-200/50 border border-slate-100 p-8 flex flex-col justify-between min-h-[520px]">
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
            
            <div className="inline-flex items-center gap-1.5 bg-slate-50 border border-slate-100 rounded-full px-3 py-1 mb-4">
              <ShieldCheck className="w-4 h-4 text-brand-primary" />
              <span className="text-xs text-slate-500 font-medium">Ambiente Seguro LGPD</span>
            </div>

            <h2 className="text-xl font-bold text-slate-800 tracking-tight">Validação de Acesso</h2>
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
                    className="w-14 h-16 text-center text-2xl font-bold text-slate-800 border-2 border-slate-200 rounded-2xl focus:border-brand-primary focus:ring-4 focus:ring-brand-primary/10 bg-slate-50/50 transition-all font-mono placeholder:text-slate-300"
                  />
                ))}
              </div>
              <p className="text-xs text-slate-400 text-center flex items-center justify-center gap-1">
                <Info className="w-3.5 h-3.5" />
                Regra: digite qualquer valor de 4 dígitos para testar
              </p>
            </div>

            <button
              type="submit"
              disabled={!isFormComplete}
              className={`w-full py-4 px-6 rounded-2xl font-semibold text-white tracking-wide transition-all duration-300 mt-10 shadow-lg flex items-center justify-center gap-2 ${
                isFormComplete 
                  ? 'bg-brand-primary hover:bg-brand-primary-dark shadow-brand-primary/25 cursor-pointer active:scale-[0.98]' 
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
    <div className="min-h-screen bg-slate-50 flex flex-col justify-between font-sans antialiased">
      {/* Container Centralizado para Simular Mobile (Mobile-First Mockup) */}
      <div className="w-full max-w-md bg-white shadow-2xl shadow-slate-200 border-x border-slate-100 flex flex-col min-h-screen mx-auto relative">
        
        {/* Header Superior */}
        <header className="sticky top-0 bg-white/95 backdrop-blur-md border-b border-slate-100 px-6 py-4 flex items-center justify-between z-10">
          <img 
            src="/Logo.png" 
            alt="Logo Inovare" 
            className="h-9 w-auto object-contain"
            onError={(e) => {
              e.currentTarget.src = 'https://placehold.co/120x40/feb56c/ffffff?text=Inovare+TI';
            }}
          />
          <div className="flex items-center gap-1 bg-emerald-50 text-emerald-700 border border-emerald-100 rounded-full px-2.5 py-0.5 text-xs font-semibold">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
            Acesso Liberado
          </div>
        </header>

        {/* Conteúdo Principal */}
        <main className="flex-1 px-5 py-6 space-y-6 overflow-y-auto pb-12">
          
          {/* Saudação Inicial */}
          <div className="space-y-1">
            <h1 className="text-xl font-bold text-slate-800 tracking-tight">Olá, Victor!</h1>
            <p className="text-xs text-slate-400">Aqui estão seus cartões para liberação das catracas.</p>
          </div>

          {/* Card 1: Informações do Agendamento (Feegow Data) */}
          <div className="bg-slate-50 border border-slate-150 rounded-3xl p-5 space-y-4">
            <div className="flex items-center justify-between border-b border-slate-200/60 pb-3">
              <span className="text-xs text-slate-400 font-semibold tracking-wider uppercase">Detalhes da Consulta</span>
              <Calendar className="w-4 h-4 text-brand-primary" />
            </div>

            <div className="space-y-3.5">
              <div>
                <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block">Paciente Principal</span>
                <span className="text-sm font-bold text-slate-700">VICTOR GABRIEL DE OLIVEIRA HASS</span>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block">CPF</span>
                  <span className="text-xs font-medium text-slate-600">***.916.171-**</span>
                </div>
                <div>
                  <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block">Nascimento</span>
                  <span className="text-xs font-medium text-slate-600">26/10/2004</span>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4 border-t border-slate-200/60 pt-3">
                <div>
                  <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block">Data</span>
                  <span className="text-xs font-bold text-emerald-600 flex items-center gap-1">
                    Hoje
                  </span>
                </div>
                <div>
                  <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block">Horário</span>
                  <span className="text-xs font-bold text-slate-700 flex items-center gap-1">
                    <Clock className="w-3 h-3 text-slate-400" />
                    14:00
                  </span>
                </div>
              </div>

              <div className="border-t border-slate-200/60 pt-3">
                <span className="text-[10px] text-slate-400 font-bold uppercase tracking-wider block">Médico / Especialidade</span>
                <span className="text-xs font-semibold text-slate-700 flex items-center gap-1.5 mt-0.5">
                  <User className="w-3.5 h-3.5 text-slate-400" />
                  Dr. Antonio Carlos Trevisan
                </span>
              </div>
            </div>
          </div>

          {/* Carrossel de QR Codes (Rolagem horizontal controlada) */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-slate-800">Cartões de Acesso (Catraca)</h3>
              <span className="text-[10px] bg-slate-100 text-slate-500 rounded-full px-2 py-0.5 font-medium">
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
              <div className="w-[88%] shrink-0 snap-center bg-white border border-slate-200/80 shadow-lg shadow-slate-100/50 rounded-3xl p-5 flex flex-col items-center justify-between border-t-4 border-t-brand-primary">
                <div className="text-center w-full">
                  <span className="text-[10px] font-bold tracking-wider text-brand-primary uppercase block">Cartão 1</span>
                  <h4 className="text-sm font-bold text-slate-800 mt-0.5">Acesso do Titular: Victor</h4>
                  <p className="text-[11px] text-slate-400 mt-0.5">Passe na leitora ao entrar</p>
                </div>

                {/* QR Code Container */}
                <div className="my-5 p-4 border border-dashed border-slate-200 rounded-2xl bg-slate-50 flex items-center justify-center relative">
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
                <div className="w-full flex items-center justify-center gap-1.5 text-slate-500 bg-slate-50 rounded-xl py-2 px-3 border border-slate-100">
                  <RefreshCw className="w-3 h-3 animate-spin text-brand-primary" style={{ animationDuration: '6s' }} />
                  <span className="text-[10px] font-medium tracking-wide">
                    Código expira em: <b className="font-bold text-slate-700 font-mono">{formatTime(secondsLeft)}</b>
                  </span>
                </div>
              </div>

              {/* Card 2: Dependente */}
              <div className="w-[88%] shrink-0 snap-center bg-white border border-slate-200/80 shadow-lg shadow-slate-100/50 rounded-3xl p-5 flex flex-col items-center justify-between border-t-4 border-t-brand-primary-dark">
                <div className="text-center w-full">
                  <span className="text-[10px] font-bold tracking-wider text-brand-primary-dark uppercase block">Cartão 2</span>
                  <h4 className="text-sm font-bold text-slate-800 mt-0.5">Acesso do Dependente: Teste</h4>
                  <p className="text-[11px] text-slate-400 mt-0.5">Passe na leitora em seguida</p>
                </div>

                {/* QR Code Container */}
                <div className="my-5 p-4 border border-dashed border-slate-200 rounded-2xl bg-slate-50 flex items-center justify-center relative">
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
                <div className="w-full flex items-center justify-center gap-1.5 text-slate-500 bg-slate-50 rounded-xl py-2 px-3 border border-slate-100">
                  <RefreshCw className="w-3 h-3 animate-spin text-brand-primary-dark" style={{ animationDuration: '6s' }} />
                  <span className="text-[10px] font-medium tracking-wide">
                    Código expira em: <b className="font-bold text-slate-700 font-mono">{formatTime(secondsLeft)}</b>
                  </span>
                </div>
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
          <div className="bg-amber-50/50 border border-amber-100 rounded-2xl p-4 flex gap-3 text-amber-800">
            <Info className="w-5 h-5 text-amber-500 shrink-0 mt-0.5" />
            <div className="space-y-1">
              <h5 className="text-xs font-bold">Instruções para Acesso</h5>
              <p className="text-[11px] leading-relaxed text-amber-700/90">
                Aproxime o QR Code na leitora da catraca. Se houver dependente, passe o primeiro cartão (Titular), aguarde a catraca girar e em seguida deslize para o lado e passe o segundo cartão.
              </p>
            </div>
          </div>

        </main>

        {/* Rodapé (SiteFooter Standard com Endereço e Link do Dev) */}
        <footer className="mt-auto border-t border-slate-100 bg-white py-6 px-6 text-center space-y-5">
          {/* Bloco de Endereço da Clínica */}
          <div className="space-y-2 text-slate-600 bg-slate-50 rounded-2xl p-4 border border-slate-100">
            <h5 className="text-xs font-bold text-slate-700 flex items-center justify-center gap-1.5">
              <MapPin className="w-4 h-4 text-brand-primary shrink-0" />
              Inovare – Serviços de Saúde
            </h5>
            <p className="text-[11px] leading-relaxed text-slate-500 max-w-[280px] mx-auto">
              R. Carlos Osternack, 111 - Vila Placidina, Ponta Grossa - PR, 84040-120
            </p>
            <p className="text-[10px] text-slate-400 italic">
              Atendimento: Segunda a sexta, 08h – 12h e 13h – 18h30
            </p>
            <a 
              href="https://share.google/JYhgFNv4A58Tz26VB" 
              target="_blank" 
              rel="noopener noreferrer" 
              className="inline-flex items-center gap-1.5 mt-1.5 px-3 py-1.5 bg-white border border-slate-200 hover:border-brand-primary text-[10px] font-bold text-slate-600 hover:text-brand-primary rounded-lg transition-colors shadow-sm shadow-slate-100"
            >
              Ver no Google Maps
              <ExternalLink className="w-2.5 h-2.5" />
            </a>
          </div>

          <div className="flex justify-center items-center gap-4 text-xs font-medium text-slate-500">
            <a href="https://itsm-inovare.ctrls.dev.br/suporte" target="_blank" rel="noopener noreferrer" className="hover:text-brand-primary transition-colors">
              Suporte Clínico
            </a>
            <span className="text-slate-300">•</span>
            <a href="https://itsm-inovare.ctrls.dev.br/privacidade" target="_blank" rel="noopener noreferrer" className="hover:text-brand-primary transition-colors">
              Políticas de Privacidade
            </a>
          </div>

          <div className="border-t border-slate-100 pt-4 flex flex-col items-center gap-2">
            <p className="text-[10px] text-slate-400 leading-normal">
              © 2026 Inovare TI • Todos os direitos reservados
            </p>
            <p className="text-[10px] text-slate-400 flex items-center justify-center gap-1">
              Feito por 
              <a 
                href="https://github.com/VictorGHss" 
                target="_blank" 
                rel="noopener noreferrer" 
                className="text-brand-primary hover:text-brand-primary-dark font-semibold transition-colors flex items-center gap-0.5 underline decoration-brand-primary/30 underline-offset-2"
              >
                VictorGHss <ExternalLink className="w-2.5 h-2.5 inline" />
              </a>
            </p>
          </div>
        </footer>

      </div>
    </div>
  );
}
