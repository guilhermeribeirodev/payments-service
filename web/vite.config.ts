import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// In dev, /v1 and /actuator are proxied to the Spring Boot service so no CORS setup is needed.
export default defineConfig({
  plugins: [react()],
  server: { proxy: { "/v1": "http://localhost:8080", "/actuator": "http://localhost:8080" } },
});
