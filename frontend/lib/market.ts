export type DailyPrice = {
  date: string; open: number | null; high: number | null; low: number | null;
  close: number; adjustedClose: number | null; volume: number | null;
};

export type MarketData = {
  ticker: string; source: string; currency: string; status: string;
  fetchedAt: string | null; retryAfter: string | null; priceBasis: string;
  latestPrice: number | null; latestTradingDate: string | null;
  high52Week: number | null; low52Week: number | null;
  return1Month: number | null; return3Month: number | null;
  return6Month: number | null; return1Year: number | null;
  history: DailyPrice[];
};
