import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { finalize } from 'rxjs';
import { PropertyClaimResponse, PropertyClaimService } from '../../../core/services/property-claim.service';

@Component({
  selector: 'app-property-claims',
  standalone: true,
  imports: [CommonModule],
  template: `
    <main class="container mt-4 claim-page">
      <header class="page-heading">
        <div>
          <p class="eyebrow">Property governance</p>
          <h2>Property Claim Requests</h2>
        </div>
        <button type="button" class="btn btn-outline-primary" [disabled]="loading || claimActionBusy" (click)="loadClaims()">
          Refresh
        </button>
      </header>

      <div *ngIf="actionMessage" class="alert alert-success" role="status" aria-live="polite">
        {{ actionMessage }}
      </div>
      <div *ngIf="actionError" class="alert alert-danger" role="alert">
        {{ actionError }}
      </div>
      <div *ngIf="loadError" class="alert alert-danger" role="alert">
        {{ loadError }}
        <button type="button" class="btn btn-sm btn-outline-danger ms-2" (click)="loadClaims()">Retry</button>
      </div>

      <section class="card mt-3" aria-labelledby="claim-table-title">
        <div class="card-body">
          <h3 id="claim-table-title" class="visually-hidden">Property claim queue</h3>
          <div *ngIf="loading" class="claim-state" role="status" aria-live="polite">Loading claim requests...</div>
          <div *ngIf="!loading && !loadError && claims.length === 0" class="claim-state">No claim requests found.</div>

          <div *ngIf="!loading && claims.length > 0" class="table-responsive">
            <table class="table table-striped align-middle">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Property</th>
                  <th>Requester</th>
                  <th>Verification</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let claim of claims; trackBy: trackClaim">
                  <td>{{ claim.id }}</td>
                  <td>{{ claim.property?.name }} (ID: {{ claim.property?.id }})</td>
                  <td>{{ claim.requesterUser?.username }} (ID: {{ claim.requesterUser?.id }})</td>
                  <td>
                    <strong>{{ claim.verificationMethod }}</strong><br>
                    <small>{{ claim.verificationData }}</small>
                  </td>
                  <td>
                    <span class="badge" [ngClass]="{
                      'bg-warning': claim.status === 'PENDING',
                      'bg-success': claim.status === 'APPROVED',
                      'bg-danger': claim.status === 'REJECTED',
                      'bg-secondary': claim.status === 'CANCELLED'
                    }">{{ claim.status }}</span>
                  </td>
                  <td>
                    <ng-container *ngIf="claim.status === 'PENDING'">
                      <div class="claim-actions" *ngIf="approvalPromptId !== claim.id && rejectionPromptId !== claim.id">
                        <button
                          type="button"
                          class="btn btn-sm btn-success"
                          [disabled]="claimActionBusy"
                          (click)="requestApproval(claim)">
                          Approve
                        </button>
                        <button
                          type="button"
                          class="btn btn-sm btn-danger"
                          [disabled]="claimActionBusy"
                          (click)="requestRejection(claim)">
                          Reject
                        </button>
                      </div>
                      <div *ngIf="approvalPromptId === claim.id" class="approval-confirmation" role="group" [attr.aria-label]="'Confirm approval for claim ' + claim.id">
                        <p>Approve this request and activate the requester as an owner when the server confirms the lifecycle is valid?</p>
                        <div class="claim-actions">
                          <button
                            type="button"
                            class="btn btn-sm btn-success"
                            [disabled]="isApproving(claim.id)"
                            (click)="confirmApproval(claim)">
                            {{ isApproving(claim.id) ? 'Approving...' : 'Confirm approval' }}
                          </button>
                          <button
                            type="button"
                            class="btn btn-sm btn-outline-secondary"
                            [disabled]="isApproving(claim.id)"
                            (click)="cancelApproval()">
                            Cancel
                          </button>
                        </div>
                      </div>
                      <div *ngIf="rejectionPromptId === claim.id" class="rejection-editor" role="group" [attr.aria-label]="'Reject claim ' + claim.id">
                        <label [for]="'claim-rejection-' + claim.id">Rejection reason</label>
                        <textarea
                          [id]="'claim-rejection-' + claim.id"
                          rows="3"
                          maxlength="500"
                          [value]="rejectionReasons[claim.id] || ''"
                          [disabled]="isRejecting(claim.id)"
                          (input)="updateRejectionReason(claim.id, $event)"
                          [attr.aria-describedby]="'claim-rejection-help-' + claim.id"></textarea>
                        <div class="rejection-help" [id]="'claim-rejection-help-' + claim.id">
                          <span>{{ rejectionReasonError(claim.id) || 'Enter 10-500 characters.' }}</span>
                          <span>{{ (rejectionReasons[claim.id] || '').length }}/500</span>
                        </div>
                        <div class="claim-actions">
                          <button
                            type="button"
                            class="btn btn-sm btn-danger"
                            [disabled]="isRejecting(claim.id) || !!rejectionReasonError(claim.id)"
                            (click)="confirmRejection(claim)">
                            {{ isRejecting(claim.id) ? 'Rejecting...' : 'Confirm rejection' }}
                          </button>
                          <button
                            type="button"
                            class="btn btn-sm btn-outline-secondary"
                            [disabled]="isRejecting(claim.id)"
                            (click)="cancelRejection()">
                            Cancel
                          </button>
                        </div>
                      </div>
                    </ng-container>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </main>
  `,
  styles: [`
    :host{display:block}.claim-page{padding-bottom:48px}.page-heading{display:flex;align-items:flex-end;justify-content:space-between;gap:20px}.page-heading h2{margin:0}.eyebrow{margin:0 0 4px;color:#1764bd;font-size:12px;font-weight:800;letter-spacing:.12em;text-transform:uppercase}.claim-state{padding:34px;text-align:center;color:#64748b}.claim-actions{display:flex;flex-wrap:wrap;gap:8px}.approval-confirmation,.rejection-editor{min-width:260px;max-width:360px;padding:12px;border:1px solid #b8c7d9;border-radius:8px;background:#f8fafc}.approval-confirmation p{margin:0 0 10px;font-size:13px;line-height:1.5;color:#334155}.rejection-editor{display:grid;gap:8px}.rejection-editor label{font-size:13px;font-weight:800;color:#334155}.rejection-editor textarea{width:100%;padding:9px 10px;border:1px solid #94a3b8;border-radius:7px;resize:vertical}.rejection-help{display:flex;justify-content:space-between;gap:12px;font-size:12px;color:#9f1239}@media(max-width:640px){.page-heading{align-items:flex-start;flex-direction:column}.page-heading button{width:100%}}
  `]
})
export class PropertyClaimsComponent implements OnInit {
  private readonly propertyClaims = inject(PropertyClaimService);

  claims: PropertyClaimResponse[] = [];
  loading = true;
  loadError = '';
  actionMessage = '';
  actionError = '';
  approvalPromptId: number | null = null;
  approvingClaimId: number | null = null;
  rejectionPromptId: number | null = null;
  rejectingClaimId: number | null = null;
  readonly rejectionReasons: Record<number, string> = {};

  get approvalBusy(): boolean {
    return this.approvingClaimId !== null;
  }

  get claimActionBusy(): boolean {
    return this.approvingClaimId !== null || this.rejectingClaimId !== null;
  }

  ngOnInit(): void {
    this.loadClaims();
  }

  loadClaims(): void {
    if (this.claimActionBusy) return;
    this.loading = true;
    this.loadError = '';
    this.propertyClaims.list().pipe(
      finalize(() => this.loading = false)
    ).subscribe({
      next: response => this.claims = response.content,
      error: () => this.loadError = 'Unable to load property claim requests. Please retry.'
    });
  }

  requestApproval(claim: PropertyClaimResponse): void {
    if (claim.status !== 'PENDING' || this.claimActionBusy) return;
    this.rejectionPromptId = null;
    this.approvalPromptId = claim.id;
    this.actionMessage = '';
    this.actionError = '';
  }

  cancelApproval(): void {
    if (this.approvingClaimId === null) this.approvalPromptId = null;
  }

  confirmApproval(claim: PropertyClaimResponse): void {
    if (claim.status !== 'PENDING' || this.approvalPromptId !== claim.id || this.claimActionBusy) return;

    this.approvingClaimId = claim.id;
    this.actionMessage = '';
    this.actionError = '';
    this.propertyClaims.approve(claim.id).pipe(
      finalize(() => this.approvingClaimId = null)
    ).subscribe({
      next: approved => {
        if (!this.isCanonicalApproval(approved)) {
          this.actionError = 'The server did not confirm owner activation. Refresh the queue before continuing.';
          return;
        }
        this.claims = this.claims.map(current => current.id === approved.id ? approved : current);
        this.approvalPromptId = null;
        this.actionMessage = `Claim #${approved.id} was approved successfully.`;
      },
      error: (error: HttpErrorResponse) => {
        this.actionError = this.approvalErrorMessage(error);
      }
    });
  }

  isApproving(claimId: number): boolean {
    return this.approvingClaimId === claimId;
  }

  requestRejection(claim: PropertyClaimResponse): void {
    if (claim.status !== 'PENDING' || this.claimActionBusy) return;
    this.approvalPromptId = null;
    this.rejectionPromptId = claim.id;
    this.rejectionReasons[claim.id] = '';
    this.actionMessage = '';
    this.actionError = '';
  }

  cancelRejection(): void {
    if (this.rejectingClaimId !== null) return;
    if (this.rejectionPromptId !== null) delete this.rejectionReasons[this.rejectionPromptId];
    this.rejectionPromptId = null;
  }

  updateRejectionReason(claimId: number, event: Event): void {
    this.rejectionReasons[claimId] = (event.target as HTMLTextAreaElement).value;
  }

  rejectionReasonError(claimId: number): string {
    const reason = (this.rejectionReasons[claimId] ?? '').trim();
    if (reason.length < 10) return 'Reason must contain at least 10 characters.';
    if (reason.length > 500) return 'Reason must not exceed 500 characters.';
    return '';
  }

  confirmRejection(claim: PropertyClaimResponse): void {
    if (claim.status !== 'PENDING' || this.rejectionPromptId !== claim.id || this.claimActionBusy) return;
    if (this.rejectionReasonError(claim.id)) return;

    const reason = this.rejectionReasons[claim.id].trim();
    this.rejectingClaimId = claim.id;
    this.actionMessage = '';
    this.actionError = '';
    this.propertyClaims.reject(claim.id, reason).pipe(
      finalize(() => this.rejectingClaimId = null)
    ).subscribe({
      next: rejected => {
        if (rejected.status !== 'REJECTED') {
          this.actionError = 'The server did not confirm claim rejection. Refresh the queue before continuing.';
          return;
        }
        this.claims = this.claims.map(current => current.id === rejected.id ? rejected : current);
        delete this.rejectionReasons[claim.id];
        this.rejectionPromptId = null;
        this.actionMessage = `Claim #${rejected.id} was rejected successfully.`;
      },
      error: (error: HttpErrorResponse) => {
        this.actionError = this.rejectionErrorMessage(error);
      }
    });
  }

  isRejecting(claimId: number): boolean {
    return this.rejectingClaimId === claimId;
  }

  trackClaim(_index: number, claim: PropertyClaimResponse): number {
    return claim.id;
  }

  private approvalErrorMessage(error: HttpErrorResponse): string {
    if (error.status === 403) return 'You do not have permission to approve property claims.';
    if (error.status === 404) return 'This claim no longer exists. Refresh the queue before trying again.';
    if (error.status === 409) return this.claimConflictMessage(error);
    if (error.status === 400) {
      return 'This claim can no longer be approved from its current state. Refresh the queue.';
    }
    return 'Unable to approve this claim right now. Please retry.';
  }

  private rejectionErrorMessage(error: HttpErrorResponse): string {
    if (error.status === 403) return 'You do not have permission to reject property claims.';
    if (error.status === 404) return 'This claim no longer exists. Refresh the queue before trying again.';
    if (error.status === 400) return 'Enter a valid rejection reason between 10 and 500 characters.';
    if (error.status === 409) return this.claimConflictMessage(error);
    return 'Unable to reject this claim right now. Please retry.';
  }

  private claimConflictMessage(error: HttpErrorResponse): string {
    const body = error.error && typeof error.error === 'object'
      ? error.error as Record<string, unknown>
      : {};
    const code = typeof body['code'] === 'string' ? body['code'] : '';
    const currentState = typeof body['currentState'] === 'string' ? body['currentState'] : '';
    if (code === 'PROPERTY_CLAIM_CONFLICT') {
      return 'Another request changed this claim at the same time. Refresh the queue before continuing.';
    }
    if (code === 'PROPERTY_CLAIM_NOT_PENDING' && ['APPROVED', 'REJECTED', 'CANCELLED'].includes(currentState)) {
      return `This claim is already ${currentState.toLowerCase()}. Refresh the queue before continuing.`;
    }
    return 'This claim can no longer be changed from its current state. Refresh the queue.';
  }

  private isCanonicalApproval(claim: PropertyClaimResponse): boolean {
    return claim.status === 'APPROVED'
      && claim.property?.status === 'ACTIVE'
      && claim.property?.approvalStatus === 'APPROVED'
      && claim.property.operationStatus === 'ACTIVE';
  }
}
