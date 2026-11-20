"use client";
import { useParams } from "next/navigation";
import { useEffect } from "react";
import { useData, type OrderDetail } from "../../data";
import { ErrorLine, RequireAccount } from "../../StoreShell";
import { ConfirmationView } from "../../account/OrderViews";

export default function OrderSuccess() { return <RequireAccount><Confirmation/></RequireAccount>; }

function Confirmation() {
  const {number} = useParams<{number:string}>();
  const source = useData<OrderDetail>("/orders/"+encodeURIComponent(number));
  const pending = source.data?.order.method==="PayHere" && source.data.order.paymentStatus==="PENDING";
  const refresh = source.refresh;
  useEffect(()=>{
    if (!pending) return;
    let remaining = 12;
    const timer = setInterval(()=>{ refresh(); if (--remaining<=0) clearInterval(timer); },5000);
    return ()=>clearInterval(timer);
  },[pending,refresh]);
  return <main className="sf-wrap sf-page"><ErrorLine error={source.error}/>
    {source.data ? <ConfirmationView detail={source.data} refresh={refresh}/> : !source.error && <p className="sf-empty">Loading order…</p>}</main>;
}
