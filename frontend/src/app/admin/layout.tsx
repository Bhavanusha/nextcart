
import type { Metadata } from "next";
import type { ReactNode } from "react";
import AdminConsole from "./AdminConsole";
import "./admin.css";

export const metadata: Metadata = {
  title: "HAVEN Admin",
  description: "Store administration for HAVEN.",
};

export default function AdminLayout({ children }: { children: ReactNode }) {
  return <AdminConsole>{children}</AdminConsole>;
}
