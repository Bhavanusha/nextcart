"use client";

import { useCallback, useEffect, useState } from "react";

export const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export class StoreError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(API + "/api/store" + path, {
    credentials: "include", ...init, headers: init?.body ? { "Content-Type": "application/json" } : undefined,
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new StoreError(response.status, error.detail ?? error.message ?? "Unable to complete this request.");
  }
  return response.status === 204 ? undefined as T : response.json();
}

export function useData<T>(path: string | null) {
  const [state, setState] = useState<{ path?: string; data?: T; error?: StoreError }>({});
  const [version, setVersion] = useState(0);
  const refresh = useCallback(() => setVersion(current => current + 1), []);
  useEffect(() => {
    if (!path) return;
    const controller = new AbortController();
    request<T>(path, { signal: controller.signal })
      .then(data => { if (!controller.signal.aborted) setState({ path, data }); })
      .catch(error => { if (!controller.signal.aborted) setState({ path, error }); });
    return () => controller.abort();
  }, [path, version]);
  return { data: state.path === path ? state.data : undefined, error: state.path === path ? state.error : undefined, refresh };
}

export type Product = {
  id: number; slug: string; name: string; price: number; compareAt?: number; currency: string;
  category?: string; categorySlug?: string; available: number; hasVariants: boolean; image?: string; featured: boolean;
};
export type Category = { id: number; parentId?: number; name: string; slug: string; image?: string; count: number };
export type Catalog = { products: Product[]; total: number; page: number; size: number; priceCeiling: number };
export type Settings = {
  name: string; currency: string; email: string; phone?: string; country?: string;
  shippingRate?: number; freeShippingMinimum?: number; logo?: string; content: Record<string, string>;
};
export type Profile = {
  firstName: string; lastName: string; email: string; phone?: string; avatar?: string;
  orderCount: number; wishlistCount: number; addressCount: number;
};
export type AddressInput = { label: string; name: string; phone: string; line1: string; line2: string; city: string; state: string; postal: string; country: string };
export type Address = AddressInput & { id: number; primary: boolean };
export type Line = { id: number; productId: number; slug: string; name: string; variant?: string; quantity: number; price: number;
  currency: string; available: number; purchasable: boolean; image?: string };
export type Totals = { subtotal: number; shipping: number; discount: number; tax: number; total: number; currency: string };
export type Quote = Totals & { lines: Line[]; coupon?: string; available: boolean; invalidCoupon: boolean; deliveryAvailable: boolean; cashOnDelivery: boolean };
export type Order = Totals & { number: string; date: string; status: string; paymentStatus: string; address: AddressInput;
  method?: string; tracking?: string; delivery?: string; count: number; firstItem?: string; estimatedFrom?: string; estimatedTo?: string };
export type OrderDetail = { order: Order; items: { id: number; image?: string; name: string; quantity: number; variant?: string; total: number }[];
  steps: { status: string; date: string }[] };
export type Orders = { orders: Order[]; counts: Record<string, number>; size: number };

export const money = (value: number, currency: string) => new Intl.NumberFormat("en-US", { style: "currency", currency }).format(value);
export const day = (value: string) => new Date(value.length === 10 ? value + "T12:00:00" : value).toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
export const productUrl = (slug: string) => "/products/" + encodeURIComponent(slug);
export const orderUrl = (number: string) => "/account/orders/" + encodeURIComponent(number);
export const title = (value: string) => value.charAt(0).toUpperCase() + value.slice(1).toLowerCase();
export const imageUrl = (value?: string) => value?.startsWith("/api/") ? API + value : value;
