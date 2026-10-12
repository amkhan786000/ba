/** Razorpay Checkout (https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/), loaded on first use. */

/** What Rahbar's order endpoints return for the payment window. */
export interface CheckoutOrder {
  key: string; order_id: string; amount: number; currency: string; name: string; description: string;
  prefill: { name?: string; email?: string; contact?: string }; test_mode: boolean;
}

/** What the payment window hands back after a successful payment (checked on the server). */
export interface CheckoutResult { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string }

const SCRIPT = 'https://checkout.razorpay.com/v1/checkout.js';
let loading: Promise<void> | null = null;

function load(): Promise<void> {
  if ((window as unknown as { Razorpay?: unknown }).Razorpay) return Promise.resolve();
  if (!loading) {
    loading = new Promise<void>((resolve, reject) => {
      const s = document.createElement('script');
      s.src = SCRIPT;
      s.async = true;
      s.onload = () => resolve();
      s.onerror = () => { loading = null; reject(new Error('The payment window could not be loaded. Check your connection and try again.')); };
      document.body.appendChild(s);
    });
  }
  return loading;
}

/**
 * Opens the payment window. Resolves with the payment's ids and signature when paid; rejects with a message when
 * the payer closes it or it fails. Only UPI, Indian cards and net banking are offered (set in the Razorpay account).
 */
export async function payWithRazorpay(order: CheckoutOrder): Promise<CheckoutResult> {
  await load();
  return new Promise<CheckoutResult>((resolve, reject) => {
    const Razorpay = (window as unknown as { Razorpay: new (o: Record<string, unknown>) => { open(): void; on(e: string, f: (r: { error?: { description?: string } }) => void): void } }).Razorpay;
    const rzp = new Razorpay({
      key: order.key,
      order_id: order.order_id,
      amount: order.amount,
      currency: order.currency,
      name: order.name,
      description: order.description,
      prefill: order.prefill,
      theme: { color: '#3d6b1f' },
      handler: (r: CheckoutResult) => resolve(r),
      modal: { ondismiss: () => reject(new Error('Payment cancelled.')) }
    });
    rzp.on('payment.failed', (r) => reject(new Error(r.error?.description || 'The payment did not go through.')));
    rzp.open();
  });
}
