import { useEffect, useMemo, useState } from 'react'
import { ArrowRight, Activity, GitBranch, ShieldCheck } from 'lucide-react'
import { Link } from 'react-router-dom'
import { getRuns } from '../api/runApi.js'
import { getWorkflows } from '../api/workflowApi.js'
import EmptyState from '../components/common/EmptyState.jsx'
import ErrorState from '../components/common/ErrorState.jsx'
import LoadingState from '../components/common/LoadingState.jsx'
import PageHeader from '../components/common/PageHeader.jsx'
import RunTable from '../components/runs/RunTable.jsx'

const runStatuses = [
  ['queued', 'Queued'],
  ['running', 'Running'],
  ['waiting_approval', 'Waiting approval'],
  ['succeeded', 'Succeeded'],
  ['failed', 'Failed'],
  ['cancelled', 'Cancelled'],
]
const emptyList = []

function OverviewPage() {
  const [refreshKey, setRefreshKey] = useState(0)
  const [result, setResult] = useState(null)

  useEffect(() => {
    let active = true
    Promise.all([getWorkflows(), getRuns()])
      .then(([workflows, runs]) => {
        if (active) setResult({ refreshKey, workflows, runs, error: null })
      })
      .catch((error) => {
        if (active) setResult({ refreshKey, workflows: [], runs: [], error })
      })
    return () => { active = false }
  }, [refreshKey])

  const workflows = result?.refreshKey === refreshKey ? result.workflows : emptyList
  const runs = result?.refreshKey === refreshKey ? result.runs : emptyList
  const error = result?.refreshKey === refreshKey ? result.error : null
  const loading = !result || result.refreshKey !== refreshKey
  const refresh = () => setRefreshKey((value) => value + 1)

  const workflowCounts = useMemo(() => ({
    draft: workflows.filter((workflow) => workflow.status === 'draft').length,
    published: workflows.filter((workflow) => workflow.status === 'published').length,
  }), [workflows])

  const runCounts = useMemo(() => Object.fromEntries(
    runStatuses.map(([status]) => [status, runs.filter((run) => run.status === status).length]),
  ), [runs])

  const workflowNames = useMemo(
    () => new Map(workflows.map((workflow) => [workflow.id, workflow.name])),
    [workflows],
  )

  return (
    <div className="operational-page">
      <PageHeader
        description="A live snapshot of workflows, execution activity, and items that need attention."
        eyebrow="Workspace"
        title="Overview"
        actions={<button className="secondary-button" onClick={refresh} type="button">Refresh</button>}
      />

      {error && <ErrorState error={error} onRetry={refresh} />}
      {loading && <LoadingState label="Loading workspace activity…" />}
      {!loading && !error && (
        <>
          <section aria-label="Workflow summary" className="metric-grid workflow-metrics">
            <MetricCard
              caption={`${workflowCounts.published} published · ${workflowCounts.draft} drafts`}
              icon={<GitBranch size={19} />}
              label="Total workflows"
              value={workflows.length}
            />
            <MetricCard
              caption="Available to run"
              icon={<ShieldCheck size={19} />}
              label="Published"
              value={workflowCounts.published}
            />
            <MetricCard
              caption="Waiting for a human decision"
              icon={<Activity size={19} />}
              label="Awaiting approval"
              value={runCounts.waiting_approval}
              emphasis="warning"
            />
          </section>

          <section aria-label="Run counts by status" className="status-count-grid">
            {runStatuses.map(([status, label]) => (
              <Link
                className={`status-count-card status-count-${status}`}
                key={status}
                to={`/runs?status=${encodeURIComponent(status)}`}
              >
                <span>{label}</span>
                <strong>{runCounts[status]}</strong>
              </Link>
            ))}
          </section>

          <section className="content-panel">
            <div className="panel-heading">
              <div>
                <h2>Recent runs</h2>
                <p>Latest activity across all workflows</p>
              </div>
              <Link className="text-link" to="/runs">
                View all runs <ArrowRight aria-hidden="true" size={15} />
              </Link>
            </div>
            {runs.length === 0 ? (
              <EmptyState
                description="Runs will appear here after a workflow is triggered."
                title="No runs yet"
              />
            ) : (
              <RunTable compact runs={runs.slice(0, 8)} workflowNames={workflowNames} />
            )}
          </section>

          <section aria-label="Quick links" className="quick-links">
            <Link className="quick-link-card" to="/workflows">
              <span className="quick-link-icon"><GitBranch size={18} /></span>
              <span><strong>Browse workflows</strong><small>Explore definitions and status</small></span>
              <ArrowRight aria-hidden="true" size={16} />
            </Link>
            <Link className="quick-link-card" to="/workflows/new">
              <span className="quick-link-icon"><ShieldCheck size={18} /></span>
              <span><strong>Create a workflow</strong><small>Build and save a new Draft</small></span>
              <ArrowRight aria-hidden="true" size={16} />
            </Link>
          </section>
        </>
      )}
    </div>
  )
}

function MetricCard({ caption, icon, label, value, emphasis }) {
  return (
    <article className={`metric-card${emphasis ? ` metric-${emphasis}` : ''}`}>
      <div className="metric-card-top">
        <span className="metric-icon">{icon}</span>
        <span className="metric-label">{label}</span>
      </div>
      <strong className="metric-value">{value}</strong>
      <span className="metric-caption">{caption}</span>
    </article>
  )
}

export default OverviewPage
