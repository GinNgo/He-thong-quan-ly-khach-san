import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { PropertyClaimResponse, PropertyClaimService } from '../../../core/services/property-claim.service';

@Component({
  selector: 'app-property-claims',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="container mt-4">
      <h2>Property Claim Requests</h2>
      
      <div class="card mt-3">
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
                  <button class="btn btn-sm btn-success me-2" *ngIf="claim.status === 'PENDING'" (click)="approve(claim.id)">Approve</button>
                  <button class="btn btn-sm btn-danger" *ngIf="claim.status === 'PENDING'" (click)="reject(claim.id)">Reject</button>
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
  private readonly propertyClaims = inject(PropertyClaimService);
  claims: PropertyClaimResponse[] = [];

  ngOnInit(): void {
    this.loadClaims();
  }

  loadClaims(): void {
    this.propertyClaims.list().subscribe({
      next: (res) => this.claims = res.content,
      error: (err) => console.error(err)
    });
  }

  approve(id: number): void {
    if (confirm('Are you sure you want to approve this claim? The user will become the OWNER of this property.')) {
      this.propertyClaims.approve(id).subscribe({
        next: () => this.loadClaims(),
        error: (err) => console.error(err)
      });
    }
  }

  reject(id: number): void {
    const reason = prompt('Enter rejection reason:');
    if (reason !== null) {
      this.propertyClaims.reject(id, reason).subscribe({
        next: () => this.loadClaims(),
        error: (err) => console.error(err)
      });
    }
  }
}
