"use client";
import Link from "next/link";
import { ErrorLine, Icon, useStore } from "../StoreShell";
import { useData, type Orders } from "../data";
import { OrdersTable } from "./OrderViews";

export default function AccountPage() {
  const { profile } = useStore();
  const history = useData<Orders>("/orders");
  if (!profile) return null;
  const metrics = [[profile.orderCount,"Orders","Lifetime Orders","order","/account/orders"],
    [profile.wishlistCount,"Items","In Wishlist","heart","/wishlist"],[profile.addressCount,"Addresses","Saved Addresses","pin","/account/addresses"]] as const;
  return <>
    <h1>Welcome back, {profile.firstName}</h1><p>Here’s an overview of your recent account activity and orders.</p>
    <div className="sf-account-metrics">{metrics.map(([count,title,description,icon,href])=><Link className="sf-panel" href={href} key={href}>
      <span className="sf-metric-icon"><Icon name={icon}/></span><span><strong>{count} {title}</strong><small>{description}</small></span></Link>)}</div>
    <section className="sf-account-section"><div className="sf-spread"><h2>Recent Orders</h2><Link className="sf-accent sf-upper" href="/account/orders">View All</Link></div>
      <ErrorLine error={history.error}/>{history.data && <OrdersTable orders={history.data.orders.slice(0,3)}/>}</section>
    <section className="sf-panel sf-account-section"><div className="sf-spread"><h2>Account Information</h2><Link className="sf-accent" href="/account/profile">Edit Profile</Link></div>
      <dl className="sf-account-info"><div><dt>Contact Name</dt><dd>{profile.firstName} {profile.lastName}</dd></div><div><dt>Email Address</dt><dd>{profile.email}</dd></div>
        <div><dt>Phone Number</dt><dd>{profile.phone || "—"}</dd></div></dl>
    </section>
  </>;
}
