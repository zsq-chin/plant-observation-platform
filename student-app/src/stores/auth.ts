import { defineStore } from "pinia"
import { fetchMe, login } from "@/api/plant"
import { clearAuth, readToken, readUser, saveAuth } from "@/utils/storage"
import type { UserInfo } from "@/types/models"

export const useAuthStore = defineStore("auth", {
  state: () => ({
    token: readToken() as string | null,
    userInfo: (readUser() as UserInfo | null) || null,
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    displayName: (state) => state.userInfo?.realName || state.userInfo?.username || "学生",
    className: (state) => state.userInfo?.className || "",
  },
  actions: {
    async login(username: string, password: string) {
      const result = await login(username, password)
      this.token = result.token
      this.userInfo = result.userInfo
      saveAuth(result.token, { ...result.userInfo } as unknown as Record<string, unknown>)
    },
    async refreshUser() {
      if (!this.token) return
      const user = await fetchMe()
      this.userInfo = user
      saveAuth(this.token, { ...user } as unknown as Record<string, unknown>)
    },
    logout() {
      this.token = null
      this.userInfo = null
      clearAuth()
    },
  },
})
