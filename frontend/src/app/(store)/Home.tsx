"use client";

import Link from "next/link";
import { useState } from "react";
import { ErrorLine, Photo, ProductGrid, useStore } from "./StoreShell";
import { request, useData, type Catalog, type Category } from "./data";

export function Collections({ title, path, link, label = "View All", always = false }: { title: string; path: string; link: string; label?: string; always?: boolean }) {
  const { data, error } = useData<Catalog>(path);
  if (!always && !data?.products.length) return null;
  return <section className="sf-section"><div className="sf-spread"><h2>{title}</h2><Link className="sf-accent sf-upper" href={link}>{label}</Link></div>
    <ErrorLine error={error}/>{data && <ProductGrid products={data.products}/>}
    {data?.products.length === 0 && <p className="sf-empty">Products will appear here as they become available.</p>}
  </section>;
}

export function CategoryTiles({ limit }: { limit?: number }) {
  const source = useData<Category[]>("/categories");
  const rows = (source.data ?? []).filter(category => !category.parentId);
  return <><ErrorLine error={source.error}/><div className="sf-category-grid">{rows.slice(0, limit).map(category =>
    <Link className="sf-category" key={category.id} href={"/shop?category=" + encodeURIComponent(category.slug)}>
      <Photo src={category.image}/><span>{category.name} <b>→</b></span>
    </Link>)}</div>{!source.error && source.data && !rows.length && <p className="sf-empty">Categories will appear here when published.</p>}</>;
}

export default function Home() {
  const { settings } = useStore();
  const content = settings?.content;
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  return <main>
    <div className="sf-wrap">
      <section className="sf-hero">
        <div><h1>{content?.heroTitle} <em>{content?.heroAccent}</em></h1><p>{content?.heroDescription}</p>
          <div className="sf-actions"><Link className="sf-btn" href="/shop">Shop Collection</Link><Link className="sf-btn outline" href="/about">Our Story</Link></div></div>
        <Photo src={content?.heroImage} className="sf-hero-image" priority/>
      </section>
      <Collections title="Featured Picks" path="/products?collection=featured&size=4" link="/shop?collection=featured" always/>
      <Collections title="Just In" path="/products?sort=new&size=4" link="/shop?sort=new" label="Discover More"/>
      <section className="sf-section"><CategoryTiles limit={3}/></section>
    </div>
    {content?.campaignTitle && <section className="sf-campaign"><Photo src={content.campaignImage}/>
      <div><h2>{content.campaignTitle}</h2><p>{content.campaignDescription}</p><Link className="sf-btn light" href="/shop?sort=new">Explore Now</Link></div></section>}
    <div className="sf-wrap"><Collections title="Most Loved" path="/products?sort=popular&size=4" link="/shop?sort=popular" label="Discover All"/></div>
    {content?.storyTitle && <section className="sf-story"><div className="sf-wrap">
      <Photo src={content.storyImage}/><div><span className="sf-upper sf-accent">Our Philosophy</span><h2>{content.storyTitle}</h2><p>{content.storyDescription}</p>
        <Link className="sf-btn outline" href="/about">Learn More</Link></div></div></section>}
    <section className="sf-newsletter sf-wrap"><h2>Stay in the Loop</h2><p>Sign up to hear about new collections and store updates.</p>
      <form className="sf-actions" onSubmit={async event => {
        event.preventDefault(); setBusy(true); setError(""); setMessage("");
        try { const data = await request<{message: string}>("/newsletter", { method: "POST", body: JSON.stringify({ email }) }); setMessage(data.message); setEmail(""); }
        catch (failure) { setError((failure as Error).message); } finally { setBusy(false); }
      }}><input aria-label="Email address" placeholder="Enter your email address" type="email" maxLength={320} required value={email} onChange={event => setEmail(event.target.value)}/>
        <button className="sf-btn" disabled={busy}>Subscribe</button></form><ErrorLine error={error}/>{message && <p role="status">{message}</p>}
    </section>
  </main>;
}
