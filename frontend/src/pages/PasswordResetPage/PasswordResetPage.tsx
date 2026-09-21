import { useState, type FormEvent } from "react";
import { authClient } from "../../auth/authClient";
import "../LoginPage/LoginPage.css";

type Step = "request" | "confirm" | "done";

/** FR-016, User Story 1 acceptance scenario 4: self-service password reset. */
export function PasswordResetPage() {
  const [step, setStep] = useState<Step>("request");
  const [identifier, setIdentifier] = useState("");
  const [resetToken, setResetToken] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleRequest(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    try {
      // FR-015: always proceeds identically, whether or not the identifier is registered.
      await authClient.requestPasswordReset(identifier);
      setStep("confirm");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleConfirm(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await authClient.confirmPasswordReset(resetToken, newPassword);
      setStep("done");
    } catch {
      setError("That code/link is invalid or has expired.");
    } finally {
      setSubmitting(false);
    }
  }

  if (step === "done") {
    return (
      <div className="login-page" data-testid="reset-done">
        <h1>Password updated</h1>
        <p>You can now sign in with your new password.</p>
      </div>
    );
  }

  if (step === "confirm") {
    return (
      <form className="login-page" data-testid="reset-confirm-form" onSubmit={handleConfirm}>
        <h1>Set a new password</h1>
        <label>
          Reset code/link
          <input data-testid="reset-token-input" value={resetToken} onChange={(e) => setResetToken(e.target.value)} />
        </label>
        <label>
          New password
          <input
            data-testid="new-password-input"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
          />
        </label>
        {error && <p className="login-page__error" data-testid="reset-error">{error}</p>}
        <button type="submit" disabled={submitting}>
          Set new password
        </button>
      </form>
    );
  }

  return (
    <form className="login-page" data-testid="reset-request-form" onSubmit={handleRequest}>
      <h1>Reset your password</h1>
      <label>
        Phone number or email
        <input data-testid="identifier-input" value={identifier} onChange={(e) => setIdentifier(e.target.value)} />
      </label>
      <button type="submit" disabled={submitting}>
        Send reset code
      </button>
    </form>
  );
}
