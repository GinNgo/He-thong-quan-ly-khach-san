import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ManagementApiService } from '../../../core/services/management-api.service';
import { StayReviewService } from '../../../core/services/stay-review.service';
import { ReviewManagementComponent } from './review-management.component';

describe('ReviewManagementComponent', () => {
  let fixture: ComponentFixture<ReviewManagementComponent>; const property = vi.fn(() => of([])); const moderate = vi.fn(); const respond = vi.fn();
  beforeEach(async () => { await TestBed.configureTestingModule({ imports: [ReviewManagementComponent], providers: [
    { provide: ManagementApiService, useValue: { context: vi.fn(() => of({ properties: [{ id: 3, name: 'Luxe' }], activePropertyId: 3 })) } },
    { provide: StayReviewService, useValue: { property, moderate, respond } },
  ] }).compileComponents(); fixture = TestBed.createComponent(ReviewManagementComponent); fixture.detectChanges(); });
  it('loads the active property review queue', () => { expect(property).toHaveBeenCalledWith(3); expect(fixture.componentInstance.loading).toBe(false); });
  it('requires a moderation reason before mutation', () => { fixture.componentInstance.moderate({ id: 7 } as any, 'HIDDEN'); expect(moderate).not.toHaveBeenCalled(); expect(fixture.componentInstance.error).toContain('5'); });
});
