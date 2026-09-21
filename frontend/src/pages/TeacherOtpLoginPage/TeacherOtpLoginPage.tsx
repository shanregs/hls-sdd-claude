import { useState, type FormEvent } from "react";
import { authClient } from "../../auth/authClient";
import { useAuth } from "../../auth/AuthContext";
import "../LoginPage/LoginPage.css";

type Step = "request" | "verify";

/** User Story 2: Teacher OTP login, from the web portal channel (FR-008). */
export function TeacherOtpLoginPage() {
  const { setTokens } = useAuth();
  const [step, setStep] = useState<Step>("request");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleRequestOtp(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    try {
      await authClient.requestOtp(phoneNumber);
      setStep("verify");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleVerify(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const tokens = await authClient.verifyOtp(phoneNumber, code, "WEB");
      setTokens(tokens);
    } catch {
      // Acceptance scenario 3: denied, but the Teacher can request a new OTP.
      setError("That code is incorrect or has expired. You can request a new one.");
    } finally {
      setSubmitting(false);
    }
  }

  if (step === "verify") {
    return (
      <form className="login-page" data-testid="otp-verify-form" onSubmit={handleVerify}>
        <h1>Enter your login code</h1>
        <label>
          Code
          <input data-testid="otp-code-input" value={code} onChange={(e) => setCode(e.target.value)} />
        </label>
        {error && <p className="login-page__error" data-testid="otp-error">{error}</p>}
        <button type="submit" disabled={submitting}>
          Sign in
        </button>
        <button type="button" onClick={() => setStep("request")} disabled={submitting}>
          Request a new code
        </button>
      </form>
    );
  }

  return (
    <form className="login-page" data-testid="otp-request-form" onSubmit={handleRequestOtp}>
      <h1>Teacher sign in</h1>
      <label>
        Phone number
        <input data-testid="otp-phone-input" value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} />
      </label>
      <button type="submit" disabled={submitting}>
        Send login code
      </button>
    </form>
  );
}
