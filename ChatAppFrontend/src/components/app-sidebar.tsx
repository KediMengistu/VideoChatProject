"use client";

import { Link } from "react-router-dom";
import { Home, LogIn, X } from "lucide-react";
import { RiVideoChatLine } from "react-icons/ri";
import { useAppSelector } from "@/hooks/useAppSelector";
import { NavDetails } from "@/components/nav-details";
import { NavUser } from "@/components/nav-user";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
  useSidebar,
} from "@/components/ui/sidebar";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export function AppSidebar() {
  const user = useAppSelector((state) => state.auth.user);
  const { isMobile, state, setOpen } = useSidebar();
  const showCloseButton = isMobile && state === "expanded";

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader
        className={cn(
          "relative flex-row items-center justify-between gap-2 border-b border-sidebar-border",
          showCloseButton && "pr-10"
        )}
      >
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton asChild size="lg" tooltip="Home">
              <Link to="/">
                <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
                  <RiVideoChatLine className="size-5" />
                </div>
                <span className="font-semibold text-sidebar-foreground">
                  Chat4U
                </span>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
        {showCloseButton && (
          <Button
            variant="ghost"
            size="icon"
            className="absolute right-2 top-1/2 -translate-y-1/2 shrink-0 text-sidebar-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
            onClick={() => setOpen(false)}
            aria-label="Close sidebar"
          >
            <X className="h-4 w-4" />
          </Button>
        )}
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton asChild tooltip="Create Room">
                <Link to="/">
                  <Home className="h-4 w-4" />
                  <span>Create Room</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>
            <SidebarMenuItem>
              <SidebarMenuButton asChild tooltip="Join Room">
                <Link to="/">
                  <LogIn className="h-4 w-4" />
                  <span>Join Room</span>
                </Link>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarGroup>
        <NavDetails className="-mt-1 pt-0" />
      </SidebarContent>
      <SidebarFooter>
        <NavUser
          user={{
            name: user?.displayName ?? "User",
            email: user?.email ?? "",
            avatar: user?.photoURL ?? "",
          }}
        />
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}
