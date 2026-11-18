
"use client";

import Link from "next/link";
import { Metric, ProductRanking, RangeControls, RevenueChart, useDateRange, WeeklyChart, type Bucket, type TopProduct } from "./analytics";
import { Badge, DataTable, Message, money, Panel, useAdminData } from "./ui";

type Totals = { totalSales: number; totalOrders: number; totalCustomers: number; totalProducts: number };
type Dashboard = Totals & { pendingOrders: number; lowStockProducts: number; cancelledOrders: number; currencyCode: string; previous: Totals;
  monthlySales: Bucket[]; weeklyOrders: Bucket[]; topProducts: TopProduct[]; updatedAt: string;
  recentOrders: { id: number; orderNumber: string; customerName: string; total: number; status: string; currencyCode: string }[] };
const ranges = [{ key: "today", label: "Today" }, { key: "7d", label: "7 Days" }, { key: "30d", label: "30 Days" },
  { key: "3m", label: "3 Months" }, { key: "custom", label: "Custom" }];

export default function AdminDashboard() {
  const range = useDateRange("7d");
  const source = useAdminData<Dashboard>("/dashboard?" + range.query);
  const data = source.data;
  return <main className="admin-content admin-stack">
    <div className="spread analytics-toolbar"><RangeControls model={range} options={ranges} />
      {data && <small>Updated {new Date(data.updatedAt).toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" })}</small>}</div>
    <Message error={source.error} />
    {!data && !source.error && <div className="admin-empty">Loading dashboard…</div>}
    {data && <>
      <div className="metric-grid">
        <Metric label="Total Sales" value={money(data.totalSales, data.currencyCode)} number={data.totalSales} previous={data.previous.totalSales} />
        <Metric label="Total Orders" icon="▣" value={data.totalOrders.toLocaleString()} number={data.totalOrders} previous={data.previous.totalOrders} />
        <Metric label="Total Customers" icon="♙" value={data.totalCustomers.toLocaleString()} number={data.totalCustomers} previous={data.previous.totalCustomers} />
        <Metric label="Total Products" icon="◇" value={data.totalProducts.toLocaleString()} number={data.totalProducts} previous={data.previous.totalProducts} />
      </div>
      <div className="admin-kpis">
        <Link className="admin-panel kpi-card" href="/admin/orders?status=PENDING"><span className="badge gold">{data.pendingOrders}</span><span><strong>Pending Orders</strong><small>Awaiting fulfillment</small></span></Link>
        <Link className="admin-panel kpi-card" href="/admin/inventory"><span className="badge red">{data.lowStockProducts}</span><span><strong>Low Stock Items</strong><small>Available stock at or below threshold</small></span></Link>
        <Link className="admin-panel kpi-card" href="/admin/orders?status=CANCELLED"><span className="badge gray">{data.cancelledOrders}</span><span><strong>Cancelled Orders</strong><small>Orders in the selected period</small></span></Link>
      </div>
      <div className="chart-pair">
        <Panel title="Sales Overview (Monthly)"><RevenueChart buckets={data.monthlySales} currency={data.currencyCode} /></Panel>
        <Panel title="Weekly Order Breakdown"><WeeklyChart buckets={data.weeklyOrders} /></Panel>
      </div>
      <div className="admin-dash-bottom">
        <Panel title="Recent Orders"><DataTable columns={["ID", "Customer", "Total", "Status"]} empty={!data.recentOrders.length ? "No orders yet." : undefined}>
          {data.recentOrders.map(order => <tr key={order.id}><td><Link href={"/admin/orders/" + order.id}><strong>#{order.orderNumber.replace(/^#/, "")}</strong></Link></td>
            <td>{order.customerName}</td><td><strong>{money(order.total, order.currencyCode)}</strong></td><td><Badge value={order.status} /></td></tr>)}
        </DataTable></Panel>
        <Panel title="Top Selling Products"><ProductRanking products={data.topProducts} currency={data.currencyCode} showRevenue /></Panel>
      </div>
    </>}
  </main>;
}
