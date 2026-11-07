function ErrorState({ error, onRetry, title }) {
  const message = typeof error === 'string' ? error : error?.message
  const needsAuthentication = error?.status === 401

  return (
    <section aria-live="assertive" className="error-state" role="alert">
      <h2>{title || (needsAuthentication ? 'Authentication required' : 'Unable to load this page')}</h2>
      <p>{message || 'An unexpected error occurred. Please try again.'}</p>
      {needsAuthentication && (
        <p className="error-hint">Check the demo token in your API configuration and reconnect.</p>
      )}
      {onRetry && (
        <button className="secondary-button" onClick={onRetry} type="button">
          Try again
        </button>
      )}
    </section>
  )
}

export default ErrorState
