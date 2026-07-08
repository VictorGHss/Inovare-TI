import React, { useState, useEffect, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import { 
  Clock, 
  ShieldCheck, 
  RefreshCw, 
  ArrowRight,
  Info,
  AlertTriangle,
  MapPin,
  Facebook,
  Instagram,
  MessageCircle,
  Github,
  Maximize2,
  Lock
} from 'lucide-react';
import api from '../../services/api';

/**
 * Interface representando a credencial física retornada pelo backend.
 * Todos os campos escritos em inglês conforme as regras de nomenclatura do projeto.
 */
interface AccessCredential {
  name: string;
  userType: 'PATIENT' | 'COMPANION';
  locator: string;
  credentialCode: string;
  cpf?: string;
  doctorName?: string;
  appointmentDateTime?: string;
}

const formatCpf = (cpf?: string) => {
  if (!cpf) return '';
  const clean = cpf.replace(/\D/g, '');
  if (clean.length !== 11) return cpf;
  return `${clean.substring(0, 3)}.${clean.substring(3, 6)}.${clean.substring(6, 9)}-${clean.substring(9)}`;
};

export default function PatientAccess() {
  const { appointmentId } = useParams<{ appointmentId: string }>();

  // --- Estados de controle do desafio de identidade (2FA por telefone) ---
  // isVerified controla se o desafio foi concluído com sucesso
  const [isVerified, setIsVerified] = useState<boolean>(false);

  // Dígitos de entrada do desafio — 4 campos separados para UX otimizada mobile
  const [digits, setDigits] = useState<string[]>(['', '', '', '']);
  const inputRefs = [
    useRef<HTMLInputElement>(null),
    useRef<HTMLInputElement>(null),
    useRef<HTMLInputElement>(null),
    useRef<HTMLInputElement>(null)
  ];

  // Estado de carregamento da requisição de validação do desafio
  const [challengeLoading, setChallengeLoading] = useState<boolean>(false);

  // Mensagem de erro do desafio exibida caso os dígitos estejam incorretos
  const [challengeError, setChallengeError] = useState<string | null>(null);

  // --- Estados de controle das credenciais retornadas após o desafio ---
  const [credentials, setCredentials] = useState<AccessCredential[]>([]);

  // Controle de QR Code em tela cheia
  const [fullscreenCard, setFullscreenCard] = useState<number | null>(null);
  const modalRef = useRef<HTMLDivElement>(null);

  // Controle do carrossel/slide horizontal
  const [activeCardIndex, setActiveCardIndex] = useState(0);
  const scrollRef = useRef<HTMLDivElement>(null);

  const openFullscreen = (index: number) => {
    setFullscreenCard(index);
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
    if (fullscreenCard !== null && credentials[fullscreenCard]) {
      const cred = credentials[fullscreenCard];
      return { 
        value: cred.credentialCode, 
        title: `Acesso do ${cred.userType === 'PATIENT' ? 'Titular' : 'Acompanhante'}: ${cred.name}` 
      };
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

  // Manipulação dos inputs dos dígitos de desafio com navegação automática entre campos
  const handleDigitChange = (index: number, val: string) => {
    // Limpa a mensagem de erro imediatamente ao paciente começar a redigitar,
    // evitando que a mensagem de erro da tentativa anterior fique travada na tela.
    setChallengeError(null);

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

    // Avança automaticamente o foco para o próximo campo ao preencher
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

  /**
   * Envia os 4 dígitos para a API de credenciais com validação do desafio.
   * Em caso de sucesso: marca isVerified = true e armazena as credenciais retornadas.
   * Em caso de erro (400/401): exibe a mensagem de erro e limpa os campos.
   */
  const handleUnlock = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!appointmentId || digits.some(d => d === '')) return;

    const phoneDigits = digits.join('');
    setChallengeLoading(true);
    setChallengeError(null);

    try {
      console.log('[PatientAccess] Enviando desafio de 4 dígitos para o agendamento:', appointmentId);
      const response = await api.get<AccessCredential[]>(
        `/v1/access/credentials/${appointmentId}?phoneDigits=${phoneDigits}`
      );
      // Desafio validado com sucesso: libera o carrossel
      setCredentials(response.data || []);
      setIsVerified(true);
    } catch (err: unknown) {
      console.error('[PatientAccess] Falha no desafio de segurança:', err);
      // Exibe mensagem de erro e limpa os campos para nova tentativa
      setChallengeError('Os dígitos informados estão incorretos. Por favor, tente novamente.');
      setDigits(['', '', '', '']);
      setTimeout(() => inputRefs[0].current?.focus(), 50);
    } finally {
      setChallengeLoading(false);
    }
  };

  // Atualiza bolinhas do carrossel ao rolar
  const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
    const container = e.currentTarget;
    const scrollLeft = container.scrollLeft;
    const width = container.clientWidth;
    const index = Math.round(scrollLeft / (width * 0.85));
    setActiveCardIndex(Math.min(Math.max(index, 0), credentials.length - 1));
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

  // === TELA DE DESAFIO DE IDENTIDADE (2FA) ===
  // Exibida antes que o usuário desbloqueie os QR Codes com os 4 dígitos do telefone
  if (!isVerified) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-brand-secondary/35 via-slate-50 to-white flex items-center justify-center p-4 font-sans antialiased">
        <div className="w-full max-w-md bg-white/90 backdrop-blur-md rounded-3xl shadow-xl shadow-brand-primary/5 border border-white/60 p-8 flex flex-col justify-between min-h-[580px] transition-all">
          
          {/* Logo da Clínica */}
          <div className="text-center">
            <img 
              src="/Logo.png" 
              alt="Logo Inovare" 
              className="h-14 w-auto mx-auto mb-6 object-contain"
              onError={(e) => {
                e.currentTarget.src = 'https://placehold.co/180x60/feb56c/ffffff?text=Inovare+TI';
              }}
            />
            
            {/* Badge de Segurança */}
            <div className="inline-flex items-center gap-1.5 bg-brand-secondary/30 border border-brand-primary/10 rounded-full px-3 py-1 mb-4">
              <ShieldCheck className="w-4 h-4 text-brand-primary-dark" />
              <span className="text-xs text-brand-primary-dark font-semibold">Verificação de Identidade</span>
            </div>

            <h2 className="text-xl font-extrabold text-slate-800 tracking-tight">Desbloquear Acesso</h2>
            
            {/* Mensagem do desafio conforme especificado */}
            <p className="text-sm text-slate-500 mt-2.5 leading-relaxed max-w-[320px] mx-auto">
              Para sua segurança e desbloqueio dos seus QR Codes de entrada, informe os{' '}
              <b>4 últimos dígitos</b> do número de telefone que recebeu a mensagem de confirmação.
            </p>
          </div>

          {/* Formulário de Desafio */}
          <form onSubmit={handleUnlock} className="mt-8 flex-1 flex flex-col justify-between">
            <div className="space-y-4">
              
              {/* Inputs dos 4 dígitos separados para otimização mobile */}
              <div className="flex justify-center gap-3.5">
                {digits.map((digit, index) => (
                  <input
                    key={index}
                    ref={inputRefs[index]}
                    id={`digit-input-${index}`}
                    type="text"
                    inputMode="numeric"
                    pattern="[0-9]*"
                    maxLength={1}
                    value={digit}
                    onChange={(e) => handleDigitChange(index, e.target.value)}
                    onKeyDown={(e) => handleDigitKeyDown(index, e)}
                    placeholder="•"
                    disabled={challengeLoading}
                    className={`w-14 h-16 text-center text-2xl font-extrabold text-slate-800 border-2 rounded-2xl focus:ring-4 bg-slate-50/50 transition-all font-mono placeholder:text-slate-300 disabled:opacity-50 disabled:cursor-wait ${
                      challengeError
                        ? 'border-red-400 focus:border-red-500 focus:ring-red-100'
                        : 'border-slate-200 focus:border-brand-primary focus:ring-brand-primary/10'
                    }`}
                  />
                ))}
              </div>

              {/* Mensagem de erro do desafio */}
              {challengeError && (
                <div className="flex items-start gap-2 bg-red-50 border border-red-200 rounded-xl px-3 py-2.5 text-red-700">
                  <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
                  <p className="text-xs font-semibold leading-relaxed">{challengeError}</p>
                </div>
              )}

              {/* Ícone de cadeado + texto de orientação */}
              <p className="text-xs text-slate-400 text-center flex items-center justify-center gap-1.5">
                <Lock className="w-3.5 h-3.5 text-brand-primary" />
                Os QR Codes são exibidos somente após a verificação
              </p>
            </div>

            {/* Botão de Desbloqueio */}
            <button
              type="submit"
              id="unlock-access-button"
              disabled={!isFormComplete || challengeLoading}
              className={`w-full py-4 px-6 rounded-2xl font-bold tracking-wide transition-all duration-300 mt-10 shadow-lg flex items-center justify-center gap-2 ${
                isFormComplete && !challengeLoading
                  ? 'bg-gradient-to-r from-brand-primary to-brand-primary-dark hover:from-brand-primary hover:to-brand-primary-dark shadow-brand-primary/25 cursor-pointer active:scale-[0.98] text-white' 
                  : 'bg-slate-200 text-slate-400 shadow-none cursor-not-allowed'
              }`}
            >
              {challengeLoading ? (
                <>
                  <RefreshCw className="w-4 h-4 animate-spin" />
                  Verificando...
                </>
              ) : (
                <>
                  Desbloquear Acesso
                  <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </form>
        </div>
      </div>
    );
  }

  // Identifica a credencial do paciente titular para os detalhes superiores
  const patientCredential = credentials.find(c => c.userType === 'PATIENT') || credentials[0];

  // === TELA PRINCIPAL (CARROSSEL DE CREDENCIAIS / CONTINGÊNCIA ARRAY VAZIO) ===
  return (
    <div className="min-h-screen bg-slate-100 flex flex-col justify-between font-sans antialiased">
      {/* Container Centralizado para Simulação Mobile (Mobile-First) */}
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
          
          {/* Saudação Inicial baseada no Paciente Titular */}
          <div className="space-y-1">
            <h1 className="text-xl font-extrabold text-slate-800 tracking-tight">
              Olá{patientCredential ? `, ${patientCredential.name.split(' ')[0]}` : ''}!
            </h1>
            <p className="text-xs text-slate-400 font-medium">Aqui estão seus cartões para liberação das catracas físicas.</p>
          </div>

          {/* Fluxo Condicional: Carrossel de Credenciais vs Mensagem de Contingência */}
          {credentials.length === 0 ? (
            /* Cenário de contingência: credenciais ainda não geradas no servidor */
            <div className="bg-brand-secondary/10 border border-brand-primary/20 rounded-3xl p-6 text-center space-y-4 shadow-sm">
              <div className="w-16 h-16 bg-brand-primary/10 rounded-full flex items-center justify-center mx-auto text-brand-primary animate-pulse">
                <Clock className="w-8 h-8" />
              </div>
              <h3 className="text-md font-bold text-slate-800">Acesso em Processamento</h3>
              <p className="text-xs text-slate-600 leading-relaxed">
                Seu acesso prévio está em processamento. 📲 Caso a catraca não libere automaticamente ao chegar, informe seu nome na recepção para liberação imediata!
              </p>
            </div>
          ) : (
            /* Carrossel de cartões de credenciais */
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-bold text-slate-800">Cartões de Acesso (Catraca)</h3>
                {credentials.length > 1 && (
                  <span className="text-[10px] bg-brand-secondary/40 text-brand-primary-dark rounded-full px-2.5 py-0.5 font-bold">
                    Deslize para o lado ({activeCardIndex + 1}/{credentials.length})
                  </span>
                )}
              </div>

              {/* Slider de rolagem horizontal com snap CSS */}
              <div 
                ref={scrollRef}
                onScroll={handleScroll}
                className="flex gap-4 overflow-x-auto snap-x snap-mandatory scrollbar-none px-1 py-2"
                style={{ scrollbarWidth: 'none', msOverflowStyle: 'none' }}
              >
                {credentials.map((cred, idx) => (
                  <div 
                    key={idx}
                    className={`w-[88%] shrink-0 snap-center bg-white border border-brand-secondary/20 shadow-lg shadow-brand-primary/5 rounded-3xl p-5 flex flex-col items-center justify-between border-t-4 ${
                      cred.userType === 'PATIENT' ? 'border-t-brand-primary' : 'border-t-brand-primary-dark'
                    }`}
                  >
                    <div className="text-center w-full">
                      <span className="text-[10px] font-bold tracking-wider text-brand-primary uppercase block">
                        Cartão {idx + 1}
                      </span>
                      <h4 className="text-sm font-bold text-slate-800 mt-0.5">{cred.name}</h4>
                      
                      {/* Tag de tipo de usuário */}
                      <span className={`inline-block text-[10px] font-extrabold uppercase px-2.5 py-0.5 rounded-full mt-1.5 ${
                        cred.userType === 'PATIENT' 
                          ? 'bg-brand-primary/10 text-brand-primary-dark' 
                          : 'bg-indigo-50 text-indigo-700 border border-indigo-100'
                      }`}>
                        {cred.userType === 'PATIENT' ? 'Paciente' : 'Acompanhante'}
                      </span>
                    </div>

                    {/* Dados Cadastrais e Assistenciais Dinâmicos */}
                    <div className="w-full mt-4 bg-slate-50 border border-slate-100 rounded-2xl p-4 space-y-3 text-left">
                      {cred.cpf && (
                        <div>
                          <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block">CPF</span>
                          <span className="text-xs font-semibold text-slate-700">{formatCpf(cred.cpf)}</span>
                        </div>
                      )}
                      {cred.doctorName && (
                        <div>
                          <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block">Médico</span>
                          <span className="text-xs font-semibold text-slate-700">{cred.doctorName}</span>
                        </div>
                      )}
                      {cred.appointmentDateTime && (
                        <div>
                          <span className="text-[9px] text-slate-400 font-bold uppercase tracking-wider block">Data e Horário da Consulta</span>
                          <span className="text-xs font-extrabold text-brand-primary-dark">{cred.appointmentDateTime}</span>
                        </div>
                      )}
                    </div>

                    {/* QR Code contendo estritamente o código numérico puro (credentialCode) */}
                    <div className="my-5 p-5 border border-dashed border-slate-200 rounded-2xl bg-slate-50 flex items-center justify-center relative">
                      <QRCodeSVG 
                        value={cred.credentialCode}
                        size={160} 
                        fgColor="#0f172a"
                        bgColor="#ffffff"
                      />
                      <div className="absolute top-2 right-2 flex items-center justify-center">
                        <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-ping"></span>
                        <span className="absolute w-2.5 h-2.5 rounded-full bg-emerald-500"></span>
                      </div>
                    </div>

                    {/* Localizador da catraca */}
                    <div className="w-full text-center bg-slate-50 rounded-xl py-2 px-3 border border-slate-100 mb-3">
                      <span className="text-[10px] font-semibold text-slate-400 block uppercase tracking-wider">Localizador Catraca</span>
                      <span className="text-xs font-bold text-slate-700 font-mono">{cred.locator}</span>
                    </div>

                    {/* Botão Ampliar QR Code para tela cheia */}
                    <button 
                      onClick={() => openFullscreen(idx)}
                      className="w-full py-3 bg-slate-900 hover:bg-slate-800 active:scale-[0.98] text-white rounded-xl text-xs font-bold transition-all flex items-center justify-center gap-1.5 shadow-sm cursor-pointer"
                    >
                      <Maximize2 className="w-3.5 h-3.5 text-brand-primary" />
                      Ampliar QR Code
                    </button>
                  </div>
                ))}
              </div>

              {/* Bolinhas de Paginação do carrossel */}
              {credentials.length > 1 && (
                <div className="flex justify-center gap-1.5 mt-2">
                  {credentials.map((_, idx) => (
                    <button 
                      key={idx}
                      onClick={() => scrollToCard(idx)}
                      className={`h-2 rounded-full transition-all duration-300 ${
                        activeCardIndex === idx ? 'w-6 bg-brand-primary' : 'w-2 bg-slate-200'
                      }`}
                      aria-label={`Ir para cartão ${idx + 1}`}
                    />
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Dica de Instruções de Uso */}
          <div className="bg-brand-secondary/15 border-l-4 border-brand-primary rounded-2xl p-4 flex gap-3 text-slate-700 shadow-sm">
            <Info className="w-5 h-5 text-brand-primary-dark shrink-0 mt-0.5" />
            <div className="space-y-1">
              <h5 className="text-xs font-bold text-brand-primary-dark uppercase tracking-wider">Instruções para Acesso</h5>
              <p className="text-[11px] leading-relaxed text-slate-600">
                Aproxime o QR Code na leitora da catraca. Se houver acompanhantes cadastrados, passe primeiro o seu cartão (Titular), aguarde a passagem e, em seguida, deslize o carrossel para passar os cartões dos acompanhantes.
              </p>
            </div>
          </div>

        </main>

        {/* Rodapé Padrão */}
        <footer className="mt-auto border-t border-brand-secondary/30 bg-white py-8 px-6 text-center space-y-6">
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

      {/* Modal de Ampliação do QR Code para Tela Cheia */}
      {fullscreenData && (
        <div 
          ref={modalRef}
          className="fixed inset-0 z-50 bg-white flex flex-col items-center justify-between p-8"
        >
          <div className="text-center mt-8">
            <span className="text-[10px] font-bold tracking-wider text-brand-primary uppercase block">Catraca de Acesso</span>
            <h4 className="text-lg font-bold text-slate-800 mt-1">{fullscreenData.title}</h4>
            <p className="text-xs text-slate-400 mt-1">Brilho da tela aumentado para leitura na catraca</p>
          </div>

          <div className="flex flex-col items-center justify-center flex-1 my-6">
            <div className="p-6 bg-white border border-brand-secondary/35 rounded-3xl shadow-xl shadow-brand-primary/5">
              {/* QR Code ampliado com apenas o credentialCode puro */}
              <QRCodeSVG 
                value={fullscreenData.value} 
                size={260} 
                fgColor="#0f172a" 
                bgColor="#ffffff"
              />
            </div>
          </div>

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
