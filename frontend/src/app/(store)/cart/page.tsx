"use client";

import Link from "next/link";
import { useState } from "react";
import { Collections } from "../Home";
import { Quantity, TotalsList } from "../Commerce";
import { Breadcrumb, ErrorLine, Photo, RequireAccount, useStore } from "../StoreShell";
import { money, productUrl } from "../data";

export default function CartPage() { return <RequireAccount><Cart/></RequireAccount>; }

function Cart() {
  const { cart, changeCart } = useStore();
  const [promo, setPromo] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function change(path: string, method: string, body?: unknown) {
    setBusy(true); setError("");
    try { await changeCart(path,method,body); if(path==="/coupon") setPromo(""); }
    catch (failure) { setError((failure as Error).message); } finally { setBusy(false); }
  }
  return <main><div className="sf-wrap sf-page">
    <Breadcrumb><strong>Cart</strong></Breadcrumb><h1>Shopping Cart {cart && <small>({cart.lines.length} items)</small>}</h1>
    <ErrorLine error={error}/>
    {cart && cart.lines.length ? <div className="sf-cart">
      <div><div className="sf-cart-row sf-cart-head"><span>Product</span><span>Quantity</span><span>Total</span></div>
        {cart.lines.map(item => <div className="sf-cart-row" key={item.id}>
          <div className="sf-cart-item"><Link href={productUrl(item.slug)} tabIndex={-1}><Photo src={item.image}/></Link><div>
            <Link className="sf-product-name" href={productUrl(item.slug)}>{item.name}</Link>
            {item.variant && <small>Style: {item.variant}</small>}
            {(!item.purchasable || item.quantity>item.available || item.currency!==cart.currency) && <small className="sf-red">Unavailable</small>}
            <button className="sf-text-button sf-accent" disabled={busy} onClick={()=>void change("/items/"+item.id,"DELETE")}>Remove</button>
          </div></div>
          <fieldset disabled={busy || !item.purchasable}><Quantity value={item.quantity} max={item.available} change={quantity=>void change("/items/"+item.id,"PUT",{quantity})}/></fieldset>
          <b>{money(item.price*item.quantity,item.currency)}</b>
        </div>)}
      </div>
      <aside className="sf-panel sf-summary"><h2>Order Summary</h2><TotalsList totals={cart} shippingKnown={false}/>
        <form onSubmit={event=>{event.preventDefault(); void change("/coupon","PUT",{code:promo});}}><label htmlFor="promo-code">Promo Code</label>
          <div className="sf-actions"><input id="promo-code" maxLength={50} placeholder="Enter code" value={promo} onChange={event=>setPromo(event.target.value)}/>
            <button className="sf-btn small" disabled={busy || !promo.trim()}>Apply</button></div></form>
        {cart.coupon && <p className={cart.invalidCoupon ? "sf-red" : "sf-green"}>{cart.coupon} {cart.invalidCoupon ? "is no longer valid" : "applied"} <button className="sf-text-button" disabled={busy} onClick={()=>void change("/coupon","PUT",{code:""})}>Remove</button></p>}
        {cart.available && !cart.invalidCoupon ? <Link className="sf-btn rust" href="/checkout">Proceed to Checkout</Link>
          : <p className="sf-red">Review the items and promo code before checking out.</p>}
        <Link className="sf-under" href="/shop">Continue Shopping</Link>
      </aside>
    </div> : <div className="sf-empty"><p>{cart ? "Your bag is empty." : "Loading your bag…"}</p><Link className="sf-btn" href="/shop">Continue Shopping</Link></div>}
  </div><div className="sf-recommend"><div className="sf-wrap"><Collections title="You May Also Like" path="/products?sort=popular&size=4" link="/shop"/></div></div></main>;
}
