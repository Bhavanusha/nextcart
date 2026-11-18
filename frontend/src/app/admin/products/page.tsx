
"use client";

import Icon from "../Icon";

import Link from "next/link";
import { useState } from "react";
import { adminRequest, Badge, DataTable, Heading, Message, money, Pagination, Search, Thumb, useAdminData, usePagination } from "../ui";

type Product = { id: number; name: string; sku: string; categoryName: string | null; categoryId: number | null; price: number; currencyCode: string;
  stockQuantity: number; reservedQuantity: number; lowStockThreshold: number; status: string; imageUrl: string | null };
type Category = { id: number; name: string };

export default function ProductsPage() {
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const [status, setStatus] = useState("");
  const [stock, setStock] = useState("");
  const [sort, setSort] = useState("");
  const [selected, setSelected] = useState<number[]>([]);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const products = useAdminData<Product[]>("/products");
  const categories = useAdminData<Category[]>("/categories");
  const query = search.trim().toLowerCase();
  const rows = (products.data ?? []).filter(product => {
    const available = product.stockQuantity - product.reservedQuantity;
    const stockStatus = available <= 0 ? "out" : available <= product.lowStockThreshold ? "low" : "in";
    return (!query || (product.name + " " + product.sku).toLowerCase().includes(query))
      && (!category || String(product.categoryId) === category) && (!status || product.status === status)
      && (!stock || stockStatus === stock);
  });
  if (sort === "name") rows.sort((a, b) => a.name.localeCompare(b.name));
  if (sort === "stock") rows.sort((a, b) => a.stockQuantity - b.stockQuantity);
  const pager = usePagination(rows, 8, [query, category, status, stock, sort].join("|"));
  const selectable = pager.rows.filter(row => row.status !== "ARCHIVED");
  const allChecked = !!selectable.length && selectable.every(row => selected.includes(row.id));

  async function archive(ids: number[]) {
    if (!confirm(ids.length === 1 ? "Archive this product?" : "Archive " + ids.length + " products?")) return;
    setSaving(true);
    setError("");
    const results = await Promise.allSettled(ids.map(id => adminRequest("/products/" + id, { method: "DELETE" })));
    const archived = ids.filter((_, index) => results[index].status === "fulfilled");
    setSelected(current => current.filter(id => !archived.includes(id)));
    if (archived.length !== ids.length) setError("Unable to archive one or more products.");
    products.refresh();
    setSaving(false);
  }

  return <main className="admin-content">
    <Heading title="Products" description="Manage your store products, pricing, and stock levels"><Link className="admin-action-button" href="/admin/products/new">+ Add Product</Link></Heading>
    <div className="admin-filter"><Search value={search} onChange={setSearch} placeholder="Filter products…" />
      <select aria-label="Category" value={category} onChange={event => setCategory(event.target.value)}><option value="">Category</option>{categories.data?.map(row => <option key={row.id} value={row.id}>{row.name}</option>)}</select>
      <select aria-label="Status" value={status} onChange={event => setStatus(event.target.value)}><option value="">Status</option><option value="ACTIVE">Active</option><option value="DRAFT">Draft</option><option value="ARCHIVED">Archived</option></select>
      <select aria-label="Stock" value={stock} onChange={event => setStock(event.target.value)}><option value="">Stock</option><option value="in">In stock</option><option value="low">Low stock</option><option value="out">Out of stock</option></select>
      <select aria-label="Sort" value={sort} onChange={event => setSort(event.target.value)}><option value="">Sort</option><option value="name">Name</option><option value="stock">Stock, lowest first</option></select>
      {!!selected.length && <button className="admin-secondary-button" disabled={saving} onClick={() => void archive(selected)}>Archive selected ({selected.length})</button>}
    </div>
    <Message error={error || products.error || categories.error} />
    <DataTable columns={[<label key="all" className="cell-identity"><input type="checkbox" aria-label="Select all products on this page" checked={allChecked}
      disabled={!selectable.length || saving} onChange={() => {
        const ids = selectable.map(row => row.id);
        setSelected(current => allChecked ? current.filter(id => !ids.includes(id)) : [...new Set([...current, ...ids])]);
      }} /> Image</label>, "Product Name", "SKU", "Category", "Price", "Stock", "Status", "Actions"]}
      empty={!products.data && !products.error ? "Loading products…" : !rows.length ? "No products found." : undefined}>
      {pager.rows.map(row => <tr key={row.id}><td><div className="cell-identity"><input type="checkbox" aria-label={"Select " + row.name}
        checked={selected.includes(row.id)} disabled={row.status === "ARCHIVED" || saving} onChange={() => setSelected(current => current.includes(row.id) ? current.filter(id => id !== row.id) : [...current, row.id])} /><Thumb src={row.imageUrl} /></div></td>
        <td><Link href={"/admin/products/" + row.id + "/edit"}><strong>{row.name}</strong></Link></td><td>{row.sku}</td><td>{row.categoryName ?? "Uncategorized"}</td>
        <td><strong>{money(row.price, row.currencyCode)}</strong></td><td className={row.stockQuantity === 0 ? "danger-text" : ""}>{row.stockQuantity}</td><td><Badge value={row.status} /></td>
        <td><Link className="admin-icon" href={"/admin/products/" + row.id + "/edit"} aria-label={"Edit " + row.name} title="Edit"><Icon name="edit" size={16} /></Link>
          {row.status !== "ARCHIVED" && <button className="admin-icon danger" title="Archive" aria-label={"Archive " + row.name} disabled={saving} onClick={() => void archive([row.id])}><Icon name="trash" size={16} /></button>}</td></tr>)}
    </DataTable>
    <Pagination {...pager} label="products" />
  </main>;
}
