export default function CompanyNotFound() {
  return <section className="panel p-8" role="alert">
    <p className="eyebrow">Company not found</p>
    <h1 className="mt-3 text-2xl font-semibold">No SEC company data found for this ticker.</h1>
    <p className="mt-3 text-slate-600">Check the symbol and search again. Some securities do not have supported SEC financial filings.</p>
  </section>;
}
