import { signOut } from "firebase/auth"
import { useNavigate } from "react-router-dom"
import { motion } from "framer-motion"
import { auth } from "@/lib/firebase"
import { useAppSelector } from "@/hooks/useAppSelector"
import { Button } from "@/components/ui/button"

export function HomePage() {
  const navigate = useNavigate()
  const user = useAppSelector((state) => state.auth.user)

  const handleSignOut = async () => {
    try {
      await signOut(auth)
      navigate("/login", { replace: true })
    } catch (error) {
      console.error("Sign out error:", error)
    }
  }

  return (
    <div className="flex min-h-svh flex-col items-center justify-center bg-background p-4">
      <motion.div
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.3 }}
        className="flex w-full max-w-md flex-col gap-6 rounded-lg border border-border bg-card p-8 shadow-sm"
      >
        <div className="flex flex-col gap-2 text-center">
          <h1 className="text-2xl font-semibold tracking-tight text-foreground">
            Welcome, {user?.displayName ?? "User"}
          </h1>
          <p className="text-sm text-muted-foreground">{user?.email}</p>
          {user?.photoURL && (
            <img
              src={user.photoURL}
              alt="Profile"
              className="mx-auto h-16 w-16 rounded-full"
            />
          )}
        </div>

        <Button onClick={handleSignOut} variant="outline" className="w-full">
          Sign out
        </Button>
      </motion.div>
    </div>
  )
}
