"use client"

import { Moon, Sun } from "lucide-react"
import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"

const STORAGE_KEY = "theme"

export function ThemeToggle() {
  const [isDark, setIsDark] = useState(false)
  const [mounted, setMounted] = useState(false)

  useEffect(() => {
    setMounted(true)
    const stored = localStorage.getItem(STORAGE_KEY) as "light" | "dark" | null
    const prefersDark = window.matchMedia("(prefers-color-scheme: dark)").matches
    const dark = stored === "dark" || (!stored && prefersDark)
    setIsDark(dark)
    document.documentElement.classList.toggle("dark", dark)
  }, [])

  useEffect(() => {
    if (!mounted) return
    document.documentElement.classList.toggle("dark", isDark)
    localStorage.setItem(STORAGE_KEY, isDark ? "dark" : "light")
  }, [isDark, mounted])

  return (
    <Button
      variant="ghost"
      size="icon"
      className="text-foreground hover:text-foreground"
      onClick={() => {
        const next = !isDark
        document.documentElement.classList.toggle("dark", next)
        localStorage.setItem(STORAGE_KEY, next ? "dark" : "light")
        setIsDark(next)
      }}
      aria-label={isDark ? "Switch to light mode" : "Switch to dark mode"}
    >
      {isDark ? (
        <Sun className="h-4 w-4" />
      ) : (
        <Moon className="h-4 w-4" />
      )}
    </Button>
  )
}
