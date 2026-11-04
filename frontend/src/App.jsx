import { useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { setApiToken } from './api/client.js'
import AppShell from './components/layout/AppShell.jsx'
import PagePlaceholder from './components/common/PagePlaceholder.jsx'
import './App.css'

const initialToken = window.sessionStorage.getItem('relay.demoToken') ?? ''
setApiToken(initialToken)

function TokenSetup({ onSave }) {
  const [token, setToken] = useState('')

  function handleSubmit(event) {
    event.preventDefault()
    const trimmedToken = token.trim()
    if (trimmedToken) {
      onSave(trimmedToken)
    }
  }

  return (
    <main className="setup-page">
      <form className="setup-card" onSubmit={handleSubmit}>
        <div className="brand-mark" aria-hidden="true">R</div>
        <p className="eyebrow">RelayFlow Console</p>
        <h1>Connect to your workspace</h1>
        <p className="muted">
          Enter the demo bearer token configured for your RelayFlow API. It is
          kept in this browser session and is never written to application logs.
        </p>
        <label className="field-label" htmlFor="demo-token">Demo bearer token</label>
        <input
          autoComplete="current-password"
          id="demo-token"
          onChange={(event) => setToken(event.target.value)}
          placeholder="Enter token"
          required
          type="password"
          value={token}
        />
        <button className="primary-button setup-submit" type="submit">Continue</button>
        <p className="setup-api-url">
          API: {import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}
        </p>
      </form>
    </main>
  )
}

function PlaceholderRoute({ title, description }) {
  return <PagePlaceholder title={title} description={description} />
}

function App() {
  const [token, setToken] = useState(initialToken)

  function saveToken(value) {
    window.sessionStorage.setItem('relay.demoToken', value)
    setApiToken(value)
    setToken(value)
  }

  function signOut() {
    window.sessionStorage.removeItem('relay.demoToken')
    setApiToken('')
    setToken('')
  }

  if (!token) {
    return <TokenSetup onSave={saveToken} />
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppShell onSignOut={signOut} />}>
          <Route
            element={
              <PlaceholderRoute
                title="Overview"
                description="Your workflow and run activity will appear here."
              />
            }
            path="/"
          />
          <Route
            element={
              <PlaceholderRoute
                title="Workflows"
                description="Browse, inspect, and publish workflow definitions."
              />
            }
            path="/workflows"
          />
          <Route
            element={
              <PlaceholderRoute
                title="Workflow details"
                description="Inspect the canonical definition as JSON or a flowchart."
              />
            }
            path="/workflows/:workflowId"
          />
          <Route
            element={
              <PlaceholderRoute
                title="Workflow Builder"
                description="Create a workflow definition and save it as a Draft."
              />
            }
            path="/workflows/new"
          />
          <Route
            element={
              <PlaceholderRoute
                title="Runs"
                description="Filter run history and open a run trace."
              />
            }
            path="/runs"
          />
          <Route
            element={
              <PlaceholderRoute
                title="Run trace"
                description="Inspect execution steps and available run actions."
              />
            }
            path="/runs/:runId"
          />
          <Route element={<Navigate replace to="/" />} path="*" />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
