import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

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
  claims: any[] = [];

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.loadClaims();
  }

  loadClaims() {
    this.http.get<any>(`${environment.apiUrl}/admin/property-claims`).subscribe({
      next: (res) => {
        this.claims = res.content || res;
      },
      error: (err) => console.error(err)
    });
  }

  approve(id: number) {
    if (confirm('Are you sure you want to approve this claim? The user will become the OWNER of this property.')) {
      this.http.post(`${environment.apiUrl}/admin/property-claims/${id}/approve`, {}).subscribe({
        next: () => this.loadClaims(),
        error: (err) => console.error(err)
      });
    }
  }

  reject(id: number) {
    const reason = prompt('Enter rejection reason:');
    if (reason !== null) {
      this.http.post(`${environment.apiUrl}/admin/property-claims/${id}/reject`, { reason }).subscribe({
        next: () => this.loadClaims(),
        error: (err) => console.error(err)
      });
    }
  }
}
