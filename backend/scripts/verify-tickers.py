#!/usr/bin/env python3
"""Live API smoke test: uses real SEC imports and PostgreSQL, never fixtures."""
import argparse
import concurrent.futures
import json
import urllib.request
import urllib.error

parser = argparse.ArgumentParser()
parser.add_argument("--base-url", default="http://localhost:8080")
parser.add_argument("--refresh", action="store_true", help="Also explicitly re-import AAPL to verify replacement is idempotent")
args = parser.parse_args()
base = args.base_url.rstrip("/") + "/api/companies/"


def request(path, method="GET"):
    with urllib.request.urlopen(urllib.request.Request(base + path, method=method), timeout=120) as response:
        return json.load(response)


profiles = {}
revenues = {}
for ticker in ("META", "AAPL", "AMZN"):
    company = request(ticker)
    raw = request(ticker + "/financials")
    normalized = request(ticker + "/financials/normalized")
    ttm = request(ticker + "/financials/ttm")
    assert company["ticker"] == ticker
    assert len(company["cik"]) == 10
    assert raw and normalized and ttm, ticker
    assert all(row["company"]["id"] == company["id"] for row in raw)
    assert all(row["company"]["ticker"] == ticker for row in raw)
    assert all(row["form"] and row["filingDate"] and row["periodEnd"] for row in raw)
    assert request(ticker.lower())["id"] == company["id"]
    assert len(request(ticker + "/financials")) == len(raw)
    assert {r["id"] for r in request(ticker + "/financials")} == {r["id"] for r in raw}
    annual = sorted((r for r in normalized if r["metric"] == "revenue" and r["period"] == "FY" and r["unit"] == "USD"), key=lambda r: r["periodEnd"])
    revenues[ticker] = annual[-1]["value"]
    profiles[ticker] = company
    print(f'{ticker}: id={company["id"]}, cik={company["cik"]}, name={company["name"]}, facts={len(raw)}, latest annual revenue={revenues[ticker]}, TTM periods={len(ttm)}')

assert len({p["id"] for p in profiles.values()}) == 3
assert profiles["META"]["name"] != profiles["AAPL"]["name"]
assert revenues["META"] != revenues["AAPL"]

with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    results = list(pool.map(request, ["AAPL/financials"] * 4))
assert all({r["id"] for r in result} == {r["id"] for r in results[0]} for result in results)

if args.refresh:
    before = request("AAPL/financials")
    after = request("AAPL/financials/import", "POST")
    def observations(rows):
        return sorted(({k: v for k, v in r.items() if k != "id"} for r in rows),
                      key=lambda r: (r["metric"], r["unit"], str(r["periodStart"]), r["periodEnd"]))
    assert observations(before) == observations(after), "Source data changed during refresh or import is not idempotent"
    assert len(request("AAPL/financials")) == len(before)
    print("AAPL explicit refresh: same observations and row count, no duplicates")

for symbol, expected in (("ZZZZZZZZZZ", 404), ("bad!ticker", 400)):
    try:
        request(symbol)
    except urllib.error.HTTPError as error:
        assert error.code == expected, (symbol, error.code)
    else:
        raise AssertionError(f"Expected {expected} for {symbol}")
print("PASS: distinct company identities/data, repeated and concurrent reads, TTM, and error statuses")
