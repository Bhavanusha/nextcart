"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import { ErrorLine, Icon, RequireAccount, signOut, useStore } from "../StoreShell";

import { Avatar } from "./OrderViews";

const links = [["/account","Dashboard","grid"],["/account/orders","My Orders","order"],["/wishlist","Wishlist","heart"],
  ["/account/addresses","Addresses","pin"],["/account/profile","Settings","account"]];

export default function AccountLayout({ children }: { children: ReactNode }) {
  return <RequireAccount><AccountShell>{children}</AccountShell></RequireAccount>;
}

function AccountShell({ children }: { children: ReactNode }) {
  const path = usePathname();
  const router = useRouter();
  const { profile } = useStore();
  const [error,setError] = useState("");
  const [busy,setBusy] = useState(false);
  if (!profile) return null;
  return <main className="sf-wrap sf-page sf-account">
    <aside className="sf-account-sidebar"><div className="sf-account-identity"><Avatar profile={profile}/><strong>{profile.firstName} {profile.lastName}</strong><span>{profile.email}</span></div>
      <nav aria-label="Account navigation">{links.map(([href,label,icon])=><Link key={href} href={href}
        aria-current={href===path || (href!=="/account" && path.startsWith(href+"/")) ? "page" : undefined}><Icon name={icon} size={18}/>{label}</Link>)}</nav>
      <button className="sf-text-button sf-accent" disabled={busy} onClick={async()=>{
        setBusy(true);setError("");
        try { await signOut(); router.replace("/"); } catch(failure) { setError((failure as Error).message);setBusy(false); }
      }}><Icon name="exit" size={18}/>Logout</button><ErrorLine error={error}/>
    </aside>
    <div className="sf-account-body">{children}</div>
  </main>;
}
