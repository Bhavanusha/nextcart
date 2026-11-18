
"use client";

import Icon from "../Icon";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState, type FormEvent } from "react";
import { adminRequest, Heading, Message, Panel, readImage, Thumb, titleCase, useAdminData } from "../ui";

type Variant = { color: string; size: string; skuOverride: string; price: string };
type Gallery = { id?: number; source: string };
type Product = {
  name: string; description: string | null; sku: string; slug: string; brandName: string | null; categoryId: number | null;
  price: number; compareAtPrice: number | null; stockQuantity: number; reservedQuantity: number; currencyCode: string;
  metaTitle: string | null; featured: boolean; status: string;
};
type Category = { id: number; name: string; active: boolean };
type Form = { name: string; description: string; sku: string; slug: string; brandName: string; categoryId: string;
  price: string; compareAtPrice: string; stockQuantity: string; currencyCode: string; metaTitle: string; featured: boolean; status: string };
const statuses = [
  ["ACTIVE", "Available for the customer storefront"],
  ["DRAFT", "Saved to the admin catalog"],
  ["ARCHIVED", "No longer available to order"],
];
const blank = { name: "", description: "", sku: "", slug: "", brandName: "", categoryId: "", price: "", compareAtPrice: "",
  stockQuantity: "0", currencyCode: "USD", metaTitle: "", featured: false, status: "ACTIVE" };
const emptyVariant = { color: "", size: "", skuOverride: "", price: "" };
const variantLabels = { color: "Color", size: "Size", skuOverride: "SKU Override", price: "Price" };

export default function ProductEditor({ id }: { id?: string }) {
  const categories = useAdminData<Category[]>("/categories");
  if (categories.error) return <main className="admin-content"><Message error={categories.error} /></main>;
  if (!categories.data) return <main className="admin-content admin-empty">Loading catalog…</main>;
  return id ? <ExistingProduct key={id} id={id} categories={categories.data} /> : <NewProduct categories={categories.data} />;
}

function NewProduct({ categories }: { categories: Category[] }) {
  const settings = useAdminData<{ currencyCode: string }>("/settings");
  if (settings.error) return <main className="admin-content"><Message error={settings.error} /></main>;
  if (!settings.data) return <main className="admin-content admin-empty">Loading store settings…</main>;
  return <Editor categories={categories} initial={{ ...blank, currencyCode: settings.data.currencyCode }} />;
}

function ExistingProduct({ id, categories }: { id: string; categories: Category[] }) {
  const product = useAdminData<Product>("/products/" + id);
  const variants = useAdminData<{ color: string | null; size: string | null; skuOverride: string | null; price: number | null }[]>("/products/" + id + "/variants");
  const photos = useAdminData<{ id: number; imageUrl: string; primary: boolean }[]>("/products/" + id + "/images");
  const error = product.error || variants.error || photos.error;
  if (error) return <main className="admin-content"><Message error={error} /></main>;
  if (!product.data || !variants.data || !photos.data) return <main className="admin-content admin-empty">Loading product…</main>;
  const p = product.data;
  const initial = { ...p, description: p.description ?? "", brandName: p.brandName ?? "", categoryId: p.categoryId?.toString() ?? "",
    price: String(p.price), compareAtPrice: p.compareAtPrice?.toString() ?? "", stockQuantity: String(p.stockQuantity), metaTitle: p.metaTitle ?? "" };
  return <Editor id={id} categories={categories} initial={initial} reserved={p.reservedQuantity}
    initialVariants={variants.data.map(row => ({ color: row.color ?? "", size: row.size ?? "", skuOverride: row.skuOverride ?? "", price: row.price?.toString() ?? "" }))}
    initialGallery={[...photos.data].sort((a, b) => Number(b.primary) - Number(a.primary)).map(row => ({ id: row.id, source: row.imageUrl }))} />;
}

function Editor({ id, categories, initial, initialVariants = [], initialGallery = [], reserved = 0 }: {
  id?: string; categories: Category[]; initial: Form; initialVariants?: Variant[]; initialGallery?: Gallery[]; reserved?: number;
}) {
  const router = useRouter();
  const [form, setForm] = useState(initial);
  const [variants, setVariants] = useState(initialVariants);
  const [gallery, setGallery] = useState(initialGallery);
  const [url, setUrl] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [reading, setReading] = useState(false);
  const update = <K extends keyof Form>(key: K, value: Form[K]) => setForm(current => ({ ...current, [key]: value }));
  const slug = (form.slug || form.name).toLowerCase().trim().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "");
  const currencyLabel = form.currencyCode === "USD" ? "$" : form.currencyCode;

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trigger = (event.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null;
    setSaving(true);
    setError("");
    try {
      await adminRequest(id ? "/products/" + id : "/products", {
        method: id ? "PUT" : "POST",
        body: JSON.stringify({ ...form, status: trigger?.value === "DRAFT" ? "DRAFT" : form.status,
          expectedStockQuantity: id ? Number(initial.stockQuantity) : undefined,
          categoryId: form.categoryId ? Number(form.categoryId) : null, price: Number(form.price),
          compareAtPrice: form.compareAtPrice === "" ? null : Number(form.compareAtPrice), stockQuantity: Number(form.stockQuantity),
          variants: variants.map(row => ({ color: row.color.trim() || null, size: row.size.trim() || null,
            skuOverride: row.skuOverride.trim() || null, price: row.price === "" ? null : Number(row.price) })),
          gallery: gallery.map(image => image.id ? { id: image.id } : { source: image.source }),
        }),
      });
      router.push("/admin/products");
    } catch (failure) {
      setError((failure as Error).message);
      setSaving(false);
    }
  }

  async function addFiles(files: File[]) {
    if (!files.length || reading || saving) return;
    if (files.length + gallery.length > 12) { setError("A product can have up to 12 images."); return; }
    setReading(true);
    setError("");
    try {
      const sources = await Promise.all(files.map(readImage));
      setGallery(current => [...current, ...sources.map(source => ({ source }))].slice(0, 12));
    } catch (failure) { setError((failure as Error).message); }
    finally { setReading(false); }
  }

  function addUrl() {
    if (gallery.length >= 12) { setError("A product can have up to 12 images."); return; }
    try {
      const parsed = new URL(url);
      if (!["https:", "http:"].includes(parsed.protocol)) throw new Error();
      setGallery(current => [...current, { source: parsed.href }]);
      setUrl("");
      setError("");
    } catch { setError("Enter a valid HTTP or HTTPS image URL."); }
  }

  return <main className="admin-content"><form onSubmit={save}>
    <Heading title={id ? "Edit Product" : "Add New Product"} description={id ? "Update your product listing" : "Create a new product listing in your catalog"}>
      {id && <Link className="admin-secondary-button" href="/admin/products">Cancel</Link>}
      <button className="admin-secondary-button" type="submit" value="DRAFT" disabled={saving || reading}>Save Draft</button>
      <button className="admin-action-button" type="submit" disabled={saving || reading}>{saving ? "Saving…" : id || form.status !== "ACTIVE" ? "Save Product" : "Publish Product"}</button>
    </Heading>
    <Message error={error} />
    <div className="admin-split"><div className="admin-stack">
      <Panel title="Basic Information"><div className="admin-fields">
        <label>Product Name<input required maxLength={255} value={form.name} onChange={event => update("name", event.target.value)} /></label>
        <Description value={form.description} change={value => update("description", value)} />
        <div className="fields-two">
          <label>Category<select value={form.categoryId} onChange={event => update("categoryId", event.target.value)}><option value="">Uncategorized</option>
            {categories.filter(category => category.active || String(category.id) === form.categoryId).map(category => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
          <label>Brand<input maxLength={150} value={form.brandName} onChange={event => update("brandName", event.target.value)} /></label>
        </div>
      </div></Panel>
      <Panel title="Pricing"><div className="admin-fields fields-two">
        <label>Price ({currencyLabel})<input required type="number" min="0" max="9999999999.99" step=".01" value={form.price} onChange={event => update("price", event.target.value)} /></label>
        <label>Compare At Price ({currencyLabel})<input type="number" min="0" max="9999999999.99" step=".01" value={form.compareAtPrice} onChange={event => update("compareAtPrice", event.target.value)} /></label>
      </div></Panel>
      <Panel title="Media & Gallery"><div className="admin-fields">
        <label className="dropzone" onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); void addFiles(Array.from(event.dataTransfer.files)); }}>
          <input type="file" accept="image/png,image/jpeg" multiple onChange={event => { void addFiles(Array.from(event.target.files ?? [])); event.target.value = ""; }} />
          <span className="upload-symbol" aria-hidden="true"><Icon name="upload" size={30} /></span><strong>{reading ? "Reading images…" : "Drag & drop product images here"}</strong><small>PNG or JPG, up to 10 MB each</small>
        </label>
        {gallery.length > 0 && <div className="admin-gallery">{gallery.map((image, index) => <div className="gallery-item" key={image.id ?? index}>
          <Thumb src={image.source} size={78} /><small>{index ? "Alt " + index : "Main"}</small>
          <button type="button" className="admin-link" onClick={() => setGallery(current => current.filter((_, position) => position !== index))}>Remove</button>
          {index > 0 && <button type="button" className="admin-link" onClick={() => setGallery(current => [current[index], ...current.filter((_, position) => position !== index)])}>Make main</button>}
        </div>)}</div>}
        <div className="spread"><input aria-label="Hosted image URL" placeholder="https://… (optional image URL)" maxLength={2048} value={url} onChange={event => setUrl(event.target.value)} />
          <button className="admin-secondary-button" type="button" disabled={!url || reading} onClick={addUrl}>Add URL</button></div>
      </div></Panel>
      <section className="admin-panel"><div className="spread"><h2>Variants</h2><button className="admin-link" type="button" disabled={variants.length >= 100} onClick={() => setVariants(current => [...current, emptyVariant])}>+ Add Variant</button></div>
        <div className="variants-scroll admin-fields"><div className="variant-grid">{Object.values(variantLabels).map(label => <span key={label}>{label}</span>)}</div>
          {variants.map((row, index) => <div className="variant-grid" key={index}>{(Object.keys(variantLabels) as (keyof Variant)[]).map(key =>
            <input key={key} aria-label={variantLabels[key] + " for variant " + (index + 1)} maxLength={100}
              type={key === "price" ? "number" : "text"} min={key === "price" ? "0" : undefined} step={key === "price" ? ".01" : undefined}
              value={row[key]} onChange={event => setVariants(current => current.map((variant, position) => position === index ? { ...variant, [key]: event.target.value } : variant))} />)}
            <button type="button" className="admin-icon danger" aria-label={"Remove variant " + (index + 1)} onClick={() => setVariants(current => current.filter((_, position) => position !== index))}>×</button>
          </div>)}
        </div>
      </section>
    </div><div className="admin-stack">
      <Panel title="Product Status"><div className="admin-fields">
        {statuses.map(([status, description]) => <label className="inline-field" key={status}><input name="status" type="radio" checked={form.status === status} onChange={() => update("status", status)} />
          <span><strong>{titleCase(status)}</strong><small className="product-status-description">{description}</small></span></label>)}
        <label className="inline-field"><input type="checkbox" checked={form.featured} onChange={event => update("featured", event.target.checked)} /> Featured product</label>
      </div></Panel>
      <Panel title="Inventory"><div className="admin-fields">
        <label>Master SKU<input required maxLength={100} value={form.sku} onChange={event => update("sku", event.target.value)} /></label>
        <label>Stock Quantity<input required type="number" min={reserved} max={2147483647} step="1" value={form.stockQuantity} onChange={event => update("stockQuantity", event.target.value)} /></label>
      </div></Panel>
      <Panel title="Search Engine Preview"><div className="admin-fields">
        <label>Meta Title<input maxLength={255} value={form.metaTitle} onChange={event => update("metaTitle", event.target.value)} /></label>
        <label>URL Slug<input maxLength={180} value={form.slug} placeholder={slug || "product-name"} onChange={event => update("slug", event.target.value)} /></label>
        {slug && <small>Preview /products/{slug}</small>}
      </div></Panel>
    </div></div>
  </form></main>;
}


function Description({ value, change }: { value: string; change: (value: string) => void }) {
  const field = useRef<HTMLTextAreaElement>(null);

  function insert(mark: string) {
    const input = field.current;
    if (!input) return;
    const { selectionStart: start, selectionEnd: end } = input;
    const selected = value.slice(start, end);
    const text = mark === "link" ? "[" + (selected || "link text") + "](https://example.com)"
      : mark === "- " ? (selected || "List item").split("\n").map(line => "- " + line).join("\n")
      : mark + (selected || "text") + mark;
    const next = value.slice(0, start) + text + value.slice(end);
    if (next.length > 10000) return;
    change(next);
    requestAnimationFrame(() => {
      input.focus();
      input.setSelectionRange(start, start + text.length);
    });
  }

  return <div className="description-field"><label htmlFor="product-description">Description</label>
    <div className="description-editor"><div className="description-toolbar" role="group" aria-label="Description formatting">
      {[["**", "Bold", "B"], ["_", "Italic", "𝘐"], ["- ", "Bulleted list", "☷"], ["link", "Link", "↗"]].map(([mark, label, icon]) =>
        <button type="button" key={mark} className="admin-icon" aria-label={label} title={label} onClick={() => insert(mark)}>{icon}</button>)}
    </div>
    <textarea id="product-description" maxLength={10000} value={value} onChange={event => change(event.target.value)} />
    </div></div>;
}
