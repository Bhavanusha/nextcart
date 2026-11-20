"use client";
import { useState } from "react";
import { Pager } from "../../Commerce";
import { ErrorLine } from "../../StoreShell";
import { title, useData, type Orders } from "../../data";
import { OrdersTable } from "../OrderViews";

export default function OrdersPage() {
  const [filter,setFilter] = useState("");
  const [page,setPage] = useState(1);
  const source = useData<Orders>("/orders?status="+filter+"&page="+page);
  const counts = source.data?.counts ?? {};
  const sum = Object.values(counts).reduce((total,n)=>total+n,0);
  const processing = (counts.PENDING ?? 0)+(counts.CONFIRMED ?? 0)+(counts.PROCESSING ?? 0);
  const total = !filter ? sum : filter==="PROCESSING" ? processing : counts[filter] ?? 0;
  return <><h1>My Orders</h1>
    <div className="sf-tabs">{["","PROCESSING","SHIPPED","DELIVERED","CANCELLED"].map(status=><button key={status} aria-pressed={filter===status}
      onClick={()=>{setPage(1);setFilter(status);}}>{status ? title(status) : "All"} ({!status ? sum : status==="PROCESSING" ? processing : counts[status] ?? 0})</button>)}</div>
    <ErrorLine error={source.error}/>
    {source.data && <><OrdersTable orders={source.data.orders} history/><Pager page={page} pages={Math.ceil(total/source.data.size)} setPage={setPage}/></>}
    {!source.data && !source.error && <p className="sf-empty">Loading orders…</p>}
  </>;
}
