function LoadingState({ label = 'Loading…' }) {
  return (
    <div aria-live="polite" className="loading-state" role="status">
      <span aria-hidden="true" className="loading-spinner" />
      <span>{label}</span>
    </div>
  )
}

export default LoadingState
