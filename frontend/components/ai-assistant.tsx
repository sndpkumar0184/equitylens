"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";

type Message = { role: "user" | "assistant"; content: string; evidence?: Evidence[] };
type Evidence = { tool: string; parameters: Record<string, unknown>; data: unknown; error: boolean };
const activities: Record<string, string> = {
  search_companies: "Found companies", get_company: "Retrieved company profile", get_financials: "Retrieved financial metrics",
  get_financial_history: "Retrieved financial history", get_market_snapshot: "Retrieved market snapshot",
  get_price_history: "Retrieved price history", get_growth_metrics: "Retrieved growth metrics",
  get_profitability_metrics: "Retrieved margin trends", compare_companies: "Compared company metrics", get_valuation_metrics: "Retrieved valuation metrics",
};

export function AIAssistant({ ticker }: { ticker?: string }) {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const request = useRef<AbortController | null>(null);
  const generation = useRef(0);
  const bottom = useRef<HTMLDivElement>(null);
  useEffect(() => () => { generation.current++; request.current?.abort(); }, []);
  useEffect(() => { bottom.current?.scrollIntoView?.({ behavior: "smooth", block: "nearest" }); }, [messages, loading]);
  const company = ticker || "AAPL";
  const suggestions = [
    `Analyze ${company}'s financial performance.`,
    `Why did ${company}'s operating margin change?`,
    "Compare AAPL, MSFT and AMZN.",
    "Show me NVDA's revenue growth over the last 5 years.",
    "What happened to AMZN's free cash flow?",
    `Compare ${company}'s price performance with its revenue growth.`,
  ];
  function clear() {
    generation.current++; request.current?.abort(); request.current = null;
    setMessages([]); setInput(""); setLoading(false); setError("");
  }
  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const content = input.trim(); if (!content || loading) return;
    const history: Message[] = [...messages, { role: "user", content }];
    if (history.length > 20 || history.reduce((n, m) => n + m.content.length, 0) > 24000) {
      setError("This conversation has reached its context limit. Clear it to start a new analysis."); return;
    }
    setMessages(history); setInput(""); setLoading(true); setError("");
    const controller = new AbortController(); request.current = controller;
    const current = ++generation.current;
    const timeout = setTimeout(() => controller.abort(), 300000);
    try {
      const response = await fetch("/api/assistant", { method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ticker, messages: history.map(({ role, content }) => ({ role, content })) }), signal: controller.signal });
      const data = await response.json();
      if (!response.ok) throw new Error(data.detail || data.error || "The assistant could not complete this analysis.");
      if (typeof data.answer !== "string") throw new Error("The assistant returned an invalid response.");
      if (current === generation.current) setMessages([...history, { role: "assistant", content: data.answer, evidence: data.evidence || [] }]);
    } catch (cause) {
      if (current === generation.current) {
        setMessages(history.slice(0, -1)); setInput(content);
        setError(controller.signal.aborted ? "Analysis timed out. Try a narrower question." : cause instanceof Error ? cause.message : "The assistant is unavailable.");
      }
    } finally {
      clearTimeout(timeout);
      if (current === generation.current) { setLoading(false); request.current = null; }
    }
  }
  return <section className="mx-auto max-w-5xl" aria-label="EquityLens AI Assistant">
    <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
      <div><p className="eyebrow">EquityLens Research</p><h1 className="mt-2 text-2xl font-bold tracking-tight">AI Assistant</h1>
        <p className="mt-2 text-sm text-slate-500">Explore financial performance with the same data behind your dashboard.</p></div>
      <div className="flex items-center gap-3"><span className="rounded-full border border-indigo-100 bg-indigo-50 px-3 py-1.5 text-xs font-semibold text-indigo-700">{ticker ? `Company context: ${ticker}` : "All companies"}</span>
        <button type="button" onClick={clear} className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-600">Clear conversation</button></div>
    </div>
    <div className="panel overflow-hidden">
      <div className="border-b border-slate-100 bg-slate-50 px-5 py-3 text-xs leading-relaxed text-slate-500">Financial research · Read-only data · Local model
        <span className="block sm:inline"> · Interpretation may be inaccurate; review the retrieved data.</span></div>
      <div className="max-h-[65vh] min-h-80 overflow-y-auto p-5 sm:p-7" role="log" aria-label="Conversation history" aria-live="polite" aria-busy={loading}>
        {messages.length === 0 && <div className="py-6"><h2 className="text-lg font-semibold">Start with a research question</h2>
          <p className="mt-2 max-w-xl text-sm leading-6 text-slate-500">Ask about growth, margins, cash flow or market performance. The assistant retrieves EquityLens data and keeps the reporting periods and sources available for review.</p>
          <div className="mt-6 grid gap-3 sm:grid-cols-2">{suggestions.map(question => <button key={question} type="button" onClick={() => setInput(question)}
            className="rounded-xl border border-slate-200 px-4 py-3 text-left text-sm leading-6 text-slate-700 transition-colors hover:border-indigo-200 hover:bg-indigo-50">{question}</button>)}</div></div>}
        {messages.map((message, index) => <article key={index} aria-label={message.role === "user" ? "Your question" : "Research answer"}
          className={`mb-5 rounded-xl p-4 sm:p-5 ${message.role === "user" ? "ml-6 bg-indigo-50 sm:ml-20" : "border border-slate-200 bg-white"}`}>
          <p className="eyebrow mb-3">{message.role === "user" ? "You" : "EquityLens AI"}</p>
          <p className="whitespace-pre-wrap text-sm leading-7 text-slate-800">{message.content}</p>
          {!!message.evidence?.length && <div className="mt-4 border-t border-slate-100 pt-4"><p className="eyebrow mb-2">Data used in this answer</p>
            {message.evidence.map((entry, i) => <details key={i} className="mb-2 rounded-lg bg-slate-50 p-3 text-xs text-slate-600">
              <summary className="cursor-pointer font-medium">{entry.error ? "Unavailable: " : "✓ "}{activities[entry.tool] || "Research data"}{typeof entry.parameters.ticker === "string" ? ` · ${entry.parameters.ticker}` : ""}</summary>
              <pre className="mt-3 max-h-64 overflow-auto whitespace-pre-wrap break-words text-xs" aria-label="Retrieved source data">{JSON.stringify(entry.data, null, 2)}</pre>
            </details>)}</div>}
        </article>)}
        {loading && <p role="status" className="flex items-center gap-3 py-4 text-sm text-indigo-700"><span aria-hidden="true" className="h-4 w-4 animate-spin rounded-full border-2 border-indigo-200 border-t-indigo-600" />Analyzing {ticker || "your question"}… retrieving data and preparing an answer.</p>}
        <div ref={bottom} />
      </div>
      <form onSubmit={send} className="border-t border-slate-200 p-5">
        <label htmlFor="research-question" className="sr-only">Research question</label>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end"><textarea id="research-question" value={input} onChange={e => setInput(e.target.value)}
          placeholder={ticker ? `Ask about ${ticker} or compare other companies…` : "Enter a company and research question…"}
          rows={2} maxLength={4000} disabled={loading} className="min-w-0 flex-1 resize-y rounded-lg border border-slate-200 bg-slate-50 px-3 py-3 text-sm disabled:opacity-60" />
          <button type="submit" disabled={loading || !input.trim()} className="primary-button disabled:cursor-not-allowed disabled:opacity-50">{loading ? "Analyzing…" : "Analyze"}</button></div>
        {error && <p role="alert" className="mt-3 text-sm text-red-700">{error}</p>}
        <p className="mt-3 text-xs leading-5 text-slate-400">Financial facts require retrieved evidence. This assistant provides research, not trade execution. Clear to start a new context.</p>
      </form>
    </div>
  </section>;
}
