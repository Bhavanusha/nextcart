
"use client";

import { useState, type FormEvent } from "react";
import { money, titleCase } from "./ui";

export type Bucket = { date: string; sales: number; orderCount: number };
export type TopProduct = { productName: string; unitsSold: number; sales: number };
type Range = { key: string; label: string };

export function useDateRange(initial: string) {
  const [range, setRange] = useState(initial);
  const [custom, setCustom] = useState({ from: "", to: "" });
  const [committed, setCommitted] = useState<{ from: string; to: string }>();
  const query = range === "custom" && committed ? new URLSearchParams(committed).toString()
    : "range=" + (range === "custom" ? initial : range);
  return { range, setRange, custom, setCustom, query,
    apply: (event: FormEvent) => { event.preventDefault(); setCommitted(custom); } };
}

export function RangeControls({ model, options, dropdown = false }: {
  model: ReturnType<typeof useDateRange>; options: Range[]; dropdown?: boolean;
}) {
  return <div className="range-control">
    {dropdown ? <select className="admin-select" aria-label="Report date range" value={model.range} onChange={event => model.setRange(event.target.value)}>
      {options.map(option => <option key={option.key} value={option.key}>{option.label}</option>)}
    </select> : <div className="admin-tabs" aria-label="Date range">{options.map(option => <button key={option.key} aria-pressed={model.range === option.key}
      onClick={() => model.setRange(option.key)}>{option.label}</button>)}</div>}
    {model.range === "custom" && <form className="admin-actions custom-dates" onSubmit={model.apply}>
      <label>From <input type="date" className="admin-select" required value={model.custom.from}
        max={model.custom.to || undefined} onChange={event => model.setCustom({ ...model.custom, from: event.target.value })} /></label>
      <label>To <input type="date" className="admin-select" required value={model.custom.to}
        min={model.custom.from || undefined} onChange={event => model.setCustom({ ...model.custom, to: event.target.value })} /></label>
      <button className="admin-secondary-button">Apply</button>
    </form>}
  </div>;
}

export function Metric({ label, value, number, previous, icon = "↗" }: {
  label: string; value: string; number: number; previous: number; icon?: string;
}) {
  const change = previous === 0 ? (number === 0 ? 0 : null) : (number - previous) / previous * 100;
  return <article className="metric-card"><div className="spread"><span>{label}</span><span className="metric-icon" aria-hidden="true">{icon}</span></div>
    <strong>{value}</strong><small>{change === null ? "No prior-period activity" : <><b className={change > 0 ? "positive-text" : change < 0 ? "danger-text" : ""}>
      {change > 0 ? "+" : ""}{change.toFixed(1)}%</b> vs last period</>}</small></article>;
}

function chartDate(date: string, field: "month" | "weekday") {
  return new Date(date + "T12:00:00").toLocaleDateString("en-US", { [field]: "short" });
}

export function RevenueChart({ buckets, currency, large = false }: { buckets: Bucket[]; currency: string; large?: boolean }) {
  const width = large ? 960 : 470;
  const height = large ? 215 : 145;
  const max = Math.max(1, ...buckets.map(bucket => bucket.sales));
  const inset = large ? 35 : 10;
  const points = buckets.map((bucket, index) => ({ x: inset + index * (width - 2 * inset) / Math.max(1, buckets.length - 1),
    y: height - 10 - bucket.sales / max * (height - 38), bucket }));
  const coordinates = points.map(point => point.x + "," + point.y).join(" ");
  return <div className={"admin-line-chart " + (large ? "revenue-chart" : "")}>
    <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" role="img" aria-label="Monthly revenue"
      style={{ color: "var(--accent)" }}>
      {[.25, .5, .75, 1].map(level => <line key={level} x1="0" x2={width} y1={height * level - 4} y2={height * level - 4} stroke="#eeeeef" />)}
      {!large && points.length > 0 && <polygon points={`10,${height - 10} ${coordinates} ${width - 10},${height - 10}`} fill="#faeee9" />}
      <polyline points={coordinates} stroke="currentColor" strokeWidth="3" vectorEffect="non-scaling-stroke" fill="none" />
      {points.map(point => <g key={point.bucket.date}><title>{chartDate(point.bucket.date, "month")}: {money(point.bucket.sales, currency)}</title>
        <circle cx={point.x} cy={point.y} r="4" fill="currentColor" stroke="white" strokeWidth="2" />
        {large && <text x={point.x} y={point.y - 12} textAnchor="middle" fontSize="11" fontWeight="600" fill="#191a1e">{money(point.bucket.sales, currency)}</text>}
      </g>)}
    </svg>
    <div className="chart-labels">{buckets.map(bucket => <span key={bucket.date}>{chartDate(bucket.date, "month")}</span>)}</div>
  </div>;
}

export function WeeklyChart({ buckets }: { buckets: Bucket[] }) {
  const max = Math.max(1, ...buckets.map(bucket => bucket.orderCount));
  return <div className="weekly-chart" role="group" aria-label="Orders by day">{buckets.map(bucket =>
    <div key={bucket.date} title={bucket.date + ": " + bucket.orderCount + " orders"} aria-label={bucket.date + ": " + bucket.orderCount + " orders"}>
      <i style={{ height: bucket.orderCount ? (bucket.orderCount / max * 80) + "%" : 0 }} />
      <small>{chartDate(bucket.date, "weekday").charAt(0)}</small>
    </div>)}</div>;
}

export function ProductRanking({ products, currency, showRevenue = false }: { products: TopProduct[]; currency: string; showRevenue?: boolean }) {
  const max = Math.max(1, ...products.map(product => product.unitsSold));
  if (!products.length) return <p className="muted-text">No sales in this period.</p>;
  return <ol className="top-products">{products.map((product, index) => <li key={product.productName}>
    {showRevenue && <span className="product-rank">{index + 1}</span>}
    <div className={showRevenue ? undefined : "ranking-body"}>
      {showRevenue ? <><strong>{product.productName}</strong><small>{product.unitsSold.toLocaleString()} sold</small></> : <>
        <div className="spread"><strong>{product.productName}</strong><small>{product.unitsSold.toLocaleString()} sold</small></div>
        <span className="product-meter"><i style={{ width: product.unitsSold / max * 100 + "%" }} /></span>
      </>}
    </div>
    {showRevenue && <b className="accent-text">{money(product.sales, currency)}</b>}
  </li>)}</ol>;
}

const statusColors: Record<string, string> = { DELIVERED: "#0cb783", SHIPPED: "#cf4e25", PROCESSING: "#f69c0e",
  CONFIRMED: "#8d789a", PENDING: "#3884f9", CANCELLED: "#ef4444" };

export function StatusChart({ rows }: { rows: { status: string; count: number }[] }) {
  const total = rows.reduce((sum, row) => sum + row.count, 0);
  if (!total) return <p className="muted-text">No orders in this period.</p>;
  const items = rows.map((row, index) => {
    const offset = rows.slice(0, index).reduce((sum, item) => sum + item.count, 0) / total * 100;
    const percent = row.count / total * 100;
    const color = statusColors[row.status] ?? "#777782";
    const segment = color + " " + offset + "% " + (offset + percent) + "%";
    return { ...row, color, segment, percent };
  });
  return <div className="status-report">
    <div className="status-donut" aria-hidden="true" style={{ background: "conic-gradient(" + items.map(item => item.segment).join(",") + ")" }} />
    <ul>{items.map(item => <li className="spread" key={item.status}><span><i className="status-legend" style={{ background: item.color }} />{titleCase(item.status)}</span>
      <strong>{item.percent.toFixed(0)}%</strong></li>)}</ul>
  </div>;
}
