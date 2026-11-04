import { useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { setApiToken } from './api/client.js'
import AppShell from './components/layout/AppShell.jsx'
import OverviewPage from './pages/OverviewPage.jsx'
import RunsPage from './pages/RunsPage.jsx'
import RunTracePage from './pages/RunTracePage.jsx'
import WorkflowDetailPage from './pages/WorkflowDetailPage.jsx'
import WorkflowBuilderPage from './pages/WorkflowBuilderPage.jsx'
import WorkflowsPage from './pages/WorkflowsPage.jsx'
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
          <Route element={<OverviewPage />} path="/" />
          <Route element={<WorkflowsPage />} path="/workflows" />
          <Route element={<WorkflowDetailPage />} path="/workflows/:workflowId" />
          <Route element={<WorkflowBuilderPage />} path="/workflows/new" />
          <Route element={<WorkflowBuilderPage />} path="/workflows/:workflowId/edit" />
          <Route element={<RunsPage />} path="/runs" />
          <Route element={<RunTracePage />} path="/runs/:runId" />
          <Route element={<Navigate replace to="/" />} path="*" />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
