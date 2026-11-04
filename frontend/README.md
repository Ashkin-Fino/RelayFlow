# RelayFlow Console

The console is a React + Vite client for the RelayFlow Spring Boot API. The
browser communicates only with the API; it does not connect to PostgreSQL, the
worker, the mock world, or the AI provider.

## Local setup

1. Copy `.env.example` to `.env.local` and set `VITE_API_BASE_URL` to the API
   origin (default: `http://localhost:8080`).
2. Install dependencies with `npm install`.
3. Start the development server with `npm run dev`.
4. Enter the demo bearer token configured through `RELAY_DEMO_TOKEN` when the
   console prompts for it.

The token is held in browser session storage for the current tab session and is
not committed or logged. Use **Change API token** in the navigation rail to
remove it from the session.

## Checks

- `npm run lint`
- `npm run build`
