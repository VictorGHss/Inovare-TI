import { Shield } from 'lucide-react';

import PageHero from '@/components/ui/PageHero';
import { useSystemLogs } from './hooks/useSystemLogs';
import LogFilters from './LogFilters';
import LogTable from './LogTable';

export default function SystemLogs() {
  const {
    filterUserId,
    setFilterUserId,
    filterAction,
    setFilterAction,
    filterSeverity,
    setFilterSeverity,
    search,
    setSearch,
    startDate,
    setStartDate,
    endDate,
    setEndDate,
    users,
    page,
    loading,
    error,
    applyFilters,
    goToPreviousPage,
    goToNextPage,
  } = useSystemLogs();

  return (
    <div className="p-4 sm:p-6 w-full max-w-full">
      <PageHero
        eyebrow="Auditoria"
        icon={<Shield size={14} />}
        title="Logs do Sistema"
        description="Trilha de auditoria com eventos sensíveis do sistema para governança, rastreabilidade e segurança operacional."
      />

      <LogFilters
        users={users}
        filterUserId={filterUserId}
        filterAction={filterAction}
        filterSeverity={filterSeverity}
        search={search}
        startDate={startDate}
        endDate={endDate}
        loading={loading}
        onFilterUserIdChange={setFilterUserId}
        onFilterActionChange={setFilterAction}
        onFilterSeverityChange={setFilterSeverity}
        onSearchChange={setSearch}
        onStartDateChange={setStartDate}
        onEndDateChange={setEndDate}
        onSubmit={applyFilters}
      />

      <LogTable
        page={page}
        loading={loading}
        error={error}
        onPreviousPage={goToPreviousPage}
        onNextPage={goToNextPage}
      />
    </div>
  );
}

