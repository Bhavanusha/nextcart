
import type { ReactNode } from "react";

const shapes: Record<string, ReactNode> = {
  dashboard: <><rect x="3" y="3" width="7" height="10" rx="1" /><rect x="14" y="3" width="7" height="6" rx="1" /><rect x="3" y="17" width="7" height="4" rx="1" /><rect x="14" y="13" width="7" height="8" rx="1" /></>,
  products: <><path d="M12 2.5 21 7v10l-9 4.5L3 17V7l9-4.5Z" /><path d="M3 7l9 5 9-5M12 12v9.5M7.5 5 17 10.1" /></>,
  categories: <><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></>,
  inventory: <><ellipse cx="12" cy="5" rx="8" ry="3" /><path d="M4 5v14c0 4 16 4 16 0V5M4 12c0 4 16 4 16 0" /></>,
  orders: <><rect x="4" y="4" width="16" height="17" rx="2" /><path d="M4 8h16M9 11a3 3 0 0 0 6 0" /></>,
  customers: <><circle cx="9" cy="8" r="3.5" /><path d="M2.5 21v-2a4 4 0 0 1 4-4h5a4 4 0 0 1 4 4v2M16 5a3.5 3.5 0 0 1 0 7M17.5 15a4 4 0 0 1 4 4v2" /></>,
  reviews: <path d="M20 17H8l-5 4V5a2 2 0 0 1 2-2h15a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1Z" />,
  coupons: <><path d="M3 3v8l9 9a2 2 0 0 0 3 0l5-5a2 2 0 0 0 0-3l-9-9H3Z" /><circle cx="8" cy="8" r="1" /></>,
  reports: <path d="M5 21v-7m7 7V9m7 12V3" />,
  settings: <><circle cx="12" cy="12" r="3" /><path d="m10 2-.7 3-2 .9-2.7-1.4-2 3.5 2.2 2.2v2.6l-2.2 2.2 2 3.5 2.7-1.4 2 .9.7 3h4l.7-3 2-.9 2.7 1.4 2-3.5-2.2-2.2v-2.6l2.2-2.2-2-3.5-2.7 1.4-2-.9L14 2h-4Z" /></>,
  bell: <><path d="M18 8a6 6 0 0 0-12 0c0 6-3 6-3 8h18c0-2-3-2-3-8ZM10 20a2 2 0 0 0 4 0" /></>,
  edit: <><path d="m4 16-1 5 5-1L20 8a2 2 0 0 0 0-3l-1-1a2 2 0 0 0-3 0L4 16ZM14 6l4 4" /></>,
  trash: <><path d="M3 6h18M6 6l1 15h10l1-15M9 6V3h6v3m-5 4v7m4-7v7" /></>,
  eye: <><path d="M2 12s4-6 10-6 10 6 10 6-4 6-10 6S2 12 2 12Z" /><circle cx="12" cy="12" r="2.5" /></>,
  hidden: <><path d="M3 3l18 18M10 6a11 11 0 0 1 2 0c6 0 10 6 10 6a15 15 0 0 1-3 3M6 7a16 16 0 0 0-4 5s4 6 10 6a11 11 0 0 0 4-1" /></>,
  check: <path d="m5 12 4 4L19 6" />,
  download: <path d="M12 3v12m-4-4 4 4 4-4M4 15v5h16v-5" />,
  upload: <><path d="M5 17a5 5 0 0 1 0-10 7 7 0 0 1 13 1 5 5 0 0 1 1 9M12 21V11m-4 4 4-4 4 4" /></>,
  arrow: <path d="m11 6-6 6 6 6m-6-6h14" />,
  chevron: <path d="m6 9 6 6 6-6" />,
};

export default function Icon({ name, size = 18 }: { name: string; size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9"
    strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{shapes[name]}</svg>;
}
