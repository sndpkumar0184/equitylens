export function DashboardLoading() {
  return <div role="status" aria-label="Loading company financials" className="space-y-6">
    <p className="text-sm text-slate-500">Loading company financials…</p>
    <div className="h-28 rounded-2xl bg-slate-200 motion-safe:animate-pulse" />
    <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">{[0, 1, 2, 3].map(i => <div key={i} className="panel h-32 motion-safe:animate-pulse" />)}</div>
    <div className="grid gap-6 lg:grid-cols-2">{[0, 1].map(i => <div key={i} className="panel h-80 motion-safe:animate-pulse" />)}</div>
  </div>;
}
