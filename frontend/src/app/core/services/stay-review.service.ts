import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export type StayReviewStatus = 'PUBLISHED' | 'HIDDEN';
export interface StayReview { id: number; reservationId: number; hotelId: number; rating: number; title?: string; comment: string; status: StayReviewStatus; moderationReason?: string; propertyResponse?: string; respondedAt?: string; createdAt: string; }
export interface CreateStayReview { rating: number; title?: string; comment: string; }

@Injectable({ providedIn: 'root' })
export class StayReviewService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;
  create(reservationId: number, request: CreateStayReview): Observable<StayReview> { return this.http.post<StayReview>(`${this.api}/reservations/${reservationId}/review`, request); }
  mine(): Observable<StayReview[]> { return this.http.get<StayReview[]>(`${this.api}/reviews/mine`); }
  property(hotelId: number): Observable<StayReview[]> { return this.http.get<StayReview[]>(`${this.api}/management/properties/${hotelId}/reviews`); }
  moderate(reviewId: number, status: StayReviewStatus, reason: string): Observable<StayReview> { return this.http.post<StayReview>(`${this.api}/management/reviews/${reviewId}/moderation`, { status, reason }); }
  respond(reviewId: number, response: string): Observable<StayReview> { return this.http.post<StayReview>(`${this.api}/management/reviews/${reviewId}/response`, { response }); }
}
