"use client";
import { Breadcrumb, useStore } from "../StoreShell";
import { money } from "../data";

export default function ShippingPage() {
  const { settings } = useStore();
  return <main className="sf-wrap sf-page"><Breadcrumb><strong>Shipping & Delivery</strong></Breadcrumb><h1>Shipping & Delivery</h1>
    {settings && <div className="sf-panel"><p>{settings.shippingRate != null ? "Standard delivery costs " + money(settings.shippingRate, settings.currency) + "." : "Delivery options are shown at checkout."}</p>
    {settings.freeShippingMinimum != null && <p>Shipping is free on orders of {money(settings.freeShippingMinimum, settings.currency)} or more.</p>}
    {settings.email && <p>For shipping or returns questions, <a className="sf-accent" href={"mailto:" + settings.email}>contact the store</a>.</p>}</div>}
  </main>;
}
