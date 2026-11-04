import { apiRequest } from './client.js'

export function getRuns({ workflowId, status } = {}) {
  const query = new URLSearchParams()
  if (workflowId) query.set('workflowId', workflowId)
  if (status) query.set('status', status)
  const search = query.toString()
  return apiRequest(`/runs${search ? `?${search}` : ''}`)
}
