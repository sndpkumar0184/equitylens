import type { Metadata } from "next";
import Link from "next/link";
import { CompanySearch } from "@/components/company-search";
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
        <CompanySearch />
      </div>
    </header>
    <main id="main-content" className="mx-auto w-full max-w-7xl px-5 py-8 sm:px-8 sm:py-10">{children}</main>
  </body></html>;
}
