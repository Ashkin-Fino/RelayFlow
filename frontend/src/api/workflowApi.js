import { apiRequest } from './client.js'

export function getWorkflows() {
  return apiRequest('/workflows')
}
