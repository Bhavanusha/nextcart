
"use client";

import Icon from "../../Icon";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import { adminRequest, Badge, Message, money, Panel, Thumb, titleCase, useAdminData } from "../../ui";

type Summary = {
  orderNumber: string; userId: number; firstName: string; lastName: string; email: string; phone: string | null;
  recipientName: string; shippingPhone: string; addressLine1: string; addressLine2: string | null; city: string;
  postalCode: string; stateOrProvince: string | null; country: string; subtotal: number; discountAmount: number;
  shippingAmount: number; totalAmount: number; currencyCode: string; orderStatus: string; paymentStatus: string;
  createdAt: string; placedAt: string | null; trackingNumber: string | null;
};
type Detail = {
  summary: Summary;
  items: { id: number; productName: string; productSku: string; quantity: number; unitPrice: number; subtotal: number; imageUrl: string | null; variantLabel: string | null }[];
  payments: { reference: string; method: string; provider: string | null; amount: number; currencyCode: string; status: string; paidAt: string | null }[];
  notes: { note: string; createdAt: string; author: string }[];
  timeline: { status: string; note: string | null; author: string | null; createdAt: string }[];
};
const statuses = ["PENDING", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED"];
const timestamp = (value: string) => new Date(value).toLocaleString("en-US", { dateStyle: "medium", timeStyle: "short" });

export default function OrderDetailPage() {
  const { id } = useParams<{ id: string }>();
  const order = useAdminData<Detail>("/orders/" + id + "/detail");
  if (order.error) return <main className="admin-content"><Message error={order.error} /></main>;
  if (!order.data) return <main className="admin-content admin-empty">Loading order…</main>;
  return <OrderView key={id} id={id} detail={order.data} refresh={order.refresh} />;
}

function OrderView({ id, detail, refresh }: { id: string; detail: Detail; refresh: () => void }) {
  const [status, setStatus] = useState("");
  const [tracking, setTracking] = useState("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const order = detail.summary;
  const placed = order.placedAt || order.createdAt;
  const timeline = [
    ...detail.timeline.map(event => ({ at: event.createdAt, text: "Order " + titleCase(event.status).toLowerCase()
      + (event.author ? " by " + event.author : "") + (event.note ? " · " + event.note : "") })),
    ...detail.payments.filter(payment => payment.paidAt).map(payment => ({ at: payment.paidAt!,
      text: "Payment confirmed for " + money(payment.amount, payment.currencyCode) })),
    { at: placed, text: "Order placed" },
  ].sort((a, b) => Date.parse(b.at) - Date.parse(a.at));

  async function update(event: FormEvent, type: "status" | "note") {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (type === "status") {
        await adminRequest("/orders/" + id + "/status", { method: "PUT",
          body: JSON.stringify({ status, trackingNumber: status === "SHIPPED" ? tracking : null }) });
        setStatus("");
        setTracking("");
      } else {
        await adminRequest("/orders/" + id + "/notes", { method: "POST", body: JSON.stringify({ note }) });
        setNote("");
      }
      refresh();
    } catch (failure) { setError((failure as Error).message); }
    finally { setBusy(false); }
  }

  return <main className="admin-content">
    <div className="admin-page-heading"><div className="order-detail-title">
      <Link className="back-button" href="/admin/orders" aria-label="Back to orders"><Icon name="arrow" size={16} /></Link>
      <div><h1>Order #{order.orderNumber.replace(/^#/, "")} <Badge value={order.orderStatus} /></h1><p>Placed on {timestamp(placed)}</p></div></div>
      <form className="admin-actions" onSubmit={event => void update(event, "status")}>
        <select className="admin-select" aria-label="Update status" value={status} onChange={event => { setStatus(event.target.value); setTracking(order.trackingNumber ?? ""); }}>
          <option value="">Update Status</option>{statuses.map(value => <option value={value} key={value}>{titleCase(value)}</option>)}</select>
        {status === "SHIPPED" && <input className="admin-select" aria-label="Tracking number" placeholder="Tracking number" maxLength={255} value={tracking} onChange={event => setTracking(event.target.value)} />}
        {status && <button className="admin-action-button" disabled={busy}>Apply</button>}
      </form>
    </div>
    <Message error={error} />
    <div className="admin-split reverse"><div className="admin-stack">
      <section className="admin-panel"><div className="spread panel-heading"><h2>Customer Information</h2><Link href={"/admin/customers?id=" + order.userId} className="admin-link">View Customer</Link></div>
        <div className="cell-identity"><span className="admin-avatar">{order.firstName[0]}{order.lastName[0]}</span><span><strong>{order.firstName} {order.lastName}</strong><br /><small>{order.email}</small></span></div>
        <div className="order-phone"><small>Phone Number</small><p>{order.phone ?? order.shippingPhone}</p></div></section>
      <Panel title="Shipping Address"><strong>{order.recipientName}</strong><p className="muted-text">{order.addressLine1}{order.addressLine2 && ", " + order.addressLine2}<br />
        {order.city}{order.stateOrProvince && ", " + order.stateOrProvince} {order.postalCode}<br />{order.country}</p>
        {order.trackingNumber && <p><small>Tracking</small> <strong>{order.trackingNumber}</strong></p>}</Panel>
      <Panel title="Payment Information">
        {detail.payments.length ? detail.payments.map(payment => <div className="spread payment-line" key={payment.reference}>
          <span><strong>{payment.provider || titleCase(payment.method)}</strong><br /><small>{payment.reference}</small></span><Badge value={payment.status} />
        </div>) : <div className="spread"><small>No payment records</small><Badge value={order.paymentStatus === "PENDING" ? "UNPAID" : order.paymentStatus} /></div>}
      </Panel>
    </div><div className="admin-stack">
      <Panel title="Items Ordered">
        {detail.items.length ? detail.items.map(item => <div className="order-item" key={item.id}><span className="cell-identity"><Thumb src={item.imageUrl} size={48} />
          <span><strong>{item.productName}</strong><br /><small>SKU: {item.productSku}{item.variantLabel && " · " + item.variantLabel}</small></span></span>
          <span className="muted-text">Qty {item.quantity}</span><span className="muted-text unit-price">{money(item.unitPrice, order.currencyCode)}</span><strong>{money(item.subtotal, order.currencyCode)}</strong></div>)
          : <p className="muted-text">No items recorded.</p>}
        <dl className="order-totals"><dt className="muted-text">Subtotal</dt><dd>{money(order.subtotal, order.currencyCode)}</dd>
          <dt className="muted-text">Shipping</dt><dd>{money(order.shippingAmount, order.currencyCode)}</dd>
          <dt className="muted-text">Discount</dt><dd>−{money(order.discountAmount, order.currencyCode)}</dd>
          <dt><strong>Total Amount</strong></dt><dd><strong className="accent-text">{money(order.totalAmount, order.currencyCode)}</strong></dd></dl>
      </Panel>
      <Panel title="Order Timeline"><ol className="order-timeline">{timeline.map((event, index) => <li key={event.at + "-" + index}>
        <strong>{event.text}</strong><br /><small>{timestamp(event.at)}</small></li>)}</ol></Panel>
      <Panel title="Internal Admin Notes">
        {detail.notes.map((entry, index) => <div className="admin-note" key={entry.createdAt + "-" + index}>
          <div className="spread"><strong>{entry.author}</strong><small>{timestamp(entry.createdAt)}</small></div><p>{entry.note}</p></div>)}
        <form className="admin-fields" onSubmit={event => void update(event, "note")}>
          <textarea aria-label="Internal admin note" required maxLength={4000} placeholder="Add an internal note only visible to staff…" value={note} onChange={event => setNote(event.target.value)} />
          <div><button className="admin-action-button note-button" disabled={busy || !note.trim()}>Add Note</button></div>
        </form>
      </Panel>
    </div></div>
  </main>;
}
