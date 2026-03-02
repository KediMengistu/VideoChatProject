import { useEffect } from "react"
import { onAuthStateChanged } from "firebase/auth"
import { useDispatch } from "react-redux"
import { auth } from "@/lib/firebase"
import { setUser, type AuthUser } from "@/store/slices/authSlice"

function toAuthUser(user: import("firebase/auth").User): AuthUser {
  return {
    uid: user.uid,
    email: user.email ?? null,
    displayName: user.displayName ?? null,
    photoURL: user.photoURL ?? null,
    emailVerified: user.emailVerified,
  }
}

export function useAuthListener() {
  const dispatch = useDispatch()

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (user) => {
      dispatch(setUser(user ? toAuthUser(user) : null))
    })

    return () => unsubscribe()
  }, [dispatch])
}
