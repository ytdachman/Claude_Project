import { useEffect, useState } from 'react'
import { api, Item } from '../api/client'

export default function ItemList() {
  const [items, setItems] = useState<Item[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [newName, setNewName] = useState('')
  const [newDesc, setNewDesc] = useState('')

  const load = async () => {
    try {
      setLoading(true)
      setItems(await api.getAll())
      setError(null)
    } catch (e) {
      setError(String(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newName.trim()) return
    await api.create({ name: newName.trim(), description: newDesc.trim() || null })
    setNewName('')
    setNewDesc('')
    load()
  }

  const handleDelete = async (id: number) => {
    await api.delete(id)
    load()
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      <h1 style={{ fontSize: '1.5rem', fontWeight: 700 }}>Items</h1>

      <form onSubmit={handleCreate} style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
        <input
          value={newName}
          onChange={e => setNewName(e.target.value)}
          placeholder="Name"
          required
          style={inputStyle}
        />
        <input
          value={newDesc}
          onChange={e => setNewDesc(e.target.value)}
          placeholder="Description (optional)"
          style={inputStyle}
        />
        <button type="submit" style={btnStyle}>Add</button>
      </form>

      {loading && <p>Loading…</p>}
      {error && <p style={{ color: 'red' }}>{error}</p>}

      {!loading && items.length === 0 && <p style={{ color: '#888' }}>No items yet.</p>}

      <ul style={{ listStyle: 'none', display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
        {items.map(item => (
          <li key={item.id} style={cardStyle}>
            <div style={{ flex: 1 }}>
              <strong>{item.name}</strong>
              {item.description && <p style={{ color: '#555', marginTop: '0.25rem' }}>{item.description}</p>}
              <p style={{ fontSize: '0.75rem', color: '#aaa', marginTop: '0.25rem' }}>
                {new Date(item.createdAt).toLocaleString()}
              </p>
            </div>
            <button onClick={() => handleDelete(item.id)} style={{ ...btnStyle, background: '#e53e3e' }}>
              Delete
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

const inputStyle: React.CSSProperties = {
  padding: '0.5rem 0.75rem',
  border: '1px solid #ccc',
  borderRadius: '6px',
  fontSize: '1rem',
  flex: 1,
  minWidth: '140px',
}

const btnStyle: React.CSSProperties = {
  padding: '0.5rem 1rem',
  background: '#3182ce',
  color: '#fff',
  border: 'none',
  borderRadius: '6px',
  cursor: 'pointer',
  fontSize: '1rem',
}

const cardStyle: React.CSSProperties = {
  background: '#fff',
  border: '1px solid #e2e8f0',
  borderRadius: '8px',
  padding: '1rem',
  display: 'flex',
  alignItems: 'flex-start',
  gap: '1rem',
}
