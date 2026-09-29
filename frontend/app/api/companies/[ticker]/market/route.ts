import { NextRequest } from "next/server";

export async function GET(request: NextRequest, context: { params: Promise<{ ticker: string }> }) {
  const { ticker } = await context.params;
  const interval = request.nextUrl.searchParams.get("interval") || "DAILY";
  if (!["DAILY", "WEEKLY", "MONTHLY", "YEARLY"].includes(interval)) return Response.json({ error: "Unsupported market interval" }, { status: 422 });
  const backend = (process.env.BACKEND_URL || "http://localhost:8080").replace(/\/$/, "");
  try {
    const response = await fetch(`${backend}/api/companies/${encodeURIComponent(ticker)}/market?interval=${interval}`, { cache: "no-store", signal: AbortSignal.timeout(120000) });
    return new Response(response.body, { status: response.status, headers: { "content-type": response.headers.get("content-type") || "application/json" } });
  } catch {
    return Response.json({ error: "Market data is temporarily unavailable" }, { status: 503 });
  }
}
