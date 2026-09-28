"use client";

import { useState, useTransition, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { companyPath, normalizeTicker } from "@/lib/ticker";

export function CompanySearch() {
  const router = useRouter();
  const [error, setError] = useState("");
  const [pending, startTransition] = useTransition();
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const ticker = normalizeTicker(String(new FormData(event.currentTarget).get("ticker") || ""));
    if (!ticker) { setError("Enter a ticker using 1–10 letters, numbers, dots or hyphens."); return; }
    setError("");
    startTransition(() => router.push(companyPath(ticker)));
  }
  return <div className="w-full sm:w-auto">
    <form onSubmit={submit} className="flex gap-2" aria-label="Company search">
      <label htmlFor="ticker" className="sr-only">Company ticker</label>
      <input id="ticker" name="ticker" placeholder="Enter a stock ticker" aria-invalid={!!error} aria-describedby={error ? "ticker-error" : undefined} autoComplete="off" spellCheck={false} className="min-w-0 flex-1 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm sm:w-60" />
      <button type="submit" className="primary-button">Search</button>
    </form>
    {error && <p id="ticker-error" role="alert" className="mt-2 text-xs text-red-700">{error}</p>}
    {pending && <p role="status" className="mt-2 text-xs text-slate-500">Loading company data… First-time imports may take a moment.</p>}
  </div>;
}
