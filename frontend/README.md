# RelayFlow Console

The console is a React + Vite client for the RelayFlow Spring Boot API. The
browser communicates only with the API; it does not connect to PostgreSQL, the
worker, the mock world, or the AI provider.

## Local setup

1. Copy `.env.example` to `.env.local`.
2. Ensure the backend is running at `http://localhost:8080`.
3. Install dependencies with `npm install`.
4. Start the development server with `npm run dev`.
5. Open the Vite URL (usually `http://localhost:5173`) and enter the demo
   bearer token configured through `RELAY_DEMO_TOKEN` when prompted.

In development, the console sends API requests through Vite's same-origin
`/api` proxy, which forwards them to `http://localhost:8080`. This avoids
browser CORS configuration for local development. Production builds use
`VITE_API_BASE_URL` (typically `/api` behind the Nginx reverse proxy).

The token is held in browser session storage for the current tab session and is
not committed or logged. Use **Change API token** in the navigation rail to
remove it from the session.

## Checks

- `npm run lint`
- `npm run build`
