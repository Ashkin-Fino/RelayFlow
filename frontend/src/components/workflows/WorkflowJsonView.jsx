function WorkflowJsonView({ definition }) {
  return (
    <pre className="workflow-json">
      <code>{JSON.stringify(definition, null, 2)}</code>
    </pre>
  )
}

export default WorkflowJsonView
