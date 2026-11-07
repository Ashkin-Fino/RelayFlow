export function mapWorkflowDefinition(definition) {
  const definitionNodes = Array.isArray(definition?.nodes) ? definition.nodes : []
  const nodeIds = new Set(definitionNodes.map((node) => node.id))
  const nodes = definitionNodes.map((node, index) => ({
    id: node.id,
    type: 'workflow',
    position: { x: (index % 3) * 260, y: Math.floor(index / 3) * 150 },
    data: {
      label: node.id,
      nodeType: node.type,
      isEntry: node.id === definition.entry,
    },
  }))
  const edges = []

  for (const node of definitionNodes) {
    const connections = node.type === 'condition'
      ? [
          ['on_true', node.on_true, 'True'],
          ['on_false', node.on_false, 'False'],
        ]
      : [['next', node.next, undefined]]

    for (const [handle, target, label] of connections) {
      if (typeof target === 'string' && nodeIds.has(target)) {
        edges.push({
          id: `${node.id}:${handle}:${target}`,
          source: node.id,
          target,
          label,
          animated: false,
          type: 'smoothstep',
          style: { stroke: '#94a3b8', strokeWidth: 1.6 },
          labelStyle: { fill: '#64748b', fontSize: 11, fontWeight: 600 },
          labelBgStyle: { fill: '#fff', fillOpacity: 0.95 },
        })
      }
    }
  }

  return { nodes, edges }
}
