import { create } from "zustand";

export const useAuthStore = create(set => ({
  visitorToken: null,
  accessToken: null,

  setVisitorToken: token =>
    set({ visitorToken: token }),

  setAccessToken: token =>
    set({ accessToken: token }),

  clear: () =>
    set({ visitorToken: null, accessToken: null }),
}));
