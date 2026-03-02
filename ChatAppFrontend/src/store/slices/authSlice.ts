import { createSlice } from "@reduxjs/toolkit"

export interface AuthUser {
  uid: string
  email: string | null
  displayName: string | null
  photoURL: string | null
  emailVerified: boolean
}

interface AuthState {
  user: AuthUser | null
  loading: boolean
  error: string | null
}

const initialState: AuthState = {
  user: null,
  loading: true,
  error: null,
}

const authSlice = createSlice({
  name: "auth",
  initialState,
  reducers: {
    setUser: (state, action: { payload: AuthUser | null }) => {
      state.user = action.payload
      state.loading = false
      state.error = null
    },
    setLoading: (state, action: { payload: boolean }) => {
      state.loading = action.payload
    },
    setError: (state, action: { payload: string | null }) => {
      state.error = action.payload
      state.loading = false
    },
    signOut: (state) => {
      state.user = null
      state.error = null
    },
  },
})

export const { setUser, setLoading, setError, signOut } = authSlice.actions
export default authSlice.reducer
