"use client";

/* eslint-disable @next/next/no-img-element */
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { API, imageUrl, money, productUrl, request, StoreError, useData, type Catalog, type Category, type Product, type Profile, type Quote, type Settings } from "./data";

const paths: Record<string, ReactNode> = {
  search: <><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4 4"/></>,
  heart: <path d="M12 20 4 12a4.7 4.7 0 0 1 6.7-6.7L12 6.6l1.3-1.3A4.7 4.7 0 0 1 20 12Z"/>,
  bag: <><rect x="4" y="4" width="16" height="17" rx="2"/><path d="M4 8h16M9 11a3 3 0 0 0 6 0"/></>,
  account: <><circle cx="12" cy="7" r="3.4"/><path d="M5.5 21v-2a5 5 0 0 1 5-5h3a5 5 0 0 1 5 5v2"/></>,
  order: <path d="m12 2 9 5v10l-9 5-9-5V7Zm0 10L3 7m9 5 9-5m-9 5v10M7.5 4.5l9 5"/>,
  pin: <><path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 0 1 14 0Z"/><circle cx="12" cy="10" r="2.5"/></>,
  grid: <><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18m-12 0v11"/></>,
  check: <path d="m5 12 4 4L19 6"/>, close: <path d="m6 6 12 12M18 6 6 18"/>,
  exit: <path d="M9 4H4v16h5m2-8h10m-4-4 4 4-4 4"/>,
};

export function Icon({ name, size = 20 }: { name: string; size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" fill="none" aria-hidden="true">{paths[name]}</svg>;
}

export function Photo({ src, alt = "", className = "", priority = false }: { src?: string; alt?: string; className?: string; priority?: boolean }) {
  const [broken, setBroken] = useState<string>();
  const url = imageUrl(src);
  if (!url || url === broken) return <span className={"sf-placeholder " + className} role={alt ? "img" : undefined} aria-label={alt || undefined}><Icon name="order" size={30}/></span>;
  return <img className={className} src={url} alt={alt} loading={priority ? "eager" : "lazy"} onError={() => setBroken(url)}/>;
}

const Context = createContext<{
  settings?: Settings; profile?: Profile; accountError?: StoreError; wishlist: Product[]; cart?: Quote;
  changeCart: (path: string, method: string, data?: unknown) => Promise<Quote>;
  wish: (product: Product) => Promise<void>; refreshProfile: () => void; refreshCart: () => void; login: () => void;
} | null>(null);

export function useStore() {
  const value = useContext(Context);
  if (!value) throw new Error("Storefront layout is required.");
  return value;
}

export default function StoreShell({ children }: { children: ReactNode }) {
  const settings = useData<Settings>("/settings");
  const account = useData<Profile>("/account");
  const cart = useData<Quote>(account.data ? "/cart" : null);
  const saved = useData<Product[]>(account.data ? "/wishlist" : null);
  const [cartChange, setCart] = useState<Quote>();
  const [wishChange, setWish] = useState<Product[]>();
  const pathname = usePathname();
  const router = useRouter();
  const [search, setSearch] = useState(false);

  function login() {
    router.push("/login?returnTo=" + encodeURIComponent(pathname + window.location.search));
  }

  async function changeCart(path: string, method: string, data?: unknown) {
    try {
      const result = await request<Quote>("/cart" + path, { method, body: data === undefined ? undefined : JSON.stringify(data) });
      setCart(result);
      return result;
    } catch (error) {
      if (error instanceof StoreError && error.status === 401) login();
      throw error;
    }
  }

  async function wish(product: Product) {
    if (!account.data) { login(); return; }
    const already = (wishChange ?? saved.data ?? []).some(item => item.id === product.id);
    setWish(await request<Product[]>("/wishlist/" + product.id, { method: "PUT", body: JSON.stringify({ saved: !already }) }));
    account.refresh();
  }

  const cartData = cartChange ?? cart.data;
  const items = cartData?.lines.length ?? 0;
  const minimal = pathname === "/checkout" || pathname.startsWith("/order-success/");
  const store = settings.data;

  return <Context.Provider value={{ settings: store, profile: account.data, accountError: account.error,
    wishlist: wishChange ?? saved.data ?? [], cart: cartData, changeCart, wish, refreshProfile: account.refresh,
    refreshCart: () => { setCart(undefined); cart.refresh(); }, login }}>
    <div className="sf">
      {(store?.freeShippingMinimum != null || store?.content.announcement) && <div className="sf-announcement">
        {store.content.announcement || "Free shipping on orders over " + money(store.freeShippingMinimum!, store.currency)}</div>}
      <header className="sf-header"><div className="sf-wrap">
        <Link href="/" className="sf-brand" aria-label={store?.name ?? "Store home"}>{store?.logo ? <Photo src={store.logo} alt={store.name}/> : store?.name}</Link>
        {!minimal && <nav aria-label="Store navigation">{[["/", "Home"], ["/shop", "Shop"], ["/categories", "Categories"], ["/about", "About"]].map(([path, label]) =>
          <Link key={path} href={path} aria-current={pathname === path || (path === "/shop" && pathname.startsWith("/products/")) ? "page" : undefined}>{label}</Link>)}</nav>}
        <div className="sf-header-actions">
          {!minimal && <><button className="sf-icon" aria-label="Search products" onClick={() => setSearch(true)}><Icon name="search"/></button>
            <Link className="sf-icon" href="/wishlist" aria-label="Wishlist"><Icon name="heart"/></Link></>}
          <Link className="sf-icon" href="/cart" aria-label={"Shopping cart, " + items + " items"}><Icon name="bag"/>{items > 0 && <span className="sf-count">{items}</span>}</Link>
          {!minimal && <Link className="sf-icon" href="/account" aria-label="My account"><Icon name="account"/></Link>}
        </div>
      </div></header>
      {children}
      <Footer store={store}/>
      {search && <Search close={() => setSearch(false)}/>}
    </div>
  </Context.Provider>;
}

function Footer({ store }: { store?: Settings }) {
  return <footer className="sf-footer"><div className="sf-wrap">
    <div className="sf-footer-columns"><div><Link href="/" className="sf-brand">{store?.name}</Link><p>{store?.content.storyDescription}</p></div>
      <div><h3>Shop</h3><Link href="/shop">All Products</Link><Link href="/shop?sort=new">New Arrivals</Link><Link href="/shop?sort=popular">Best Sellers</Link><Link href="/shop?collection=sale">Sale</Link></div>
      <div><h3>Company</h3><Link href="/about">Our Story</Link><Link href="/categories">Categories</Link></div>
      <div><h3>Support</h3>{store?.email && <a href={"mailto:" + store.email}>Contact</a>}<Link href="/account/orders">My Orders</Link><Link href="/shipping">Shipping & Delivery</Link></div>
      {store?.phone && <div><h3>Connect</h3><a href={"tel:" + store.phone}>{store.phone}</a></div>}
    </div><div className="sf-copyright">© {new Date().getFullYear()} {store?.name}. All rights reserved.</div>
  </div></footer>;
}

function Search({ close }: { close: () => void }) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [value, setValue] = useState("");
  const [recent, setRecent] = useState<string[]>(() => {
    try { return JSON.parse(localStorage.getItem("haven-recent-searches") ?? "[]"); } catch { return []; }
  });
  const cats = useData<Category[]>("/categories");
  const catalog = useData<Catalog>("/products?size=4&q=" + encodeURIComponent(value.trim()));
  const router = useRouter();
  useEffect(() => { dialog.current?.showModal(); }, []);
  function remember() {
    const term = value.trim();
    if (!term) return;
    const next = [term, ...recent.filter(item => item !== term)].slice(0, 5);
    setRecent(next);
    try { localStorage.setItem("haven-recent-searches", JSON.stringify(next)); } catch {}
  }
  return <dialog ref={dialog} className="sf-dialog sf-search" aria-label="Search products" onCancel={event => { event.preventDefault(); close(); }}>
    <form className="sf-searchbar" onSubmit={event => { event.preventDefault(); remember(); router.push("/shop?q=" + encodeURIComponent(value.trim())); close(); }}>
      <button className="sf-icon" aria-label="View search results"><Icon name="search"/></button>
      <input autoFocus type="search" value={value} maxLength={150} placeholder="Search products…" aria-label="Search products" onChange={event => setValue(event.target.value)}/>
      <button className="sf-icon sf-circle" type="button" aria-label="Close search" onClick={close}><Icon name="close" size={15}/></button>
    </form>
    {!value && recent.length > 0 && <section><h3>Recent searches</h3><div className="sf-chips">{recent.map(item => <button type="button" key={item} onClick={() => setValue(item)}>{item}</button>)}</div></section>}
    {!value && !!cats.data?.length && <section><h3>Popular categories</h3><div className="sf-search-categories">{[...cats.data].sort((a,b) => b.count-a.count).slice(0,3).map(cat =>
      <Link key={cat.id} href={"/shop?category=" + encodeURIComponent(cat.slug)} onClick={close}><Photo src={cat.image}/><span>{cat.name}</span></Link>)}</div></section>}
    <section><h3>{value ? "Search results" : "Suggested products"}</h3><ErrorLine error={catalog.error}/>
      {catalog.data?.products.map(product => <div className="sf-search-row" key={product.id}><Photo src={product.image}/>
        <Link href={productUrl(product.slug)} onClick={() => { remember(); close(); }}><small>{product.category}</small><strong>{product.name}</strong></Link>
        <b>{money(product.price, product.currency)}</b><QuickAdd product={product} compact onNavigate={close}/></div>)}
      {catalog.data?.products.length === 0 && <p>No products found.</p>}
    </section>
  </dialog>;
}

export function QuickAdd({ product, compact, onNavigate }: { product: Product; compact?: boolean; onNavigate?: () => void }) {
  const shop = useStore();
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState("");
  if (product.hasVariants && product.available > 0) return <Link className={"sf-btn " + (compact ? "small" : "")} href={productUrl(product.slug)} onClick={onNavigate}>Choose options</Link>;
  return <><button className={"sf-btn " + (compact ? "small" : "")} type="button" disabled={busy || !product.available} onClick={async () => {
    setBusy(true); setNote("");
    try { await shop.changeCart("/items", "POST", { productId: product.id, quantity: 1 }); setNote("Added to bag"); }
    catch (error) { if (!(error instanceof StoreError && error.status === 401)) setNote((error as Error).message); }
    finally { setBusy(false); }
  }}>{product.available ? (compact ? "+ Add" : "+ Quick Add") : "Out of stock"}</button>{note && <small className="sf-action-note" role="status">{note}</small>}</>;
}

export function WishlistButton({ product, labeled = false }: { product: Product; labeled?: boolean }) {
  const shop = useStore();
  const saved = shop.wishlist.some(item => item.id === product.id);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  return <><button className={labeled ? "sf-text-button" : "sf-heart sf-circle"} aria-label={saved ? "Remove from wishlist" : "Add to wishlist"}
    aria-pressed={saved} disabled={busy} type="button" onClick={async () => {
      setBusy(true); setError("");
      try { await shop.wish(product); } catch (failure) { setError((failure as Error).message); } finally { setBusy(false); }
    }}><Icon name={saved && !labeled ? "close" : "heart"} size={18}/>{labeled && (saved ? "Saved to Wishlist" : "Add to Wishlist")}</button>
    {error && <small className="sf-action-note" role="alert">{error}</small>}</>;
}

export function ProductGrid({ products, columns = 4, wishlist = false }: { products: Product[]; columns?: number; wishlist?: boolean }) {
  return <div className={"sf-products columns-" + columns}>{products.map(product => <article key={product.id} className="sf-product">
    <div className="sf-product-image"><Link href={productUrl(product.slug)} tabIndex={-1} aria-hidden="true"><Photo src={product.image}/></Link>
      {!!product.compareAt && product.compareAt > product.price && <span className="sf-sale">−{Math.round(100 * (1-product.price/product.compareAt))}%</span>}
      <WishlistButton product={product}/>
      {!wishlist && <div className="sf-quick"><QuickAdd product={product}/></div>}
    </div>
    <small>{product.category}</small><Link className="sf-product-name" href={productUrl(product.slug)}>{product.name}</Link>
    <div className="sf-price"><b>{money(product.price, product.currency)}</b>{!!product.compareAt && product.compareAt > product.price && <s>{money(product.compareAt, product.currency)}</s>}{wishlist && <span className={product.available ? "sf-green" : "sf-red"}>{product.available ? "In Stock" : "Out of Stock"}</span>}</div>
    {wishlist && <div className="sf-wishlist-add"><QuickAdd product={product}/></div>}
  </article>)}</div>;
}

export function ErrorLine({ error }: { error?: Error | string }) {
  return error ? <p className="sf-error" role="alert">{typeof error === "string" ? error : error.message}</p> : null;
}

export function Breadcrumb({ children }: { children: ReactNode }) {
  return <div className="sf-crumb"><Link href="/">Home</Link><span>/</span>{children}</div>;
}

export function RequireAccount({ children }: { children: ReactNode }) {
  const { profile, accountError, login } = useStore();
  if (profile) return children;
  return <section className="sf-wrap sf-page sf-empty">
    {accountError?.status === 401 || accountError?.status === 403 ? <><h1>Sign in to continue</h1><button className="sf-btn" onClick={login}>Sign In</button></>
      : accountError ? <ErrorLine error={accountError}/> : <p>Loading your account…</p>}
  </section>;
}

export async function signOut() {
  const response = await fetch(API + "/api/auth/logout", { method: "POST", credentials: "include" });
  if (!response.ok) throw new Error("Could not sign out. Try again.");
}
