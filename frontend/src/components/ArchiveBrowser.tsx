import { useEffect, useState, useCallback } from 'react'
import { api, Screenshot } from '../api/client'

export default function ArchiveBrowser() {
  const today = new Date().toISOString().slice(0, 10)

  const [date, setDate] = useState(today)
  const [screenshots, setScreenshots] = useState<Screenshot[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<Screenshot | null>(null)
  const [viewMode, setViewMode] = useState<'pdf' | 'image'>('pdf')
  const [capturing, setCapturing] = useState(false)
  const [captureMsg, setCaptureMsg] = useState<string | null>(null)
  const [searchInput, setSearchInput] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [syncing, setSyncing] = useState(false)
  const [syncMsg, setSyncMsg] = useState<string | null>(null)

  const load = useCallback(async (d: string, q?: string) => {
    setLoading(true)
    setError(null)
    try {
      const data = await api.screenshots.list(q ? { q } : { date: d })
      setScreenshots(data)
    } catch (e) {
      setError(String(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load(date, searchQuery || undefined) }, [date, searchQuery, load])

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    setSearchQuery(searchInput.trim())
  }

  const clearSearch = () => {
    setSearchInput('')
    setSearchQuery('')
  }

  const handleSyncSources = async () => {
    setSyncing(true)
    setSyncMsg(null)
    try {
      const msg = await api.sources.syncFromWikipedia()
      setSyncMsg(msg)
    } catch (e) {
      setSyncMsg(`Error: ${e}`)
    } finally {
      setSyncing(false)
    }
  }

  const handleDeleteAll = async () => {
    if (!confirm('Delete all screenshot records?')) return
    await fetch('/api/screenshots/all', { method: 'DELETE' })
    setScreenshots([])
  }

  const handleCaptureNow = async () => {
    setCapturing(true)
    setCaptureMsg(null)
    try {
      await api.screenshots.captureNow()
      setCaptureMsg('Capture started — this takes a minute. Refresh in 60s.')
      setTimeout(() => load(today), 70_000)
    } catch (e) {
      setCaptureMsg(`Error: ${e}`)
    } finally {
      setCapturing(false)
    }
  }

  return (
    <div style={{ fontFamily: 'system-ui, sans-serif', maxWidth: 1200, margin: '0 auto', padding: '1.5rem' }}>

      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.75rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, margin: 0 }}>📰 News Front Page Archive</h1>
        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
            <button
              onClick={() => setDate(d => {
                const prev = new Date(d)
                prev.setDate(prev.getDate() - 1)
                return prev.toISOString().slice(0, 10)
              })}
              disabled={!!searchQuery}
              style={{ padding: '0.4rem 0.6rem', borderRadius: 6, border: '1px solid #ccc', background: searchQuery ? '#f0f0f0' : '#fff', cursor: searchQuery ? 'default' : 'pointer', fontSize: '0.95rem' }}
            >‹</button>
            <input
              type="date"
              value={date}
              max={today}
              disabled={!!searchQuery}
              onChange={e => setDate(e.target.value)}
              style={{ padding: '0.4rem 0.6rem', borderRadius: 6, border: '1px solid #ccc', fontSize: '0.95rem', opacity: searchQuery ? 0.5 : 1 }}
            />
            <button
              onClick={() => setDate(d => {
                const next = new Date(d)
                next.setDate(next.getDate() + 1)
                const nextStr = next.toISOString().slice(0, 10)
                return nextStr > today ? today : nextStr
              })}
              disabled={date >= today || !!searchQuery}
              style={{ padding: '0.4rem 0.6rem', borderRadius: 6, border: '1px solid #ccc', background: (date >= today || searchQuery) ? '#f0f0f0' : '#fff', cursor: (date >= today || searchQuery) ? 'default' : 'pointer', fontSize: '0.95rem' }}
            >›</button>
          </div>
          <button
            onClick={handleCaptureNow}
            disabled={capturing}
            style={{
              padding: '0.4rem 0.9rem', borderRadius: 6, border: 'none',
              background: capturing ? '#aaa' : '#2b6cb0', color: '#fff',
              cursor: capturing ? 'default' : 'pointer', fontSize: '0.95rem'
            }}
          >
            {capturing ? 'Capturing…' : 'Capture Today'}
          </button>
          <button
            onClick={handleSyncSources}
            disabled={syncing}
            style={{
              padding: '0.4rem 0.9rem', borderRadius: 6, border: 'none',
              background: syncing ? '#aaa' : '#6b46c1', color: '#fff',
              cursor: syncing ? 'default' : 'pointer', fontSize: '0.95rem'
            }}
          >
            {syncing ? 'Syncing…' : 'Sync Sources'}
          </button>
          <button
            onClick={handleDeleteAll}
            style={{
              padding: '0.4rem 0.9rem', borderRadius: 6, border: 'none',
              background: '#e53e3e', color: '#fff',
              cursor: 'pointer', fontSize: '0.95rem'
            }}
          >
            Delete All
          </button>
        </div>
      </div>

      {/* Search bar */}
      <form onSubmit={handleSearch} style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.25rem' }}>
        <input
          type="text"
          value={searchInput}
          onChange={e => setSearchInput(e.target.value)}
          placeholder="Search across all captured pages…"
          style={{
            flex: 1, padding: '0.45rem 0.75rem', borderRadius: 6,
            border: '1px solid #cbd5e0', fontSize: '0.95rem', outline: 'none'
          }}
        />
        <button
          type="submit"
          style={{
            padding: '0.45rem 1rem', borderRadius: 6, border: 'none',
            background: '#2b6cb0', color: '#fff', cursor: 'pointer', fontSize: '0.95rem'
          }}
        >
          Search
        </button>
        {searchQuery && (
          <button
            type="button"
            onClick={clearSearch}
            style={{
              padding: '0.45rem 0.75rem', borderRadius: 6, border: '1px solid #ccc',
              background: '#fff', cursor: 'pointer', fontSize: '0.95rem', color: '#555'
            }}
          >
            ✕ Clear
          </button>
        )}
      </form>

      {captureMsg && (
        <div style={{ background: '#ebf8ff', border: '1px solid #90cdf4', borderRadius: 6, padding: '0.75rem 1rem', marginBottom: '1rem', color: '#2c5282' }}>
          {captureMsg}
        </div>
      )}

      {syncMsg && (
        <div style={{ background: '#faf5ff', border: '1px solid #d6bcfa', borderRadius: 6, padding: '0.75rem 1rem', marginBottom: '1rem', color: '#553c9a', whiteSpace: 'pre-line' }}>
          {syncMsg}
        </div>
      )}

      {/* Context label */}
      <p style={{ color: '#666', marginBottom: '1rem' }}>
        {searchQuery
          ? <>Search results for <strong>"{searchQuery}"</strong> — {screenshots.length} match{screenshots.length !== 1 ? 'es' : ''}, newest first</>
          : <>Showing front pages from <strong>{date}</strong></>
        }
      </p>

      {/* States */}
      {loading && <p style={{ color: '#888' }}>Loading…</p>}
      {error   && <p style={{ color: '#e53e3e' }}>{error}</p>}
      {!loading && !error && screenshots.length === 0 && (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#888' }}>
          <p style={{ fontSize: '1.1rem' }}>
            {searchQuery ? `No results found for "${searchQuery}".` : 'No screenshots for this date.'}
          </p>
          <p style={{ fontSize: '0.9rem', marginTop: '0.5rem' }}>
            {searchQuery
              ? 'Try a different keyword, or make sure screenshots have been captured.'
              : 'Try clicking "Capture Today" or pick a different date.'}
          </p>
        </div>
      )}

      {/* Grid */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
        gap: '1.25rem',
      }}>
        {screenshots.map(s => (
          <div
            key={s.id}
            onClick={() => { setSelected(s); setViewMode('pdf') }}
            style={{
              border: '1px solid #e2e8f0', borderRadius: 10,
              overflow: 'hidden', cursor: 'pointer', background: '#fff',
              boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
              transition: 'box-shadow 0.15s',
            }}
            onMouseEnter={e => (e.currentTarget.style.boxShadow = '0 4px 12px rgba(0,0,0,0.15)')}
            onMouseLeave={e => (e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.08)')}
          >
            <div style={{ height: 180, background: '#f7fafc', overflow: 'hidden', display: 'flex', alignItems: 'flex-start' }}>
              <img
                src={api.screenshots.imageUrl(s.id)}
                alt={s.sourceName}
                style={{ width: '100%', objectFit: 'cover', objectPosition: 'top' }}
                loading="lazy"
              />
            </div>
            <div style={{ padding: '0.75rem 1rem' }}>
              <p style={{ fontWeight: 600, margin: 0 }}>{s.sourceName}</p>
              {searchQuery && (
                <p style={{ fontSize: '0.8rem', color: '#4a5568', margin: '0.2rem 0 0' }}>{s.capturedDate}</p>
              )}
              <a
                href={s.sourceUrl}
                target="_blank"
                rel="noopener noreferrer"
                onClick={e => e.stopPropagation()}
                style={{ fontSize: '0.8rem', color: '#718096', textDecoration: 'none' }}
              >
                {s.sourceUrl}
              </a>
            </div>
          </div>
        ))}
      </div>

      {/* Lightbox */}
      {selected && (
        <div
          onClick={() => setSelected(null)}
          style={{
            position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.85)',
            zIndex: 1000, overflowY: 'scroll',
            WebkitOverflowScrolling: 'touch',
          }}
        >
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '2rem 1rem', minHeight: '100%' }}>
          <div
            onClick={e => e.stopPropagation()}
            style={{ background: '#fff', borderRadius: 10, overflow: 'hidden', maxWidth: 1100, width: '100%', marginBottom: '2rem' }}
          >
            <div style={{ padding: '0.75rem 1rem', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
              <div>
                <strong>{selected.sourceName}</strong>
                <span style={{ color: '#718096', marginLeft: '0.75rem', fontSize: '0.9rem' }}>{selected.capturedDate}</span>
              </div>
              <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                {selected.pdfPath && (
                  <>
                    <button
                      onClick={() => setViewMode('pdf')}
                      style={{ padding: '0.3rem 0.75rem', borderRadius: 6, border: '1px solid #cbd5e0', fontSize: '0.875rem', cursor: 'pointer', background: viewMode === 'pdf' ? '#2b6cb0' : '#fff', color: viewMode === 'pdf' ? '#fff' : '#333' }}
                    >
                      PDF
                    </button>
                    <button
                      onClick={() => setViewMode('image')}
                      style={{ padding: '0.3rem 0.75rem', borderRadius: 6, border: '1px solid #cbd5e0', fontSize: '0.875rem', cursor: 'pointer', background: viewMode === 'image' ? '#2b6cb0' : '#fff', color: viewMode === 'image' ? '#fff' : '#333' }}
                    >
                      Image
                    </button>
                  </>
                )}
                <button
                  onClick={() => setSelected(null)}
                  style={{ background: 'none', border: 'none', fontSize: '1.25rem', cursor: 'pointer', color: '#666' }}
                >
                  ✕
                </button>
              </div>
            </div>
            {selected.pdfPath && viewMode === 'pdf' ? (
              <iframe
                src={api.screenshots.pdfUrl(selected.id)}
                style={{ width: '100%', height: '85vh', border: 'none', display: 'block' }}
                title={selected.sourceName}
              />
            ) : (
              <img
                src={api.screenshots.imageUrl(selected.id)}
                alt={selected.sourceName}
                style={{ width: '100%', display: 'block' }}
              />
            )}
          </div>
        </div>
        </div>
      )}
    </div>
  )
}
