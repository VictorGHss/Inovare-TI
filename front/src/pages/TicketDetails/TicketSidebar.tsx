import {
  Calendar,
  CheckCircle2,
  Clock,
  Laptop,
  Monitor,
  Package,
  Tag,
  UserRound,
} from 'lucide-react';
import { Link } from 'react-router-dom';

import SlaBadge from '../../components/SlaBadge';
import type { Asset, Ticket } from '../../types/models';

interface TicketSidebarProps {
  ticket: Ticket;
  assets: Asset[];
  loadingAssets: boolean;
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return '-';

  try {
    return new Date(iso).toLocaleDateString('pt-BR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return '-';
  }
}

export default function TicketSidebar({ ticket, assets, loadingAssets }: TicketSidebarProps) {
  return (
    <aside className="flex flex-col gap-4">
      <section className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm">
        <h3 className="mb-4 text-sm font-semibold text-slate-700">Informacoes</h3>
        <ul className="flex flex-col gap-3 text-sm">
          <li className="flex items-start gap-2.5 text-slate-600">
            <UserRound size={15} className="mt-0.5 shrink-0 text-slate-400" />
            <div>
              <p className="text-xs text-slate-400">Solicitante</p>
              <p className="font-medium text-slate-700">{ticket.requesterName}</p>
            </div>
          </li>
          <li className="flex items-start gap-2.5 text-slate-600">
            <Tag size={15} className="mt-0.5 shrink-0 text-slate-400" />
            <div>
              <p className="text-xs text-slate-400">Categoria</p>
              <p className="font-medium text-slate-700">{ticket.categoryName}</p>
            </div>
          </li>
          <li className="flex items-start gap-2.5 text-slate-600">
            <Calendar size={15} className="mt-0.5 shrink-0 text-slate-400" />
            <div>
              <p className="text-xs text-slate-400">Criado em</p>
              <p className="font-medium text-slate-700">{formatDate(ticket.createdAt)}</p>
            </div>
          </li>
          <li className="flex items-start gap-2.5 text-slate-600">
            <Clock size={15} className="mt-0.5 shrink-0 text-slate-400" />
            <div>
              <p className="text-xs text-slate-400">Prazo SLA</p>
              <div className="flex items-center gap-2">
                <p className="font-medium text-slate-700">
                  {ticket.slaDeadline ? formatDate(ticket.slaDeadline) : 'Sem prazo'}
                </p>
                <SlaBadge
                  deadline={ticket.slaDeadline}
                  status={ticket.status}
                  closedAt={ticket.closedAt}
                />
              </div>
            </div>
          </li>
          {ticket.requestedItemName && (
            <li className="flex items-start gap-2.5 text-slate-600">
              <Package size={15} className="mt-0.5 shrink-0 text-slate-400" />
              <div>
                <p className="text-xs text-slate-400">Item Solicitado</p>
                <p className="font-medium text-slate-700">
                  {ticket.requestedItemName}
                  {ticket.requestedQuantity != null && (
                    <span className="font-normal text-slate-400"> x {ticket.requestedQuantity}</span>
                  )}
                </p>
              </div>
            </li>
          )}
          {ticket.closedAt && (
            <li className="flex items-start gap-2.5 text-slate-600">
              <CheckCircle2 size={15} className="mt-0.5 shrink-0 text-green-500" />
              <div>
                <p className="text-xs text-slate-400">Fechado em</p>
                <p className="font-medium text-slate-700">{formatDate(ticket.closedAt)}</p>
              </div>
            </li>
          )}
        </ul>
      </section>

      <section className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm">
        <h3 className="mb-4 text-sm font-semibold text-slate-700">Equipamentos do Solicitante</h3>

        {loadingAssets ? (
          <p className="text-sm text-slate-400">Carregando equipamentos...</p>
        ) : assets.length === 0 ? (
          <p className="text-sm italic text-slate-400">Nenhum equipamento registrado.</p>
        ) : (
          <ul className="flex flex-col gap-3">
            {assets.map((asset) => {
              const normalizedName = asset.name.toLowerCase();
              const isLaptop =
                normalizedName.includes('notebook') || normalizedName.includes('laptop');
              const AssetIcon = isLaptop ? Laptop : Monitor;

              return (
                <li key={asset.id} className="rounded-2xl border border-slate-200 bg-[#fff8f1] p-3">
                  <div className="flex items-start gap-2.5">
                    <AssetIcon size={16} className="mt-0.5 shrink-0 text-slate-500" />
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-slate-700">{asset.name}</p>
                      <p className="mt-0.5 text-xs text-slate-500">Patrimonio: {asset.patrimonyCode}</p>
                      <p className="mt-1 whitespace-pre-wrap break-words text-xs text-slate-500">
                        {asset.specifications || 'Sem especificacoes.'}
                      </p>
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      {ticket.relatedTicketIds && ticket.relatedTicketIds.length > 0 && (
        <section className="rounded-2xl border border-[#feb56c]/35 bg-white p-6 shadow-sm">
          <h3 className="mb-4 text-sm font-semibold text-slate-700">Chamados Relacionados</h3>
          <ul className="flex flex-col gap-2">
            {ticket.relatedTicketIds.map((relId) => (
              <li key={relId}>
                <Link
                  to={`/tickets/${relId}`}
                  className="inline-flex items-center gap-1.5 text-xs font-semibold text-brand-primary hover:text-brand-primary-dark transition-colors break-all bg-brand-secondary/20 hover:bg-brand-secondary/40 px-3 py-2 rounded-xl border border-brand-primary/10 w-full"
                >
                  Chamado #{relId.slice(0, 8).toUpperCase()}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}
    </aside>
  );
}
