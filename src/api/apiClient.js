import { useAuthStore } from "../store/authStore";

const API_BASE = "http://localhost:9999";

export async function apiFetch(
  path,
  { method = "GET", body, headers = {} } = {}
) {
  const token = useAuthStore.getState().accessToken;

  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  if (res.status === 401) {
    useAuthStore.getState().clear();
  }

  return res;
}
