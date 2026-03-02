import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom"
import { AnimatePresence } from "framer-motion"
import { useAuthListener } from "@/hooks/useAuthListener"
import { ProtectedRoute } from "@/components/ProtectedRoute"
import { LoginPage } from "@/pages/LoginPage"
import { HomePage } from "@/pages/HomePage"

function App() {
  useAuthListener()

  return (
    <BrowserRouter>
      <AnimatePresence mode="wait">
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <HomePage />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AnimatePresence>
    </BrowserRouter>
  )
}

export default App
