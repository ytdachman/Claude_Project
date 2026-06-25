// ── Items ────────────────────────────────────────────────────────────────────

export interface Item {
  id: number
  name: string
  description: string | null
  createdAt: string
}

// ── Screenshots ───────────────────────────────────────────────────────────────

export interface Screenshot {
  id: number
  sourceName: string
  sourceUrl: string
  capturedDate: string   // "YYYY-MM-DD"
  filePath: string
  pdfPath: string | null
  createdAt: string
}

export interface NewsSource {
  name: string
  url: string
}

// ── HTTP helper ───────────────────────────────────────────────────────────────

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`)
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

// ── API ───────────────────────────────────────────────────────────────────────

export const api = {
  // Items
  items: {
    getAll: () => request<Item[]>('/api/items'),
    getById: (id: number) => request<Item>(`/api/items/${id}`),
    create: (body: Omit<Item, 'id' | 'createdAt'>) =>
      request<Item>('/api/items', { method: 'POST', body: JSON.stringify(body) }),
    update: (id: number, body: Omit<Item, 'id' | 'createdAt'>) =>
      request<Item>(`/api/items/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
    delete: (id: number) =>
      request<void>(`/api/items/${id}`, { method: 'DELETE' }),
  },

  // Screenshots
  screenshots: {
    list: (params?: { date?: string; source?: string; q?: string }) => {
      const qs = new URLSearchParams()
      if (params?.q)      qs.set('q', params.q)
      if (params?.date)   qs.set('date', params.date)
      if (params?.source) qs.set('source', params.source)
      const query = qs.toString() ? `?${qs}` : ''
      return request<Screenshot[]>(`/api/screenshots${query}`)
    },
    sources: () => request<NewsSource[]>('/api/screenshots/sources'),
    imageUrl: (id: number) => `/api/screenshots/${id}/image`,
    pdfUrl:   (id: number) => `/api/screenshots/${id}/pdf`,
    captureNow: () => fetch('/api/screenshots/capture-now', { method: 'POST' }).then(r => r.text()),
  },

  sources: {
    syncFromWikipedia: () => fetch('/api/sources/sync', { method: 'POST' }).then(r => r.text()),
  },
}
