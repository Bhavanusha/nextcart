"use client";
import { useParams } from "next/navigation";
import { useData, type OrderDetail } from "../../../data";
import { ErrorLine } from "../../../StoreShell";
import { OrderView } from "../../OrderViews";

export default function OrderPage() {
  const {number} = useParams<{number:string}>();
  const source = useData<OrderDetail>("/orders/"+encodeURIComponent(number));
  return <><ErrorLine error={source.error}/>
    {source.data ? <OrderView detail={source.data}/> : !source.error && <p>Loading order…</p>}</>;
}
