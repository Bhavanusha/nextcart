
"use client";

import Icon from "../Icon";

import Link from "next/link";
import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Badge, DataTable, dateLabel, Heading, Message, money, Pagination, titleCase, useAdminData, usePagination } from "../ui";

type Order = { id: number; orderNumber: string; customerName: string; customerEmail: string; createdAt: string; total: number;
  paymentStatus: string; orderStatus: string; itemCount: number; currencyCode: string };
type OrderResponse = { orders: Order[]; counts: Record<string, number> };
export default function OrdersPage() {
  return <Suspense fallback={<main className="admin-content admin-empty">Loading orders…</main>}><OrderList /></Suspense>;
}

function OrderList() {
  const params = useSearchParams();
  const fromUrl = params.get("status") ?? "";
  const [selection, setSelection] = useState({ source: fromUrl, status: fromUrl });
  const filter = selection.source === fromUrl ? selection.status : fromUrl;
  const source = useAdminData<OrderResponse>("/orders");
  const rows = source.data?.orders ?? [];
  const counts = source.data?.counts ?? {};
  const statuses = ["", "PENDING", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED"];
  const pager = usePagination(rows.filter(row => !filter || row.orderStatus === filter), 8, filter);
  return <main className="admin-content">
    <Heading title="Orders" description="Monitor, fulfill, and track your customer sales transactions" />
    <div className="admin-tabs" aria-label="Fulfillment status">{statuses.map(status => <button type="button" key={status}
      aria-pressed={filter === status} onClick={() => setSelection({ source: fromUrl, status })}>
      {status ? titleCase(status) : "All"}<span className="tab-count">{(status ? counts[status] ?? 0 : rows.length).toLocaleString()}</span></button>)}</div>
    <Message error={source.error} />
    <DataTable columns={["Order ID", "Customer", "Date", "Items Count", "Total", "Payment", "Fulfillment Status", "Actions"]}
      empty={!source.data && !source.error ? "Loading orders…" : !pager.total ? "No orders found." : undefined}>
      {pager.rows.map(order => <tr key={order.id}>
        <td><Link href={"/admin/orders/" + order.id}><strong>#{order.orderNumber.replace(/^#/, "")}</strong></Link></td>
        <td><strong className="block">{order.customerName}</strong><small>{order.customerEmail}</small></td>
        <td>{dateLabel(order.createdAt)}</td><td>{order.itemCount} {order.itemCount === 1 ? "item" : "items"}</td>
        <td><strong>{money(order.total, order.currencyCode)}</strong></td>
        <td><Badge value={order.paymentStatus === "PENDING" ? "UNPAID" : order.paymentStatus} /></td><td><Badge value={order.orderStatus} /></td>
        <td><Link className="admin-icon" title="View order" aria-label={"View order " + order.orderNumber} href={"/admin/orders/" + order.id}><Icon name="orders" size={16} /></Link></td>
      </tr>)}
    </DataTable>
    <Pagination {...pager} label="orders" />
  </main>;
}
