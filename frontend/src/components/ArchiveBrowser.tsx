import { useEffect, useState, useCallback } from 'react'
import { api, Screenshot } from '../api/client'

export default function ArchiveBrowser() {
  const today = new Date().toISOString().slice(0, 10)

  const [date, setDate] = useState(today)
  const [screenshots, setScreenshots] = useState<Screenshot[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<Screenshot | null>(null)
  const [capturing, setCapturing] = useState(false)
  const [captureMsg, setCaptureMsg] = useState<string | null>(null)

  const load = useCallback(async (d: string) => {
    setLoading(true)
    setError(null)
    try {
      const data = await api.screenshots.list({ date: d })
      setScreenshots(data)
    } catch (e) {
      setError(String(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { load(date) }, [date, load])

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
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem', flexWrap: 'wrap', gap: '0.75rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, margin: 0 }}>📰 News Front Page Archive</h1>
        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>
          <input
            type="date"
            value={date}
            max={today}
            onChange={e => setDate(e.target.value)}
            style={{ padding: '0.4rem 0.6rem', borderRadius: 6, border: '1px solid #ccc', fontSize: '0.95rem' }}
          />
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
        </div>
      </div>

      {captureMsg && (
        <div style={{ background: '#ebf8ff', border: '1px solid #90cdf4', borderRadius: 6, padding: '0.75rem 1rem', marginBottom: '1rem', color: '#2c5282' }}>
          {captureMsg}
        </div>
      )}

      {/* Date label */}
      <p style={{ color: '#666', marginBottom: '1rem' }}>
        Showing front pages from <strong>{date}</strong>
      </p>

      {/* States */}
      {loading && <p style={{ color: '#888' }}>Loading…</p>}
      {error   && <p style={{ color: '#e53e3e' }}>{error}</p>}
      {!loading && !error && screenshots.length === 0 && (
        <div style={{ textAlign: 'center', padding: '3rem', color: '#888' }}>
          <p style={{ fontSize: '1.1rem' }}>No screenshots for this date.</p>
          <p style={{ fontSize: '0.9rem', marginTop: '0.5rem' }}>
            Try clicking "Capture Today" or pick a different date.
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
            onClick={() => setSelected(s)}
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
            display: 'flex', flexDirection: 'column', alignItems: 'center',
            justifyContent: 'flex-start', zIndex: 1000, overflowY: 'auto',
            padding: '2rem 1rem',
          }}
        >
          <div
            onClick={e => e.stopPropagation()}
            style={{ background: '#fff', borderRadius: 10, overflow: 'hidden', maxWidth: 1100, width: '100%' }}
          >
            <div style={{ padding: '0.75rem 1rem', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <strong>{selected.sourceName}</strong>
                <span style={{ color: '#718096', marginLeft: '0.75rem', fontSize: '0.9rem' }}>{selected.capturedDate}</span>
              </div>
              <button
                onClick={() => setSelected(null)}
                style={{ background: 'none', border: 'none', fontSize: '1.25rem', cursor: 'pointer', color: '#666' }}
              >
                ✕
              </button>
            </div>
            <img
              src={api.screenshots.imageUrl(selected.id)}
              alt={selected.sourceName}
              style={{ width: '100%', display: 'block' }}
            />
          </div>
        </div>
      )}
    </div>
  )
}
