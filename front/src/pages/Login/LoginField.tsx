// Componente de campo reutilizável para o formulário de login
import { type ReactNode, type ChangeEvent } from 'react';

interface LoginFieldProps {
  id: string;
  type: 'email' | 'password' | 'text';
  label: string;
  placeholder: string;
  value: string;
  onChange: (value: string) => void;
  icon: ReactNode;
}

export default function LoginField({
  id,
  type,
  label,
  placeholder,
  value,
  onChange,
  icon,
}: LoginFieldProps) {
  function handleChange(e: ChangeEvent<HTMLInputElement>) {
    onChange(e.target.value);
  }

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-gray-300">
        {label}
      </label>
      <div className="relative flex items-center">
        <span className="absolute left-3 pointer-events-none">{icon}</span>
        <input
          id={id}
          type={type}
          placeholder={placeholder}
          value={value}
          onChange={handleChange}
          required
          className="w-full bg-gray-800 text-white placeholder-gray-500 text-sm rounded-xl pl-9 pr-4 py-2.5 border border-gray-700 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 transition-colors"
        />
      </div>
    </div>
  );
}
