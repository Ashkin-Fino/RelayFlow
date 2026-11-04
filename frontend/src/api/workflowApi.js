import { apiRequest } from './client.js'

export function getWorkflows() {
  return apiRequest('/workflows')
}

export function getWorkflow(workflowId) {
  return apiRequest(`/workflows/${encodeURIComponent(workflowId)}`)
}

export function triggerWorkflow(workflowId, input) {
  return apiRequest(`/workflows/${encodeURIComponent(workflowId)}/trigger`, {
    method: 'POST',
    body: { input },
  })
}
