import { afterEach, expect, test, vi } from "vitest";
import { NextRequest } from "next/server";
import { POST } from "./route";
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllEnvs(); });
function request(body: string, origin = "http://localhost:3000") {
  return new NextRequest("http://localhost:3000/api/assistant", { method: "POST", headers: { origin, "content-type": "application/json" }, body });
}
test("proxy keeps backend credentials server-side and preserves unavailable state", async () => {
  vi.stubEnv("BACKEND_URL", "http://internal:8080"); vi.stubEnv("AI_ACCESS_TOKEN", "server-test-token");
  const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue(Response.json({ detail: "Local AI unavailable" }, { status: 503 }));
  const response = await POST(request(JSON.stringify({ ticker: "META", messages: [{ role: "user", content: "Analyze" }] })));
  expect(response.status).toBe(503);expect(await response.json()).toEqual({ detail: "Local AI unavailable" });
  expect(fetch.mock.calls[0][0]).toBe("http://internal:8080/api/assistant/chat");
  expect(fetch.mock.calls[0][1]?.headers).toEqual({ "Content-Type": "application/json", Authorization: "Bearer server-test-token" });
});
test("cross-origin, malformed JSON and oversized requests are rejected before forwarding", async () => {
  const fetch = vi.spyOn(globalThis, "fetch");
  expect((await POST(request("{}", "https://evil.example"))).status).toBe(403);
  expect((await POST(request("bad"))).status).toBe(400);
  expect((await POST(request("x".repeat(65537)))).status).toBe(413);
  expect(fetch).not.toHaveBeenCalled();
});

 test("production internal hostname does not reject actual same-origin browser Host", async () => {
  vi.spyOn(globalThis, "fetch").mockResolvedValue(Response.json({ answer: "Based on EquityLens data", evidence: [] }));
  const request = new NextRequest("http://localhost:3006/api/assistant", { method: "POST",
    headers: { host: "127.0.0.1:3006", origin: "http://127.0.0.1:3006", "content-type": "application/json" }, body: "{}" });
  expect((await POST(request)).status).toBe(200);
});
