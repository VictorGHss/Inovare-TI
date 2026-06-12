import { useState, useEffect, useRef } from 'react';
import { Search, ChevronDown, X } from 'lucide-react';

interface DropdownOption {
  id: string;
  name: string;
}

interface SearchableDropdownProps {
  options: DropdownOption[];
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  disabled?: boolean;
}

/**
 * Componente de dropdown filtrável e pesquisável com design premium.
 * Apresenta as opções ordenadas alfabeticamente de forma estrita e dispõe de uma barra de pesquisa interna rápida.
 * 
 * @param options Lista de opções disponíveis para seleção pelo utilizador.
 * @param value O valor atualmente selecionado (id da opção).
 * @param onChange Função chamada quando o utilizador seleciona uma nova opção.
 * @param placeholder Texto de substituição exibido quando não há seleção.
 * @param className Classes CSS adicionais do ecrã.
 * @param disabled Se o dropdown está desativado.
 */
export default function SearchableDropdown({
  options,
  value,
  onChange,
  placeholder = 'Selecione uma opção...',
  className = '',
  disabled = false,
}: SearchableDropdownProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const containerRef = useRef<HTMLDivElement>(null);

  // Ordena as opções alfabeticamente de forma estrita pelo nome
  const sortedOptions = [...options].sort((a, b) => a.name.localeCompare(b.name));

  // Filtra as opções com base no termo de pesquisa introduzido pelo utilizador
  const filteredOptions = sortedOptions.filter((opt) =>
    opt.name.toLowerCase().includes(searchTerm.toLowerCase())
  );

  // Obtém a opção atualmente selecionada
  const selectedOption = options.find((opt) => opt.id === value);

  // Fecha o dropdown quando o utilizador clica fora do componente
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Limpa o termo de pesquisa ao fechar o dropdown
  useEffect(() => {
    if (!isOpen) {
      setSearchTerm('');
    }
  }, [isOpen]);

  const handleSelect = (id: string) => {
    onChange(id);
    setIsOpen(false);
  };

  return (
    <div ref={containerRef} className={`relative w-full ${className}`}>
      <button
        type="button"
        disabled={disabled}
        onClick={() => setIsOpen((prev) => !prev)}
        className="w-full flex items-center justify-between rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm text-slate-800 shadow-sm focus:outline-none focus:ring-2 focus:ring-brand-primary/30 focus:border-brand-primary transition disabled:opacity-60 disabled:cursor-not-allowed text-left font-medium"
      >
        <span className={selectedOption ? 'text-slate-800' : 'text-slate-400'}>
          {selectedOption ? selectedOption.name : placeholder}
        </span>
        <ChevronDown size={16} className={`text-slate-400 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
      </button>

      {isOpen && (
        <div className="absolute z-50 mt-1 max-h-64 w-full overflow-hidden rounded-xl border border-slate-200 bg-white shadow-lg flex flex-col top-full left-0">
          {/* Barra de pesquisa interna rápida */}
          <div className="relative p-2 border-b border-slate-100 flex items-center">
            <Search size={14} className="absolute left-4 text-slate-400" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Pesquisar..."
              className="w-full rounded-lg bg-slate-50 pl-8 pr-8 py-1.5 text-xs text-slate-800 focus:outline-none focus:ring-1 focus:ring-brand-primary/30 focus:bg-white transition"
              autoFocus
            />
            {searchTerm && (
              <button
                type="button"
                onClick={() => setSearchTerm('')}
                className="absolute right-4 text-slate-400 hover:text-slate-600"
              >
                <X size={14} />
              </button>
            )}
          </div>

          {/* Lista de opções ordenada */}
          <ul className="overflow-y-auto max-h-48 divide-y divide-slate-50">
            {filteredOptions.length === 0 ? (
              <li className="px-4 py-3 text-xs text-slate-400 text-center">
                Nenhuma opção encontrada
              </li>
            ) : (
              filteredOptions.map((opt) => (
                <li key={opt.id}>
                  <button
                    type="button"
                    onClick={() => handleSelect(opt.id)}
                    className={`w-full text-left px-4 py-2.5 text-xs transition-colors hover:bg-brand-secondary/30 hover:text-brand-primary-dark ${
                      opt.id === value ? 'bg-brand-secondary/20 text-brand-primary-dark font-semibold' : 'text-slate-700'
                    }`}
                  >
                    {opt.name}
                  </button>
                </li>
              ))
            )}
          </ul>
        </div>
      )}
    </div>
  );
}
