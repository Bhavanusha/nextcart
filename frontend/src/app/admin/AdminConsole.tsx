
"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import AdminGuard, { type AdminIdentity } from "@/components/AdminGuard";
import { API_URL, Message, Search, Thumb, useAdminData } from "./ui";
import Icon from "./Icon";

const navigation = [
  { group: "Main", items: [
    { label: "Dashboard", href: "/admin", title: "Overview Dashboard" },
    { label: "Products", href: "/admin/products", title: "Products Management" },
    { label: "Categories", href: "/admin/categories", title: "Category Management" },
    { label: "Inventory", href: "/admin/inventory", title: "Inventory Management" },
  ] },
  { group: "Commerce", items: [
    { label: "Orders", href: "/admin/orders", title: "Orders Management" },
    { label: "Customers", href: "/admin/customers", title: "Customer Management" },
    { label: "Reviews", href: "/admin/reviews", title: "Review Feedback Hub" },
  ] },
  { group: "Marketing", items: [{ label: "Coupons", href: "/admin/coupons", title: "Discount & Promotion Center" }] },
  { group: "Analytics", items: [{ label: "Reports", href: "/admin/reports", title: "Analytics & Reports" }] },
  { group: "System", items: [{ label: "Settings", href: "/admin/settings", title: "Settings" }] },
];
const items = navigation.flatMap(section => section.items);

export default function AdminConsole({ children }: { children: ReactNode }) {
  return <AdminGuard>{identity => <AdminShell identity={identity}>{children}</AdminShell>}</AdminGuard>;
}

function AdminShell({ identity, children }: { identity: AdminIdentity; children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [search, setSearch] = useState("");
  const [menu, setMenu] = useState("");
  const [loggingOut, setLoggingOut] = useState(false);
  const [error, setError] = useState("");
  const matches = search.trim() ? items.filter(item => (item.label + " " + item.title).toLowerCase().includes(search.trim().toLowerCase())) : [];
  const current = items.find(item => item.href === pathname || (item.href !== "/admin" && pathname.startsWith(item.href + "/")));
  const title = pathname.endsWith("/products/new") ? "Add New Product" : pathname.endsWith("/edit") ? "Edit Product"
    : pathname.startsWith("/admin/orders/") ? "Orders Manager" : current?.title;
  const name = identity.firstName + " " + identity.lastName;
  const initials = ((identity.firstName[0] ?? "") + (identity.lastName[0] ?? "")).toUpperCase();
  const { data: settings, refresh } = useAdminData<{
    storeName: string; logoUrl: string | null; preferences: { orderAlerts: boolean; lowStockAlerts: boolean };
  }>("/settings");
  useEffect(() => {
    const update = () => refresh();
    window.addEventListener("haven-settings-changed", update);
    return () => window.removeEventListener("haven-settings-changed", update);
  }, [refresh]);

  async function logout() {
    setLoggingOut(true);
    setError("");
    try {
      const response = await fetch(API_URL + "/api/auth/logout", { method: "POST", credentials: "include" });
      if (!response.ok) throw new Error();
      router.replace("/login");
    } catch {
      setError("Unable to sign out. Try again.");
      setLoggingOut(false);
    }
  }

  return <div className="admin-shell">
    <aside className={"admin-sidebar " + (menu === "nav" ? "nav-open" : "")}>
      <Link className="admin-brand" href="/admin" onClick={() => setMenu("")}>{settings?.logoUrl ? <Thumb src={settings.logoUrl} size={28} /> : <span className="admin-brand-mark">{(settings?.storeName ?? "HAVEN").charAt(0)}</span>}<span>{settings?.storeName ?? "HAVEN"} <small>Admin</small></span></Link>
      <nav className="admin-nav" aria-label="Admin navigation">{navigation.map(section => <div key={section.group} className="nav-group">
        <p>{section.group}</p>{section.items.map(item => <Link key={item.href} aria-current={current?.href === item.href ? "page" : undefined} href={item.href} onClick={() => setMenu("")}><Icon name={item.href.split("/")[2] ?? "dashboard"} />{item.label}</Link>)}
      </div>)}</nav>
      <div className="admin-user"><span className="admin-avatar">{initials}</span><span><strong>{name}</strong><small>System Admin</small></span></div>
    </aside>
    <div className="admin-main">
      <header className="admin-header">
        <button className="mobile-menu admin-secondary-button" aria-label="Toggle navigation" aria-expanded={menu === "nav"} onClick={() => setMenu(menu === "nav" ? "" : "nav")}>☰</button>
        <strong>{title}</strong>
        <form className="header-search" onSubmit={event => { event.preventDefault(); if (matches[0]) { router.push(matches[0].href); setSearch(""); } }}>
          <Search value={search} onChange={setSearch} placeholder="Search admin resources…" />
          {search.trim() && <div className="header-popover">{matches.length ? matches.map(item => <Link key={item.href} href={item.href} onClick={() => setSearch("")}>{item.label}</Link>) : <span>No matching pages.</span>}</div>}
        </form>
        <div className="admin-header-actions">
          <button className="notification-button" aria-label="Notifications" aria-expanded={menu === "notifications"} onClick={() => setMenu(menu === "notifications" ? "" : "notifications")}><Icon name="bell" size={19} /></button>
          <button className="account-button" aria-expanded={menu === "account"} onClick={() => setMenu(menu === "account" ? "" : "account")}><span className="admin-avatar">{initials}</span><b>{name}</b><Icon name="chevron" size={15} /></button>
          {menu === "account" && <div className="header-popover"><button onClick={logout} disabled={loggingOut}>{loggingOut ? "Signing out…" : "Sign out"}</button></div>}
          {menu === "notifications" && <Notifications close={() => setMenu("")} orderAlerts={settings?.preferences.orderAlerts ?? true} lowStockAlerts={settings?.preferences.lowStockAlerts ?? true} />}
        </div>
      </header>
      {error && <Message error={error} />}
      {children}
    </div>
  </div>;
}

function Notifications({ close, orderAlerts, lowStockAlerts }: { close: () => void; orderAlerts: boolean; lowStockAlerts: boolean }) {
  const { data, error } = useAdminData<{ pendingOrders: number; lowStockProducts: number; outOfStockProducts: number }>("/dashboard");
  return <div className="header-popover"><Message error={error} />{data ? <>
    {orderAlerts && <Link href="/admin/orders?status=PENDING" onClick={close}>{data.pendingOrders} pending orders</Link>}
    {lowStockAlerts && <Link href="/admin/inventory" onClick={close}>{(data.lowStockProducts ?? data.outOfStockProducts)} low-stock items</Link>}
    {!orderAlerts && !lowStockAlerts && <span>Admin notifications are turned off.</span>}
  </> : !error && <span>Loading notifications…</span>}</div>;
}
