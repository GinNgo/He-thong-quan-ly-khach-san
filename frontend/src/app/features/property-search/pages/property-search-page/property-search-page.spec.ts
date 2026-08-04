import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { AmenityService } from '../../../../core/services/amenity.service';
import { ClientApiService } from '../../../../core/services/client-api.service';
import { HomeSearchStateService } from '../../../client/home/services/home-search-state.service';
import { PropertySearchPageComponent } from './property-search-page';

describe('PropertySearchPageComponent amenity flow', () => {
  it('round-trips URL amenity ids into search and subsequent filter navigation', () => {
    const router = { navigate: vi.fn() };
    const api = {
      searchHotels: vi.fn(() => of({
        content: [{ id: 7, name: 'LuxeStay', amenities: ['Wi-Fi miễn phí'] }],
        totalElements: 1, totalPages: 1, number: 0, size: 20
      }))
    };
    const state = { updateLocation: vi.fn(), updateDates: vi.fn(), updateGuests: vi.fn() };
    TestBed.configureTestingModule({ providers: [
      { provide: ActivatedRoute, useValue: { queryParams: of({ amenityIds: '1,2', pageNumber: 1 }) } },
      { provide: Router, useValue: router },
      { provide: ClientApiService, useValue: api },
      { provide: AmenityService, useValue: { publicCatalog: () => of([
        { id: 1, code: 'WIFI', nameVi: 'Wi-Fi miễn phí', category: 'INTERNET', sortOrder: 1, status: 'ACTIVE' }
      ]) } },
      { provide: HomeSearchStateService, useValue: state }
    ] });

    const component = TestBed.runInInjectionContext(() => new PropertySearchPageComponent());
    component.ngOnInit();

    expect(api.searchHotels).toHaveBeenCalledWith(expect.objectContaining({ amenityIds: [1, 2] }));
    expect(component.properties()[0].amenities).toEqual(['Wi-Fi miễn phí']);
    component.onFiltersChanged({
      minPrice: 0, maxPrice: 10000000, propertyTypes: [], starRatings: [],
      minReviewScore: null, amenityIds: [1]
    });
    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({
      queryParams: expect.objectContaining({ amenityIds: '1', pageNumber: 1 })
    }));
    component.ngOnDestroy();
  });
});
