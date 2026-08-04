import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { FeedbackStateComponent } from '../../../shared/components/feedback-state/feedback-state.component';

@Component({
  selector: 'app-property-imports',
  standalone: true,
  imports: [CommonModule, FormsModule, FeedbackStateComponent],
  template: `
    <div class="container mt-4">
      <h2>Automated Property Import</h2>
      <p *ngIf="actionError" class="alert alert-danger" role="alert">{{ actionError }}</p>
      
      <div class="card mb-4">
        <div class="card-body">
          <h5 class="card-title">Search & Stage</h5>
          <div class="row g-3">
            <div class="col-md-3">
              <label>Provider</label>
              <select class="form-select" [(ngModel)]="provider">
                <option value="NOMINATIM">Nominatim (OSM)</option>
              </select>
            </div>
            <div class="col-md-3">
              <label>Keyword</label>
              <input type="text" class="form-control" [(ngModel)]="searchKeyword" placeholder="e.g. hotel in hanoi">
            </div>
            <div class="col-md-2">
              <label>Max Results</label>
              <input type="number" class="form-control" [(ngModel)]="maxResults">
            </div>
            <div class="col-md-2 d-flex align-items-end">
              <button class="btn btn-primary w-100" (click)="searchAndStage()" [disabled]="loading">
                {{ loading ? 'Searching...' : 'Search' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-body">
          <h5 class="card-title">Import Batches</h5>
          <app-feedback-state *ngIf="batchesLoading" state="loading" title="Loading import batches"
            message="Checking staged property import jobs." />
          <app-feedback-state *ngIf="!batchesLoading && batchesError" state="error"
            title="Import batches unavailable" [message]="batchesError" actionLabel="Retry"
            (actionTriggered)="loadBatches()" />
          <app-feedback-state *ngIf="!batchesLoading && !batchesError && batches.length === 0" state="empty"
            title="No import batches" message="Search a provider to stage the first review batch." />
          <table *ngIf="!batchesLoading && !batchesError && batches.length" class="table table-striped mt-3">
            <thead>
              <tr>
                <th>ID</th>
                <th>Keyword</th>
                <th>Found</th>
                <th>New</th>
                <th>Dup</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let batch of batches">
                <td>{{ batch.id }}</td>
                <td>{{ batch.searchKeyword }}</td>
                <td>{{ batch.totalFound }}</td>
                <td><span class="badge bg-success">{{ batch.totalNew }}</span></td>
                <td><span class="badge bg-warning">{{ batch.totalDuplicate }}</span></td>
                <td>{{ batch.status }}</td>
                <td>
                  <button class="btn btn-sm btn-info me-2" (click)="viewItems(batch.id)">View Items</button>
                  <button class="btn btn-sm btn-success" *ngIf="batch.status === 'PREVIEW_READY'" (click)="importBatch(batch.id)">Import</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="modal d-block" tabindex="-1" *ngIf="showItemsModal">
        <div class="modal-dialog modal-xl">
          <div class="modal-content">
            <div class="modal-header">
              <h5 class="modal-title">Batch Items</h5>
              <button type="button" class="btn-close" (click)="showItemsModal = false"></button>
            </div>
            <div class="modal-body" style="max-height: 70vh; overflow-y: auto;">
              <app-feedback-state *ngIf="itemsLoading" state="loading" title="Loading batch items"
                message="Reading staged property candidates." />
              <app-feedback-state *ngIf="!itemsLoading && itemsError" state="error" title="Batch items unavailable"
                [message]="itemsError" actionLabel="Retry" (actionTriggered)="retryItems()" />
              <app-feedback-state *ngIf="!itemsLoading && !itemsError && selectedBatchItems.length === 0"
                state="empty" title="No staged items" message="This batch does not contain reviewable properties." />
              <table *ngIf="!itemsLoading && !itemsError && selectedBatchItems.length"
                class="table table-sm table-bordered">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Address</th>
                    <th>Duplicate Status</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let item of selectedBatchItems" [ngClass]="{'table-warning': item.duplicateStatus !== 'NEW'}">
                    <td>{{ item.rawName }}</td>
                    <td>{{ item.rawAddress }}</td>
                    <td>{{ item.duplicateStatus }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class PropertyImportsComponent implements OnInit {
  provider = 'NOMINATIM';
  searchKeyword = '';
  maxResults = 50;
  loading = false;
  batchesLoading = false;
  batchesError = '';
  actionError = '';
  itemsLoading = false;
  itemsError = '';
  selectedBatchId: number | null = null;
  
  batches: any[] = [];
  selectedBatchItems: any[] = [];
  showItemsModal = false;

  constructor(private http: HttpClient, private cdr: ChangeDetectorRef) {}

  ngOnInit() {
    this.loadBatches();
  }

  loadBatches() {
    this.batchesLoading = true;
    this.batchesError = '';
    this.http.get<any>(`${environment.apiUrl}/admin/property-imports`).subscribe({
      next: (res) => {
        this.batches = res.content || res;
        this.batchesLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.batchesLoading = false;
        this.batchesError = err?.error?.message || 'Unable to load import batches. Retry when the provider queue is available.';
        this.cdr.markForCheck();
      }
    });
  }

  searchAndStage() {
    this.loading = true;
    this.actionError = '';
    const body = {
      keyword: this.searchKeyword,
      maxResults: this.maxResults
    };
    this.http.post(`${environment.apiUrl}/admin/property-imports/search?provider=${this.provider}`, body).subscribe({
      next: () => {
        this.loading = false;
        this.loadBatches();
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.loading = false;
        this.actionError = err?.error?.message || 'Unable to stage provider results. Your current batches were not changed.';
        this.cdr.markForCheck();
      }
    });
  }

  viewItems(batchId: number) {
    this.selectedBatchId = batchId;
    this.selectedBatchItems = [];
    this.itemsLoading = true;
    this.itemsError = '';
    this.showItemsModal = true;
    this.http.get<any>(`${environment.apiUrl}/admin/property-imports/${batchId}/items`).subscribe({
      next: (res) => {
        this.selectedBatchItems = res.content || res;
        this.itemsLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.itemsLoading = false;
        this.itemsError = err?.error?.message || 'Unable to load staged items for this batch.';
        this.cdr.markForCheck();
      }
    });
  }

  retryItems() {
    if (this.selectedBatchId !== null) this.viewItems(this.selectedBatchId);
  }

  importBatch(batchId: number) {
    if (confirm('Are you sure you want to import valid properties from this batch?')) {
      this.actionError = '';
      this.http.post(`${environment.apiUrl}/admin/property-imports/${batchId}/import`, {}).subscribe({
        next: () => {
          alert('Import successful!');
          this.loadBatches();
          this.cdr.markForCheck();
        },
        error: (err) => {
          this.actionError = err?.error?.message || 'Unable to import this batch. Review its status before retrying.';
          this.cdr.markForCheck();
        }
      });
    }
  }
}
