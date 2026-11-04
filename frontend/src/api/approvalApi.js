import { apiRequest } from './client.js'

export function approveApproval(approvalId) {
  return apiRequest(`/approvals/${encodeURIComponent(approvalId)}/approve`, { method: 'POST' })
}

export function rejectApproval(approvalId, reason) {
  return apiRequest(`/approvals/${encodeURIComponent(approvalId)}/reject`, {
    method: 'POST',
    body: reason ? { reason } : {},
  })
}
