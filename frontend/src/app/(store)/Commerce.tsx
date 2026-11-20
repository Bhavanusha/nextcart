"use client";

import Link from "next/link";
import { Photo } from "./StoreShell";
import { money, productUrl, type AddressInput, type Line, type Totals } from "./data";

export function Quantity({ value,max,change }: { value:number; max:number; change:(quantity:number)=>void }) {
  return <div className="sf-quantity"><button type="button" aria-label="Decrease quantity" disabled={value<=1} onClick={()=>change(value-1)}>−</button>
    <strong>{value}</strong><button type="button" aria-label="Increase quantity" disabled={value>=Math.min(max,999)} onClick={()=>change(value+1)}>+</button></div>;
}

export function Pager({ page, pages, setPage }: { page: number; pages: number; setPage: (page: number) => void }) {
  if (pages < 2) return null;
  const displayed = [...new Set([1, Math.max(1,page-1), page, Math.min(pages,page+1), pages])].sort((a,b)=>a-b);
  return <nav className="sf-pager" aria-label="Pagination"><button disabled={page === 1} onClick={() => setPage(page-1)}>Prev</button>
    {displayed.map((value, index) => <span key={value}>{index>0 && value-displayed[index-1]>1 && <i>…</i>}
      <button aria-current={page === value ? "page" : undefined} onClick={() => setPage(value)}>{value}</button></span>)}
    <button disabled={page >= pages} onClick={() => setPage(page+1)}>Next</button>
  </nav>;
}

export function TotalsList({ totals, label = "Estimated Total", shippingKnown = true }: { totals: Totals; label?: string; shippingKnown?: boolean }) {
  return <dl className="sf-totals"><div><dt>Subtotal</dt><dd>{money(totals.subtotal,totals.currency)}</dd></div>
    <div><dt>Shipping</dt><dd>{shippingKnown ? money(totals.shipping,totals.currency) : "Calculated at checkout"}</dd></div>
    {totals.discount > 0 && <div><dt>Discount</dt><dd className="sf-accent">−{money(totals.discount,totals.currency)}</dd></div>}
    {totals.tax > 0 && <div><dt>Tax</dt><dd>{money(totals.tax,totals.currency)}</dd></div>}
    <div className="sf-grand"><dt>{label}</dt><dd>{money(shippingKnown ? totals.total : totals.total-totals.shipping,totals.currency)}</dd></div>
  </dl>;
}

export function BagLines({ lines }: { lines: Line[] }) {
  return <div className="sf-bag-lines">{lines.map(item => <div key={item.id} className="sf-line">
    <Photo src={item.image}/><div><Link href={productUrl(item.slug)}>{item.name}</Link><small>Qty {item.quantity}</small></div><b>{money(item.quantity*item.price,item.currency)}</b>
  </div>)}</div>;
}

export const blankAddress: AddressInput = {label:"Home",name:"",phone:"",line1:"",line2:"",city:"",state:"",postal:"",country:""};

export function AddressFields({ value, change, labeled = false }: { value: AddressInput; change: (value: AddressInput) => void; labeled?: boolean }) {
  const input = (key: keyof AddressInput, label: string, max: number, autoComplete?: string, type="text") =>
    <label>{label}<input type={type} autoComplete={autoComplete} required={key!=="line2"} maxLength={max} value={value[key] ?? ""}
      onChange={event => change({...value,[key]:event.target.value})}/></label>;
  return <div className="sf-fields">
    {labeled ? <div className="sf-fields-two">{input("label","Address Label",40)}{input("name","Full Name",200,"name")}</div> : input("name","Full Name",200,"name")}
    {labeled ? <div className="sf-fields-two">{input("phone","Phone Number",30,"tel","tel")}{input("country","Country",100,"country-name")}</div> : input("phone","Phone Number",30,"tel","tel")}
    {input("line1","Street Address",255,"address-line1")}{input("line2","Apartment, Suite, Unit (Optional)",255,"address-line2")}
    {labeled ? <div className="sf-fields-three">{input("city","City",100,"address-level2")}{input("state","State / Province",100,"address-level1")}{input("postal","Postal Code",20,"postal-code")}</div>
      : <><div className="sf-fields-two">{input("city","City",100,"address-level2")}{input("state","State / Province",100,"address-level1")}</div>
        <div className="sf-fields-two">{input("postal","Postal Code",20,"postal-code")}{input("country","Country",100,"country-name")}</div></>}
  </div>;
}

export function AddressBlock({ address }: { address: Omit<AddressInput,"label"> }) {
  return <div className="sf-address-text"><strong>{address.name}</strong><span>{[address.line1,address.line2].filter(Boolean).join(", ")}</span>
    <span>{[address.city,address.state].filter(Boolean).join(", ")} {address.postal}</span><span>{address.country}</span></div>;
}
