import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { PropertyOwnerMembership, PropertyOwnershipService } from '../../../core/services/property-ownership.service';

@Component({
  selector: 'app-owner-invitation-accept',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <main class="invitation-page">
      <section class="invitation-card">
        <p class="eyebrow">Owner invitation</p>
        <h1>Join a property as co-owner</h1>
        <p>The invitation grants no authority until the authenticated, verified invited account accepts the owner terms.</p>

        <div *ngIf="error" class="feedback error" role="alert">{{ error }}</div>
        <div *ngIf="accepted" class="accepted" role="status" aria-live="polite">
          <strong>Invitation accepted</strong>
          <span>{{ accepted.email }} is now {{ accepted.role }}.</span>
          <span>The 7-day owner cooling period starts from the server acceptance time.</span>
          <a routerLink="/">Return to LuxeStay</a>
        </div>

        <ng-container *ngIf="!accepted">
          <label class="terms">
            <input type="checkbox" [(ngModel)]="ownerTermsAccepted" [disabled]="submitting || !token">
            <span>I accept the owner terms, retained audit history, property-level financial responsibility boundaries, and the 7-day cooling period.</span>
          </label>
          <button type="button" [disabled]="submitting || !token || !ownerTermsAccepted" (click)="accept()">
            {{ submitting ? 'Accepting...' : 'Accept invitation' }}
          </button>
        </ng-container>
      </section>
    </main>
  `,
  styles: [`
    :host{display:block}.invitation-page{min-height:100vh;display:grid;place-items:center;padding:24px;background:radial-gradient(circle at top left,#dbeafe,transparent 34%),#f6f8fb}.invitation-card{display:grid;gap:18px;width:min(620px,100%);padding:34px;border:1px solid #d8e2ee;border-radius:22px;background:#fff;box-shadow:0 24px 70px rgba(15,42,67,.12)}h1{margin:0;color:#102a43}.invitation-card>p{margin:0;color:#52677d;line-height:1.65}.eyebrow{color:#1764bd!important;font-size:12px;font-weight:900;letter-spacing:.13em;text-transform:uppercase}.terms{display:flex;align-items:flex-start;gap:10px;padding:15px;border-radius:13px;background:#f4f7fb;color:#334155;line-height:1.55}.terms input{margin-top:4px}button,a{display:inline-flex;min-height:44px;align-items:center;justify-content:center;padding:0 18px;border:0;border-radius:11px;background:#123f73;color:#fff;text-decoration:none;font-weight:800}button:disabled{opacity:.55}.feedback{padding:13px;border-radius:11px}.feedback.error{background:#fff1f2;color:#9f1239}.accepted{display:grid;gap:8px;padding:18px;border-radius:14px;background:#ecfdf5;color:#166534}.accepted a{justify-self:start;margin-top:8px}
  `]
})
export class OwnerInvitationAcceptComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly ownership = inject(PropertyOwnershipService);

  token = '';
  ownerTermsAccepted = false;
  submitting = false;
  error = '';
  accepted: PropertyOwnerMembership | null = null;

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token')?.trim() ?? '';
    if (!this.token) this.error = 'This invitation link is incomplete or invalid.';
  }

  accept(): void {
    if (!this.token || !this.ownerTermsAccepted || this.submitting) return;
    this.submitting = true;
    this.error = '';
    this.ownership.acceptInvitation(this.token, true).pipe(
      finalize(() => this.submitting = false)
    ).subscribe({
      next: membership => {
        this.accepted = membership;
        this.token = '';
      },
      error: error => this.error = this.safeError(error)
    });
  }

  private safeError(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) return 'The invitation could not be accepted. Please retry.';
    const body = error.error && typeof error.error === 'object' ? error.error as Record<string, unknown> : {};
    const code = typeof body['code'] === 'string' ? body['code'] : '';
    if (error.status === 403) return 'Sign in with the verified email address that received this invitation.';
    if (error.status === 409 || ['OWNER_INVITATION_EXPIRED', 'OWNER_INVITATION_INVALID'].includes(code)) {
      return 'This invitation is expired, cancelled, already used, or no longer valid.';
    }
    if (error.status === 400) return 'Accept the owner terms before continuing.';
    return 'The invitation could not be accepted. Please retry.';
  }
}
