"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { Quantity } from "../../Commerce";
import { useEffect, useState } from "react";
import { Breadcrumb, ErrorLine, Photo, ProductGrid, WishlistButton, useStore } from "../../StoreShell";
import { day, money, productUrl, StoreError, useData, type Product } from "../../data";

type Detail = { product: Product; info: {description?: string; brand?: string; weight?: number}; images: {url: string; alt?: string}[];
  variants: {id: number; color?: string; size?: string; price?: number}[];
  reviews: {rating: number; title?: string; comment?: string; createdAt: string; author: string}[];
  ratings: {rating: number; count: number}[]; related: Product[] };

export default function ProductPage() {
  const { slug } = useParams<{slug: string}>();
  const source = useData<Detail>("/products/" + encodeURIComponent(slug));
  return <main className="sf-wrap sf-page"><ErrorLine error={source.error}/>
    {source.data ? <Details key={slug} detail={source.data}/> : !source.error && <p>Loading product…</p>}</main>;
}

function Details({ detail }: { detail: Detail }) {
  const shop = useStore();
  const router = useRouter();
  const { product, info, images, variants, reviews, ratings } = detail;
  const [photo, setPhoto] = useState(0);
  const [selection, setSelection] = useState<number | undefined>(variants[0]?.id);
  const [quantity, setQuantity] = useState(1);
  const [tab, setTab] = useState("Description");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [recent] = useState<Product[]>(() => {
    try { return (JSON.parse(localStorage.getItem("haven-recent-products") ?? "[]") as Product[]).filter(item => item.id !== product.id).slice(0,4); }
    catch { return []; }
  });
  useEffect(() => {
    try { localStorage.setItem("haven-recent-products", JSON.stringify([product, ...recent].slice(0,5))); } catch {}
  }, [product,recent]);
  const variant = variants.find(item => item.id === selection);
  const colors = [...new Set(variants.flatMap(item => item.color ? [item.color] : []))];
  const sizes = variants.filter(item => item.color === variant?.color);
  const count = ratings.reduce((sum,row)=>sum+row.count,0);
  const average = count ? ratings.reduce((sum,row)=>sum+row.count*row.rating,0)/count : 0;
  async function add(now: boolean) {
    setBusy(true); setError(""); setMessage("");
    try {
      await shop.changeCart("/items","POST",{productId:product.id,variantId:selection,quantity});
      if (now) router.push("/checkout");
      else setMessage("Added to bag.");
    } catch (failure) {
      if (!(failure instanceof StoreError && failure.status === 401)) setError((failure as Error).message);
    } finally { setBusy(false); }
  }
  return <>
    <Breadcrumb><Link href="/shop">Shop</Link><span>/</span>{product.categorySlug && <><Link href={"/shop?category="+encodeURIComponent(product.categorySlug)}>{product.category}</Link><span>/</span></>}<strong>{product.name}</strong></Breadcrumb>
    <div className="sf-product-detail">
      <div><Photo className="sf-main-photo" src={images[photo]?.url ?? product.image} alt={images[photo]?.alt ?? product.name} priority/>
        {images.length > 1 && <div className="sf-gallery">{images.map((image,index) => <button key={index} type="button" aria-label={"View image "+(index+1)}
          aria-pressed={photo===index} onClick={()=>setPhoto(index)}><Photo src={image.url}/></button>)}</div>}</div>
      <div className="sf-product-buy"><span className="sf-upper sf-accent">{product.category}</span><h1>{product.name}</h1>
        <a href="#reviews" className="sf-review-link"><Stars rating={average}/> {count ? "(" + count + " reviews)" : "No reviews yet"}</a>
        <div className="sf-big-price"><b>{money(variant?.price ?? product.price,product.currency)}</b>{product.compareAt != null && product.compareAt > (variant?.price ?? product.price) && <s>{money(product.compareAt, product.currency)}</s>}</div>
        {info.description && <p className="sf-product-description">{info.description}</p>}
        {!!colors.length && <fieldset><legend>Color{variant?.color && ": " + variant.color}</legend><div className="sf-option-row">{colors.map(color =>
          <button key={color} type="button" aria-pressed={variant?.color === color} onClick={()=>setSelection(variants.find(item=>item.color===color)?.id)}>{color}</button>)}</div></fieldset>}
        {!!sizes.length && sizes.some(item=>item.size) && <fieldset><legend>Size / Set</legend><div className="sf-option-row">{sizes.map(item =>
          <button key={item.id} type="button" aria-pressed={item.id===selection} onClick={()=>setSelection(item.id)}>{item.size ?? "Standard"}</button>)}</div></fieldset>}
        <div className="sf-actions"><div><span className="sf-upper">Quantity</span><Quantity value={quantity} max={product.available} change={setQuantity}/></div>
          <span className={product.available ? "sf-green" : "sf-red"}>{product.available ? "● In Stock" : "Out of Stock"}</span></div>
        <div className="sf-buy-actions">
          <button className="sf-btn" disabled={busy || !product.available} onClick={()=>void add(false)}>Add to Cart</button>
          <button className="sf-btn outline" disabled={busy || !product.available} onClick={()=>void add(true)}>Buy It Now</button>
        </div>
        <WishlistButton product={product} labeled/><ErrorLine error={error}/>{message && <p role="status" className="sf-green">{message}</p>}
      </div>
    </div>
    <section className="sf-section"><div className="sf-tabs">{["Description","Specifications","Shipping & Returns"].map(label =>
      <button key={label} aria-pressed={label===tab} onClick={()=>setTab(label)}>{label}</button>)}</div>
      <div className="sf-tab-description">{tab==="Description" ? <p>{info.description || "Product description coming soon."}</p>
        : tab==="Specifications" ? <dl>{info.brand && <><dt>Brand</dt><dd>{info.brand}</dd></>}{info.weight != null && <><dt>Weight</dt><dd>{info.weight}</dd></>}
          {!info.brand && info.weight == null && <div>No additional specifications.</div>}</dl>
        : <Link className="sf-accent" href="/shipping">See shipping information and contact details</Link>}</div>
    </section>
    <section className="sf-section" id="reviews"><h2>Customer Reviews</h2>
      {count ? <><div className="sf-ratings"><div><strong>{average.toFixed(1)}</strong><br/><Stars rating={average}/><p>Based on {count} reviews</p></div>
        <div>{[5,4,3,2,1].map(rating=>{const percent=Math.round(100*(ratings.find(item=>item.rating===rating)?.count ?? 0)/count);
          return <div className="sf-rating-row" key={rating}><span>{rating} stars</span><meter aria-label={rating+"-star share"} min={0} max={100} value={percent}/><span>{percent}%</span></div>;})}</div></div>
      {reviews.map((review,index)=><article className="sf-review" key={index}><div className="sf-spread"><div><b>{review.author}</b><br/><Stars rating={review.rating}/></div><span>{day(review.createdAt)}</span></div>
        {review.title && <strong>{review.title}</strong>}<p>{review.comment}</p></article>)}</> : <p>No reviews have been published for this product.</p>}
    </section>
    {!!detail.related.length && <section className="sf-section"><h2>Related Products</h2><ProductGrid products={detail.related}/></section>}
    {!!recent.length && <section className="sf-section"><h2>Recently Viewed</h2><div className="sf-products columns-4">{recent.map(item =>
      <Link key={item.id} href={productUrl(item.slug)}><Photo className="sf-recent-photo" src={item.image}/><span>{item.name}</span></Link>)}</div></section>}
  </>;
}

function Stars({ rating }: { rating: number }) { return <span className="sf-stars" aria-label={rating.toFixed(1)+" out of 5"}>{"★".repeat(Math.round(rating))}{"☆".repeat(5-Math.round(rating))}</span>; }
