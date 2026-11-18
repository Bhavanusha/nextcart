
"use client";

import Icon from "../Icon";

import { useState } from "react";
import { adminRequest, Badge, DataTable, dateLabel, Heading, Message, Pagination, useAdminData, usePagination } from "../ui";

type Review = { id: number; customerName: string; productName: string; rating: number; title: string | null; comment: string | null;
  approved: boolean; hidden: boolean; createdAt: string };
const tabs = [{ key: "", label: "All Reviews" }, { key: "PENDING", label: "Pending Moderation" },
  { key: "APPROVED", label: "Approved" }, { key: "HIDDEN", label: "Hidden" }];
const reviewStatus = (row: Review) => row.hidden ? "HIDDEN" : row.approved ? "APPROVED" : "PENDING";

export default function ReviewsPage() {
  const source = useAdminData<Review[]>("/reviews");
  const [filter, setFilter] = useState("");
  const [error, setError] = useState("");
  const [pending, setPending] = useState<number>();
  const rows = source.data ?? [];
  const counts = rows.reduce<Record<string, number>>((result, row) => {
    const value = reviewStatus(row);
    result[value] = (result[value] ?? 0) + 1;
    return result;
  }, {});
  const pager = usePagination(rows.filter(row => !filter || reviewStatus(row) === filter), 6, filter);

  async function moderate(row: Review, status?: string) {
    if (!status && !confirm("Permanently delete this review?")) return;
    setPending(row.id);
    setError("");
    try {
      await adminRequest("/reviews/" + row.id + (status ? "/moderation?status=" + status : ""), { method: status ? "PUT" : "DELETE" });
      source.refresh();
    } catch (failure) { setError((failure as Error).message); }
    finally { setPending(undefined); }
  }

  return <main className="admin-content">
    <Heading title="Customer Reviews" description="Moderate and analyze product ratings and text reviews" />
    <div className="admin-tabs review-tabs" aria-label="Review status">{tabs.map(tab => <button key={tab.key} aria-pressed={filter === tab.key} onClick={() => setFilter(tab.key)}>
      {tab.label}{tab.key && <span className="tab-count">{counts[tab.key] ?? 0}</span>}</button>)}</div>
    <Message error={error || source.error} />
    <DataTable className="compact" columns={["Customer", "Product Name", "Rating", "Review Content", "Submitted Date", "Status", "Actions"]}
      empty={!source.data && !source.error ? "Loading reviews…" : !pager.total ? "No reviews found." : undefined}>
      {pager.rows.map(row => {
        const content = [row.title, row.comment].filter(Boolean).join(" — ");
        return <tr key={row.id}><td><strong>{row.customerName}</strong></td><td>{row.productName}</td>
          <td><span className="review-stars" aria-label={row.rating + " out of 5 stars"}>{"★".repeat(row.rating)}<span>{"☆".repeat(5 - row.rating)}</span></span></td>
          <td><span className="cell-truncate" title={content}>{content || "—"}</span></td><td>{dateLabel(row.createdAt)}</td><td><Badge value={reviewStatus(row)} /></td>
          <td><button className="admin-icon positive" aria-label="Approve review" title="Approve" disabled={pending !== undefined || reviewStatus(row) === "APPROVED"} onClick={() => void moderate(row, "APPROVED")}><Icon name="check" size={16} /></button>
            <button className="admin-icon" aria-label="Hide review" title="Hide" disabled={pending !== undefined || row.hidden} onClick={() => void moderate(row, "HIDDEN")}><Icon name="hidden" size={16} /></button>
            <button className="admin-icon danger" aria-label="Delete review" title="Delete" disabled={pending !== undefined} onClick={() => void moderate(row)}><Icon name="trash" size={16} /></button></td></tr>;
      })}
    </DataTable>
    <Pagination {...pager} label="reviews" />
  </main>;
}
