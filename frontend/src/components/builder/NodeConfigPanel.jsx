import { useState } from 'react'
import { Trash2 } from 'lucide-react'

function NodeConfigPanel({ node, catalogEntry, nodes, edges, onChange, onDelete }) {
  if (!node) {
    return (
      <section className="builder-panel node-config-panel">
        <div className="builder-panel-heading"><h2>Node settings</h2></div>
        <p className="builder-hint">Select a node on the canvas to configure its ID and parameters.</p>
      </section>
    )
  }

  function changeParam(name, value) {
    onChange({
      ...node,
      data: { ...node.data, params: { ...node.data.params, [name]: value } },
    })
  }

  return (
    <section className="builder-panel node-config-panel">
      <div className="builder-panel-heading">
        <div>
          <h2>Node settings</h2>
          <span className="node-type-caption">{node.data.nodeType}</span>
        </div>
        <button
          aria-label={`Delete ${node.id}`}
          className="icon-button danger-icon-button"
          onClick={() => onDelete(node.id)}
          title="Delete node"
          type="button"
        >
          <Trash2 size={15} />
        </button>
      </div>
      <label className="builder-field">
        <span>Node ID</span>
        <input
          onChange={(event) => onChange({ ...node, id: event.target.value, data: { ...node.data, label: event.target.value } })}
          value={node.id}
        />
      </label>

      <div className="builder-fields-list">
        {Object.entries(catalogEntry.params).map(([name, field]) => (
          <CatalogField
            field={field}
            key={`${node.id}:${name}`}
            name={name}
            onChange={(value) => changeParam(name, value)}
            value={node.data.params?.[name]}
          />
        ))}
      </div>
      {catalogEntry.description && <p className="builder-hint">{catalogEntry.description}</p>}
      <p className="builder-hint">
        {catalogEntry.branches
          ? 'Connect True and False handles to destination nodes; leave a branch unconnected to end that path.'
          : 'Connect the right handle to the next node; leave it unconnected to end the workflow.'}
      </p>
      <label className="builder-field">
        <span>Outgoing connection</span>
        <span className="connection-list">
          {(catalogEntry.branches || ['next']).map((branch) => {
            const edge = edges.find((item) => item.source === node.id && item.sourceHandle === branch)
            return (
              <span className="connection-item" key={branch}>
                <small>{branchLabel(branch)}</small>
                <span className="connection-target">{edge?.target || 'End'}</span>
              </span>
            )
          })}
          {nodes.length < 2 && <small>Add another node to create a connection.</small>}
        </span>
      </label>
    </section>
  )
}

function CatalogField({ field, name, onChange, value }) {
  const label = `${name}${field.required ? ' *' : ''}`
  const description = field.description || (field.templatable ? 'Supports template references such as {{trigger.body.value}}.' : '')

  if (field.enum) {
    return (
      <label className="builder-field">
        <span>{label}</span>
        <select onChange={(event) => onChange(event.target.value)} value={value ?? ''}>
          <option value="">Select…</option>
          {field.enum.map((option) => <option key={option} value={option}>{option}</option>)}
        </select>
        {description && <small>{description}</small>}
      </label>
    )
  }

  if (field.type === 'object') {
    return (
      <ObjectField
        description={description}
        label={label}
        onChange={onChange}
        value={value}
      />
    )
  }

  return (
    <label className="builder-field">
      <span>{label}</span>
      {field.type === 'string' && (name === 'prompt' || name === 'message') ? (
        <textarea
          onChange={(event) => onChange(event.target.value)}
          placeholder={field.templatable ? 'Text or {{trigger.body.field}}' : ''}
          rows={name === 'prompt' ? 5 : 3}
          value={value ?? ''}
        />
      ) : (
        <input
          onChange={(event) => {
            if (field.type === 'number') {
              onChange(event.target.value === '' ? '' : Number(event.target.value))
            } else {
              onChange(event.target.value)
            }
          }}
          placeholder={field.templatable ? 'Text or {{trigger.body.field}}' : ''}
          step={field.type === 'number' ? 'any' : undefined}
          type={field.type === 'number' ? 'number' : 'text'}
          value={value ?? ''}
        />
      )}
      {description && <small>{description}</small>}
    </label>
  )
}

function ObjectField({ description, label, onChange, value }) {
  const [text, setText] = useState(() => typeof value === 'string' ? value : JSON.stringify(value ?? {}, null, 2))
  const [error, setError] = useState('')

  return (
    <label className="builder-field">
      <span>{label}</span>
      <textarea
        className="builder-json-field"
        onBlur={() => {
          if (text.trim().startsWith('{{') && text.trim().endsWith('}}')) {
            onChange(text.trim())
            setError('')
            return
          }
          try {
            const parsed = JSON.parse(text)
            if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
              throw new Error('Value must be a JSON object.')
            }
            onChange(parsed)
            setError('')
          } catch (parseError) {
            setError(parseError.message)
          }
        }}
        onChange={(event) => setText(event.target.value)}
        rows={4}
        value={text}
      />
      {error && <small className="builder-field-error">{error}</small>}
      {!error && description && <small>{description}</small>}
    </label>
  )
}

function branchLabel(branch) {
  if (branch === 'on_true') return 'True'
  if (branch === 'on_false') return 'False'
  return 'Next'
}

export default NodeConfigPanel
