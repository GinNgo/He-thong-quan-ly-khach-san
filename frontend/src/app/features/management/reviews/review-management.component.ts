import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, finalize } from 'rxjs';
import { ManagementApiService, ManagedProperty } from '../../../core/services/management-api.service';
import { StayReview, StayReviewService, StayReviewStatus } from '../../../core/services/stay-review.service';

@Component({ selector: 'app-review-management', standalone: true, imports: [CommonModule, FormsModule], templateUrl: './review-management.component.html', styleUrl: './review-management.component.css' })
export class ReviewManagementComponent implements OnInit {
  private readonly management = inject(ManagementApiService); private readonly reviewsApi = inject(StayReviewService); private readonly cdr = inject(ChangeDetectorRef);
  properties: ManagedProperty[] = []; hotelId?: number; reviews: StayReview[] = []; loading = true; actionId: number | null = null; error = ''; success = '';
  responseDraft: Record<number, string> = {}; reasonDraft: Record<number, string> = {};
  ngOnInit(): void { this.management.context().subscribe({ next: context => { this.properties = context.properties; this.hotelId = context.activePropertyId; this.load(); }, error: () => { this.loading = false; this.error = 'Không thể tải cơ sở / Properties could not be loaded.'; } }); }
  load(): void { if (!this.hotelId) { this.loading = false; this.reviews = []; return; } this.loading = true; this.error = ''; this.reviewsApi.property(this.hotelId).pipe(finalize(() => { this.loading = false; this.cdr.markForCheck(); })).subscribe({ next: reviews => this.reviews = reviews, error: err => this.error = err.error?.message || 'Không thể tải đánh giá / Reviews could not be loaded.' }); }
  moderate(review: StayReview, status: StayReviewStatus): void { const reason = (this.reasonDraft[review.id] || '').trim(); if (reason.length < 5) { this.error = 'Nhập lý do ít nhất 5 ký tự / Enter a reason of at least 5 characters.'; return; } this.run(review.id, this.reviewsApi.moderate(review.id, status, reason)); }
  respond(review: StayReview): void { const response = (this.responseDraft[review.id] || '').trim(); if (response.length < 2) { this.error = 'Nhập phản hồi / Enter a response.'; return; } this.run(review.id, this.reviewsApi.respond(review.id, response)); }
  private run(id: number, request: Observable<StayReview>): void { if (this.actionId !== null) return; this.actionId = id; this.error = ''; this.success = ''; request.pipe(finalize(() => { this.actionId = null; this.cdr.markForCheck(); })).subscribe({ next: updated => { this.reviews = this.reviews.map(item => item.id === id ? updated : item); this.success = 'Đã cập nhật / Updated.'; }, error: err => this.error = err.error?.message || 'Không thể cập nhật / Update failed.' }); }
}
