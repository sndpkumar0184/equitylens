"use client";

export default function ErrorPage({ retry }: { error: Error & { digest?: string }; retry: () => void }) {
  return <section className="panel p-8 sm:p-12" role="alert">
    <p className="eyebrow">Company data unavailable</p>
    <h1 className="mt-3 text-2xl font-semibold">We couldn’t load this company.</h1>
    <p className="mt-3 max-w-xl text-slate-600">Check the ticker and try again. The company may not be available, or the financial data service may be temporarily offline.</p>
    <button className="primary-button mt-6" onClick={retry}>Try again</button>
  </section>;
}
