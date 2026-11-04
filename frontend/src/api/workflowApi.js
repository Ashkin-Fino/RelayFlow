import { apiRequest } from './client.js'

export function getWorkflows() {
  return apiRequest('/workflows')
}

export function getWorkflow(workflowId) {
  return apiRequest(`/workflows/${encodeURIComponent(workflowId)}`)
}

export function createWorkflow(definition) {
  return apiRequest('/workflows', {
    method: 'POST',
    body: definition,
  })
}

export function publishWorkflow(workflowId) {
  return apiRequest(`/workflows/${encodeURIComponent(workflowId)}/publish`, {
    method: 'POST',
  })
}

export function triggerWorkflow(workflowId, input) {
  return apiRequest(`/workflows/${encodeURIComponent(workflowId)}/trigger`, {
    method: 'POST',
    body: { input },
  })
}
