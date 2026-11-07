import { useEffect, useMemo, useState } from 'react'
import { ArrowLeft, ArrowRight, Play } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { getWorkflow, publishWorkflow, triggerWorkflow } from '../api/workflowApi.js'
import ErrorState from '../components/common/ErrorState.jsx'
import LoadingState from '../components/common/LoadingState.jsx'
import PageHeader from '../components/common/PageHeader.jsx'
import StatusBadge from '../components/common/StatusBadge.jsx'
import WorkflowGraphView from '../components/workflows/WorkflowGraphView.jsx'
import WorkflowJsonView from '../components/workflows/WorkflowJsonView.jsx'
import { formatDate } from '../utils/format.js'

function WorkflowDetailPage() {
  const { workflowId } = useParams()
  const navigate = useNavigate()
  const [refreshKey, setRefreshKey] = useState(0)
  const [result, setResult] = useState(null)
  const [view, setView] = useState('flowchart')
  const [showTrigger, setShowTrigger] = useState(false)
  const [triggerInput, setTriggerInput] = useState('{\n  \n}')
  const [triggerError, setTriggerError] = useState(null)
  const [triggering, setTriggering] = useState(false)
  const [publishing, setPublishing] = useState(false)
  const [publishError, setPublishError] = useState(null)

  useEffect(() => {
    let active = true
    getWorkflow(workflowId)
      .then((workflow) => {
        if (active) setResult({ workflowId, refreshKey, workflow, error: null })
      })
      .catch((error) => {
        if (active) setResult({ workflowId, refreshKey, workflow: null, error })
      })
    return () => { active = false }
  }, [workflowId, refreshKey])

  const resultIsCurrent = result?.workflowId === workflowId && result.refreshKey === refreshKey
  const workflow = resultIsCurrent ? result.workflow : null
  const error = resultIsCurrent ? result.error : null
  const loading = !resultIsCurrent
  const definition = workflow?.definition
  const triggerType = definition?.trigger?.type
  const canManuallyTrigger = triggerType === 'manual'
  const canTriggerNow = canManuallyTrigger && workflow?.status === 'published'
  const nodeCount = Array.isArray(definition?.nodes) ? definition.nodes.length : 0
  const triggerPayloadSample = useMemo(() => buildTriggerPayloadSample(definition), [definition])

  const triggerLabel = useMemo(
    () => canTriggerNow
      ? 'Trigger workflow'
      : canManuallyTrigger ? 'Publish this workflow to trigger it' : 'Manual trigger is not supported',
    [canManuallyTrigger, canTriggerNow],
  )

  async function submitTrigger(event) {
    event.preventDefault()
    setTriggerError(null)
    let parsedInput
    try {
      parsedInput = JSON.parse(triggerInput)
    } catch {
      setTriggerError(new Error('Input must be valid JSON.'))
      return
    }
    if (!parsedInput || typeof parsedInput !== 'object' || Array.isArray(parsedInput)) {
      setTriggerError(new Error('Input must be a JSON object.'))
      return
    }

    setTriggering(true)
    try {
      const response = await triggerWorkflow(workflowId, parsedInput)
      if (!response?.run_id) {
        throw new Error('The API response did not include a run_id.')
      }
      setShowTrigger(false)
      navigate(`/runs/${encodeURIComponent(response.run_id)}`)
    } catch (requestError) {
      setTriggerError(requestError)
    } finally {
      setTriggering(false)
    }
  }

  async function handlePublish() {
    setPublishing(true)
    setPublishError(null)
    try {
      await publishWorkflow(workflowId)
      setRefreshKey((key) => key + 1)
    } catch (requestError) {
      setPublishError(requestError)
    } finally {
      setPublishing(false)
    }
  }

  return (
    <div className="operational-page">
      <Link className="back-link" to="/workflows"><ArrowLeft size={15} /> Workflows</Link>
      {error && <ErrorState error={error} onRetry={() => setRefreshKey((key) => key + 1)} />}
      {loading && <LoadingState label="Loading workflow…" />}
      {!loading && !error && workflow && (
        <>
          <PageHeader
            actions={
              <>
                {workflow.status === 'draft' ? (
                  <Link
                    className="secondary-button button-link"
                    to={`/workflows/${encodeURIComponent(workflow.id)}/edit`}
                  >
                    Edit <ArrowRight size={15} />
                  </Link>
                ) : (
                  <Link
                    className="secondary-button button-link"
                    to={`/runs?workflowId=${encodeURIComponent(workflow.id)}`}
                  >
                    View runs <ArrowRight size={15} />
                  </Link>
                )}
                {canManuallyTrigger && (
                  <button
                    className="primary-button button-link"
                    disabled={!canTriggerNow}
                    onClick={() => {
                      setTriggerError(null)
                      setShowTrigger(true)
                    }}
                    title={triggerLabel}
                    type="button"
                  >
                    <Play aria-hidden="true" size={15} /> Trigger
                  </button>
                )}
                {workflow.status === 'draft' && (
                  <button
                    className="primary-button"
                    disabled={publishing}
                    onClick={handlePublish}
                    type="button"
                  >
                    {publishing ? 'Publishing…' : 'Publish workflow'}
                  </button>
                )}
              </>
            }
            description={workflow.description || 'Workflow definition and execution settings.'}
            eyebrow="Workflow details"
            title={workflow.name}
          />

          {publishError && <ErrorState error={publishError} title="Could not publish workflow" />}

          <section aria-label="Workflow metadata" className="workflow-detail-summary">
            <div className="workflow-status-block">
              <StatusBadge status={workflow.status} />
              <span className="mono-text">{workflow.id}</span>
            </div>
            <div className="workflow-meta-grid">
              <MetaItem label="Trigger" value={triggerType || '—'} />
              <MetaItem label="Entry node" value={definition?.entry || '—'} mono />
              <MetaItem label="Nodes" value={nodeCount} />
              <MetaItem label="Last updated" value={formatDate(workflow.updated_at)} />
            </div>
          </section>

          <section aria-labelledby="trigger-payload-title" className="content-panel trigger-payload-panel">
            <div className="trigger-payload-heading">
              <div>
                <h2 id="trigger-payload-title">Sample trigger payload</h2>
                <p>
                  {triggerType === 'webhook'
                    ? 'Send this JSON as the webhook request body.'
                    : triggerType === 'manual'
                      ? 'Use this object as the input when manually triggering the workflow.'
                      : 'This structure is inferred from the workflow’s trigger.body template references.'}
                </p>
              </div>
            </div>
            <pre className="trigger-payload-json"><code>{JSON.stringify(triggerPayloadSample.payload, null, 2)}</code></pre>
            <p className="trigger-payload-note">
              {triggerPayloadSample.hasReferences
                ? triggerPayloadSample.omittedArrayIndexes
                  ? 'Replace the placeholder values with your data. Array paths using indices above 1000 are omitted from this sample.'
                  : 'Replace the placeholder values with your data.'
                : triggerPayloadSample.usesWholeBody
                  ? 'The workflow uses the entire trigger body; add the JSON fields it needs.'
                  : 'No trigger.body fields are referenced, so an empty object is shown.'}
            </p>
          </section>

          <section className="content-panel workflow-definition-panel">
            <div className="definition-toolbar">
              <div>
                <h2>Workflow definition</h2>
                <p>JSON and flowchart are views of the same stored definition.</p>
              </div>
              <div aria-label="Workflow definition view" className="filter-tabs" role="tablist">
                <button
                  aria-selected={view === 'flowchart'}
                  className={view === 'flowchart' ? 'filter-tab selected' : 'filter-tab'}
                  onClick={() => setView('flowchart')}
                  role="tab"
                  type="button"
                >
                  Flowchart
                </button>
                <button
                  aria-selected={view === 'json'}
                  className={view === 'json' ? 'filter-tab selected' : 'filter-tab'}
                  onClick={() => setView('json')}
                  role="tab"
                  type="button"
                >
                  JSON
                </button>
              </div>
            </div>
            <div className="definition-view" role="tabpanel">
              {view === 'flowchart'
                ? <WorkflowGraphView definition={definition} />
                : <WorkflowJsonView definition={definition} />}
            </div>
          </section>
        </>
      )}

      {showTrigger && (
        <div className="modal-backdrop" onMouseDown={(event) => {
          if (event.target === event.currentTarget && !triggering) setShowTrigger(false)
        }}>
          <form aria-labelledby="trigger-dialog-title" className="modal-card" onSubmit={submitTrigger} role="dialog" aria-modal="true">
            <div className="modal-heading">
              <div>
                <p className="eyebrow">Manual trigger</p>
                <h2 id="trigger-dialog-title">Start a workflow run</h2>
              </div>
              <button
                aria-label="Close dialog"
                className="modal-close"
                disabled={triggering}
                onClick={() => setShowTrigger(false)}
                type="button"
              >
                ×
              </button>
            </div>
            <p className="modal-description">
              Provide the JSON object that will be available to nodes as trigger input.
            </p>
            <label className="field-label" htmlFor="trigger-input">Input JSON object</label>
            <textarea
              autoFocus
              className="json-input"
              id="trigger-input"
              onChange={(event) => setTriggerInput(event.target.value)}
              spellCheck="false"
              value={triggerInput}
            />
            {triggerError && <div className="inline-error" role="alert">{triggerError.message}</div>}
            <div className="modal-actions">
              <button className="secondary-button" disabled={triggering} onClick={() => setShowTrigger(false)} type="button">
                Cancel
              </button>
              <button className="primary-button button-link" disabled={triggering} type="submit">
                <Play size={14} /> {triggering ? 'Starting…' : 'Start run'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  )
}

function buildTriggerPayloadSample(definition) {
  const paths = new Map()
  let usesWholeBody = false

  function collectPaths(value) {
    if (typeof value === 'string') {
      const templatePattern = /\{\{\s*trigger\.body(?:\.([^{}]*?))?\s*\}\}/g
      let match = templatePattern.exec(value)
      while (match) {
        const path = match[1]?.trim()
        if (path === undefined || path === '') {
          usesWholeBody = true
        } else {
          const segments = path.split('.')
          if (segments.every((segment) => segment.length > 0)) {
            paths.set(JSON.stringify(segments), segments)
          }
        }
        match = templatePattern.exec(value)
      }
    } else if (Array.isArray(value)) {
      value.forEach(collectPaths)
    } else if (value && typeof value === 'object') {
      Object.values(value).forEach(collectPaths)
    }
  }

  collectPaths(definition)
  const payload = {}
  let omittedArrayIndexes = false

  function isArrayIndex(segment) {
    return /^(0|[1-9]\d*)$/.test(segment) && Number.isSafeInteger(Number(segment))
  }

  function insertPath(container, segments, offset) {
    const segment = segments[offset]
    const isLast = offset === segments.length - 1
    const value = isLast ? '<value>' : (isArrayIndex(segments[offset + 1]) ? [] : {})

    if (Array.isArray(container)) {
      if (!isArrayIndex(segment)) return
      const index = Number(segment)
      if (index > 1000) {
        omittedArrayIndexes = true
        return
      }
      if (isLast) {
        if (container[index] === undefined) container[index] = value
        return
      }
      if (!container[index] || typeof container[index] !== 'object') {
        container[index] = value
      }
      insertPath(container[index], segments, offset + 1)
      return
    }

    if (isLast) {
      if (container[segment] === undefined) container[segment] = value
      return
    }
    if (!container[segment] || typeof container[segment] !== 'object') {
      container[segment] = value
    }
    insertPath(container[segment], segments, offset + 1)
  }

  paths.forEach((segments) => insertPath(payload, segments, 0))
  return {
    payload,
    hasReferences: paths.size > 0,
    usesWholeBody,
    omittedArrayIndexes,
  }
}

function MetaItem({ label, value, mono }) {
  return (
    <div className="workflow-meta-item">
      <span>{label}</span>
      <strong className={mono ? 'mono-text' : ''}>{value}</strong>
    </div>
  )
}

export default WorkflowDetailPage
