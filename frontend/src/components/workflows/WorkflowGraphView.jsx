import { useMemo } from 'react'
import { Background, Controls, Handle, Position, ReactFlow } from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { mapWorkflowDefinition } from '../../utils/workflowGraphMapper.js'

function WorkflowNode({ data }) {
  return (
    <div className={`workflow-flow-node${data.isEntry ? ' entry-node' : ''}`}>
      <Handle position={Position.Left} type="target" />
      {data.isEntry && <span className="flow-entry-label">ENTRY</span>}
      <strong>{data.label}</strong>
      <span className="flow-node-type">{data.nodeType}</span>
      <Handle position={Position.Right} type="source" />
    </div>
  )
}

const nodeTypes = { workflow: WorkflowNode }

function WorkflowGraphView({ definition }) {
  const { nodes, edges } = useMemo(() => mapWorkflowDefinition(definition), [definition])

  if (nodes.length === 0) {
    return <p className="muted graph-empty">This definition does not contain any nodes.</p>
  }

  return (
    <div aria-label="Workflow flowchart" className="workflow-graph">
      <ReactFlow
        edges={edges}
        fitView
        fitViewOptions={{ padding: 0.25 }}
        nodes={nodes}
        nodesConnectable={false}
        nodesDraggable={false}
        nodeTypes={nodeTypes}
        zoomOnDoubleClick={false}
      >
        <Background color="#dce3ed" gap={20} size={1} />
        <Controls />
      </ReactFlow>
    </div>
  )
}

export default WorkflowGraphView
