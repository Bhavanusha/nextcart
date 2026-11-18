
"use client";

import Icon from "../Icon";

import { useState, type FormEvent } from "react";
import { adminRequest, Badge, DataTable, dateLabel, Heading, Message, money, Panel, useAdminData } from "../ui";

type Coupon = { id: number; code: string; description: string | null; discountType: "PERCENTAGE" | "FIXED_AMOUNT";
  discountValue: number; minimumOrderAmount: number; maximumDiscount: number | null; usageLimit: number | null;
  usedCount: number; startsAt: string; expiresAt: string | null; active: boolean; status: string };
const empty = { code: "", description: "", discountType: "PERCENTAGE", discountValue: "", minimumOrderAmount: "0",
  maximumDiscount: "", usageLimit: "", startsAt: "", expiresAt: "", active: true };
function localTime(value: string | null) {
  if (!value) return "";
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

export default function CouponsPage() {
  const source = useAdminData<Coupon[]>("/coupons");
  const { data: settings } = useAdminData<{ currencyCode: string }>("/settings");
  const [editing, setEditing] = useState<Coupon | "new">();
  const [form, setForm] = useState(empty);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const rows = source.data ?? [];
  const currency = settings?.currencyCode ?? "USD";

  function open(row?: Coupon) {
    setError("");
    setEditing(row ?? "new");
    setForm(row ? { code: row.code, description: row.description ?? "", discountType: row.discountType,
      discountValue: String(row.discountValue), minimumOrderAmount: String(row.minimumOrderAmount),
      maximumDiscount: row.maximumDiscount?.toString() ?? "", usageLimit: row.usageLimit?.toString() ?? "",
      startsAt: localTime(row.startsAt), expiresAt: localTime(row.expiresAt), active: row.active }
      : { ...empty, startsAt: localTime(new Date().toISOString()) });
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    const id = editing && editing !== "new" ? editing.id : undefined;
    setBusy(true);
    setError("");
    try {
      await adminRequest("/coupons" + (id ? "/" + id : ""), { method: id ? "PUT" : "POST",
        body: JSON.stringify({ ...form, discountValue: Number(form.discountValue), minimumOrderAmount: Number(form.minimumOrderAmount),
          maximumDiscount: form.discountType === "PERCENTAGE" && form.maximumDiscount !== "" ? Number(form.maximumDiscount) : null,
          usageLimit: form.usageLimit ? Number(form.usageLimit) : null, startsAt: new Date(form.startsAt).toISOString(),
          expiresAt: form.expiresAt ? new Date(form.expiresAt).toISOString() : null }),
      });
      setEditing(undefined);
      source.refresh();
    } catch (failure) { setError((failure as Error).message); }
    finally { setBusy(false); }
  }

  async function deactivate(row: Coupon) {
    if (!confirm("Deactivate coupon " + row.code + "?")) return;
    setBusy(true);
    setError("");
    try { await adminRequest("/coupons/" + row.id, { method: "DELETE" }); source.refresh(); }
    catch (failure) { setError((failure as Error).message); }
    finally { setBusy(false); }
  }

  return <main className="admin-content">
    <Heading title="Coupons" description="Create, configure and track active marketing promotion codes"><button className="admin-action-button" onClick={() => open()}>+ Create Coupon</button></Heading>
    <Message error={error || source.error} />
    {editing && <div className="admin-editor-inline"><Panel title={editing === "new" ? "Create Coupon" : "Edit Coupon"}><form className="admin-fields" onSubmit={save}>
      <div className="fields-two"><label>Code<input value={form.code} maxLength={50} required onChange={event => setForm({ ...form, code: event.target.value })} /></label>
        <label>Discount Type<select value={form.discountType} onChange={event => setForm({ ...form, discountType: event.target.value })}><option value="PERCENTAGE">Percentage</option><option value="FIXED_AMOUNT">Fixed</option></select></label></div>
      <label>Description<textarea value={form.description} onChange={event => setForm({ ...form, description: event.target.value })} /></label>
      <div className="fields-two">
        <label>Discount<input required type="number" min=".01" max={form.discountType === "PERCENTAGE" ? 100 : undefined} step=".01" value={form.discountValue} onChange={event => setForm({ ...form, discountValue: event.target.value })} /></label>
        <label>Minimum Order<input type="number" min="0" step=".01" value={form.minimumOrderAmount} onChange={event => setForm({ ...form, minimumOrderAmount: event.target.value })} /></label>
        {form.discountType === "PERCENTAGE" && <label>Maximum Discount<input type="number" min=".01" step=".01" value={form.maximumDiscount} onChange={event => setForm({ ...form, maximumDiscount: event.target.value })} /></label>}
        <label>Usage Limit<input type="number" min={editing === "new" ? 1 : Math.max(1, editing.usedCount)} step="1" value={form.usageLimit} onChange={event => setForm({ ...form, usageLimit: event.target.value })} /></label>
        <label>Starts At<input required type="datetime-local" value={form.startsAt} onChange={event => setForm({ ...form, startsAt: event.target.value })} /></label>
        <label>Expires At<input type="datetime-local" min={form.startsAt} value={form.expiresAt} onChange={event => setForm({ ...form, expiresAt: event.target.value })} /></label>
      </div>
      <label className="inline-field"><input type="checkbox" checked={form.active} onChange={event => setForm({ ...form, active: event.target.checked })} /> Enabled</label>
      <div className="admin-actions"><button className="admin-action-button" disabled={busy}>Save Coupon</button><button type="button" className="admin-secondary-button" onClick={() => setEditing(undefined)}>Cancel</button></div>
    </form></Panel></div>}
    <DataTable columns={["Code", "Discount", "Type", "Min Order", "Max Discount", "Usage Limit", "Expiry Date", "Status", "Actions"]}
      empty={!source.data && !source.error ? "Loading coupons…" : !rows.length ? "No coupons found." : undefined}>
      {rows.map(row => {
        const percentage = row.discountType === "PERCENTAGE";
        return <tr key={row.id}><td><strong className="code-label">{row.code}</strong></td><td><strong className="accent-text">{percentage ? row.discountValue + "%" : money(row.discountValue, currency)}</strong></td>
          <td>{percentage ? "Percentage" : "Fixed"}</td><td>{row.minimumOrderAmount ? money(row.minimumOrderAmount, currency) : "No min"}</td>
          <td>{row.maximumDiscount === null ? "—" : money(row.maximumDiscount, currency)}</td><td>{row.usedCount} / {row.usageLimit ?? "∞"}</td>
          <td>{row.expiresAt ? dateLabel(row.expiresAt) : "Ongoing"}</td><td><Badge value={row.status} /></td>
          <td><button className="admin-icon" title="Edit" aria-label={"Edit coupon " + row.code} onClick={() => open(row)}><Icon name="edit" size={16} /></button>
            {row.active && <button className="admin-icon danger" title="Deactivate" aria-label={"Deactivate coupon " + row.code} disabled={busy} onClick={() => void deactivate(row)}><Icon name="trash" size={16} /></button>}</td></tr>;
      })}
    </DataTable>
  </main>;
}
