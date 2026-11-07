function EmptyState({ title, description }) {
  return (
    <div className="empty-state">
      <span className="empty-state-mark" aria-hidden="true">—</span>
      <h2>{title}</h2>
      <p>{description}</p>
    </div>
  )
}

export default EmptyState
