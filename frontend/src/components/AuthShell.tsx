"use client";

import Link from "next/link";
import { FormEvent, ReactNode, useState } from "react";
import "../app/auth.css";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export async function submitAuthRequest<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${API_URL}/api/auth${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify(body),
  });

  const data = (await response.json()) as T & { detail?: string; message?: string };
  if (!response.ok) {
    throw new Error(data.detail ?? data.message ?? "Something went wrong. Please try again.");
  }
  return data;
}

export function AuthShell({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <main className="auth-page">
      <div className="auth-image" aria-label="Warm interior" role="img" />
      <section className="auth-panel">
        <div className="auth-content">
          <div className="brand">NEXTCART</div>
          <h1>{title}</h1>
          <p className="auth-intro">{description}</p>
          {children}
        </div>
      </section>
    </main>
  );
}

export function PasswordField({
  label,
  name,
  value,
  onChange,
  required = true,
}: {
  label: string;
  name: string;
  value: string;
  onChange: (value: string) => void;
  required?: boolean;
}) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="field">
      <label htmlFor={name}>{label}</label>
      <div className="password-input">
        <input
          id={name}
          name={name}
          type={visible ? "text" : "password"}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          required={required}
        />
        <button
          className="password-toggle"
          type="button"
          aria-label={visible ? "Hide password" : "Show password"}
          onClick={() => setVisible((current) => !current)}
        >
          {visible ? "Hide" : "Show"}
        </button>
      </div>
    </div>
  );
}

export function AuthError({ message }: { message: string }) {
  return message ? <p className="auth-message" role="alert">{message}</p> : null;
}

export function AuthFooter({ children }: { children: ReactNode }) {
  return <div className="auth-footer">{children}</div>;
}

export function preventDefault(handler: () => Promise<void>) {
  return async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await handler();
  };
}

export function AuthLink({ href, children }: { href: string; children: ReactNode }) {
  return <Link className="auth-link" href={href}>{children}</Link>;
}
