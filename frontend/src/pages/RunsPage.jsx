import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getRuns } from '../api/runApi.js'
import { getWorkflows } from '../api/workflowApi.js'
import EmptyState from '../components/common/EmptyState.jsx'
import ErrorState from '../components/common/ErrorState.jsx'
import LoadingState from '../components/common/LoadingState.jsx'
import PageHeader from '../components/common/PageHeader.jsx'
import RunTable from '../components/runs/RunTable.jsx'

const statuses = [
  ['', 'All statuses'],
  ['queued', 'Queued'],
  ['running', 'Running'],
  ['waiting_approval', 'Pending approval'],
  ['succeeded', 'Succeeded'],
  ['failed', 'Failed'],
  ['cancelled', 'Cancelled'],
]
const emptyList = []

function RunsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const workflowId = searchParams.get('workflowId') || ''
  const status = searchParams.get('status') || ''
  const [refreshKey, setRefreshKey] = useState(0)
  const [result, setResult] = useState(null)
  const queryKey = `${workflowId}\u0000${status}\u0000${refreshKey}`

  useEffect(() => {
    let active = true
    Promise.all([
      getRuns({ workflowId, status }),
      getWorkflows(),
    ])
      .then(([runs, workflows]) => {
        if (active) setResult({ queryKey, runs, workflows, error: null })
      })
      .catch((error) => {
        if (active) setResult({ queryKey, runs: [], workflows: [], error })
      })
    return () => { active = false }
  }, [queryKey, workflowId, status])

  const resultIsCurrent = result?.queryKey === queryKey
  const runs = resultIsCurrent ? result.runs : emptyList
  const workflows = resultIsCurrent ? result.workflows : emptyList
  const error = resultIsCurrent ? result.error : null
  const loading = !resultIsCurrent
  const refresh = () => setRefreshKey((value) => value + 1)
  const workflowNames = useMemo(
    () => new Map(workflows.map((workflow) => [workflow.id, workflow.name])),
    [workflows],
  )

  function updateFilter(key, value) {
    const nextParams = new URLSearchParams(searchParams)
    if (value) {
      nextParams.set(key, value)
    } else {
      nextParams.delete(key)
    }
    setSearchParams(nextParams)
  }

  function clearFilters() {
    setSearchParams({})
  }

  const hasFilters = Boolean(workflowId || status)

  return (
    <div className="operational-page">
      <PageHeader
        description="Review execution history and open a run to inspect its trace."
        eyebrow="Operations"
        title="Runs"
        actions={<button className="secondary-button" onClick={refresh} type="button">Refresh</button>}
      />

      <section aria-label="Run filters" className="filter-panel">
        <div className="filter-field">
          <label htmlFor="workflow-filter">Workflow</label>
          <select
            id="workflow-filter"
            onChange={(event) => updateFilter('workflowId', event.target.value)}
            value={workflowId}
          >
            <option value="">All workflows</option>
            {workflowId && !workflows.some((workflow) => workflow.id === workflowId) && (
              <option value={workflowId}>{workflowId} (not in workflow list)</option>
            )}
            {workflows.map((workflow) => (
              <option key={workflow.id} value={workflow.id}>{workflow.name}</option>
            ))}
          </select>
        </div>
        <div className="filter-field">
          <label htmlFor="status-filter">Run status</label>
          <select
            id="status-filter"
            onChange={(event) => updateFilter('status', event.target.value)}
            value={status}
          >
            {status && !statuses.some(([value]) => value === status) && (
              <option value={status}>Unsupported status: {status}</option>
            )}
            {statuses.map(([value, label]) => (
              <option key={value || 'all'} value={value}>{label}</option>
            ))}
          </select>
        </div>
        {hasFilters && (
          <button className="clear-filters-button" onClick={clearFilters} type="button">
            Clear filters
          </button>
        )}
        <div aria-live="polite" className="filter-summary">
          {loading ? 'Updating results…' : `${runs.length} ${runs.length === 1 ? 'run' : 'runs'}`}
        </div>
      </section>

      {error && <ErrorState error={error} onRetry={refresh} />}
      {loading && <LoadingState label="Loading runs…" />}
      {!loading && !error && (
        <section className="content-panel">
          {runs.length === 0 ? (
            <EmptyState
              description={hasFilters
                ? 'Try changing or clearing the selected filters.'
                : 'Runs will appear here when a workflow is triggered.'}
              title={hasFilters ? 'No matching runs' : 'No runs yet'}
            />
          ) : (
            <RunTable runs={runs} workflowNames={workflowNames} />
          )}
          <div className="panel-footer">
            {hasFilters ? 'Filtered results' : 'All runs'} · newest first
          </div>
        </section>
      )}
    </div>
  )
}

export default RunsPage
