import type { ReactNode } from 'react';

interface PageHeroProps {
  title: ReactNode;
  description?: string;
  eyebrow?: string;
  actions?: ReactNode;
  icon?: ReactNode;
  className?: string;
}

/**
 * Cabeçalho padronizado para páginas autenticadas.
 * Mantém identidade visual consistente entre os módulos.
 */
export default function PageHero({
  title,
  description,
  eyebrow,
  actions,
  icon,
  className,
}: PageHeroProps) {
  return (
    <section
      className={`relative mb-6 overflow-hidden rounded-3xl border border-slate-200 bg-white p-6 shadow-sm sm:p-7 ${className ?? ''}`.trim()}
    >
      <div className="absolute -top-14 right-0 h-36 w-36 rounded-full bg-brand-secondary/40 blur-2xl" />
      <div className="absolute -bottom-14 -left-8 h-36 w-36 rounded-full bg-sky-100 blur-2xl" />

      <div className="relative flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          {eyebrow && (
            <span className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold uppercase tracking-[0.16em] text-slate-500">
              {icon}
              {eyebrow}
            </span>
          )}
          <h1 className="mt-3 flex flex-wrap items-center gap-2 text-3xl font-bold tracking-tight text-slate-900">{title}</h1>
          {description && <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">{description}</p>}
        </div>

        {actions && <div className="relative flex items-center gap-2 flex-wrap">{actions}</div>}
      </div>
    </section>
  );
}
