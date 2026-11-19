"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Pager } from "../Commerce";
import { Suspense } from "react";
import { Breadcrumb, ErrorLine, ProductGrid, useStore } from "../StoreShell";
import { money, useData, type Catalog, type Category } from "../data";

export default function ShopPage() {
  return <Suspense fallback={<main className="sf-wrap sf-page">Loading products…</main>}><Shop/></Suspense>;
}

function Shop() {
  const params = useSearchParams();
  const router = useRouter();
  const { settings } = useStore();
  const categories = useData<Category[]>("/categories");
  const data = useData<Catalog>("/products?" + params.toString());
  const query = params.get("q");
  const selected = params.getAll("category");
  const stock = params.get("stock") ?? "";
  const priceCurrency = settings?.currency;
  const total = data.data?.total ?? 0;
  const page = data.data?.page ?? 1;
  const pages = Math.ceil(total / (data.data?.size ?? 9));

  function apply(name: string, value: string, multiple = false) {
    const next = new URLSearchParams(params);
    if (name !== "page") next.delete("page");
    if (!multiple) { if (value) next.set(name, value); else next.delete(name); }
    else if (next.getAll(name).includes(value)) next.delete(name, value);
    else next.append(name, value);
    router.replace("/shop" + (next.size ? "?" + next.toString() : ""), { scroll: name === "page" });
  }

  return <main className="sf-wrap sf-page">
    <Breadcrumb><strong>Shop</strong></Breadcrumb>
    <h1>{query ? "Search results for “" + query + "”" : "All Products"} {data.data && <small>({total} products)</small>}</h1>
    <div className="sf-shop">
      <aside className="sf-filters" aria-label="Product filters">
        <fieldset><legend>Category</legend>
          {categories.data?.map(cat => <label key={cat.id} className="sf-choice"><input type="checkbox" checked={selected.includes(cat.slug)}
            onChange={() => apply("category", cat.slug, true)}/><span>{cat.name}</span><small>{cat.count}</small></label>)}
        </fieldset>
        <fieldset><legend>Price Range</legend><form className="sf-price-filter" key={params.get("min") + "|" + params.get("max")} onSubmit={event => {
          event.preventDefault(); const values = new FormData(event.currentTarget); const next = new URLSearchParams(params); next.delete("page");
          for (const key of ["min", "max"]) { const value = String(values.get(key)); if (value) next.set(key,value); else next.delete(key); }
          router.replace("/shop?" + next.toString(), {scroll:false});
        }}>
          <input name="min" type="number" min="0" step=".01" aria-label="Minimum price" placeholder="Min" defaultValue={params.get("min") ?? ""}/>
          <input name="max" type="number" min="0" step=".01" aria-label="Maximum price" placeholder={priceCurrency && data.data?.priceCeiling ? money(data.data.priceCeiling, priceCurrency) : "Max"} defaultValue={params.get("max") ?? ""}/>
          <button className="sf-btn small" type="submit">Apply</button>
        </form></fieldset>
        <fieldset><legend>Availability</legend>{[["in","In Stock"],["out","Out of Stock"]].map(([key,label]) => <label className="sf-choice" key={key}>
          <input type="checkbox" checked={stock === key} onChange={() => apply("stock", stock === key ? "" : key)}/>{label}</label>)}</fieldset>
        <fieldset><legend>Sort by</legend><select aria-label="Sort by" value={params.get("sort") ?? "featured"} onChange={event => apply("sort",event.target.value)}>
          <option value="featured">Featured</option><option value="new">Newest</option><option value="popular">Best Sellers</option>
          <option value="price-low">Price: Low to High</option><option value="price-high">Price: High to Low</option></select></fieldset>
        {!!params.size && <Link className="sf-accent" href="/shop">Clear filters</Link>}
      </aside>
      <div><ErrorLine error={data.error || categories.error}/>
        {!data.data && !data.error && <p className="sf-empty">Loading products…</p>}
        {data.data && (data.data.products.length ? <ProductGrid columns={3} products={data.data.products}/> : <p className="sf-empty">No products match these filters.</p>)}
        {pages > 1 && <Pager page={page} pages={pages} setPage={page => apply("page", String(page))}/>}
      </div>
    </div>
  </main>;
}
