"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { AuthError, AuthFooter, AuthLink, AuthShell, PasswordField, preventDefault, submitAuthRequest } from "../../components/AuthShell";

export default function RegisterPage() {
  const router = useRouter();
  const [form, setForm] = useState({ firstName: "", lastName: "", email: "", phone: "", password: "", confirmPassword: "" });
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const update = (key: keyof typeof form, value: string) => setForm((current) => ({ ...current, [key]: value }));
  const handleSubmit = preventDefault(async () => {
    if (form.password !== form.confirmPassword) {
      setError("Passwords do not match.");
      return;
    }
    setSubmitting(true);
    setError("");
    try {
      await submitAuthRequest("/register", form);
      router.push("/login");
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Unable to create your account.");
    } finally {
      setSubmitting(false);
    }
  });

  return (
    <AuthShell title="Create Account" description="Join us to track orders, curate wishlists, and receive members-only collections.">
      <form className="auth-form" onSubmit={handleSubmit}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
          <div className="field"><label htmlFor="firstName">First name</label><input id="firstName" value={form.firstName} onChange={(event) => update("firstName", event.target.value)} required /></div>
          <div className="field"><label htmlFor="lastName">Last name</label><input id="lastName" value={form.lastName} onChange={(event) => update("lastName", event.target.value)} required /></div>
        </div>
        <div className="field"><label htmlFor="email">Email address</label><input id="email" type="email" value={form.email} onChange={(event) => update("email", event.target.value)} required /></div>
        <div className="field"><label htmlFor="phone">Phone number</label><input id="phone" type="tel" value={form.phone} onChange={(event) => update("phone", event.target.value)} /></div>
        <PasswordField label="Password" name="password" value={form.password} onChange={(value) => update("password", value)} />
        <PasswordField label="Confirm password" name="confirmPassword" value={form.confirmPassword} onChange={(value) => update("confirmPassword", value)} />
        <AuthError message={error} />
        <button className="primary-button" type="submit" disabled={submitting}>{submitting ? "CREATING ACCOUNT..." : "CREATE ACCOUNT"}</button>
      </form>
      <AuthFooter>Already have an account? <AuthLink href="/login">Sign in</AuthLink></AuthFooter>
    </AuthShell>
  );
}
