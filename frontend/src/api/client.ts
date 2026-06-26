// ── Type definitions ──────────────────────────────────────────────────────────
//
// These interfaces describe the shape of the JSON objects that the backend
// sends back. TypeScript uses them to catch typos and wrong field names at
// compile time rather than at runtime.

/** An item from the generic /api/items scaffold — not used by the archive UI. */
export interface Item {
  id: number
  name: string
  description: string | null
  createdAt: string
}

/**
 * A captured snapshot of one news site on one date.
 * Mirrors the Screenshot JPA entity in the backend.
 *
 * filePath and pdfPath are full file-system paths on the server —
 * they're not used directly in the browser; instead we use imageUrl()/pdfUrl()
 * which hit the backend's streaming endpoints.
 */
export interface Screenshot {
  id: number
  sourceName: string    // e.g. "BBC News"
  sourceUrl:  string    // e.g. "https://www.bbc.com/news"
  capturedDate: string  // "YYYY-MM-DD" — what date the capture was taken
  filePath: string      // server-side path to the .png file
  pdfPath: string | null // server-side path to the .pdf file (null if PDF failed)
  createdAt: string     // when the DB record was created (ISO timestamp)
}

/** A news source entry from the backend — name + URL pair. */
export interface NewsSource {
  name: string
  url: string
}

// ── HTTP helper ───────────────────────────────────────────────────────────────

/**
 * A thin wrapper around the browser's built-in fetch() API.
 *
 * Why have a wrapper at all? Two reasons:
 *   1. fetch() doesn't throw on HTTP error status codes (4xx, 5xx) — it only
 *      throws on network failures. This wrapper converts bad HTTP statuses into
 *      thrown errors so callers don't have to check res.ok every time.
 *   2. It automatically sets Content-Type: application/json on every request
 *      so we don't have to repeat that header everywhere.
 *
 * The <T> type parameter lets TypeScript know what shape of data to expect back.
 * For example: request<Screenshot[]>('/api/screenshots') returns Screenshot[].
 *
 * The Vite dev server (vite.config.ts) proxies /api/* to http://localhost:8080,
 * so these relative URLs work in development without hardcoding the backend port.
 */
async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options, // spread caller's options after, so they can override headers if needed
  })

  // fetch() considers any completed HTTP response a "success", even 404 or 500.
  // We throw here so the caller's try/catch or .catch() handles errors uniformly.
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`)

  // 204 No Content has no body — calling .json() would throw, so return undefined.
  if (res.status === 204) return undefined as T

  return res.json() as Promise<T>
}

// ── API client ────────────────────────────────────────────────────────────────

/**
 * Centralised API client object. All backend calls go through here so there's
 * one place to update if URLs or request formats change.
 *
 * Usage in components:
 *   import { api } from '../api/client'
 *   const screenshots = await api.screenshots.list({ date: '2025-06-01' })
 */
export const api = {

  // Generic scaffold items — not used by the main archive UI
  items: {
    getAll:  ()                                     => request<Item[]>('/api/items'),
    getById: (id: number)                           => request<Item>(`/api/items/${id}`),
    create:  (body: Omit<Item, 'id' | 'createdAt'>) =>
      request<Item>('/api/items', { method: 'POST', body: JSON.stringify(body) }),
    update:  (id: number, body: Omit<Item, 'id' | 'createdAt'>) =>
      request<Item>(`/api/items/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    delete:  (id: number)                           =>
      request<void>(`/api/items/${id}`, { method: 'DELETE' }),
  },

  screenshots: {
    /**
     * Fetches a list of screenshots from the backend.
     * Exactly one of these query parameters should be provided:
     *   q      → full-text search across all captured page text
     *   date   → show all captures for a specific date ("YYYY-MM-DD")
     *   source → show all captures for a specific source by name
     * If none are provided, all screenshots are returned.
     */
    list: (params?: { date?: string; source?: string; q?: string }) => {
      const qs = new URLSearchParams()
      // Only add params that were actually provided (not undefined/empty)
      if (params?.q)      qs.set('q',      params.q)
      if (params?.date)   qs.set('date',   params.date)
      if (params?.source) qs.set('source', params.source)
      const query = qs.toString() ? `?${qs}` : '' // only add "?" if there are params
      return request<Screenshot[]>(`/api/screenshots${query}`)
    },

    /** Not currently used in the UI — kept for potential future use. */
    sources: () => request<NewsSource[]>('/api/screenshots/sources'),

    /**
     * Returns the URL for a screenshot's PNG image.
     * Used directly as the `src` of an <img> tag — the browser fetches it.
     * The backend streams the file from disk at this endpoint.
     */
    imageUrl: (id: number) => `/api/screenshots/${id}/image`,

    /**
     * Returns the URL for a screenshot's PDF.
     * Used directly as the `src` of an <iframe> — the browser renders the PDF.
     */
    pdfUrl: (id: number) => `/api/screenshots/${id}/pdf`,

    /**
     * Triggers a fresh capture of all enabled news sources right now.
     * Returns plain text (e.g. "OK: BBC News\nFAIL: AP News — timeout").
     *
     * Note: We use raw fetch() here (not the request() helper) because we want
     * the plain-text response, not JSON.
     */
    captureNow: () =>
      fetch('/api/screenshots/capture-now', { method: 'POST' }).then(r => r.text()),

    /**
     * Deletes screenshot folders on disk that contain no PNG files.
     * Called automatically at 5:58 AM; this endpoint allows manual triggering.
     * Returns plain text listing which folders were deleted.
     */
    cleanupFolders: () =>
      fetch('/api/screenshots/empty-folders', { method: 'DELETE' }).then(r => r.text()),
  },

}
