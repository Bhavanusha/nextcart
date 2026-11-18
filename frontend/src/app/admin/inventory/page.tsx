
"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { adminRequest, Badge, DataTable, dateLabel, exportCsv, Heading, Message, Search, Thumb, useAdminData } from "../ui";

type Stock = { id: number; name: string; sku: string; stockQuantity: number; reservedQuantity: number; availableQuantity: number;
  status: string; categoryName: string | null; variants: string | null; imageUrl: string | null; updatedAt: string };
const reasons = { SHIPMENT: "New Shipment Received", RECOUNT: "Inventory Recount", DAMAGE: "Damaged Items", RETURN: "Customer Return", OTHER: "Other" };

export default function InventoryPage() {
  const source = useAdminData<Stock[]>("/inventory");
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [status, setStatus] = useState("");
  const [selected, setSelected] = useState<Stock>();
  const q = search.trim().toLowerCase();
  const categories = Array.from(new Set(source.data?.flatMap(row => row.categoryName ? [row.categoryName] : []))).sort();
  const rows = (source.data ?? []).filter(row => (row.name + " " + row.sku + " " + (row.variants ?? "")).toLowerCase().includes(q)
    && (!category || category === row.categoryName) && (!status || status === row.status));

  function exportStock() {
    exportCsv("haven-inventory", [["Product", "SKU", "Variant", "Current", "Reserved", "Available", "Status", "Last Updated"],
      ...rows.map(row => [row.name, row.sku, row.variants, row.stockQuantity, row.reservedQuantity, row.availableQuantity, row.status, row.updatedAt])]);
  }

  return <main className="admin-content">
    <Heading title="Inventory" description="Track real-time stock levels, available quantity, and reservations"><button className="admin-secondary-button" onClick={exportStock} disabled={!rows.length}>Export CSV</button></Heading>
    <div className="admin-filter"><Search value={search} onChange={setSearch} placeholder="Search SKU or product name…" />
      <select aria-label="Category" value={category} onChange={event => setCategory(event.target.value)}><option value="">Category: All</option>{categories.map(value => <option key={value}>{value}</option>)}</select>
      <select aria-label="Stock status" value={status} onChange={event => setStatus(event.target.value)}><option value="">Stock Status: All</option>
        <option value="IN_STOCK">In Stock</option><option value="LOW_STOCK">Low Stock</option><option value="OUT_OF_STOCK">Out of Stock</option></select></div>
    <Message error={source.error} />
    <DataTable columns={["Product", "SKU", "Variant", "Current", "Reserved", "Available", "Status", "Last Updated", "Actions"]}
      empty={!source.data && !source.error ? "Loading inventory…" : !rows.length ? "No inventory found." : undefined}>
      {rows.map(row => <tr key={row.id}><td><button className="inventory-product cell-identity" onClick={() => setSelected(row)}><Thumb src={row.imageUrl} size={36} /><strong>{row.name}</strong></button></td>
        <td>{row.sku}</td><td><span title={row.variants ?? undefined} className="cell-truncate">{row.variants || "—"}</span></td>
        <td><strong>{row.stockQuantity}</strong></td><td>{row.reservedQuantity}</td><td className="positive-text">{row.availableQuantity}</td>
        <td><Badge value={row.status} /></td><td>{dateLabel(row.updatedAt)}</td>
        <td><button className="admin-link" onClick={() => setSelected(row)}>Adjust</button></td></tr>)}
    </DataTable>
    {selected && <StockDialog key={selected.id} stock={selected} close={() => setSelected(undefined)} refresh={source.refresh} />}
  </main>;
}

function StockDialog({ stock, close, refresh }: { stock: Stock; close: () => void; refresh: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [quantity, setQuantity] = useState("");
  const [reason, setReason] = useState("SHIPMENT");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const change = Number(quantity);
  const preview = stock.stockQuantity + change;
  const valid = Number.isInteger(change) && change !== 0 && preview >= stock.reservedQuantity && preview <= 2147483647;
  useEffect(() => { dialog.current?.showModal(); }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!valid) return;
    setSaving(true);
    setError("");
    try {
      await adminRequest("/inventory/" + stock.id + "/adjust", { method: "POST",
        body: JSON.stringify({ adjustmentQuantity: change, reason }) });
      refresh();
      close();
    } catch (failure) { setError((failure as Error).message); setSaving(false); }
  }

  return <dialog ref={dialog} className="admin-dialog" aria-labelledby="stock-title" onCancel={event => { event.preventDefault(); if (!saving) close(); }}>
    <h2 id="stock-title">Stock Adjustment</h2><button className="admin-icon dialog-close" aria-label="Close" disabled={saving} onClick={close}>×</button>
    <form className="admin-fields" onSubmit={submit}>
      <div className="dialog-identity cell-identity"><Thumb src={stock.imageUrl} /><span><strong>{stock.name}</strong><br /><small>{stock.sku}{stock.variants && " · " + stock.variants}</small></span></div>
      <div className="spread"><span className="muted-text">Current Stock</span><strong>{stock.stockQuantity} units</strong></div>
      <label>Adjustment Quantity<input required type="text" inputMode="numeric" pattern="[+\-]?[0-9]+" value={quantity} onChange={event => setQuantity(event.target.value)} placeholder="+5" /></label>
      <label>Reason for adjustment<select value={reason} onChange={event => setReason(event.target.value)}>{Object.entries(reasons).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <div className="spread stock-preview"><strong>Preview New Stock</strong><strong>{Number.isFinite(preview) ? preview + " units" : "—"}</strong></div>
      {Number.isInteger(change) && preview < stock.reservedQuantity && <small>Keep at least {stock.reservedQuantity} reserved units.</small>}
      <Message error={error} />
      <div className="admin-actions end-actions"><button type="button" className="admin-secondary-button" disabled={saving} onClick={close}>Cancel</button><button className="admin-action-button" disabled={!valid || saving}>Apply Adjustment</button></div>
    </form>
  </dialog>;
}
