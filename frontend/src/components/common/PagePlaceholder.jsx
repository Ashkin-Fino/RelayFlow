import { ArrowUpRight } from 'lucide-react'

function PagePlaceholder({ title, description }) {
  return (
    <section className="page-placeholder">
      <div className="page-heading">
        <p className="eyebrow">RelayFlow Console</p>
        <h1>{title}</h1>
        <p className="page-description">{description}</p>
      </div>
      <div className="placeholder-card">
        <div className="placeholder-icon" aria-hidden="true">
          <ArrowUpRight size={20} />
        </div>
        <div>
          <h2>Console foundation is ready</h2>
          <p>
            This page will be connected to the RelayFlow API in the next
            implementation step.
          </p>
        </div>
      </div>
      <div className="page-footnote">
        API base URL: <code>{import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'}</code>
      </div>
    </section>
  )
}

export default PagePlaceholder
