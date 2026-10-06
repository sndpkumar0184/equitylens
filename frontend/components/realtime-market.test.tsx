import { afterEach, expect, test, vi } from "vitest";
import { cleanup, render, screen, waitFor, act } from "@testing-library/react";
import { RealtimeMarket, type LiveState } from "./realtime-market";

class MockStream {
  static instances: MockStream[] = [];
  onopen: (() => void) | null = null;
  onerror: (() => void) | null = null;
  listener: ((event: { data: string }) => void) | null = null;
  close = vi.fn();
  constructor(public url: string) { MockStream.instances.push(this); }
  addEventListener(_name: string, listener: (event: { data: string }) => void) { this.listener = listener; }
}
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); MockStream.instances = []; });
const state: LiveState = { symbol: "AAPL", provider: "SYNTHETIC", feed: "FIXTURE", synthetic: true,
  status: "AVAILABLE", ingestion: { state: "DISABLED" }, reference: null,
  bar: { close: 126, volume: 300, vwap: 126, lastEventTime: new Date().toISOString(), windowEnd: new Date().toISOString(), quality: "OBSERVED_TRADES_UNCORRECTED" },
  analytics: { return1mPct: 0.8, return5mPct: 4.1, rollingVolume: 420, rollingVwap: 125.286, volumeRatio: 10,
    volatilityPct: 0.1, anomaly: true, windowEnd: new Date().toISOString() } };
function setup(data: LiveState | null = state) {
  vi.stubGlobal("EventSource", MockStream);
  vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(JSON.stringify(data), { status: data ? 200 : 503 }));
}
test("shows source, synthetic label and deterministic metrics with SSE lifecycle", async () => {
  setup(); const view = render(<RealtimeMarket ticker="AAPL" />);
  await waitFor(() => expect(screen.getByText("Synthetic development data")).toBeTruthy());
  expect(screen.getByText(/SYNTHETIC \/ FIXTURE/)).toBeTruthy();
  expect(screen.getByText("420")).toBeTruthy(); expect(screen.getByText("10×")).toBeTruthy();
  expect(screen.getByText(/Unusual observed volume/)).toBeTruthy();
  act(() => MockStream.instances[0].onopen?.()); expect(screen.getByText("Connected")).toBeTruthy();
  act(() => MockStream.instances[0].onerror?.()); expect(screen.getByText(/Disconnected/)).toBeTruthy();
  view.unmount(); expect(MockStream.instances[0].close).toHaveBeenCalled();
});
test("reference price preserves partial feed and does not invent analytics", async () => {
  setup({ ...state, synthetic: false, provider: "TIINGO", feed: "IEX_REFERENCE", bar: null, analytics: null,
    reference: { price: 123.5, eventTime: "2026-01-01T00:00:00Z" } });
  render(<RealtimeMarket ticker="AAPL" />);
  await waitFor(() => expect(screen.getByText("123.5 USD")).toBeTruthy());
  expect(screen.getByText(/partial market feed/)).toBeTruthy();
  expect(screen.getByText(/analytics stale/)).toBeTruthy(); expect(screen.getAllByText("N/A")).toHaveLength(6);
});
test("error and unknown observations are explicit; wrong-symbol SSE is ignored", async () => {
  setup(null); render(<RealtimeMarket ticker="AAPL" />);
  await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("unavailable"));
  act(() => MockStream.instances[0].listener?.({ data: JSON.stringify({ ...state, symbol: "META" }) }));
  expect(screen.queryByText("126 USD")).toBeNull();
});
