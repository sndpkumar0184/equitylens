import { NextRequest } from "next/server";

export async function GET(request: NextRequest, context: { params: Promise<{ ticker: string; path?: string[] }> }) {
  const { ticker, path = [] } = await context.params;
  if (!/^[A-Z0-9][A-Z0-9.-]{0,9}$/.test(ticker) || path.length > 1 ||
      (path.length === 1 && !["bars", "analytics", "anomalies", "events"].includes(path[0]))) {
    return Response.json({ error: "Invalid realtime request" }, { status: 400 });
  }
  const query = new URLSearchParams();
  for (const name of ["interval", "rangeMinutes", "limit"]) {
    const value = request.nextUrl.searchParams.get(name);
    if (value !== null) query.set(name, value);
  }
  const live = path[0] === "events";
  const backend = (process.env.BACKEND_URL || "http://localhost:8080").replace(/\/$/, "");
  try {
    const response = await fetch(`${backend}/api/market/realtime/${encodeURIComponent(ticker)}${path.length ? `/${path[0]}` : ""}?${query}`, {
      cache: "no-store", signal: live ? request.signal : AbortSignal.timeout(10000),
    });
    return new Response(response.body, { status: response.status, headers: {
      "content-type": response.headers.get("content-type") || "application/json",
      "cache-control": "no-cache, no-transform", "x-accel-buffering": "no",
    } });
  } catch {
    return Response.json({ error: "Realtime analytics temporarily unavailable" }, { status: 503 });
  }
}
