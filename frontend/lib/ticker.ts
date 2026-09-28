export function normalizeTicker(value: string) {
  const ticker = value.trim().toUpperCase();
  return /^[A-Z0-9][A-Z0-9.-]{0,9}$/.test(ticker) ? ticker : null;
}

export function companyPath(ticker: string) {
  return `/company/${encodeURIComponent(ticker)}`;
}
