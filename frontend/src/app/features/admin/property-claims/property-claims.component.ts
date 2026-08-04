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
        <button type="button" class="btn btn-outline-primary" [disabled]="loading || approvalBusy" (click)="loadClaims()">
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
                      <div class="claim-actions" *ngIf="approvalPromptId !== claim.id; else approvalPrompt">
                        <button
                          type="button"
                          class="btn btn-sm btn-success"
                          [disabled]="approvalBusy"
                          (click)="requestApproval(claim)">
                          Approve
                        </button>
                        <button
                          type="button"
                          class="btn btn-sm btn-danger"
                          [disabled]="approvalBusy"
                          (click)="reject(claim.id)">
                          Reject
                        </button>
                      </div>
                      <ng-template #approvalPrompt>
                        <div class="approval-confirmation" role="group" [attr.aria-label]="'Confirm approval for claim ' + claim.id">
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
                      </ng-template>
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
    :host{display:block}.claim-page{padding-bottom:48px}.page-heading{display:flex;align-items:flex-end;justify-content:space-between;gap:20px}.page-heading h2{margin:0}.eyebrow{margin:0 0 4px;color:#1764bd;font-size:12px;font-weight:800;letter-spacing:.12em;text-transform:uppercase}.claim-state{padding:34px;text-align:center;color:#64748b}.claim-actions{display:flex;flex-wrap:wrap;gap:8px}.approval-confirmation{min-width:260px;max-width:360px;padding:12px;border:1px solid #b8c7d9;border-radius:8px;background:#f8fafc}.approval-confirmation p{margin:0 0 10px;font-size:13px;line-height:1.5;color:#334155}@media(max-width:640px){.page-heading{align-items:flex-start;flex-direction:column}.page-heading button{width:100%}}
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

  get approvalBusy(): boolean {
    return this.approvingClaimId !== null;
  }

  ngOnInit(): void {
    this.loadClaims();
  }

  loadClaims(): void {
    if (this.approvalBusy) return;
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
    if (claim.status !== 'PENDING' || this.approvingClaimId !== null) return;
    this.approvalPromptId = claim.id;
    this.actionMessage = '';
    this.actionError = '';
  }

  cancelApproval(): void {
    if (this.approvingClaimId === null) this.approvalPromptId = null;
  }

  confirmApproval(claim: PropertyClaimResponse): void {
    if (claim.status !== 'PENDING' || this.approvalPromptId !== claim.id || this.approvingClaimId !== null) return;

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

  reject(id: number): void {
    const reason = prompt('Enter rejection reason:');
    if (reason !== null) {
      this.propertyClaims.reject(id, reason).subscribe({
        next: () => this.loadClaims(),
        error: () => this.actionError = 'Unable to reject this claim. Please retry.'
      });
    }
  }

  trackClaim(_index: number, claim: PropertyClaimResponse): number {
    return claim.id;
  }

  private approvalErrorMessage(error: HttpErrorResponse): string {
    if (error.status === 403) return 'You do not have permission to approve property claims.';
    if (error.status === 404) return 'This claim no longer exists. Refresh the queue before trying again.';
    if (error.status === 400 || error.status === 409) {
      return 'This claim can no longer be approved from its current state. Refresh the queue.';
    }
    return 'Unable to approve this claim right now. Please retry.';
  }

  private isCanonicalApproval(claim: PropertyClaimResponse): boolean {
    return claim.status === 'APPROVED'
      && claim.property?.status === 'ACTIVE'
      && claim.property?.approvalStatus === 'APPROVED'
      && claim.property.operationStatus === 'ACTIVE';
  }
}
