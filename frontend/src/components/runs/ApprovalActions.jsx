import { useState } from 'react'
import { Check, X } from 'lucide-react'
import { approveApproval, rejectApproval } from '../../api/approvalApi.js'

function ApprovalActions({ approvalId, onComplete, onError, busy, setBusy }) {
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')

  async function decide(decision) {
    setBusy(true)
    onError(null)
    try {
      if (decision === 'approved') {
        await approveApproval(approvalId)
      } else {
        await rejectApproval(approvalId, reason.trim())
      }
      setRejecting(false)
      setReason('')
      await onComplete()
    } catch (error) {
      onError(error)
    } finally {
      setBusy(false)
    }
  }

  if (rejecting) {
    return (
      <div className="reject-confirm">
        <label htmlFor={`reject-reason-${approvalId}`}>Reason (optional)</label>
        <textarea
          id={`reject-reason-${approvalId}`}
          onChange={(event) => setReason(event.target.value)}
          placeholder="Add a note for the run trace"
          rows={2}
          value={reason}
        />
        <div>
          <button className="secondary-button" disabled={busy} onClick={() => setRejecting(false)} type="button">
            Keep pending
          </button>
          <button className="danger-button" disabled={busy} onClick={() => decide('rejected')} type="button">
            Confirm rejection
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="approval-action-buttons">
      <button className="approve-button" disabled={busy} onClick={() => decide('approved')} type="button">
        <Check aria-hidden="true" size={15} /> Approve
      </button>
      <button className="danger-button" disabled={busy} onClick={() => setRejecting(true)} type="button">
        <X aria-hidden="true" size={15} /> Reject
      </button>
    </div>
  )
}

export default ApprovalActions
