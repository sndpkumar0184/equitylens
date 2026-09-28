export function DashboardLoading() {
  return <div role="status" aria-label="Loading company financials" className="space-y-6">
    <p className="text-sm text-slate-500">Loading company data… First-time SEC imports may take a moment.</p>
    <div className="h-28 rounded-2xl bg-slate-200 motion-safe:animate-pulse" />
    <div className="grid grid-cols-2 gap-4 xl:grid-cols-3">{[0, 1, 2, 3, 4, 5].map(i => <div key={i} className="panel h-32 motion-safe:animate-pulse" />)}</div>
    <div className="grid gap-6 lg:grid-cols-2">{[0, 1, 2, 3, 4, 5].map(i => <div key={i} className="panel h-80 motion-safe:animate-pulse" />)}</div>
  </div>;
}
