"use client";
import Link from "next/link";
import { AddressBlock, TotalsList } from "../Commerce";
import { Icon, Photo } from "../StoreShell";
import { day, money, orderUrl, title, type Order, type OrderDetail } from "../data";

export function Badge({ status }: { status: string }) { return <span className={"sf-badge "+status.toLowerCase()}>{title(status)}</span>; }

export function OrdersTable({ orders, history = false }: { orders: Order[]; history?: boolean }) {
  return <div className="sf-table-scroll"><table className="sf-order-table">
    <thead><tr><th>{history ? "Order" : "Order ID"}</th><th>Date</th><th>Items</th><th>Total</th>{history && <th>Payment</th>}<th>Status</th><th>Action</th></tr></thead>
    <tbody>{orders.map(order=><tr key={order.number}><td><b>#{order.number}</b></td><td>{day(order.date)}</td>
      <td>{history ? <span className="sf-truncate" title={order.firstItem}>{order.firstItem || "—"}</span> : order.count + (order.count===1 ? " item" : " items")}</td><td><b>{money(order.total,order.currency)}</b></td>
      {history && <td>{order.method || title(order.paymentStatus)}</td>}<td><Badge status={order.status}/></td>
      <td><Link className="sf-accent" href={orderUrl(order.number)}>{history ? "Manage" : "View Details"}</Link></td></tr>)}
      {!orders.length && <tr><td colSpan={history?7:6} className="sf-empty">No orders to show.</td></tr>}</tbody>
  </table></div>;
}

const tracking = [["PLACED","Order Placed"],["CONFIRMED","Confirmed"],["PROCESSING","Processing"],["SHIPPED","Shipped"],["DELIVERED","Delivered"]];

function Tracking({ detail }: { detail: OrderDetail }) {
  const { order, steps } = detail;
  const current = tracking.findIndex(([status])=>status===order.status);
  return <section className="sf-panel sf-tracking"><h3>Shipment Tracking</h3>
    {order.status==="CANCELLED" ? <p>This order was cancelled.</p> : <ol>{tracking.map(([status,label],index)=>{
      const date = status==="PLACED" ? order.date : steps.find(step=>step.status===status)?.date;
      return <li key={status} data-done={index<current || !!date} data-current={index===current}>
        <span>{index<current || date ? <Icon name="check" size={14}/> : null}</span><strong>{label}</strong>{date && <small>{day(date)}</small>}</li>;
    })}</ol>}
    {order.tracking && <p>Tracking number <strong>{order.tracking}</strong></p>}
    {order.estimatedTo && !["CANCELLED","DELIVERED"].includes(order.status) && <p>Estimated delivery <strong>{estimate(order)}</strong></p>}
  </section>;
}

function Items({ detail, summary = false }: { detail: OrderDetail; summary?: boolean }) {
  return <div className={summary?"sf-bag-lines":"sf-items-ordered"}>{detail.items.map(item=><article className="sf-line" key={item.id}>
    <Photo src={item.image}/><div><strong>{item.name}</strong>{item.variant && <small>Variant: {item.variant}</small>}<small>Qty: {item.quantity}</small></div>
    <b>{money(item.total,detail.order.currency)}</b></article>)}</div>;
}

export function OrderView({ detail }: { detail: OrderDetail }) {
  const { order } = detail;
  return <>
    <Link className="sf-under" href="/account/orders">← Back to Orders</Link>
    <div className="sf-spread sf-order-heading"><div><h1>Order #{order.number}</h1><p>Placed on {day(order.date)}</p></div><Badge status={order.status}/></div>
    <Tracking detail={detail}/>
    <div className="sf-order-split"><section><h2>Items Ordered</h2><Items detail={detail}/></section>
      <aside className="sf-stacked">
        <section className="sf-panel"><h3>Order Summary</h3><TotalsList totals={order} label="Total"/></section>
        <section className="sf-panel"><h3>Shipping Address</h3><AddressBlock address={order.address}/></section>
        <section className="sf-panel"><h3>Payment Method</h3><div className="sf-spread"><span>{order.method || "Payment"}</span><Badge status={order.paymentStatus}/></div></section>
      </aside>
    </div>
  </>;
}

function estimate(order: Order) {
  if (!order.estimatedTo) return order.delivery || "See Order Details";
  return order.estimatedFrom && order.estimatedFrom !== order.estimatedTo
    ? day(order.estimatedFrom) + " – " + day(order.estimatedTo) : day(order.estimatedTo);
}

export function ConfirmationView({ detail, refresh }: { detail: OrderDetail; refresh: () => void }) {
  const { order } = detail;
  const paid = order.paymentStatus==="PAID";
  const failed = order.paymentStatus==="FAILED" || order.status==="CANCELLED";
  const pending = !paid && !failed && order.method==="PayHere";
  const label = failed ? "Order update" : pending ? "Payment Pending" : "Order Placed Successfully!";
  return <div className="sf-confirmation">
    <header><span className={"sf-confirm-icon " + (pending || failed ? "neutral" : "")}><Icon name={failed?"close":pending?"bag":"check"} size={26}/></span><h1>{label}</h1>
      <p>{failed ? "Check the order below for its latest status." : pending ? "We’re waiting for PayHere to confirm your payment." : "Your order has been confirmed."}</p></header>
    <div className="sf-panel sf-confirm-meta"><div><h3>Order Number</h3><b>#{order.number}</b></div>
      <div><h3>{order.estimatedTo ? "Estimated Delivery" : "Delivery"}</h3><b>{estimate(order)}</b></div><div><h3>Payment Method</h3><b>{order.method || title(order.paymentStatus)}</b></div></div>
    <div className="sf-confirm-split"><section><h2>Items Ordered</h2><Items detail={detail} summary/></section>
      <section><h2>Shipping To</h2><AddressBlock address={order.address}/><hr/>
        <TotalsList totals={order} label={paid ? "Total Paid" : "Order Total"}/></section>
    </div>
    <div className="sf-actions">{pending && <button className="sf-btn outline" onClick={refresh}>Check Payment Status</button>}
      <Link className="sf-btn" href={orderUrl(order.number)}>View Order Details</Link><Link className="sf-btn outline" href="/shop">Continue Shopping</Link></div>
  </div>;
}


export function Avatar({ profile }: { profile: {firstName: string; lastName: string; avatar?: string} }) {
  return profile.avatar ? <Photo className="sf-avatar" src={profile.avatar}/> :
    <span className="sf-avatar" aria-hidden="true">{profile.firstName.charAt(0)}{profile.lastName.charAt(0)}</span>;
}
