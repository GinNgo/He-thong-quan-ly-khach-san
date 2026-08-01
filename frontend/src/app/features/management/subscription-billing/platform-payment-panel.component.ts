import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  PlatformBillingService,
  PlatformOrder,
  PlatformOrderDetails,
  PlatformPaymentAttempt,
} from '../../../core/services/platform-billing.service';

@Component({
  selector: 'app-platform-payment-panel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="payment-panel" *ngIf="order" aria-labelledby="platform-payment-title">
      <div class="panel-heading">
        <div>
          <p class="eyebrow">Payment boundary</p>
          <h3 id="platform-payment-title">Simulator / sandbox status</h3>
        </div>
        <span class="environment" [attr.data-mode]="attempt?.environment || 'SIMULATOR'">
          {{ attempt?.environment || 'SIMULATOR' }}
        </span>
      </div>

      <p class="panel-copy">The backend creates and verifies the payment attempt. This screen cannot activate a subscription.</p>
      <div class="provider-form" *ngIf="!attempt">
        <label>Provider<select [(ngModel)]="provider"><option value="SIMULATOR">SIMULATOR</option><option value="MOMO">MOMO sandbox</option><option value="VNPAY">VNPAY sandbox</option><option value="ZALOPAY">ZALOPAY sandbox</option></select></label>
        <label>Method<select [(ngModel)]="method"><option [value]="provider">{{ provider }}</option></select></label>
        <button type="button" class="primary" [disabled]="busy" (click)="createAttempt()">{{ busy ? 'Creating...' : 'Create payment attempt' }}</button>
      </div>

      <div class="attempt-card" *ngIf="attempt">
        <div class="status-line"><span class="status-dot" [attr.data-status]="attempt.status"></span><strong>{{ statusTitle(attempt.status) }}</strong><small>{{ statusMessage(attempt.status) }}</small></div>
        <dl>
          <div><dt>Expected amount</dt><dd>{{ attempt.expectedAmount | number:'1.0-0' }} {{ attempt.currency }}</dd></div>
          <div><dt>Merchant</dt><dd>{{ attempt.merchantReferenceMasked || 'Masked by backend' }}</dd></div>
          <div><dt>Provider reference</dt><dd>{{ attempt.providerOrderReference }}</dd></div>
          <div><dt>Expires</dt><dd>{{ attempt.expiresAt | date:'dd/MM/yyyy HH:mm:ss' }}</dd></div>
        </dl>
        <div class="actions"><button type="button" class="secondary" [disabled]="busy" (click)="refreshStatus()">{{ busy ? 'Refreshing...' : 'Refresh server status' }}</button></div>
      </div>

      <p class="server-effect" *ngIf="orderStatus === 'APPLIED'">Subscription activation was applied by verified server evidence. No client activation action exists.</p>
      <p class="error" *ngIf="error" role="alert">{{ error }}</p>
    </section>
  `,
  styles: [`
    :host { display: block; }
    .payment-panel { margin: 1rem 0 2rem; padding: 1.25rem; border: 1px solid var(--hotel-border); border-radius: 1rem; background: #0f2f3a; color: #f8fafc; box-shadow: 0 16px 34px rgba(15,47,58,.18); }
    .panel-heading { display: flex; justify-content: space-between; gap: 1rem; align-items: flex-start; }.eyebrow { margin: 0; color: #7dd3c7; font-size: .7rem; font-weight: 800; letter-spacing: .13em; text-transform: uppercase; }.panel-heading h3 { margin: .25rem 0 0; font-size: 1.25rem; }.environment { padding: .3rem .6rem; border: 1px solid rgba(255,255,255,.2); border-radius: 999px; font-size: .7rem; font-weight: 800; }.environment[data-mode='PRODUCTION'] { color: #fecaca; border-color: #ef4444; }
    .panel-copy { color: rgba(248,250,252,.7); font-size: .85rem; }.provider-form { display: grid; grid-template-columns: 1fr 1fr auto; gap: .75rem; align-items: end; margin-top: 1rem; }.provider-form label { display: grid; gap: .35rem; color: #cbd5e1; font-size: .75rem; }.provider-form select { min-height: 2.55rem; padding: .5rem .65rem; border: 1px solid rgba(255,255,255,.2); border-radius: .6rem; background: #173f49; color: #fff; }.primary, .secondary { min-height: 2.55rem; padding: .5rem .8rem; border: 0; border-radius: .6rem; font: inherit; font-weight: 800; cursor: pointer; }.primary { color: #0f2f3a; background: #f0a35b; }.secondary { color: #d7f3e9; background: #176b68; }.primary:disabled, .secondary:disabled { opacity: .55; cursor: not-allowed; }
    .attempt-card { margin-top: 1rem; padding: 1rem; border: 1px solid rgba(255,255,255,.14); border-radius: .8rem; background: rgba(255,255,255,.06); }.status-line { display: grid; grid-template-columns: auto auto 1fr; gap: .55rem; align-items: center; }.status-line small { justify-self: end; color: #cbd5e1; }.status-dot { width: .65rem; height: .65rem; border-radius: 50%; background: #fbbf24; }.status-dot[data-status='SUCCESS'] { background: #34d399; box-shadow: 0 0 0 .25rem rgba(52,211,153,.13); }.status-dot[data-status='FAILED'], .status-dot[data-status='CANCELLED'], .status-dot[data-status='EXPIRED'] { background: #fb7185; }.attempt-card dl { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: .65rem; margin: 1rem 0; }.attempt-card dl div { display: grid; gap: .2rem; padding: .65rem; border-radius: .6rem; background: rgba(15,47,58,.65); }.attempt-card dt { color: #94a3b8; font-size: .7rem; }.attempt-card dd { margin: 0; overflow-wrap: anywhere; font-size: .85rem; font-weight: 700; }.actions { display: flex; justify-content: flex-end; }.server-effect { margin: 1rem 0 0; padding: .7rem; border-radius: .6rem; background: rgba(52,211,153,.12); color: #a7f3d0; font-size: .8rem; }.error { margin: 1rem 0 0; color: #fecdd3; }
    @media (max-width: 720px) { .provider-form { grid-template-columns: 1fr; }.attempt-card dl { grid-template-columns: 1fr; }.status-line { grid-template-columns: auto 1fr; }.status-line small { grid-column: 1 / -1; justify-self: start; }.actions .secondary { width: 100%; } }
  `],
})
export class PlatformPaymentPanelComponent {
  private readonly billing = inject(PlatformBillingService);

  @Input({ required: true }) order: PlatformOrder | null = null;
  @Output() orderChanged = new EventEmitter<PlatformOrderDetails>();

  provider = 'SIMULATOR';
  method = 'SIMULATOR';
  attempt: PlatformPaymentAttempt | null = null;
  orderStatus = '';
  busy = false;
  error = '';

  createAttempt(): void {
    if (!this.order || this.busy) return;
    this.busy = true;
    this.error = '';
    this.method = this.provider;
    this.billing.createPaymentAttempt(
      this.order.publicId,
      { provider: this.provider, method: this.method },
      this.idempotencyKey('platform-attempt'),
    ).subscribe({
      next: (attempt) => {
        this.attempt = attempt;
        this.busy = false;
      },
      error: (err) => {
        this.error = err?.error?.message || 'The server could not create a platform payment attempt.';
        this.busy = false;
      },
    });
  }

  refreshStatus(): void {
    if (!this.order || this.busy) return;
    this.busy = true;
    this.error = '';
    this.billing.getOrder(this.order.publicId).subscribe({
      next: (details) => {
        this.orderStatus = details.status;
        this.attempt = details.attempts.find((item) => item.publicId === this.attempt?.publicId)
          || details.attempts.at(-1)
          || this.attempt;
        this.busy = false;
        this.orderChanged.emit(details);
      },
      error: (err) => {
        this.error = err?.error?.message || 'The server status could not be refreshed.';
        this.busy = false;
      },
    });
  }

  statusTitle(status: PlatformPaymentAttempt['status']): string {
    return ({ CREATED: 'Created', PENDING: 'Waiting for provider', PROCESSING: 'Verifying provider evidence', SUCCESS: 'Payment verified', FAILED: 'Payment failed', CANCELLED: 'Attempt cancelled', EXPIRED: 'Attempt expired' })[status];
  }

  statusMessage(status: PlatformPaymentAttempt['status']): string {
    return ({ CREATED: 'Not submitted', PENDING: 'No entitlement change yet', PROCESSING: 'No entitlement change yet', SUCCESS: 'Server applies the subscription exactly once', FAILED: 'No activation', CANCELLED: 'No activation', EXPIRED: 'Create a new order or attempt' })[status];
  }

  private idempotencyKey(prefix: string): string {
    const randomUuid = globalThis.crypto?.randomUUID?.();
    return randomUuid ? `${prefix}-${randomUuid}` : `${prefix}-${Date.now()}`;
  }
}
