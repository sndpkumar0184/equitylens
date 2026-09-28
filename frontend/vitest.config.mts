import { defineConfig } from "vitest/config";
import { fileURLToPath } from "node:url";

export default defineConfig({
  resolve: { alias: { "@": fileURLToPath(new URL(".", import.meta.url)) } },
  test: { alias: { "server-only": "next/dist/compiled/server-only/empty.js" }, environment: "jsdom", include: ["**/*.test.{ts,tsx,mjs}"] },
});
