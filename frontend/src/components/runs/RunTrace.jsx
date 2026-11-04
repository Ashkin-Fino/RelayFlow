import { useState } from 'react'
import { ChevronDown, ChevronUp, Clock3, Hash, ShieldCheck } from 'lucide-react'
import StatusBadge from '../common/StatusBadge.jsx'
import { formatDate } from '../../utils/format.js'

function jsonText(value) {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'string') return value
  return JSON.stringify(value, null, 2)
}

function RunTrace({ steps }) {
  return (
    <div className="trace-list">
      {steps.map((step, index) => (
        <TraceStep index={index} key={`${step.sequence}-${step.node_id}`} step={step} />
      ))}
    </div>
  )
}

function TraceStep({ index, step }) {
  const [expanded, setExpanded] = useState(index === 0 || step.status === 'failed' || step.type === 'approval')

  return (
    <article className={`trace-step${step.status === 'failed' ? ' trace-step-failed' : ''}`}>
      <div aria-hidden="true" className="trace-sequence">{step.sequence}</div>
      <div className="trace-step-card">
        <button
          aria-expanded={expanded}
          className="trace-step-toggle"
          onClick={() => setExpanded((value) => !value)}
          type="button"
        >
          <span className="trace-step-heading">
            <span className="trace-node-icon"><Hash size={15} /></span>
            <span className="trace-node-name">
              <strong>{step.node_id}</strong>
              <small>{step.type} · attempt {step.attempt}</small>
            </span>
            <StatusBadge status={step.status} />
          </span>
          <span className="trace-step-summary">
            {step.duration_ms != null && <span><Clock3 size={13} /> {step.duration_ms} ms</span>}
            {expanded ? <ChevronUp size={17} /> : <ChevronDown size={17} />}
          </span>
        </button>

        {expanded && (
          <div className="trace-step-details">
            <div className="trace-timing">
              <span>Started <strong>{formatDate(step.started_at)}</strong></span>
              <span>Finished <strong>{formatDate(step.finished_at)}</strong></span>
            </div>
            <div className="trace-data-grid">
              <JsonBlock label="Resolved input" value={step.input} />
              <JsonBlock label="Output" value={step.output} />
              {step.error && <JsonBlock label="Error" value={step.error} tone="error" />}
            </div>
            {(step.tokens_prompt != null || step.tokens_completion != null || step.idempotency_key) && (
              <div className="trace-metadata">
                {step.tokens_prompt != null && (
                  <span>Prompt tokens <strong>{step.tokens_prompt}</strong></span>
                )}
                {step.tokens_completion != null && (
                  <span>Completion tokens <strong>{step.tokens_completion}</strong></span>
                )}
                {step.idempotency_key && (
                  <span className="trace-idempotency">Idempotency key <code>{step.idempotency_key}</code></span>
                )}
              </div>
            )}
            {step.type === 'approval' && <ApprovalDetails step={step} />}
          </div>
        )}
      </div>
    </article>
  )
}

function JsonBlock({ label, value, tone }) {
  return (
    <section className={`trace-json-block${tone ? ` trace-json-${tone}` : ''}`}>
      <h3>{label}</h3>
      <pre>{jsonText(value)}</pre>
    </section>
  )
}

function ApprovalDetails({ step }) {
  if (!step.approval_id) {
    return (
      <div className="approval-details">
        <ShieldCheck size={15} />
        <span>Approval record is not available yet.</span>
      </div>
    )
  }

  return (
    <div className="approval-details">
      <ShieldCheck size={15} />
      <div>
        <span>Approval <code>{step.approval_id}</code></span>
        <StatusBadge status={step.approval_status} />
        {step.approval_decided_by && <span>Decided by {step.approval_decided_by}</span>}
        {step.approval_decided_at && <span>{formatDate(step.approval_decided_at)}</span>}
        {step.approval_reason && <span>Reason: {step.approval_reason}</span>}
      </div>
    </div>
  )
}

export default RunTrace
