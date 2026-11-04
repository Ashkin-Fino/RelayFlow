export function formatDate(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

export function shortId(value, length = 18) {
  if (!value || value.length <= length) return value || '—'
  return `${value.slice(0, length)}…`
}
