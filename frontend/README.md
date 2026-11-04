# RelayFlow Console

The console is a React + Vite client for the RelayFlow Spring Boot API. The
browser communicates only with the API; it does not connect to PostgreSQL, the
worker, the mock world, or the AI provider.

## Local setup

1. Copy `.env.example` to `.env.local` and set `VITE_API_BASE_URL` to the API
   origin (default: `http://localhost:8080`).
2. Configure the backend's `RELAY_FRONTEND_ORIGIN` to match the console's
   origin (defaults allow `http://localhost:5173` and
   `http://127.0.0.1:5173`; multiple origins may be comma-separated) so the API
   permits browser requests from this console.
3. Install dependencies with `npm install`.
4. Start the development server with `npm run dev`.
5. Enter the demo bearer token configured through `RELAY_DEMO_TOKEN` when the
   console prompts for it.

The token is held in browser session storage for the current tab session and is
not committed or logged. Use **Change API token** in the navigation rail to
remove it from the session.

## Checks

- `npm run lint`
- `npm run build`
