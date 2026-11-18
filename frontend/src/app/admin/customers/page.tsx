
"use client";

import Icon from "../Icon";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { adminRequest, Badge, DataTable, dateLabel, Heading, Message, money, Pagination, Search, useAdminData, usePagination } from "../ui";

type Customer = { id: number; firstName: string; lastName: string; email: string; phone: string | null; active: boolean;
  joinedAt: string; orderCount: number; spending: { currency: string; amount: number }[] };
const fullName = (row: Customer) => row.firstName + " " + row.lastName;
const spent = (row: Customer, baseCurrency: string) => row.spending.length
  ? row.spending.map(value => money(value.amount, value.currency)).join(" / ") : money(0, baseCurrency);

export default function CustomersPage() {
  return <Suspense fallback={<main className="admin-content admin-empty">Loading customers…</main>}><CustomerList /></Suspense>;
}

function CustomerList() {
  const params = useSearchParams();
  const router = useRouter();
  const source = useAdminData<Customer[]>("/customers");
  const settings = useAdminData<{ currencyCode: string }>("/settings");
  const currency = settings.data?.currencyCode ?? "USD";
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [selected, setSelected] = useState<number>();
  const [menu, setMenu] = useState<number>();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const rows = source.data ?? [];
  const query = search.trim().toLowerCase();
  const pager = usePagination(rows.filter(row => (fullName(row) + " " + row.email).toLowerCase().includes(query)
    && (!status || row.active === (status === "active"))), 8, query + "|" + status);
  const detail = rows.find(row => row.id === (selected ?? Number(params.get("id"))));

  function closeDetail() {
    setSelected(undefined);
    if (params.has("id")) router.replace("/admin/customers");
  }

  async function toggle(customer: Customer) {
    setSaving(true);
    setError("");
    try {
      await adminRequest("/customers/" + customer.id + "/status?active=" + !customer.active, { method: "PUT" });
      source.refresh();
      setMenu(undefined);
    } catch (failure) { setError((failure as Error).message); }
    finally { setSaving(false); }
  }

  return <main className="admin-content">
    <Heading title="Customers" description={source.data ? rows.length.toLocaleString() + " total registered customers" : undefined} />
    <div className="admin-filter"><Search value={search} onChange={setSearch} placeholder="Filter customers by name or email…" />
      <select aria-label="Status" value={status} onChange={event => setStatus(event.target.value)}><option value="">Status: All</option><option value="active">Active</option><option value="inactive">Inactive</option></select></div>
    <Message error={error || source.error || (params.has("id") && source.data && !detail ? "Customer not found." : undefined)} />
    <DataTable columns={["Avatar · Customer Name", "Email", "Phone", "Orders", "Total Spent", "Joined Date", "Status", "Actions"]}
      empty={!source.data && !source.error ? "Loading customers…" : !pager.total ? "No customers found." : undefined}>
      {pager.rows.map(row => <tr key={row.id}><td><div className="cell-identity"><span className="admin-avatar">{row.firstName[0]}{row.lastName[0]}</span><strong>{fullName(row)}</strong></div></td>
        <td>{row.email}</td><td>{row.phone ?? "—"}</td><td>{row.orderCount}</td><td><strong>{spent(row, currency)}</strong></td>
        <td>{dateLabel(row.joinedAt)}</td><td><Badge value={row.active ? "ACTIVE" : "INACTIVE"} /></td>
        <td><button className="admin-icon" title="View customer" aria-label={"View " + fullName(row)} onClick={() => setSelected(row.id)}><Icon name="eye" size={16} /></button>
          <button className="admin-icon" title="More actions" aria-label={"More actions for " + fullName(row)} aria-expanded={menu === row.id} onClick={() => setMenu(menu === row.id ? undefined : row.id)}>⋮</button>
          {menu === row.id && <button className="admin-link" disabled={saving} onClick={() => void toggle(row)}>{row.active ? "Deactivate" : "Activate"}</button>}</td></tr>)}
    </DataTable>
    <Pagination {...pager} label="customers" />
    {detail && <CustomerDialog key={detail.id} row={detail} close={closeDetail} currency={currency} />}
  </main>;
}

function CustomerDialog({ row, close, currency }: { row: Customer; close: () => void; currency: string }) {
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => { dialog.current?.showModal(); }, []);
  return <dialog ref={dialog} className="admin-dialog" aria-labelledby="customer-title" onCancel={event => { event.preventDefault(); close(); }}>
    <h2 id="customer-title">{fullName(row)}</h2><button className="admin-icon dialog-close" aria-label="Close" onClick={close}>×</button>
    <dl className="customer-details">
      <dt>Email</dt><dd>{row.email}</dd><dt>Phone</dt><dd>{row.phone ?? "—"}</dd>
      <dt>Joined</dt><dd>{dateLabel(row.joinedAt)}</dd><dt>Orders</dt><dd>{row.orderCount}</dd>
      <dt>Total Spent</dt><dd>{spent(row, currency)}</dd><dt>Status</dt><dd><Badge value={row.active ? "ACTIVE" : "INACTIVE"} /></dd>
    </dl>
  </dialog>;
}
