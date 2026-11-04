import catalog from '../../../data/node_catalog.json'

export const nodeCatalog = catalog.nodes
export const triggerCatalog = catalog.triggers

export function createWorkflowId() {
  const suffix = globalThis.crypto?.randomUUID?.().replaceAll('-', '').slice(0, 12)
    || Math.random().toString(36).slice(2, 14)
  return `wf_${suffix}`
}

export function createNodeId(type, nodes) {
  const base = type.replaceAll('_', '')
  let suffix = nodes.filter((node) => node.type === type).length + 1
  let id = `${base}_${suffix}`
  while (nodes.some((node) => node.id === id)) {
    suffix += 1
    id = `${base}_${suffix}`
  }
  return id
}

export function workflowDefinitionToBuilderState(definition) {
  const nodes = definition.nodes.map((node, index) => ({
    id: node.id,
    type: 'builder',
    position: { x: 90 + (index % 3) * 270, y: 80 + Math.floor(index / 3) * 175 },
    data: {
      label: node.id,
      nodeType: node.type,
      params: node.params || {},
      isEntry: node.id === definition.entry,
    },
  }))
  const edges = definition.nodes.flatMap((node) => {
    const branches = node.type === 'condition'
      ? ['on_true', 'on_false']
      : ['next']
    return branches.flatMap((branch) => node[branch]
      ? [{
        id: `${node.id}-${branch}-${node[branch]}`,
        source: node.id,
        sourceHandle: branch,
        target: node[branch],
        type: 'smoothstep',
        style: { stroke: '#7890b5', strokeWidth: 1.8 },
      }]
      : [])
  })
  return { nodes, edges }
}

export function serializeWorkflow({ id, name, description, triggerType, triggerConfig,
  maxSteps, timeoutSeconds, maxAiTokens, entry, nodes, edges }) {
  const definitionNodes = nodes.map((node) => {
    const catalogEntry = nodeCatalog.find((item) => item.type === node.data.nodeType)
    const definitionNode = {
      id: node.id,
      type: node.data.nodeType,
      params: Object.fromEntries(
        Object.entries(node.data.params || {}).filter(([, value]) => value !== '' && value !== undefined),
      ),
    }

    if (catalogEntry?.branches) {
      for (const branch of catalogEntry.branches) {
        definitionNode[branch] = edges.find(
          (edge) => edge.source === node.id && edge.sourceHandle === branch,
        )?.target || null
      }
    } else {
      definitionNode.next = edges.find(
        (edge) => edge.source === node.id && (edge.sourceHandle || 'next') === 'next',
      )?.target || null
    }
    return definitionNode
  })

  const trigger = { type: triggerType, ...triggerConfig }
  const limits = { max_steps: Number(maxSteps) }
  if (timeoutSeconds !== '') limits.timeout_seconds = Number(timeoutSeconds)
  if (maxAiTokens !== '') limits.max_ai_tokens = Number(maxAiTokens)

  return {
    id: id.trim(),
    name: name.trim(),
    ...(description.trim() ? { description: description.trim() } : {}),
    trigger,
    entry,
    limits,
    nodes: definitionNodes,
  }
}

export function validateWorkflowDraft(definition) {
  const errors = []
  if (!definition.id) errors.push('Workflow ID is required.')
  if (!definition.name) errors.push('Workflow name is required.')
  if (!definition.entry || !definition.nodes.some((node) => node.id === definition.entry)) {
    errors.push('Choose an entry node that exists in this workflow.')
  }
  const ids = definition.nodes.map((node) => node.id)
  if (new Set(ids).size !== ids.length) errors.push('Node IDs must be unique.')
  if (ids.some((id) => !id.trim())) errors.push('Node IDs must not be blank.')
  if (!Number.isInteger(definition.limits.max_steps) || definition.limits.max_steps < 1) {
    errors.push('Maximum steps must be a positive integer.')
  }
  if (definition.nodes.length === 0) errors.push('Add at least one node.')

  for (const node of definition.nodes) {
    const spec = nodeCatalog.find((item) => item.type === node.type)
    if (!spec) {
      errors.push(`${node.id}: unsupported node type "${node.type}".`)
      continue
    }
    for (const [name, field] of Object.entries(spec.params)) {
      const value = node.params[name]
      const present = value !== undefined && value !== null
        && (typeof value !== 'string' || value.trim() !== '')
        && value !== ''
      if (field.required && !present) {
        errors.push(`${node.id}: "${name}" is required.`)
        continue
      }
      if (!present) continue
      if (field.type === 'number' && (typeof value !== 'number' || !Number.isFinite(value))) {
        errors.push(`${node.id}: "${name}" must be a number.`)
      } else if (field.type === 'object'
        && !(value && typeof value === 'object' && !Array.isArray(value))
        && !(field.templatable && typeof value === 'string' && /^\{\{\s*[^{}]+\s*}}$/.test(value.trim()))) {
        errors.push(`${node.id}: "${name}" must be a JSON object or a whole template reference.`)
      } else if (field.type === 'string' && typeof value !== 'string') {
        errors.push(`${node.id}: "${name}" must be text.`)
      }
      if (field.enum && !field.enum.includes(value)) {
        errors.push(`${node.id}: "${name}" must be one of ${field.enum.join(', ')}.`)
      }
    }
  }

  for (const trigger of triggerCatalog) {
    if (trigger.type !== definition.trigger.type) continue
    for (const [name, field] of Object.entries(trigger.config)) {
      if (field.required && !definition.trigger[name]?.trim()) {
        errors.push(`Trigger "${trigger.type}" requires "${name}".`)
      }
    }
  }

  return errors
}
