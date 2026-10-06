import { afterEach, expect, test, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { AIAssistant } from "./ai-assistant";
afterEach(() => { cleanup(); vi.restoreAllMocks(); });
const response = { answer: "Based on EquityLens data: FY2025 revenue was $100. Interpretation is uncertain.", evidence: [{ tool: "get_financial_history", parameters: { ticker: "AAPL", metric: "revenue" }, data: { source: "SEC", period: "FY2025", filingDate: "2026-02-01" }, error: false }] };

for (const ticker of ["META", "AAPL", "AMZN", "NVDA"]) test(`${ticker} context is displayed and explicitly sent to backend`, async () => {
  const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue(Response.json(response));
  render(<AIAssistant ticker={ticker} />);
  expect(screen.getByText(`Company context: ${ticker}`)).toBeTruthy();
  fireEvent.change(screen.getByLabelText("Research question"), { target: { value: "Why did margins fall?" } });
  fireEvent.click(screen.getByRole("button", { name: "Analyze" }));
  await waitFor(() => expect(screen.getByText(response.answer)).toBeTruthy());
  const [url, init] = fetch.mock.calls[0];
  expect(url).toBe("/api/assistant");
  expect(JSON.parse(String(init?.body))).toEqual({ ticker, messages: [{ role: "user", content: "Why did margins fall?" }] });
  expect(screen.getByText(/Retrieved financial history/)).toBeTruthy();
  expect(screen.getByLabelText("Retrieved source data").textContent).toContain("filingDate");
});

test("suggestions fill input, conversations retain history and clear resets", async () => {
  const fetch = vi.spyOn(globalThis, "fetch").mockResolvedValue(Response.json(response));
  render(<AIAssistant ticker="AAPL" />);
  fireEvent.click(screen.getByRole("button", { name: "Analyze AAPL's financial performance." }));
  expect((screen.getByLabelText("Research question") as HTMLTextAreaElement).value).toContain("AAPL");
  fireEvent.click(screen.getByRole("button", { name: "Analyze" }));
  await waitFor(() => expect(screen.getByText(response.answer)).toBeTruthy());
  fireEvent.change(screen.getByLabelText("Research question"), { target: { value: "Compare with NVDA" } });
  fireEvent.click(screen.getByRole("button", { name: "Analyze" }));
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
  expect(JSON.parse(String(fetch.mock.calls[1][1]?.body)).messages).toHaveLength(3);
  fireEvent.click(screen.getByRole("button", { name: "Clear conversation" }));
  expect(screen.queryByText(response.answer)).toBeNull();
  expect(screen.getByText("Start with a research question")).toBeTruthy();
});

test("loading is accessible; clear aborts and prevents stale responses", async () => {
  let resolve!: (r: Response) => void;
  const fetch = vi.spyOn(globalThis, "fetch").mockImplementation(() => new Promise(r => { resolve = r; }));
  render(<AIAssistant ticker="META" />);
  fireEvent.change(screen.getByLabelText("Research question"), { target: { value: "Analyze META" } });
  fireEvent.click(screen.getByRole("button", { name: "Analyze" }));
  expect(screen.getByRole("status").textContent).toContain("Analyzing META");
  expect(screen.getByRole("button", { name: "Analyzing…" }).hasAttribute("disabled")).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "Clear conversation" }));
  expect(fetch.mock.calls[0][1]?.signal?.aborted).toBe(true);
  resolve(Response.json(response));
  await waitFor(() => expect(screen.queryByText(response.answer)).toBeNull());
});

test("runtime unavailable shows error and preserves question for retry", async () => {
  vi.spyOn(globalThis, "fetch").mockResolvedValue(Response.json({ detail: "Local AI is unavailable. Start Ollama." }, { status: 503 }));
  render(<AIAssistant ticker="NVDA" />);
  fireEvent.change(screen.getByLabelText("Research question"), { target: { value: "Revenue growth?" } });
  fireEvent.click(screen.getByRole("button", { name: "Analyze" }));
  await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("Start Ollama"));
  expect((screen.getByLabelText("Research question") as HTMLTextAreaElement).value).toBe("Revenue growth?");
  expect(screen.queryByRole("article", { name: "Research answer" })).toBeNull();
});

test("changing company by remount clears prior company conversation", async () => {
  vi.spyOn(globalThis, "fetch").mockResolvedValue(Response.json(response));
  const view = render(<AIAssistant key="AAPL" ticker="AAPL" />);
  fireEvent.change(screen.getByLabelText("Research question"), { target: { value: "Revenue?" } });
  fireEvent.click(screen.getByRole("button", { name: "Analyze" }));
  await waitFor(() => expect(screen.getByText(response.answer)).toBeTruthy());
  view.rerender(<AIAssistant key="META" ticker="META" />);
  expect(screen.queryByText(response.answer)).toBeNull();
  expect(screen.getByText("Company context: META")).toBeTruthy();
});
