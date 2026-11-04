import { Handle, Position } from '@xyflow/react'

function BuilderFlowNode({ data, selected }) {
  const isCondition = data.nodeType === 'condition'
  return (
    <div className={`builder-flow-node${selected ? ' selected' : ''}${data.isEntry ? ' entry-node' : ''}`}>
      <Handle position={Position.Left} type="target" />
      {data.isEntry && <span className="flow-entry-label">ENTRY</span>}
      <strong>{data.label}</strong>
      <span className="flow-node-type">{data.nodeType}</span>
      {isCondition ? (
        <>
          <span className="branch-handle-label branch-true">True</span>
          <span className="branch-handle-label branch-false">False</span>
          <Handle id="on_true" position={Position.Right} style={{ top: '38%' }} type="source" />
          <Handle id="on_false" position={Position.Right} style={{ top: '68%' }} type="source" />
        </>
      ) : (
        <Handle id="next" position={Position.Right} type="source" />
      )}
    </div>
  )
}

export default BuilderFlowNode
