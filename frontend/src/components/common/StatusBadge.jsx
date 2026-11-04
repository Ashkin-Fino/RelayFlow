const statusLabels = {
  queued: 'Queued',
  running: 'Running',
  waiting_approval: 'Waiting approval',
  succeeded: 'Succeeded',
  failed: 'Failed',
  cancelled: 'Cancelled',
  pending: 'Pending',
  approved: 'Approved',
  rejected: 'Rejected',
  draft: 'Draft',
  published: 'Published',
}

function StatusBadge({ status }) {
  const normalizedStatus = status?.toLowerCase() || 'unknown'
  const label = statusLabels[normalizedStatus] || status || 'Unknown'

  return (
    <span className={`status-badge status-${normalizedStatus}`}>
      <span aria-hidden="true" className="status-dot" />
      {label}
    </span>
  )
}

export default StatusBadge
