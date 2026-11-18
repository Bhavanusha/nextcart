"use client";

import { useState } from "react";
import { AuthError, AuthFooter, AuthLink, AuthShell, PasswordField, preventDefault, submitAuthRequest } from "../../components/AuthShell";

export default function ResetPasswordPage() {
  const [token, setToken] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const handleSubmit = preventDefault(async () => {
    setError("");
    setMessage("");
    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }
    try {
      const response = await submitAuthRequest<{ message: string }>("/reset-password", { token, password });
      setMessage(response.message);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Unable to reset your password.");
    }
  });

  return (
    <AuthShell title="Reset Password" description="Enter the reset token from your email and choose a new secure password.">
      <form className="auth-form" onSubmit={handleSubmit}>
        <div className="field"><label htmlFor="token">Reset token</label><input id="token" value={token} onChange={(event) => setToken(event.target.value)} required /></div>
        <PasswordField label="New password" name="password" value={password} onChange={setPassword} />
        <PasswordField label="Confirm new password" name="confirmPassword" value={confirmPassword} onChange={setConfirmPassword} />
        <AuthError message={error || message} />
        <button className="primary-button" type="submit">RESET PASSWORD</button>
      </form>
      <AuthFooter><AuthLink href="/login">← Back to sign in</AuthLink></AuthFooter>
    </AuthShell>
  );
}
