import { useAuthStore } from "../store/authStore";
import { ENV } from "../config/env";
const API_BASE = `${ENV.API_BASE}`;

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
