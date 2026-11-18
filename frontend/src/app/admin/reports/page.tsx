
"use client";

import Icon from "../Icon";
import { Metric, ProductRanking, RangeControls, RevenueChart, StatusChart, useDateRange, type Bucket, type TopProduct } from "../analytics";
import { DataTable, dateLabel, exportCsv, Message, money, Panel, useAdminData } from "../ui";

type Report = { from: string; to: string; totalSales: number; totalOrders: number; averageOrderValue: number; newCustomers: number; currencyCode: string;
  previous: { revenue: number; orders: number; averageOrderValue: number; newCustomers: number };
  topProducts: TopProduct[]; monthlySales: Bucket[]; salesByDay: Bucket[]; orderStatuses: { status: string; count: number }[];
  categorySales: { categoryName: string; productCount: number; revenue: number; orders: number; averagePrice: number }[] };
const ranges = [{ key: "7d", label: "Last 7 Days" }, { key: "30d", label: "Last 30 Days" },
  { key: "3m", label: "Last 3 Months" }, { key: "custom", label: "Custom Range" }];

export default function ReportsPage() {
  const range = useDateRange("30d");
  const source = useAdminData<Report>("/reports?" + range.query);
  const report = source.data;
  const currency = report?.currencyCode ?? "USD";

  function download() {
    if (!report) return;
    exportCsv("haven-report-" + report.from + "-to-" + report.to, [
      ["From", "To", "Currency", "Revenue", "Orders", "Average Order Value", "New Customers"],
      [report.from, report.to, currency, report.totalSales, report.totalOrders, report.averageOrderValue, report.newCustomers],
      [], ["Date", "Revenue", "Orders"], ...report.salesByDay.map(row => [row.date, row.sales, row.orderCount]),
      [], ["Category", "Products", "Revenue", "Orders", "Average Price"],
      ...report.categorySales.map(row => [row.categoryName, row.productCount, row.revenue, row.orders, row.averagePrice]),
      [], ["Order Status", "Orders"], ...report.orderStatuses.map(row => [row.status, row.count]),
      [], ["Product", "Units Sold", "Sales"], ...report.topProducts.map(row => [row.productName, row.unitsSold, row.sales]),
    ]);
  }

  return <main className="admin-content admin-stack">
    <div className="spread analytics-toolbar"><h1>Performance Overview</h1><div className="admin-actions">
      <RangeControls model={range} options={ranges} dropdown /><button className="admin-action-button" disabled={!report} onClick={download}><Icon name="download" size={17} /> Export CSV</button>
    </div></div>
    <Message error={source.error} />
    {!report && !source.error && <div className="admin-empty">Loading report…</div>}
    {report && <>
      <div className="metric-grid">
        <Metric label="Total Revenue" value={money(report.totalSales, currency)} number={report.totalSales} previous={report.previous.revenue} />
        <Metric label="Total Orders" value={report.totalOrders.toLocaleString()} number={report.totalOrders} previous={report.previous.orders} />
        <Metric label="Avg Order Value" value={money(report.averageOrderValue, currency)} number={report.averageOrderValue} previous={report.previous.averageOrderValue} />
        <Metric label="New Customers" value={report.newCustomers.toLocaleString()} number={report.newCustomers} previous={report.previous.newCustomers} />
      </div>
      <section className="admin-panel"><div className="spread panel-heading">
        <div><h2>Revenue Growth Trends</h2><small>Monthly paid order totals through {dateLabel(report.to)} in {currency}</small></div>
        <strong className="accent-text">{money(report.monthlySales.reduce((total, row) => total + row.sales, 0), currency)}</strong></div>
        <RevenueChart buckets={report.monthlySales} currency={currency} large />
      </section>
      <div className="chart-pair">
        <Panel title="Orders by Status"><StatusChart rows={report.orderStatuses} /></Panel>
        <Panel title="Top Selling Products"><ProductRanking products={report.topProducts} currency={currency} /></Panel>
      </div>
      <Panel title="Sales by Category"><DataTable columns={["Category Name", "Products Count", "Revenue", "Orders", "Avg Price"]}
        empty={!report.categorySales.length ? "No category sales in this period." : undefined}>
        {report.categorySales.map(row => <tr key={row.categoryName}><td><strong>{row.categoryName}</strong></td><td>{row.productCount}</td>
          <td><strong>{money(row.revenue, currency)}</strong></td><td>{row.orders}</td><td>{money(row.averagePrice, currency)}</td></tr>)}
      </DataTable></Panel>
    </>}
  </main>;
}
