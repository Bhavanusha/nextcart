
"use client";

import Icon from "../Icon";

import { useState, type FormEvent } from "react";
import { adminRequest, Badge, DataTable, dateLabel, Heading, Message, Panel, Thumb, useAdminData } from "../ui";

type Category = { id: number; name: string; description: string | null; imageUrl: string | null; parentId: number | null;
  productCount: number; active: boolean; createdAt: string };
const blank = { name: "", description: "", imageUrl: "", parentId: "", active: true };

export default function CategoriesPage() {
  const source = useAdminData<Category[]>("/categories");
  const [editing, setEditing] = useState<Category | "new">();
  const [form, setForm] = useState(blank);
  const [collapsed, setCollapsed] = useState<number[]>([]);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const categories = source.data ?? [];
  const grouped = new Map<number | null, Category[]>();
  for (const row of categories) grouped.set(row.parentId, [...(grouped.get(row.parentId) ?? []), row]);
  const seen = new Set<number>();
  const rows: { category: Category; depth: number }[] = [];
  function visit(category: Category, depth: number, visible: boolean): void {
    if (seen.has(category.id)) return;
    seen.add(category.id);
    if (visible) rows.push({ category, depth });
    grouped.get(category.id)?.forEach(child => visit(child, depth + 1, visible && !collapsed.includes(category.id)));
  }
  grouped.get(null)?.forEach(category => visit(category, 0, true));
  categories.forEach(category => visit(category, 0, true));

  function open(category?: Category) {
    setError("");
    setEditing(category ?? "new");
    setForm(category ? { name: category.name, description: category.description ?? "", imageUrl: category.imageUrl ?? "",
      parentId: category.parentId?.toString() ?? "", active: category.active } : blank);
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError("");
    const id = editing && editing !== "new" ? editing.id : undefined;
    try {
      await adminRequest("/categories" + (id ? "/" + id : ""), { method: id ? "PUT" : "POST",
        body: JSON.stringify({ ...form, parentId: form.parentId ? Number(form.parentId) : null, imageUrl: form.imageUrl || null }) });
      setEditing(undefined);
      source.refresh();
    } catch (failure) { setError((failure as Error).message); }
    finally { setSaving(false); }
  }

  async function deactivate(category: Category) {
    if (!confirm("Mark " + category.name + " as draft?")) return;
    setError("");
    setSaving(true);
    try {
      await adminRequest("/categories/" + category.id, { method: "DELETE" });
      source.refresh();
    } catch (failure) { setError((failure as Error).message); }
    finally { setSaving(false); }
  }

  return <main className="admin-content">
    <Heading title="Categories" description="Manage and organize your store catalog hierarchy"><button className="admin-action-button" onClick={() => open()}>+ Add Category</button></Heading>
    <Message error={error || source.error} />
    {editing && <div className="admin-editor-inline"><Panel title={editing === "new" ? "Add Category" : "Edit Category"}><form className="admin-fields" onSubmit={save}>
      <label>Category Name<input required maxLength={150} value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} /></label>
      <label>Description<textarea maxLength={2000} value={form.description} onChange={event => setForm({ ...form, description: event.target.value })} /></label>
      <label>Image URL<input maxLength={2048} type="url" value={form.imageUrl} onChange={event => setForm({ ...form, imageUrl: event.target.value })} /></label>
      <div className="fields-two"><label>Parent Category<select value={form.parentId} onChange={event => setForm({ ...form, parentId: event.target.value })}><option value="">None</option>
        {categories.filter(category => editing === "new" || category.id !== editing.id).map(category => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
      <label>Status<select value={form.active ? "ACTIVE" : "DRAFT"} onChange={event => setForm({ ...form, active: event.target.value === "ACTIVE" })}><option>ACTIVE</option><option>DRAFT</option></select></label></div>
      <div className="admin-actions"><button className="admin-action-button" disabled={saving}>Save Category</button><button type="button" className="admin-secondary-button" onClick={() => { setEditing(undefined); setError(""); }}>Cancel</button></div>
    </form></Panel></div>}
    <DataTable className="categories-table" columns={["Category Name", "Products Count", "Status", "Created Date", "Actions"]}
      empty={!source.data && !source.error ? "Loading categories…" : !rows.length ? "No categories found." : undefined}>
      {rows.map(({ category, depth }) => <tr key={category.id}>
        <td><div className="cell-identity" style={{ paddingInlineStart: Math.min(6, depth) * 28 }}>
          {grouped.has(category.id) ? <button className="admin-icon" aria-expanded={!collapsed.includes(category.id)} aria-label={"Toggle " + category.name} onClick={() => setCollapsed(current => current.includes(category.id) ? current.filter(id => id !== category.id) : [...current, category.id])}>{collapsed.includes(category.id) ? "›" : "⌄"}</button>
            : <span className="category-indent" aria-hidden="true">{depth ? "└" : ""}</span>}
          <Thumb src={category.imageUrl} size={32} /><strong>{category.name}</strong>
        </div></td>
        <td>{category.productCount.toLocaleString()} {category.productCount === 1 ? "product" : "products"}</td><td><Badge value={category.active ? "ACTIVE" : "DRAFT"} /></td><td>{dateLabel(category.createdAt)}</td>
        <td><button title="Edit" className="admin-icon" aria-label={"Edit " + category.name} onClick={() => open(category)}><Icon name="edit" size={16} /></button>
          {category.active && <button title="Mark as draft" className="admin-icon danger" disabled={saving} aria-label={"Mark " + category.name + " as draft"} onClick={() => void deactivate(category)}><Icon name="trash" size={16} /></button>}</td>
      </tr>)}
    </DataTable>
  </main>;
}
