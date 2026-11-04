import { useEffect, useState } from 'react'
import { ArrowLeft, Ban, Clock3, RefreshCw } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { cancelRun, getRunTrace } from '../api/runApi.js'
import ApprovalActions from '../components/runs/ApprovalActions.jsx'
import RunTrace from '../components/runs/RunTrace.jsx'
import EmptyState from '../components/common/EmptyState.jsx'
import ErrorState from '../components/common/ErrorState.jsx'
import LoadingState from '../components/common/LoadingState.jsx'
import PageHeader from '../components/common/PageHeader.jsx'
import StatusBadge from '../components/common/StatusBadge.jsx'
import { formatDate } from '../utils/format.js'

const cancellableStatuses = new Set(['queued', 'running', 'waiting_approval'])

function RunTracePage() {
  const { runId } = useParams()
  const [refreshKey, setRefreshKey] = useState(0)
  const [result, setResult] = useState(null)
  const [actionBusy, setActionBusy] = useState(false)
  const [actionError, setActionError] = useState(null)

  useEffect(() => {
    let active = true
    getRunTrace(runId)
      .then((trace) => {
        if (active) setResult({ runId, refreshKey, trace, error: null })
      })
      .catch((error) => {
        if (active) setResult({ runId, refreshKey, trace: null, error })
      })
    return () => { active = false }
  }, [runId, refreshKey])

  const resultIsCurrent = result?.runId === runId && result.refreshKey === refreshKey
  const trace = resultIsCurrent ? result.trace : null
  const error = resultIsCurrent ? result.error : null
  const loading = !resultIsCurrent
  const steps = Array.isArray(trace?.steps) ? trace.steps : []
  const canCancel = trace && cancellableStatuses.has(trace.status) && !trace.cancel_requested

  function refresh() {
    setActionError(null)
    setRefreshKey((value) => value + 1)
  }

  async function refreshTrace() {
    const updatedTrace = await getRunTrace(runId)
    setResult({ runId, refreshKey, trace: updatedTrace, error: null })
  }

  async function requestCancellation() {
    if (!trace || !cancellableStatuses.has(trace.status) || trace.cancel_requested) return
    const confirmation = window.confirm(
      trace.status === 'running'
        ? 'Request cancellation? The current node may finish before execution stops.'
        : 'Cancel this run now?',
    )
    if (!confirmation) return

    setActionBusy(true)
    setActionError(null)
    try {
      await cancelRun(runId)
      await refreshTrace()
    } catch (requestError) {
      setActionError(requestError)
    } finally {
      setActionBusy(false)
    }
  }

  async function reloadAfterDecision() {
    setActionError(null)
    await refreshTrace()
  }

  return (
    <div className="operational-page">
      <Link className="back-link" to="/runs"><ArrowLeft size={15} /> Runs</Link>
      {error && <ErrorState error={error} onRetry={refresh} />}
      {loading && <LoadingState label="Loading run trace…" />}
      {!loading && !error && trace && (
        <>
          <PageHeader
            actions={
              <>
                <button className="secondary-button button-link" disabled={actionBusy} onClick={refresh} type="button">
                  <RefreshCw size={14} /> Refresh
                </button>
                {canCancel && (
                  <button className="danger-button button-link" disabled={actionBusy} onClick={requestCancellation} type="button">
                    <Ban size={14} /> {actionBusy ? 'Updating…' : 'Cancel run'}
                  </button>
                )}
              </>
            }
            description={`Workflow ${trace.workflow_id} · ${trace.trigger_type || 'unknown'} trigger`}
            eyebrow="Run trace"
            title={<span className="mono-heading">{trace.run_id}</span>}
          />

          {actionError && <ErrorState error={actionError} title="Action could not be completed" />}
          {trace.cancel_requested && trace.status === 'running' && (
            <div className="notice-banner" role="status">
              Cancellation requested. The current node may finish before the run stops.
            </div>
          )}

          <section aria-label="Run summary" className="run-summary-grid">
            <SummaryItem label="Status"><StatusBadge status={trace.status} /></SummaryItem>
            <SummaryItem label="Started"><span><Clock3 size={13} /> {formatDate(trace.started_at)}</span></SummaryItem>
            <SummaryItem label="Finished">{formatDate(trace.finished_at)}</SummaryItem>
            <SummaryItem label="Steps executed">{trace.steps_executed}</SummaryItem>
            <SummaryItem label="AI tokens used">{trace.ai_tokens_used}</SummaryItem>
            <SummaryItem label="Current node"><span className="mono-text">{trace.current_node_id || '—'}</span></SummaryItem>
          </section>

          {trace.error && (
            <section className="run-error-panel">
              <h2>Run error</h2>
              <pre>{JSON.stringify(trace.error, null, 2)}</pre>
            </section>
          )}

          <section className="content-panel run-trace-panel">
            <div className="panel-heading">
              <div>
                <h2>Execution trace</h2>
                <p>Steps are shown in execution order.</p>
              </div>
              <span className="trace-step-count">{steps.length} {steps.length === 1 ? 'step' : 'steps'}</span>
            </div>
            {steps.length === 0 ? (
              <EmptyState
                description="The worker has not recorded a step for this run yet."
                title="No trace steps yet"
              />
            ) : (
              <div className="run-trace-content">
                <RunTrace steps={steps} />
                {trace.status === 'waiting_approval' && steps
                  .filter((step) => step.type === 'approval' && step.approval_status === 'pending' && step.approval_id)
                  .map((step) => (
                    <section className="approval-action-panel" key={step.approval_id}>
                      <div>
                        <h2>Approval required</h2>
                        <p>{step.node_id} is waiting for a decision.</p>
                      </div>
                      <ApprovalActions
                        approvalId={step.approval_id}
                        busy={actionBusy}
                        onComplete={reloadAfterDecision}
                        onError={setActionError}
                        setBusy={setActionBusy}
                      />
                    </section>
                  ))}
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}

function SummaryItem({ label, children }) {
  return (
    <div className="run-summary-item">
      <span>{label}</span>
      <strong>{children}</strong>
    </div>
  )
}

export default RunTracePage
