import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';

import { ClientApiService } from '../../../../core/services/client-api.service';
import { PropertySearchPageComponent } from './property-search-page';

describe('PropertySearchPageComponent landmark recovery', () => {
  let fixture: ComponentFixture<PropertySearchPageComponent>;
  let component: PropertySearchPageComponent;
  let api: { searchHotels: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    api = { searchHotels: vi.fn(() => of({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 })) };
    router = { navigate: vi.fn(() => Promise.resolve(true)) };

    await TestBed.configureTestingModule({
      imports: [PropertySearchPageComponent],
      providers: [
        { provide: ClientApiService, useValue: api },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: {
          queryParams: of({
            landmarkId: '501',
            provinceId: '48',
            radiusKm: '5',
            displayLocation: 'Cầu Rồng, Đà Nẵng',
            checkInDate: '2026-08-01',
            checkOutDate: '2026-08-02',
            adultCount: '2',
            roomCount: '1'
          })
        } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PropertySearchPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('restores landmark state and requests nearest results after URL reload', () => {
    expect(component.isLandmarkSearch).toBe(true);
    expect(component.landmarkRadius).toBe(5);
    expect(component.stateService.state()).toEqual(expect.objectContaining({
      landmarkId: 501,
      provinceId: 48,
      radiusKm: 5,
      selectedSuggestionType: 'LANDMARK'
    }));
    expect(api.searchHotels).toHaveBeenCalledWith(expect.objectContaining({
      landmarkId: '501',
      radiusKm: '5',
      sortBy: 'NEAREST'
    }));
  });

  it('offers radius expansion and province recovery from the empty state', () => {
    component.expandLandmarkRadius();
    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({
      queryParams: expect.objectContaining({ radiusKm: 10, pageNumber: 1 })
    }));

    component.searchLandmarkProvince();
    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({
      queryParams: expect.objectContaining({ landmarkId: null, radiusKm: null, displayLocation: null })
    }));
  });

  it('keeps translated sort options referentially stable between change-detection passes', () => {
    expect(component.sortOptions()).toBe(component.sortOptions());
  });
});
