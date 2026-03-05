"use client"

import { ChevronsUpDown, LogOut, Trash2 } from "lucide-react"
import { signOut, deleteUser } from "firebase/auth"
import { useNavigate } from "react-router-dom"
import { auth } from "@/lib/firebase"
import {
  Avatar,
  AvatarFallback,
} from "@/components/ui/avatar"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar"

export function NavUser({
  user,
}: {
  user: {
    name: string
    email: string
    avatar: string
  }
}) {
  const { isMobile } = useSidebar()
  const navigate = useNavigate()
  const fallbackLetter = (user.email?.[0] ?? user.name?.[0] ?? "U").toUpperCase()

  const handleLogOut = async () => {
    try {
      await signOut(auth)
      navigate("/login", { replace: true })
    } catch (error) {
      console.error("Sign out error:", error)
    }
  }

  const handleDeleteAccount = async () => {
    const currentUser = auth.currentUser
    if (!currentUser) return
    try {
      await deleteUser(currentUser)
      navigate("/login", { replace: true })
    } catch (error) {
      console.error("Delete account error:", error)
    }
  }

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton
              size="lg"
              className="cursor-pointer data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground data-[state=open]:ring-0 data-[state=open]:outline-none focus-visible:ring-0 focus-visible:outline-none"
            >
              <Avatar className="h-8 w-8 rounded-lg">
                <AvatarFallback className="rounded-lg">{fallbackLetter}</AvatarFallback>
              </Avatar>
              <div className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-medium">{user.name}</span>
                <span className="truncate text-xs">{user.email}</span>
              </div>
              <ChevronsUpDown className="ml-auto size-4" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="w-(--radix-dropdown-menu-trigger-width) min-w-56 rounded-lg border-sidebar-border outline-none ring-0 cursor-pointer"
            side={isMobile ? "bottom" : "right"}
            align="end"
            sideOffset={4}
          >
            <DropdownMenuLabel className="p-0 font-normal">
              <div className="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
                <Avatar className="h-8 w-8 rounded-lg">
                  <AvatarFallback className="rounded-lg">{fallbackLetter}</AvatarFallback>
                </Avatar>
                <div className="grid flex-1 text-left text-sm leading-tight">
                  <span className="truncate font-medium">{user.name}</span>
                  <span className="truncate text-xs">{user.email}</span>
                </div>
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={handleLogOut} className="cursor-pointer">
              <LogOut />
              Log out
            </DropdownMenuItem>
            <DropdownMenuItem
              variant="destructive"
              onClick={handleDeleteAccount}
              className="cursor-pointer"
            >
              <Trash2 />
              Delete Account
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
