import { afterEach, expect, test } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { TrendChart } from "./trend-chart";

afterEach(cleanup);

test("keyboard inspection shows exact negative values and reporting period", () => {
  render(<TrendChart title="Earnings" data={[
    { period: "FY", periodEnd: "2024-12-31", earnings: -1234567 },
    { period: "FY", periodEnd: "2025-12-31", earnings: 2000000 },
  ]} series={[{ key: "earnings", label: "Earnings", color: "#4f46e5" }]} />);
  fireEvent.focus(screen.getByRole("button", { name: /FY · Dec 31, 2024/ }));
  expect(screen.getByText("-$1,234,567")).toBeTruthy();
  expect(screen.getByText("FY · Dec 31, 2024")).toBeTruthy();
});

test("missing series are explicitly unavailable, while zero is real data", () => {
  render(<TrendChart title="Margins" format="percent" data={[
    { period: "FY", periodEnd: "2025-12-31", net: 0, gross: null },
  ]} series={[{ key: "net", label: "Net margin", color: "#4f46e5" }, { key: "gross", label: "Gross margin", color: "#0f766e" }]} />);
  expect(screen.getByRole("img")).toBeTruthy();
  expect(screen.getByText("Gross margin unavailable")).toBeTruthy();
  expect(screen.getAllByText("0%").length).toBeGreaterThan(0);
});
