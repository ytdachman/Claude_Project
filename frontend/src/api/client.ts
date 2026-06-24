export interface Item {
  id: number
  name: string
  description: string | null
  createdAt: string
}

const BASE = '/api/items'

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}: ${res.statusText}`)
  }
  // 204 No Content has no body
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

export const api = {
  getAll: () => request<Item[]>(BASE),

  getById: (id: number) => request<Item>(`${BASE}/${id}`),

  create: (body: Omit<Item, 'id' | 'createdAt'>) =>
    request<Item>(BASE, { method: 'POST', body: JSON.stringify(body) }),

  update: (id: number, body: Omit<Item, 'id' | 'createdAt'>) =>
    request<Item>(`${BASE}/${id}`, { method: 'PUT', body: JSON.stringify(body) }),

  delete: (id: number) =>
    request<void>(`${BASE}/${id}`, { method: 'DELETE' }),
}
