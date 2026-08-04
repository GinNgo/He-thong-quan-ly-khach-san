import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { StayReviewService } from './stay-review.service';

describe('StayReviewService', () => {
  let service: StayReviewService; let http: HttpTestingController;
  beforeEach(() => { TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] }); service = TestBed.inject(StayReviewService); http = TestBed.inject(HttpTestingController); });
  afterEach(() => http.verify());
  it('uses owner and property-scoped review contracts', () => {
    service.create(42, { rating: 9, comment: 'A clean and pleasant stay.' }).subscribe();
    const create = http.expectOne(`${environment.apiUrl}/reservations/42/review`); expect(create.request.method).toBe('POST'); create.flush({});
    service.mine().subscribe(); const mine = http.expectOne(`${environment.apiUrl}/reviews/mine`); expect(mine.request.method).toBe('GET'); mine.flush([]);
    service.property(3).subscribe(); const property = http.expectOne(`${environment.apiUrl}/management/properties/3/reviews`); expect(property.request.method).toBe('GET'); property.flush([]);
  });
  it('uses dedicated moderation and response commands', () => {
    service.moderate(7, 'HIDDEN', 'Personal information').subscribe(); const moderation = http.expectOne(`${environment.apiUrl}/management/reviews/7/moderation`); expect(moderation.request.body).toEqual({ status: 'HIDDEN', reason: 'Personal information' }); moderation.flush({});
    service.respond(7, 'Thank you').subscribe(); const response = http.expectOne(`${environment.apiUrl}/management/reviews/7/response`); expect(response.request.body).toEqual({ response: 'Thank you' }); response.flush({});
  });
});
