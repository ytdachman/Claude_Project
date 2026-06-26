import { useEffect, useState, useCallback } from 'react'
import { api, Screenshot } from '../api/client'

/**
 * The main (and only) UI component for the News Front Page Archive.
 *
 * What it does:
 *   - Shows a grid of captured news site screenshots for a selected date
 *   - Lets the user search across all captures by keyword
 *   - Opens a lightbox when a card is clicked, showing the PDF or full image
 *   - Provides buttons to capture today, sync news sources, and delete records
 *
 * State management: all state lives in this component (no Redux or Context needed
 * for an app this small). React's useState hook stores each piece of UI state and
 * re-renders the component automatically whenever state changes.
 */
export default function ArchiveBrowser() {
  // today's date as "YYYY-MM-DD" — used as the default date and as the "max" on the date input
  const today = new Date().toISOString().slice(0, 10)

  // ── State ──────────────────────────────────────────────────────────────────

  /** Currently selected date in "YYYY-MM-DD" format. Drives which screenshots are shown. */
  const [date, setDate] = useState(today)

  /** The list of screenshots fetched from the backend for the current date or search query. */
  const [screenshots, setScreenshots] = useState<Screenshot[]>([])

  /** True while waiting for the backend to respond — shows "Loading…" */
  const [loading, setLoading] = useState(false)

  /** Non-null when an API call fails — shows an error message in red. */
  const [error, setError] = useState<string | null>(null)

  /** The screenshot currently open in the lightbox, or null if none is open. */
  const [selected, setSelected] = useState<Screenshot | null>(null)

  /**
   * Whether the lightbox is showing the PDF or the PNG image.
   * Defaults to 'pdf' because PDFs tend to be more readable.
   * The Image button appears only when a PDF exists (pdfPath != null).
   */
  const [viewMode, setViewMode] = useState<'pdf' | 'image'>('pdf')

  /** True while the "Capture Today" request is in flight — disables the button. */
  const [capturing, setCapturing] = useState(false)

  /** Status message shown in a blue info box after clicking "Capture Today". */
  const [captureMsg, setCaptureMsg] = useState<string | null>(null)

  /**
   * The text the user has typed into the search box (updated on every keystroke).
   * This is separate from searchQuery so the grid doesn't re-fetch on every keypress —
   * only when the user submits the form.
   */
  const [searchInput, setSearchInput] = useState('')

  /**
   * The submitted search term (set when the user presses Enter or clicks Search).
   * When this is non-empty, the grid shows search results instead of date-filtered captures.
   */
  const [searchQuery, setSearchQuery] = useState('')

  // ── Data fetching ──────────────────────────────────────────────────────────

  /**
   * Fetches screenshots from the backend and updates the grid.
   *
   * useCallback wraps this function so its reference stays stable across renders.
   * Without useCallback, a new function object would be created on every render,
   * which would trigger the useEffect below on every render — causing an infinite loop.
   *
   * @param d  The date to load captures for (used when q is absent)
   * @param q  Search keyword (takes priority over d when present)
   */
  const load = useCallback(async (d: string, q?: string) => {
    setLoading(true)
    setError(null)
    try {
      // If a search query exists, search across all dates; otherwise filter by date
      const data = await api.screenshots.list(q ? { q } : { date: d })
      setScreenshots(data)
    } catch (e) {
      setError(String(e))
    } finally {
      setLoading(false) // always clear the spinner, even if the request failed
    }
  }, [])

  /**
   * Automatically re-fetches whenever the date or search query changes.
   * The dependency array [date, searchQuery, load] tells React: "run this
   * effect whenever any of these values change."
   */
  useEffect(() => { load(date, searchQuery || undefined) }, [date, searchQuery, load])

  // ── Event handlers ─────────────────────────────────────────────────────────

  /** Called when the user submits the search form (Enter key or Search button). */
  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()               // prevent the default form submission (page reload)
    setSearchQuery(searchInput.trim()) // trim whitespace, then commit the query
  }

  /** Clears the search query and goes back to showing captures for the selected date. */
  const clearSearch = () => {
    setSearchInput('')
    setSearchQuery('')
  }

  /**
   * Deletes all screenshot database records after a confirmation dialog.
   * Does NOT delete files on disk — just clears the DB rows.
   * window.confirm() shows a native browser "Are you sure?" dialog.
   */
  const handleDeleteAll = async () => {
    if (!confirm('Delete all screenshot records?')) return
    await fetch('/api/screenshots/all', { method: 'DELETE' })
    setScreenshots([]) // clear the grid immediately without refetching
  }

  /**
   * Triggers an immediate capture of all enabled sources.
   * Capture happens server-side and takes ~1 minute per source.
   * After 70 seconds we auto-refresh the grid to show the new captures.
   */
  const handleCaptureNow = async () => {
    setCapturing(true)
    setCaptureMsg(null)
    try {
      await api.screenshots.captureNow()
      setCaptureMsg('Capture started — this takes a minute. Refresh in 60s.')
      // Wait 70s then reload the grid so the newly captured screenshots appear
      setTimeout(() => load(today), 70_000)
    } catch (e) {
      setCaptureMsg(`Error: ${e}`)
    } finally {
      setCapturing(false)
    }
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div style={{ fontFamily: 'system-ui, sans-serif', maxWidth: 1200, margin: '0 auto', padding: '1.5rem' }}>

      {/* ── Header row ── */}
      {/*
        The header has two sections side by side (space-between):
          Left:  page title
          Right: date navigation + action buttons
        flexWrap: wrap makes them stack vertically on narrow screens.
      */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1rem', flexWrap: 'wrap', gap: '0.75rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, margin: 0 }}>📰 News Front Page Archive</h1>
        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', flexWrap: 'wrap' }}>

          {/* Date navigator: ‹ [date picker] › */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
            {/* Previous day button — disabled when a search query is active */}
            <button
              onClick={() => setDate(d => {
                const prev = new Date(d)
                prev.setDate(prev.getDate() - 1)
                return prev.toISOString().slice(0, 10)
              })}
              disabled={!!searchQuery}
              style={{ padding: '0.4rem 0.6rem', borderRadius: 6, border: '1px solid #ccc', background: searchQuery ? '#f0f0f0' : '#fff', cursor: searchQuery ? 'default' : 'pointer', fontSize: '0.95rem' }}
            >‹</button>

            {/* Date picker input — also disabled during a search so date doesn't interfere */}
            <input
              type="date"
              value={date}
              max={today}   // can't navigate to the future
              disabled={!!searchQuery}
              onChange={e => setDate(e.target.value)}
              style={{ padding: '0.4rem 0.6rem', borderRadius: 6, border: '1px solid #ccc', fontSize: '0.95rem', opacity: searchQuery ? 0.5 : 1 }}
            />

            {/* Next day button — also disabled if already at today */}
            <button
              onClick={() => setDate(d => {
                const next = new Date(d)
                next.setDate(next.getDate() + 1)
                const nextStr = next.toISOString().slice(0, 10)
                return nextStr > today ? today : nextStr // clamp to today
              })}
              disabled={date >= today || !!searchQuery}
              style={{ padding: '0.4rem 0.6rem', borderRadius: 6, border: '1px solid #ccc', background: (date >= today || searchQuery) ? '#f0f0f0' : '#fff', cursor: (date >= today || searchQuery) ? 'default' : 'pointer', fontSize: '0.95rem' }}
            >›</button>
          </div>

          {/* Capture Today button — triggers immediate screenshot of all sources */}
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

          {/* Delete All button — removes all screenshot DB records */}
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

      {/* ── Search bar ── */}
      {/*
        onSubmit fires when the user presses Enter or clicks the Search button.
        The Clear button only appears when a search is active.
      */}
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
        {/* Only show Clear when there's an active search query */}
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

      {/* Capture status message (shown after clicking "Capture Today") */}
      {captureMsg && (
        <div style={{ background: '#ebf8ff', border: '1px solid #90cdf4', borderRadius: 6, padding: '0.75rem 1rem', marginBottom: '1rem', color: '#2c5282' }}>
          {captureMsg}
        </div>
      )}

      {/* Context label — tells the user what they're looking at */}
      <p style={{ color: '#666', marginBottom: '1rem' }}>
        {searchQuery
          // Search mode: show how many results were found
          ? <>Search results for <strong>"{searchQuery}"</strong> — {screenshots.length} match{screenshots.length !== 1 ? 'es' : ''}, newest first</>
          // Date mode: show which date is selected
          : <>Showing front pages from <strong>{date}</strong></>
        }
      </p>

      {/* Loading / error / empty states */}
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

      {/* ── Screenshot grid ── */}
      {/*
        CSS Grid with auto-fill: automatically creates as many columns as fit,
        each at least 280px wide. The grid grows/shrinks with the window.
      */}
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
            // Hover shadow effect — direct DOM manipulation is fine for simple transitions
            onMouseEnter={e => (e.currentTarget.style.boxShadow = '0 4px 12px rgba(0,0,0,0.15)')}
            onMouseLeave={e => (e.currentTarget.style.boxShadow = '0 1px 3px rgba(0,0,0,0.08)')}
          >
            {/* Thumbnail — 180px tall, crops from the top so the site header is visible */}
            <div style={{ height: 180, background: '#f7fafc', overflow: 'hidden', display: 'flex', alignItems: 'flex-start' }}>
              <img
                src={api.screenshots.imageUrl(s.id)}
                alt={s.sourceName}
                style={{ width: '100%', objectFit: 'cover', objectPosition: 'top' }}
                loading="lazy" // browser only fetches the image when it scrolls into view
              />
            </div>

            {/* Card footer — source name, optional date (in search mode), link */}
            <div style={{ padding: '0.75rem 1rem' }}>
              <p style={{ fontWeight: 600, margin: 0 }}>{s.sourceName}</p>
              {/* Show the capture date only in search mode (where results span multiple dates) */}
              {searchQuery && (
                <p style={{ fontSize: '0.8rem', color: '#4a5568', margin: '0.2rem 0 0' }}>{s.capturedDate}</p>
              )}
              {/* e.stopPropagation() prevents the card click (which opens the lightbox)
                  from firing when the user clicks the URL link */}
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

      {/* ── Lightbox (modal overlay) ── */}
      {/*
        Only rendered when a screenshot is selected (selected !== null).
        Clicking the dark backdrop (the outer div) closes the lightbox.
        Clicking inside the white content panel (e.stopPropagation) does not close it.
      */}
      {selected && (
        <div
          onClick={() => setSelected(null)}
          style={{
            position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.85)',
            zIndex: 1000, overflowY: 'scroll', // allows scrolling the full image on narrow screens
            WebkitOverflowScrolling: 'touch',  // smooth inertia scrolling on iOS
          }}
        >
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '2rem 1rem', minHeight: '100%' }}>
            {/* White content panel */}
            <div
              onClick={e => e.stopPropagation()} // don't close when clicking inside
              style={{ background: '#fff', borderRadius: 10, overflow: 'hidden', maxWidth: 1100, width: '100%', marginBottom: '2rem' }}
            >
              {/* Lightbox toolbar — source name, date, view mode toggle, close button */}
              <div style={{ padding: '0.75rem 1rem', borderBottom: '1px solid #e2e8f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.5rem' }}>
                <div>
                  <strong>{selected.sourceName}</strong>
                  <span style={{ color: '#718096', marginLeft: '0.75rem', fontSize: '0.9rem' }}>{selected.capturedDate}</span>
                </div>
                <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                  {/* PDF/Image toggle — only shown when a PDF exists */}
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
                  {/* Close button */}
                  <button
                    onClick={() => setSelected(null)}
                    style={{ background: 'none', border: 'none', fontSize: '1.25rem', cursor: 'pointer', color: '#666' }}
                  >
                    ✕
                  </button>
                </div>
              </div>

              {/* Content area: PDF iframe or full image */}
              {selected.pdfPath && viewMode === 'pdf' ? (
                /*
                  <iframe> lets the browser render the PDF using its built-in PDF viewer.
                  This gives the user zoom, text selection, and print controls for free.
                  85vh = 85% of the viewport height, so the toolbar stays visible.
                */
                <iframe
                  src={api.screenshots.pdfUrl(selected.id)}
                  style={{ width: '100%', height: '85vh', border: 'none', display: 'block' }}
                  title={selected.sourceName}
                />
              ) : (
                /*
                  <img> for the full-page PNG screenshot.
                  width: 100% makes it fill the panel; the user can scroll vertically
                  to see the full page since the outer div has overflowY: scroll.
                */
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
