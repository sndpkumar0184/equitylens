import { afterEach, expect, test, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { CompanySearch } from "./company-search";
import { companyPath, normalizeTicker } from "@/lib/ticker";

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
afterEach(() => { cleanup(); push.mockClear(); });

test("ticker search trims whitespace, uppercases, and navigates to the company route", () => {
  render(<CompanySearch />);
  fireEvent.change(screen.getByRole("textbox", { name: "Company ticker" }), { target: { value: " aapl " } });
  fireEvent.submit(screen.getByRole("form", { name: "Company search" }));
  expect(push).toHaveBeenCalledWith("/company/AAPL");
});

test.each(["", " ", "A/PL", "toolongticker"])("invalid search %j stays on page with a helpful error", value => {
  render(<CompanySearch />);
  fireEvent.change(screen.getByRole("textbox"), { target: { value } });
  fireEvent.submit(screen.getByRole("form"));
  expect(screen.getByRole("alert").textContent).toContain("Enter a ticker");
  expect(push).not.toHaveBeenCalled();
});

test("ticker route utility supports stock-class punctuation without accepting paths", () => {
  expect(normalizeTicker(" brk-b ")).toBe("BRK-B");
  expect(normalizeTicker("../AAPL")).toBeNull();
  expect(companyPath("AMZN")).toBe("/company/AMZN");
});
