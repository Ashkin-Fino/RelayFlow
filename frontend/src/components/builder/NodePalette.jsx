import { Plus } from 'lucide-react'

function NodePalette({ nodes, onAdd }) {
  return (
    <section className="builder-panel palette-panel">
      <div className="builder-panel-heading">
        <h2>Node palette</h2>
        <span>{nodes.length} types</span>
      </div>
      <div className="node-palette-list">
        {nodes.map((node) => (
          <button className="palette-node" key={node.type} onClick={() => onAdd(node)} type="button">
            <span className={`palette-node-icon node-icon-${node.type}`} aria-hidden="true">
              {node.type.slice(0, 1).toUpperCase()}
            </span>
            <span>
              <strong>{node.type}</strong>
              <small>{node.description}</small>
            </span>
            <Plus aria-hidden="true" size={15} />
          </button>
        ))}
      </div>
    </section>
  )
}

export default NodePalette
