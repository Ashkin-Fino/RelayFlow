import { useEffect, useMemo, useState } from 'react'
import { ArrowUpRight, Plus } from 'lucide-react'
import { Link } from 'react-router-dom'
import { getWorkflows } from '../api/workflowApi.js'
import EmptyState from '../components/common/EmptyState.jsx'
import ErrorState from '../components/common/ErrorState.jsx'
import LoadingState from '../components/common/LoadingState.jsx'
import PageHeader from '../components/common/PageHeader.jsx'
import StatusBadge from '../components/common/StatusBadge.jsx'
import { shortId } from '../utils/format.js'

const filters = [
  ['all', 'All workflows'],
  ['draft', 'Draft'],
  ['published', 'Published'],
]
const emptyList = []

function WorkflowsPage() {
  const [refreshKey, setRefreshKey] = useState(0)
  const [result, setResult] = useState(null)
  const [filter, setFilter] = useState('all')

  useEffect(() => {
    let active = true
    getWorkflows()
      .then((workflows) => {
        if (active) setResult({ refreshKey, workflows, error: null })
      })
      .catch((error) => {
        if (active) setResult({ refreshKey, workflows: [], error })
      })
    return () => { active = false }
  }, [refreshKey])

  const workflows = result?.refreshKey === refreshKey ? result.workflows : emptyList
  const error = result?.refreshKey === refreshKey ? result.error : null
  const loading = !result || result.refreshKey !== refreshKey
  const refresh = () => setRefreshKey((value) => value + 1)

  const visibleWorkflows = useMemo(
    () => filter === 'all' ? workflows : workflows.filter((workflow) => workflow.status === filter),
    [filter, workflows],
  )

  return (
    <div className="operational-page">
      <PageHeader
        actions={
          <Link className="primary-button button-link" to="/workflows/new">
            <Plus aria-hidden="true" size={16} /> New workflow
          </Link>
        }
        description="Browse workflow definitions and their publication state."
        eyebrow="Workspace"
        title="Workflows"
      />

      <div className="list-toolbar">
        <div aria-label="Filter workflows by status" className="filter-tabs" role="group">
          {filters.map(([value, label]) => (
            <button
              aria-pressed={filter === value}
              className={filter === value ? 'filter-tab selected' : 'filter-tab'}
              key={value}
              onClick={() => setFilter(value)}
              type="button"
            >
              {label}
              {value !== 'all' && (
                <span className="filter-count">
                  {workflows.filter((workflow) => workflow.status === value).length}
                </span>
              )}
            </button>
          ))}
        </div>
        <button className="secondary-button" onClick={refresh} type="button">Refresh</button>
      </div>

      {error && <ErrorState error={error} onRetry={refresh} />}
      {loading && <LoadingState label="Loading workflows…" />}
      {!loading && !error && (
        <section className="content-panel">
          {visibleWorkflows.length === 0 ? (
            <EmptyState
              description={filter === 'all'
                ? 'Create a workflow to get started.'
                : `There are no ${filter} workflows.`}
              title="No workflows found"
            />
          ) : (
            <div className="table-scroll">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Workflow</th>
                    <th>Workflow ID</th>
                    <th>Status</th>
                    <th aria-label="Actions" />
                  </tr>
                </thead>
                <tbody>
                  {visibleWorkflows.map((workflow) => (
                    <tr key={workflow.id}>
                      <td>
                        <Link className="table-primary-link" to={`/workflows/${encodeURIComponent(workflow.id)}`}>
                          {workflow.name}
                        </Link>
                      </td>
                      <td>
                        <span className="mono-text subdued-cell" title={workflow.id}>
                          {shortId(workflow.id, 28)}
                        </span>
                      </td>
                      <td><StatusBadge status={workflow.status} /></td>
                      <td>
                        <Link
                          aria-label={`Open workflow ${workflow.name}`}
                          className="icon-link"
                          title="Open workflow"
                          to={`/workflows/${encodeURIComponent(workflow.id)}`}
                        >
                          <ArrowUpRight aria-hidden="true" size={16} />
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="panel-footer">
            Showing {visibleWorkflows.length} of {workflows.length} workflows
          </div>
        </section>
      )}
    </div>
  )
}

export default WorkflowsPage
