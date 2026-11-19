import type { ReactNode } from "react";
import StoreShell from "./StoreShell";
import "./store.css";

export default function Layout({ children }: { children: ReactNode }) {
  return <StoreShell>{children}</StoreShell>;
}
