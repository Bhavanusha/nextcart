"use client";
import Link from "next/link";
import { Breadcrumb, ProductGrid, RequireAccount, useStore } from "../StoreShell";

export default function WishlistPage() { return <RequireAccount><Wishlist/></RequireAccount>; }

function Wishlist() {
  const { wishlist } = useStore();
  return <main className="sf-wrap sf-page"><Breadcrumb><strong>Wishlist</strong></Breadcrumb>
    <h1>My Wishlist <em>({wishlist.length} items)</em></h1>
    {wishlist.length ? <ProductGrid products={wishlist} columns={3} wishlist/> :
      <div className="sf-empty"><p>Your wishlist is empty.</p><Link className="sf-btn" href="/shop">Explore the Shop</Link></div>}
  </main>;
}
