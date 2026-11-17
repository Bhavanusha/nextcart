"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { AuthError, AuthFooter, AuthLink, AuthShell, PasswordField, preventDefault, submitAuthRequest } from "../../components/AuthShell";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(false);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = preventDefault(async () => {
    setSubmitting(true);
    setError("");
    try {
      await submitAuthRequest("/login", { email, password });
      router.push("/");
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Unable to sign in.");
    } finally {
      setSubmitting(false);
    }
  });

  return (
    <AuthShell title="Welcome Back" description="Sign in to your account to manage orders and access saved picks.">
      <form className="auth-form" onSubmit={handleSubmit}>
        <div className="field">
          <label htmlFor="email">Email address</label>
          <input id="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
        </div>
        <PasswordField label="Password" name="password" value={password} onChange={setPassword} />
        <div className="auth-row">
          <label className="checkbox">
            <input type="checkbox" checked={remember} onChange={(event) => setRemember(event.target.checked)} />
            Remember me
          </label>
          <AuthLink href="/forgot-password">Forgot password?</AuthLink>
        </div>
        <AuthError message={error} />
        <button className="primary-button" type="submit" disabled={submitting}>
          {submitting ? "SIGNING IN..." : "SIGN IN"}
        </button>
      </form>
      <AuthFooter>Don&apos;t have an account? <AuthLink href="/register">Create one</AuthLink></AuthFooter>
    </AuthShell>
  );
}
