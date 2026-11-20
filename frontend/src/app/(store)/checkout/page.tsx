"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { AddressBlock, AddressFields, BagLines, blankAddress, TotalsList } from "../Commerce";
import { ErrorLine, Icon, RequireAccount, useStore } from "../StoreShell";
import { money, request, StoreError, useData, type Address, type AddressInput, type Quote } from "../data";

type CheckoutData = { quote: Quote; payHere: boolean; addresses: Address[] };
type Placed = { number: string; payment?: { action: string; fields: Record<string,string> } };
const steps = ["Shipping","Delivery","Payment","Review"];

export default function CheckoutPage() { return <RequireAccount><Checkout/></RequireAccount>; }

function Checkout() {
  const source = useData<CheckoutData>("/checkout");
  return <main className="sf-wrap sf-page"><ErrorLine error={source.error}/>
    {source.data ? <CheckoutForm data={source.data} refresh={source.refresh}/> : !source.error && <p>Loading checkout…</p>}
  </main>;
}

function CheckoutForm({ data, refresh }: { data: CheckoutData; refresh: () => void }) {
  const { profile, settings, refreshCart } = useStore();
  const router = useRouter();
  const [stage,setStage] = useState(0);
  const [address,setAddress] = useState<AddressInput>(data.addresses[0] ?? {...blankAddress,
    name: profile ? profile.firstName + " " + profile.lastName : "", phone: profile?.phone ?? "", country:settings?.country ?? ""});
  const [selected,setSelected] = useState(String(data.addresses[0]?.id ?? ""));
  const [save,setSave] = useState(false);
  const [method,setMethod] = useState(data.payHere && data.quote.total > 0 ? "PAYHERE" : data.quote.cashOnDelivery ? "COD" : "");
  const [key,setKey] = useState("");
  const [busy,setBusy] = useState(false);
  const [error,setError] = useState("");
  const { quote } = data;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError("");
    if (stage < 3) { setStage(stage+1); return; }
    const checkoutKey = key || crypto.randomUUID();
    setKey(checkoutKey); setBusy(true);
    try {
      const result = await request<Placed>("/checkout",{method:"POST",body:JSON.stringify({key:checkoutKey,address,saveAddress:save,
        method,total:quote.total,currency:quote.currency})});
      refreshCart();
      if (result.payment) {
        const form = document.createElement("form");
        form.method="POST"; form.action=result.payment.action;
        for (const [name,value] of Object.entries(result.payment.fields)) {
          const input = document.createElement("input"); input.type="hidden"; input.name=name; input.value=value; form.append(input);
        }
        document.body.append(form); form.submit();
      } else router.replace("/order-success/"+encodeURIComponent(result.number));
    } catch (failure) { setError((failure as Error).message); if (failure instanceof StoreError) refresh(); setBusy(false); }
  }

  if (!quote.available || quote.invalidCoupon) return <div className="sf-empty"><h1>Review Your Bag</h1><p>Checkout requires available items and a valid promo code.</p><div className="sf-actions sf-checkout-empty"><Link className="sf-btn" href="/cart">Back to Cart</Link><Link className="sf-btn outline" href="/account/orders">View My Orders</Link></div></div>;
  const payAvailable = method==="PAYHERE" ? data.payHere && quote.total>0 : method==="COD" && quote.cashOnDelivery;
  return <>
    <ol className="sf-checkout-steps">{steps.map((step,index)=><li key={step} aria-current={index===stage ? "step" : undefined} data-complete={index<stage}>
      <span>{index<=stage ? <Icon name="check" size={13}/> : index+1}</span>{index+1}. {step}</li>)}</ol>
    <div className="sf-checkout">
      <form onSubmit={submit} className="sf-checkout-form">
        {stage===0 && <><h1>Shipping Address</h1>
          {!!data.addresses.length && <label className="sf-checkout-address">Saved Addresses<select value={selected} onChange={event=>{
            setSelected(event.target.value); const saved=data.addresses.find(item=>String(item.id)===event.target.value);
            setAddress(saved ?? {...blankAddress,country:settings?.country ?? ""}); setSave(false);
          }}><option value="">Use a new address</option>{data.addresses.map(item=><option key={item.id} value={item.id}>{item.label} — {item.line1}</option>)}</select></label>}
          <AddressFields value={address} change={value=>{setAddress(value);setSelected("");}}/>
          {!selected && <label className="sf-choice"><input type="checkbox" checked={save} onChange={event=>setSave(event.target.checked)}/>Save this address for future orders</label>}</>}
        {stage===1 && <><h1>Delivery Method</h1><div className="sf-panel sf-spread"><b>Standard Delivery</b>
          <span>{quote.deliveryAvailable ? (quote.shipping ? money(quote.shipping,quote.currency) : "Free") : "Unavailable"}</span></div>
          {!quote.deliveryAvailable && <p className="sf-red">Delivery is currently unavailable for this order.</p>}</>}
        {stage===2 && <><h1>Payment Method</h1><div className="sf-panel sf-fields">
          {data.payHere && quote.total>0 && <label className="sf-choice"><input type="radio" name="payment" checked={method==="PAYHERE"} onChange={()=>setMethod("PAYHERE")}/>Pay online with PayHere</label>}
          {quote.cashOnDelivery && <label className="sf-choice"><input type="radio" name="payment" checked={method==="COD"} onChange={()=>setMethod("COD")}/>Cash on Delivery</label>}
          {!(data.payHere && quote.total>0) && !quote.cashOnDelivery && <p>No payment method is currently available.</p>}
          {method==="PAYHERE" && <p>Complete your payment on PayHere’s secure checkout page.</p>}
        </div></>}
        {stage===3 && <><h1>Review Your Order</h1><div className="sf-panel"><h2>Shipping Address</h2><AddressBlock address={address}/>
          <hr/><p><b>Payment</b> {method==="PAYHERE" ? "PayHere" : "Cash on Delivery"}</p></div></>}
        <ErrorLine error={error}/><div className="sf-actions sf-checkout-actions">
          <button className="sf-btn" disabled={busy || (stage>=1 && !quote.deliveryAvailable) || (stage>=2 && !payAvailable)}>
            {busy ? "Placing order…" : stage===3 ? (method==="PAYHERE" ? "Continue to PayHere" : "Place Order") : "Continue to "+steps[stage+1]}</button>
          {stage ? <button className="sf-text-button sf-under" type="button" disabled={busy} onClick={()=>{setStage(stage-1);setError("");}}>Back to {steps[stage-1]}</button>
            : <Link className="sf-under" href="/cart">Back to Cart</Link>}
        </div>
      </form>
      <aside className="sf-panel sf-summary"><h2>In Your Bag</h2><BagLines lines={quote.lines}/><TotalsList totals={quote} shippingKnown={stage>0 && quote.deliveryAvailable}/></aside>
    </div>
  </>;
}
