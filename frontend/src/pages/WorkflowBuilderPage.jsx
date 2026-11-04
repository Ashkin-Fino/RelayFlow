import { useMemo, useState } from 'react'
import {
  addEdge,
  Background,
  Controls,
  ReactFlow,
  useEdgesState,
  useNodesState,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { ArrowLeft, Save } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { createWorkflow } from '../api/workflowApi.js'
import ErrorState from '../components/common/ErrorState.jsx'
import BuilderFlowNode from '../components/builder/BuilderFlowNode.jsx'
import NodeConfigPanel from '../components/builder/NodeConfigPanel.jsx'
import NodePalette from '../components/builder/NodePalette.jsx'
import PageHeader from '../components/common/PageHeader.jsx'
import { createNodeId, createWorkflowId, nodeCatalog, serializeWorkflow,
  triggerCatalog, validateWorkflowDraft } from '../utils/workflowBuilder.js'

const flowNodeTypes = { builder: BuilderFlowNode }

function WorkflowBuilderPage() {
  const navigate = useNavigate()
  const [nodes, setNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const [selectedNodeId, setSelectedNodeId] = useState(null)
  const [workflowId, setWorkflowId] = useState(createWorkflowId)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [triggerType, setTriggerType] = useState('manual')
  const [triggerConfig, setTriggerConfig] = useState({})
  const [maxSteps, setMaxSteps] = useState('20')
  const [timeoutSeconds, setTimeoutSeconds] = useState('')
  const [maxAiTokens, setMaxAiTokens] = useState('')
  const [entry, setEntry] = useState('')
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState(null)
  const [showValidation, setShowValidation] = useState(false)

  const selectedNode = nodes.find((node) => node.id === selectedNodeId) || null
  const selectedCatalogEntry = selectedNode
    ? nodeCatalog.find((node) => node.type === selectedNode.data.nodeType)
    : null
  const definition = useMemo(() => serializeWorkflow({
    id: workflowId,
    name,
    description,
    triggerType,
    triggerConfig,
    maxSteps,
    timeoutSeconds,
    maxAiTokens,
    entry,
    nodes,
    edges,
  }), [workflowId, name, description, triggerType, triggerConfig, maxSteps, timeoutSeconds, maxAiTokens, entry, nodes, edges])
  const validationErrors = useMemo(() => validateWorkflowDraft(definition), [definition])
  const activeTrigger = triggerCatalog.find((trigger) => trigger.type === triggerType)

  function addNode(catalogEntry) {
    const nodeId = createNodeId(catalogEntry.type, nodes)
    const index = nodes.length
    const newNode = {
      id: nodeId,
      type: 'builder',
      position: { x: 90 + (index % 3) * 270, y: 80 + Math.floor(index / 3) * 175 },
      data: {
        label: nodeId,
        nodeType: catalogEntry.type,
        params: {},
        isEntry: nodes.length === 0,
      },
    }
    setNodes((current) => [...current, newNode])
    if (!entry) setEntry(nodeId)
    setSelectedNodeId(nodeId)
    setShowValidation(false)
  }

  function connectNodes(connection) {
    if (connection.source === connection.target) return
    const source = nodes.find((node) => node.id === connection.source)
    const sourceSpec = nodeCatalog.find((item) => item.type === source?.data.nodeType)
    const handle = connection.sourceHandle || 'next'
    if (!sourceSpec?.branches?.includes(handle) && handle !== 'next') return
    if (!sourceSpec?.branches && handle !== 'next') return

    setEdges((current) => addEdge({
      ...connection,
      sourceHandle: handle,
      type: 'smoothstep',
      style: { stroke: '#7890b5', strokeWidth: 1.8 },
      label: handle === 'on_true' ? 'True' : handle === 'on_false' ? 'False' : undefined,
      labelStyle: { fill: '#64748b', fontSize: 10, fontWeight: 600 },
      labelBgStyle: { fill: '#fff', fillOpacity: 0.95 },
    }, current.filter((edge) => !(edge.source === connection.source && edge.sourceHandle === handle))))
    setShowValidation(false)
  }

  function updateNode(updatedNode) {
    const oldId = selectedNodeId
    const newId = updatedNode.id
    if (newId !== oldId && nodes.some((node) => node.id === newId)) {
      setSaveError(new Error(`A node with ID "${newId}" already exists.`))
      return
    }
    setSaveError(null)
    setNodes((current) => current.map((node) => node.id === oldId ? updatedNode : node))
    if (newId !== oldId) {
      setEdges((current) => current.map((edge) => ({
        ...edge,
        source: edge.source === oldId ? newId : edge.source,
        target: edge.target === oldId ? newId : edge.target,
      })))
      if (entry === oldId) setEntry(newId)
      setSelectedNodeId(newId)
    }
    setShowValidation(false)
  }

  function deleteNode(nodeId) {
    const remaining = nodes.filter((node) => node.id !== nodeId)
    setNodes(remaining)
    setEdges((current) => current.filter((edge) => edge.source !== nodeId && edge.target !== nodeId))
    if (entry === nodeId) {
      const nextEntry = remaining[0]?.id || ''
      setEntry(nextEntry)
    }
    setSelectedNodeId(null)
    setShowValidation(false)
  }

  function updateTriggerType(value) {
    setTriggerType(value)
    setTriggerConfig({})
    setShowValidation(false)
  }

  async function saveDraft() {
    setShowValidation(true)
    setSaveError(null)
    if (validationErrors.length) return

    setSaving(true)
    try {
      const response = await createWorkflow(definition)
      if (!response?.id) throw new Error('The API response did not include a workflow ID.')
      navigate(`/workflows/${encodeURIComponent(response.id)}`)
    } catch (error) {
      setSaveError(error)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="operational-page builder-page">
      <Link className="back-link" to="/workflows"><ArrowLeft size={15} /> Workflows</Link>
      <PageHeader
        actions={
          <button
            className="primary-button button-link"
            disabled={saving}
            onClick={saveDraft}
            type="button"
          >
            <Save aria-hidden="true" size={15} /> {saving ? 'Saving…' : 'Save as Draft'}
          </button>
        }
        description="Build a canonical workflow definition. The API validates it when you save or publish."
        eyebrow="Workflow Builder"
        title="Create workflow"
      />

      <div className="builder-settings-grid">
        <section className="builder-panel workflow-settings-panel">
          <div className="builder-panel-heading"><h2>Workflow settings</h2></div>
          <label className="builder-field">
            <span>Workflow name *</span>
            <input onChange={(event) => setName(event.target.value)} placeholder="e.g. Order review" value={name} />
          </label>
          <label className="builder-field">
            <span>Workflow ID *</span>
            <input onChange={(event) => setWorkflowId(event.target.value)} value={workflowId} />
            <small>Must be unique. Used in API paths and run history.</small>
          </label>
          <label className="builder-field">
            <span>Description</span>
            <textarea onChange={(event) => setDescription(event.target.value)} rows={2} value={description} />
          </label>
          <div className="builder-fields-row">
            <label className="builder-field">
              <span>Trigger type *</span>
              <select onChange={(event) => updateTriggerType(event.target.value)} value={triggerType}>
                {triggerCatalog.map((trigger) => (
                  <option key={trigger.type} value={trigger.type}>{trigger.type}</option>
                ))}
              </select>
            </label>
            <label className="builder-field">
              <span>Entry node *</span>
              <select onChange={(event) => setEntry(event.target.value)} value={entry}>
                <option value="">Select node…</option>
                {nodes.map((node) => <option key={node.id} value={node.id}>{node.id}</option>)}
              </select>
            </label>
          </div>
          <TriggerConfigFields
            config={activeTrigger?.config || {}}
            values={triggerConfig}
            onChange={(key, value) => setTriggerConfig((current) => ({ ...current, [key]: value }))}
          />
          <div className="builder-panel-divider" />
          <div className="builder-panel-heading compact-builder-heading"><h2>Execution limits</h2></div>
          <div className="builder-fields-row">
            <NumberField label="Maximum steps *" min="1" onChange={setMaxSteps} value={maxSteps} />
            <NumberField label="Timeout (seconds)" min="0" onChange={setTimeoutSeconds} value={timeoutSeconds} />
            <NumberField label="AI token limit" min="0" onChange={setMaxAiTokens} value={maxAiTokens} />
          </div>
        </section>

        <NodePalette nodes={nodeCatalog} onAdd={addNode} />
      </div>

      {saveError && <ErrorState error={saveError} title="Could not save workflow" />}
      {showValidation && validationErrors.length > 0 && (
        <section aria-live="polite" className="validation-panel">
          <strong>Complete these items before saving:</strong>
          <ul>{validationErrors.map((error) => <li key={error}>{error}</li>)}</ul>
        </section>
      )}

      <section aria-label="Workflow graph editor" className="builder-canvas-panel">
        <div className="builder-panel-heading canvas-panel-heading">
          <div>
            <h2>Workflow graph</h2>
            <span>Drag from node handles to connect steps. Select a node to edit it.</span>
          </div>
          <span className="canvas-help">Condition handles: True / False</span>
        </div>
        <div className="builder-canvas">
          <ReactFlow
            edges={edges}
            fitView
            fitViewOptions={{ padding: 0.22 }}
            isValidConnection={(connection) => connection.source !== connection.target}
            nodes={nodes.map((node) => ({
              ...node,
              data: { ...node.data, isEntry: node.id === entry },
            }))}
            onConnect={connectNodes}
            onEdgesChange={(changes) => {
              onEdgesChange(changes)
              setShowValidation(false)
            }}
            onNodeClick={(_, node) => setSelectedNodeId(node.id)}
            onNodesChange={onNodesChange}
            nodeTypes={flowNodeTypes}
            nodesConnectable
            nodesDraggable
            onPaneClick={() => setSelectedNodeId(null)}
            onNodesDelete={(deleted) => deleted.forEach((node) => deleteNode(node.id))}
            onEdgesDelete={() => setShowValidation(false)}
            deleteKeyCode={['Backspace', 'Delete']}
            zoomOnDoubleClick={false}
          >
            <Background color="#dce3ed" gap={20} size={1} />
            <Controls />
          </ReactFlow>
          {nodes.length === 0 && (
            <div className="canvas-empty-state">
              <strong>Start with a node</strong>
              <span>Choose a node type from the palette to add it to the graph.</span>
            </div>
          )}
        </div>
      </section>

      <NodeConfigPanel
        catalogEntry={selectedCatalogEntry}
        edges={edges}
        nodes={nodes}
        node={selectedNode}
        onChange={updateNode}
        onDelete={deleteNode}
      />
    </div>
  )
}

function TriggerConfigFields({ config, values, onChange }) {
  return Object.entries(config).map(([name, field]) => (
    <label className="builder-field" key={name}>
      <span>{name}{field.required ? ' *' : ''}</span>
      <input
        onChange={(event) => onChange(name, event.target.value)}
        placeholder={field.description || ''}
        type={name === 'secret' ? 'password' : 'text'}
        value={values[name] || ''}
      />
      {field.description && <small>{field.description}</small>}
    </label>
  ))
}

function NumberField({ label, min, onChange, value }) {
  return (
    <label className="builder-field">
      <span>{label}</span>
      <input
        min={min}
        onChange={(event) => onChange(event.target.value)}
        step="1"
        type="number"
        value={value}
      />
    </label>
  )
}

export default WorkflowBuilderPage
