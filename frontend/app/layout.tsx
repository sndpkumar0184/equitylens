import type { Metadata } from "next";
import Link from "next/link";
import Form from "next/form";
import "./globals.css";

export const metadata: Metadata = {
  title: "EquityLens | Company research",
  description: "Explore company financials, reporting trends, and financial statements with EquityLens.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return <html lang="en" className="h-full antialiased"><body className="min-h-full">
    <a href="#main-content" className="sr-only focus:not-sr-only focus:absolute focus:z-20 focus:bg-white focus:p-4">Skip to financial dashboard</a>
    <header className="border-b border-slate-200 bg-white">
      <div className="mx-auto flex max-w-7xl flex-col gap-4 px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-8">
        <Link href="/" className="flex items-center gap-2.5 text-xl font-bold tracking-tight"><span aria-hidden="true" className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-600 text-base text-white">E</span>EquityLens<span className="ml-3 hidden border-l border-slate-200 pl-4 text-xs font-medium tracking-normal text-slate-400 lg:inline">Financial research</span></Link>
        <Form action="/" className="flex w-full gap-2 sm:w-auto">
          <label htmlFor="ticker" className="sr-only">Company ticker</label>
          <input id="ticker" name="ticker" placeholder="Enter ticker, e.g. META" required maxLength={10} pattern="[A-Za-z0-9][A-Za-z0-9.\-]{0,9}" autoComplete="off" spellCheck={false} className="min-w-0 flex-1 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm sm:w-60" />
          <button type="submit" className="primary-button">Search</button>
        </Form>
      </div>
    </header>
    <main id="main-content" className="mx-auto w-full max-w-7xl px-5 py-8 sm:px-8 sm:py-10">{children}</main>
  </body></html>;
}
