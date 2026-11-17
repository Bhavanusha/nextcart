"use client";

import { useState } from "react";
import { AuthError, AuthFooter, AuthLink, AuthShell, preventDefault, submitAuthRequest } from "../../components/AuthShell";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const handleSubmit = preventDefault(async () => {
    setError("");
    setMessage("");
    try {
      const response = await submitAuthRequest<{ message: string }>("/forgot-password", { email });
      setMessage(response.message);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Unable to send the reset link.");
    }
  });

  return (
    <AuthShell title="Forgot Password" description="Enter your email address and we&apos;ll send you a secure link to reset your password and recover your account.">
      <form className="auth-form" onSubmit={handleSubmit}>
        <div className="field"><label htmlFor="email">Email address</label><input id="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></div>
        <AuthError message={error || message} />
        <button className="primary-button" type="submit">SEND RESET LINK</button>
      </form>
      <AuthFooter><AuthLink href="/login">← Back to sign in</AuthLink></AuthFooter>
    </AuthShell>
  );
}
