import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { FeedbackStateComponent } from '../../../shared/components/feedback-state/feedback-state.component';

interface ClaimPropertySummary {
  id: number;
  code: string | null;
  name: string | null;
  approvalStatus: string | null;
  operationStatus: string | null;
}

interface ClaimUserSummary {
  id: number;
  username: string | null;
  email: string | null;
  fullName: string | null;
}

interface PropertyClaimResponse {
  id: number;
  property: ClaimPropertySummary | null;
  requesterUser: ClaimUserSummary | null;
  verificationMethod: string | null;
  verificationData: string | null;
  note: string | null;
  status: string;
  reviewedBy: ClaimUserSummary | null;
  reviewedAt: string | null;
  rejectionReason: string | null;
  createdAt: string | null;
}

interface PropertyClaimPage {
  content: PropertyClaimResponse[];
}

@Component({
  selector: 'app-property-claims',
  standalone: true,
  imports: [CommonModule, FeedbackStateComponent],
  template: `
    <div class="container mt-4">
      <h2>Property Claim Requests</h2>

      <app-feedback-state *ngIf="loading" state="loading" title="Loading property claims"
        message="Checking the property claim review queue." />
      <app-feedback-state *ngIf="!loading && loadError" state="error" title="Property claims unavailable"
        [message]="loadError" actionLabel="Retry" (actionTriggered)="loadClaims()" />
      <app-feedback-state *ngIf="!loading && !loadError && claims.length === 0" state="empty"
        title="No property claims" message="New ownership claims will appear here for review." />
      <p *ngIf="actionError" class="alert alert-danger mt-3" role="alert">
        {{ actionError }} <button type="button" class="btn btn-link p-0" (click)="loadClaims()">Reload queue</button>
      </p>

      <div class="card mt-3" *ngIf="!loading && !loadError && claims.length > 0">
        <div class="card-body">
          <table class="table table-striped">
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
              <tr *ngFor="let claim of claims">
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
                    'bg-danger': claim.status === 'REJECTED'
                  }">{{ claim.status }}</span>
                </td>
                <td>
                  <button class="btn btn-sm btn-success me-2" *ngIf="claim.status === 'PENDING'"
                    [disabled]="actionClaimId === claim.id" (click)="approve(claim.id)">Approve</button>
                  <button class="btn btn-sm btn-danger" *ngIf="claim.status === 'PENDING'"
                    [disabled]="actionClaimId === claim.id" (click)="reject(claim.id)">Reject</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `
})
export class PropertyClaimsComponent implements OnInit {
  claims: PropertyClaimResponse[] = [];
  loading = false;
  loadError = '';
  actionError = '';
  actionClaimId: number | null = null;

  constructor(private http: HttpClient, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.loadClaims();
  }

  loadClaims() {
    this.loading = true;
    this.loadError = '';
    this.http.get<PropertyClaimPage | PropertyClaimResponse[]>(`${environment.apiUrl}/admin/property-claims`).subscribe({
      next: (res) => {
        this.claims = Array.isArray(res) ? res : res.content;
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.loading = false;
        this.loadError = err?.error?.message || 'Unable to load property claims. Check access and retry.';
        this.cdr.markForCheck();
      }
    });
  }

  approve(id: number) {
    if (confirm('Are you sure you want to approve this claim? The user will become the OWNER of this property.')) {
      this.actionClaimId = id;
      this.actionError = '';
      this.http.post(`${environment.apiUrl}/admin/property-claims/${id}/approve`, {}).subscribe({
        next: () => { this.actionClaimId = null; this.loadClaims(); this.cdr.markForCheck(); },
        error: (err) => {
          this.actionClaimId = null;
          this.actionError = err?.error?.message || 'Unable to approve this claim. Reload the queue before retrying.';
          this.cdr.markForCheck();
        }
      });
    }
  }

  reject(id: number) {
    const reason = prompt('Enter rejection reason:');
    if (reason !== null) {
      this.actionClaimId = id;
      this.actionError = '';
      this.http.post(`${environment.apiUrl}/admin/property-claims/${id}/reject`, { reason }).subscribe({
        next: () => { this.actionClaimId = null; this.loadClaims(); this.cdr.markForCheck(); },
        error: (err) => {
          this.actionClaimId = null;
          this.actionError = err?.error?.message || 'Unable to reject this claim. Reload the queue before retrying.';
          this.cdr.markForCheck();
        }
      });
    }
  }
}
