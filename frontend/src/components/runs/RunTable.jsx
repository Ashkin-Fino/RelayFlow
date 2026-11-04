import { ArrowUpRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import StatusBadge from '../common/StatusBadge.jsx'
import { formatDate, shortId } from '../../utils/format.js'

function RunTable({ runs, workflowNames = new Map(), compact = false }) {
  return (
    <div className="table-scroll">
      <table className="data-table">
        <thead>
          <tr>
            <th>Run ID</th>
            <th>Workflow</th>
            {!compact && <th>Trigger</th>}
            <th>Status</th>
            {!compact && <th>Current node</th>}
            <th>Started</th>
            {!compact && <th>Finished</th>}
            <th aria-label="Actions" />
          </tr>
        </thead>
        <tbody>
          {runs.map((run) => (
            <tr key={run.run_id}>
              <td>
                <Link className="table-primary-link mono-text" title={run.run_id} to={`/runs/${encodeURIComponent(run.run_id)}`}>
                  {shortId(run.run_id)}
                </Link>
              </td>
              <td>
                <Link className="table-primary-link" title={run.workflow_id} to={`/workflows/${encodeURIComponent(run.workflow_id)}`}>
                  <span>{workflowNames.get(run.workflow_id) || shortId(run.workflow_id, 22)}</span>
                  {workflowNames.has(run.workflow_id) && (
                    <small className="table-secondary-text">{run.workflow_id}</small>
                  )}
                </Link>
              </td>
              {!compact && <td className="capitalize-text">{run.trigger_type || '—'}</td>}
              <td><StatusBadge status={run.status} /></td>
              {!compact && <td className="mono-text subdued-cell">{run.current_node_id || '—'}</td>}
              <td className="date-cell">{formatDate(run.started_at || run.created_at)}</td>
              {!compact && <td className="date-cell">{formatDate(run.finished_at)}</td>}
              <td>
                <Link
                  aria-label={`Open trace for run ${run.run_id}`}
                  className="icon-link"
                  title="Open run trace"
                  to={`/runs/${encodeURIComponent(run.run_id)}`}
                >
                  <ArrowUpRight aria-hidden="true" size={16} />
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default RunTable
