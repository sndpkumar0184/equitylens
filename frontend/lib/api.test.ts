import { afterEach, expect, test, vi } from "vitest";
vi.mock("server-only", () => ({}));
import { ApiError, getDashboard, getMarket } from "./api";

afterEach(() => { vi.unstubAllGlobals(); vi.unstubAllEnvs(); });

test("market uses its own ticker endpoint and failures are isolated", async () => {
  vi.stubEnv("BACKEND_URL", "http://backend.test:8080");
  const payload = { ticker: "AMZN", history: [] };
  const fetch = vi.fn().mockResolvedValueOnce({ ok: true, json: async () => payload })
    .mockResolvedValueOnce({ ok: false, status: 503 });
  vi.stubGlobal("fetch", fetch);
  expect(await getMarket("AMZN")).toBe(payload);
  expect(fetch.mock.calls[0][0]).toBe("http://backend.test:8080/api/companies/AMZN/market?interval=DAILY");
  expect(await getMarket("META")).toBeNull();
});

test("dashboard makes one consolidated backend request", async () => {
  vi.stubEnv("BACKEND_URL", "http://backend.test:8080");
  const payload = { company: { ticker: "AAPL" }, annual: {}, quarterly: {} };
  const fetch = vi.fn().mockResolvedValue({ ok: true, json: async () => payload });
  vi.stubGlobal("fetch", fetch);
  expect(await getDashboard("AAPL")).toBe(payload);
  expect(fetch).toHaveBeenCalledTimes(1);
  expect(fetch.mock.calls[0][0]).toBe("http://backend.test:8080/api/companies/AAPL/dashboard");
  expect(fetch.mock.calls[0][1].cache).toBe("no-store");
});

test("unknown company status reaches the route's not-found handling", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 404 }));
  await expect(getDashboard("UNKNOWN")).rejects.toMatchObject({ status: 404 });
  expect(new ApiError(503).status).toBe(503);
});
