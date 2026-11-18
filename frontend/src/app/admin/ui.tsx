
"use client";

/* eslint-disable @next/next/no-img-element */
import { useCallback, useEffect, useState, type ReactNode } from "react";

export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export async function adminRequest<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(API_URL + "/api/admin" + path, {
    ...options,
    credentials: "include",
    headers: options?.body ? { "Content-Type": "application/json", ...options.headers } : options?.headers,
  });
  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new Error(problem?.detail || problem?.message || (response.status === 409 ? "This value already exists." : "Unable to complete the request."));
  }
  if (response.status === 204) return undefined as T;
  const body = await response.text();
  return body ? JSON.parse(body) as T : undefined as T;
}

export function useAdminData<T>(path: string) {
  const [state, setState] = useState<{ data?: T; error?: string; path: string }>({ path: "" });
  const [version, setVersion] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    adminRequest<T>(path, { signal: controller.signal })
      .then(data => { if (!controller.signal.aborted) setState({ data, path }); })
      .catch(error => { if (!controller.signal.aborted) setState({ error: error.message, path }); });
    return () => controller.abort();
  }, [path, version]);
  const refresh = useCallback(() => setVersion(current => current + 1), []);
  return { data: state.path === path ? state.data : undefined, error: state.path === path ? state.error : undefined,
    refresh };
}

export function Heading({ title, description, children }: { title: string; description?: string; children?: ReactNode }) {
  return <div className="admin-page-heading"><div><h1>{title}</h1>{description && <p>{description}</p>}</div>{children && <div className="admin-actions">{children}</div>}</div>;
}

export function Panel({ title, children, className = "" }: { title: string; children: ReactNode; className?: string }) {
  return <section className={"admin-panel " + className}><h2>{title}</h2>{children}</section>;
}

export function titleCase(value: string) {
  return value.toLowerCase().replaceAll("_", " ").replace(/(^| )\w/g, character => character.toUpperCase());
}

const colors: Record<string, string> = {
  ACTIVE: "green", IN_STOCK: "green", PAID: "green", DELIVERED: "green", APPROVED: "green",
  PENDING: "gold", CONFIRMED: "gold", PROCESSING: "gold", LOW_STOCK: "gold", SCHEDULED: "gold", DRAFT: "gold", UNPAID: "gold",
  EXPIRED: "red", OUT_OF_STOCK: "red", FAILED: "red", REFUNDED: "red", SHIPPED: "orange",
};

export function Badge({ value, label }: { value: string; label?: string }) {
  return <span className={"badge " + (colors[value] ?? "gray")}>{label ?? titleCase(value)}</span>;
}

export function Thumb({ src, label, size = 40 }: { src?: string | null; label?: string; size?: number }) {
  const [failed, setFailed] = useState<string>();
  const width = { width: size, height: size };
  const url = src?.startsWith("/api/media/") ? API_URL + src : src;
  return url && url !== failed
    ? <img className="admin-thumb" src={url} alt={label ?? ""} style={width} onError={() => setFailed(url)} />
    : <span className="admin-thumb admin-thumb-empty" aria-hidden="true" style={width}>◇</span>;
}

export function DataTable({ columns, children, empty, className = "" }: {
  columns: (string | ReactNode)[]; children?: ReactNode; empty?: string; className?: string;
}) {
  return <div className="admin-table-scroll"><table className={"haven-table " + className}>
    <thead><tr>{columns.map((column, index) => <th key={index} scope="col">{column}</th>)}</tr></thead>
    <tbody>{empty ? <tr><td className="table-empty" colSpan={columns.length}>{empty}</td></tr> : children}</tbody>
  </table></div>;
}

export function usePagination<T>(items: T[], size = 8, resetKey = "") {
  const [state, setState] = useState({ key: resetKey, page: 1 });
  const total = items.length;
  const pages = Math.max(1, Math.ceil(total / size));
  const page = state.key === resetKey ? Math.min(state.page, pages) : 1;
  const start = (page - 1) * size;
  return { rows: items.slice(start, start + size), page, pages, total, start: total ? start + 1 : 0,
    end: Math.min(start + size, total), setPage: (value: number) => setState({ key: resetKey, page: value }) };
}

export function Pagination({ page, pages, total, start, end, setPage, label }: {
  page: number; pages: number; total: number; start: number; end: number; setPage: (value: number) => void; label: string;
}) {
  if (!total) return null;
  const visible = [...new Set([1, Math.max(2, page - 1), page, Math.min(pages, page + 1), 3, pages])]
    .filter(value => value <= pages).sort((left, right) => left - right);
  return <div className="admin-pagination"><span>Showing {start}–{end} of {total.toLocaleString()} {label}</span>
    <nav aria-label={label + " pagination"}><button onClick={() => setPage(page - 1)} disabled={page === 1}>Previous</button>
      {visible.map((value, index) => <span key={value}>{index > 0 && value - visible[index - 1] > 1 && <span className="pagination-gap">…</span>}
        <button aria-current={page === value ? "page" : undefined} onClick={() => setPage(value)}>{value}</button></span>)}
      <button onClick={() => setPage(page + 1)} disabled={page === pages}>Next</button></nav></div>;
}

export function Search({ value, onChange, placeholder }: { value: string; onChange: (value: string) => void; placeholder: string }) {
  return <label className="admin-search"><span aria-hidden="true" className="search-mark" /><input type="search" aria-label={placeholder} placeholder={placeholder} value={value} onChange={event => onChange(event.target.value)} /></label>;
}

export function Message({ error, success }: { error?: string; success?: string }) {
  if (error) return <p className="admin-alert" role="alert">{error}</p>;
  if (success) return <p className="admin-success" role="status">{success}</p>;
  return null;
}

export const money = (value: number, currency = "USD") =>
  new Intl.NumberFormat("en-US", { style: "currency", currency }).format(value);

export const dateLabel = (value: string) =>
  new Date(value.length === 10 ? value + "T00:00:00" : value).toLocaleDateString("en-US", { month: "short", day: "2-digit", year: "numeric" });

export function exportCsv(name: string, rows: (string | number | null | undefined)[][]) {
  const csv = rows.map(row => row.map(cell => {
    const value = String(cell ?? "");
    const safe = typeof cell === "string" && /^(?:[\t\r\n]|\s*[=+@-])/.test(value) ? "'" + value : value;
    return '"' + safe.replaceAll('"', '""') + '"';
  }).join(",")).join("\r\n");
  const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = name + ".csv";
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function readImage(file: File) {
  return new Promise<string>((resolve, reject) => {
    if (!["image/png", "image/jpeg"].includes(file.type) || file.size > 10 * 1024 * 1024) {
      reject(new Error("Use a PNG or JPEG image up to 10 MB."));
      return;
    }
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new Error("Unable to read the image."));
    reader.readAsDataURL(file);
  });
}
