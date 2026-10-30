import { FormEvent, useCallback, useEffect, useState } from "react";
import { ApiError, Payment, PaymentStatus, cancelPayment, capturePayment, createPayment, listPayments } from "./api/client";
import { formatMoney, toMinorUnits } from "./money";

const STATUSES: (PaymentStatus | "")[] = ["", "AUTHORISED", "CAPTURED", "CANCELLED"];

export function App() {
  const [payments, setPayments] = useState<Payment[]>([]);
  const [filter, setFilter] = useState<PaymentStatus | "">("");
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      setPayments((await listPayments(filter || undefined)).items);
      setError(undefined);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unexpected error");
    } finally {
      setLoading(false);
    }
  }, [filter]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const act = async (fn: () => Promise<unknown>) => {
    try {
      await fn();
      await refresh();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Unexpected error");
    }
  };

  return (
    <main>
      <h1>Payments</h1>
      <NewPaymentForm onCreate={(a, c, r) => act(() => createPayment(a, c, r))} />
      {error && <p role="alert" className="error">{error}</p>}
      <label>
        Status{" "}
        <select value={filter} onChange={(e) => setFilter(e.target.value as PaymentStatus | "")}>
          {STATUSES.map((s) => (
            <option key={s} value={s}>{s || "All"}</option>
          ))}
        </select>
      </label>
      {loading ? (
        <p>Loading…</p>
      ) : (
        <table>
          <thead>
            <tr><th>Reference</th><th>Amount</th><th>Status</th><th>Created</th><th /></tr>
          </thead>
          <tbody>
            {payments.map((p) => (
              <tr key={p.id}>
                <td>{p.reference}</td>
                <td>{formatMoney(p.amount, p.currency)}</td>
                <td><span className={`badge ${p.status.toLowerCase()}`}>{p.status}</span></td>
                <td>{new Date(p.createdAt).toLocaleString()}</td>
                <td>
                  {p.status === "AUTHORISED" && (
                    <>
                      <button onClick={() => act(() => capturePayment(p.id))}>Capture</button>{" "}
                      <button className="secondary" onClick={() => act(() => cancelPayment(p.id))}>Cancel</button>
                    </>
                  )}
                </td>
              </tr>
            ))}
            {payments.length === 0 && <tr><td colSpan={5}>No payments yet.</td></tr>}
          </tbody>
        </table>
      )}
    </main>
  );
}

function NewPaymentForm({ onCreate }: { onCreate: (amount: number, currency: string, reference: string) => void }) {
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState("GBP");
  const [reference, setReference] = useState("");
  const minor = toMinorUnits(amount);
  const valid = minor !== undefined && /^[A-Z]{3}$/.test(currency) && reference.trim().length > 0;

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!valid) return;
    onCreate(minor, currency, reference.trim());
    setAmount("");
    setReference("");
  };

  return (
    <form onSubmit={submit}>
      <input aria-label="Amount" placeholder="Amount (15.00)" value={amount} onChange={(e) => setAmount(e.target.value)} />
      <input aria-label="Currency" maxLength={3} value={currency} onChange={(e) => setCurrency(e.target.value.toUpperCase())} />
      <input aria-label="Reference" placeholder="Reference" maxLength={64} value={reference} onChange={(e) => setReference(e.target.value)} />
      <button disabled={!valid}>Authorise</button>
    </form>
  );
}
