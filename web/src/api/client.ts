import type { components } from "./schema";

export type Payment = components["schemas"]["Payment"];
export type PaymentPage = components["schemas"]["PaymentPage"];
export type PaymentStatus = components["schemas"]["PaymentStatus"];
export type Problem = components["schemas"]["Problem"];

/** Error carrying the RFC 9457 problem body returned by the API. */
export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly problem: Problem | undefined,
  ) {
    super(problem?.detail ?? `Request failed with status ${status}`);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, init);
  if (!res.ok) {
    const problem = await res.json().catch(() => undefined);
    throw new ApiError(res.status, problem);
  }
  return (await res.json()) as T;
}

export const listPayments = (status?: PaymentStatus) =>
  request<PaymentPage>(`/v1/payments?limit=50${status ? `&status=${status}` : ""}`);

export const createPayment = (amount: number, currency: string, reference: string) =>
  request<Payment>("/v1/payments", {
    method: "POST",
    headers: { "Content-Type": "application/json", "Idempotency-Key": crypto.randomUUID() },
    body: JSON.stringify({ amount, currency, reference }),
  });

export const capturePayment = (id: string) => request<Payment>(`/v1/payments/${id}/capture`, { method: "POST" });
export const cancelPayment = (id: string) => request<Payment>(`/v1/payments/${id}/cancel`, { method: "POST" });
