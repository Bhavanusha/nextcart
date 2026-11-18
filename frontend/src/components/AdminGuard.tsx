
"use client";

import { useEffect, useState, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";

export type AdminIdentity = { firstName: string; lastName: string };

export default function AdminGuard({ children }: { children: (identity: AdminIdentity) => ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [identity, setIdentity] = useState<AdminIdentity>();
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    fetch((process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080") + "/api/admin/session", {
      credentials: "include", signal: controller.signal,
    }).then(async response => {
      if (controller.signal.aborted) return;
      if (response.status === 401 || response.status === 403) {
        setIdentity(undefined);
        router.replace("/login?returnTo=" + encodeURIComponent(pathname + window.location.search));
      } else if (response.ok) {
        const user = await response.json() as AdminIdentity;
        if (!controller.signal.aborted) { setIdentity(user); setFailed(false); }
      } else {
        setFailed(true);
      }
    }).catch(() => { if (!controller.signal.aborted) setFailed(true); });
    return () => controller.abort();
  }, [pathname, router, attempt]);

  if (failed) return <main className="admin-guard-loading"><div>Unable to check administrator access. <button onClick={() => { setFailed(false); setAttempt(value => value + 1); }}>Try again</button></div></main>;
  return identity ? children(identity) : <main className="admin-guard-loading">Checking administrator access…</main>;
}
