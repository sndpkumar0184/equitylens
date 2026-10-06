import { NextRequest } from "next/server";

export async function POST(request: NextRequest) {
  const origin = request.headers.get("origin");
  // Next.js may construct nextUrl using its internal listening hostname in production.
  // Compare against the actual request Host so same-origin browser requests still work.
  const host = request.headers.get("host");
  const requestOrigin = host ? `${request.nextUrl.protocol}//${host}` : request.nextUrl.origin;
  if (origin && origin !== requestOrigin) return Response.json({ error: "Invalid request origin" }, { status: 403 });
  try {
    const text = await request.text();
    if (text.length > 65536) return Response.json({ error: "Conversation is too large" }, { status: 413 });
    let body: unknown;
    try { body = JSON.parse(text); } catch { return Response.json({ error: "Invalid conversation" }, { status: 400 }); }
    const backend = (process.env.BACKEND_URL || "http://localhost:8080").replace(/\/$/, "");
    const headers: Record<string, string> = { "Content-Type": "application/json" };
    if (process.env.AI_ACCESS_TOKEN) headers.Authorization = `Bearer ${process.env.AI_ACCESS_TOKEN}`;
    const response = await fetch(`${backend}/api/assistant/chat`, { method: "POST", headers, body: JSON.stringify(body), cache: "no-store", signal: AbortSignal.timeout(290000) });
    return new Response(response.body, { status: response.status, headers: { "content-type": "application/json", "cache-control": "no-store" } });
  } catch { return Response.json({ error: "The research assistant is temporarily unavailable. Check the backend and local model configuration." }, { status: 503 }); }
}
