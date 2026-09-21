import { useState, type FormEvent } from "react";
import { authClient } from "../../auth/authClient";
import { useAuth } from "../../auth/AuthContext";
import "./LoginPage.css";

type Step = "credentials" | "mfa";

/**
 * User Story 1: password login for Director/Manager/Admin/Accounts Officer,
 * with an MFA step when enabled (acceptance scenario 3) and a generic error
 * on bad credentials (FR-015, acceptance scenario 2).
 */
export function LoginPage() {
  const { setTokens } = useAuth();
  const [step, setStep] = useState<Step>("credentials");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [password, setPassword] = useState("");
  const [mfaChallengeId, setMfaChallengeId] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleCredentialsSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const result = await authClient.login(phoneNumber, password);
      if (authClient.isMfaChallenge(result)) {
        setMfaChallengeId(result.mfaChallengeId);
        setStep("mfa");
      } else {
        setTokens(result);
      }
    } catch {
      // FR-015: deliberately generic — never reveals which field was wrong.
      setError("Incorrect phone number or password.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleMfaSubmit(event: FormEvent) {
    event.preventDefault();
    if (!mfaChallengeId) return;
    setError(null);
    setSubmitting(true);
    try {
      const tokens = await authClient.verifyMfa(mfaChallengeId, code);
      setTokens(tokens);
    } catch {
      setError("Incorrect or expired code.");
    } finally {
      setSubmitting(false);
    }
  }

  if (step === "mfa") {
    return (
      <form className="login-page" data-testid="mfa-form" onSubmit={handleMfaSubmit}>
        <h1>Enter your verification code</h1>
        <label>
          Code
          <input
            data-testid="mfa-code-input"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            autoComplete="one-time-code"
          />
        </label>
        {error && <p className="login-page__error" data-testid="login-error">{error}</p>}
        <button type="submit" disabled={submitting}>
          Verify
        </button>
      </form>
    );
  }

  return (
    <form className="login-page" data-testid="login-form" onSubmit={handleCredentialsSubmit}>
      <h1>Sign in</h1>
      <label>
        Phone number
        <input
          data-testid="phone-input"
          value={phoneNumber}
          onChange={(e) => setPhoneNumber(e.target.value)}
          autoComplete="username"
        />
      </label>
      <label>
        Password
        <input
          data-testid="password-input"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
        />
      </label>
      {error && <p className="login-page__error" data-testid="login-error">{error}</p>}
      <button type="submit" disabled={submitting}>
        Sign in
      </button>
    </form>
  );
}
