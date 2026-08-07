<<<<<<< HEAD
import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { BehaviorSubject, of, Subject } from 'rxjs';

import { ClientApiService, Hotel } from '../../../../core/services/client-api.service';
import { StickySearchBarComponent } from '../../../client/home/components/sticky-search-bar/sticky-search-bar.component';
import { HomeSearchStateService } from '../../../client/home/services/home-search-state.service';
import { PropertyResultCardComponent } from '../../components/property-result-card/property-result-card';
import { PropertySearchPageComponent } from './property-search-page';

@Component({
  selector: 'app-sticky-search-bar',
  standalone: true,
  template: '',
})
class StickySearchBarStubComponent {
  @Input() isVisible = false;
  @Input() embedded = false;
}

@Component({
  selector: 'app-property-result-card',
  standalone: true,
  imports: [CommonModule],
  template: '',
})
class PropertyResultCardStubComponent {
  @Input({ required: true }) property!: Hotel;
  @Output() viewDetails = new EventEmitter<number>();
}

describe('PropertySearchPageComponent filter contract', () => {
  let fixture: ComponentFixture<PropertySearchPageComponent>;
  let component: PropertySearchPageComponent;
  let queryParams: BehaviorSubject<Params>;
  let router: { navigate: ReturnType<typeof vi.fn> };
  let api: { searchHotels: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    queryParams = new BehaviorSubject<Params>({
      checkInDate: '2026-08-10',
      checkOutDate: '2026-08-12',
      sortBy: 'RATING',
      pageNumber: '4',
      pageSize: '50',
      minPrice: '400000',
      maxPrice: '600000',
      propertyTypes: 'resort,RESORT,unknown',
      starRatings: '4,5,5,0',
      minReviewScore: '0',
    });
    router = { navigate: vi.fn().mockResolvedValue(true) };
    api = {
      searchHotels: vi.fn().mockReturnValue(of({
        content: [], totalElements: 0, totalPages: 0, number: 3, size: 50,
      })),
    };
    const state = signal({
      checkInDate: new Date(2026, 7, 10),
      checkOutDate: new Date(2026, 7, 12),
      adultCount: 2,
      childCount: 0,
      roomCount: 1,
    });
    const stateService = {
      state,
      guestSummary: signal('2 adults - 1 room'),
      updateLocation: vi.fn(),
      updateDates: vi.fn(),
      updateGuests: vi.fn(),
      bookingQueryParams: vi.fn().mockReturnValue({}),
    };
=======
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
>>>>>>> codex/ui-functional-audit-polish

    await TestBed.configureTestingModule({
      imports: [PropertySearchPageComponent],
      providers: [
<<<<<<< HEAD
        { provide: ActivatedRoute, useValue: { queryParams } },
        { provide: Router, useValue: router },
        { provide: ClientApiService, useValue: api },
        { provide: HomeSearchStateService, useValue: stateService },
      ],
    })
      .overrideComponent(PropertySearchPageComponent, {
        remove: { imports: [StickySearchBarComponent, PropertyResultCardComponent] },
        add: { imports: [StickySearchBarStubComponent, PropertyResultCardStubComponent] },
      })
      .compileComponents();
=======
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
>>>>>>> codex/ui-functional-audit-polish

    fixture = TestBed.createComponent(PropertySearchPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
<<<<<<< HEAD
    await fixture.whenStable();
  });

  it('canonicalizes route filters before issuing the API request and rendering chips', () => {
    expect(api.searchHotels).toHaveBeenCalledWith(expect.objectContaining({
      propertyTypes: ['RESORT'],
      starRatings: [5, 4],
      minReviewScore: 0,
      minPrice: 400000,
      maxPrice: 600000,
      pageNumber: 4,
      pageSize: 50,
      sortBy: 'RATING',
    }));
    expect(component.activeFilterCount).toBe(5);
    expect(fixture.nativeElement.querySelectorAll('[data-filter-chip]').length).toBe(5);
    expect(fixture.nativeElement.querySelector('[data-filter-chip="reviewScore:0"]')).not.toBeNull();
    const priceChip = fixture.nativeElement.querySelector('[data-filter-chip="price"]') as HTMLButtonElement;
    expect(priceChip.textContent).toContain('400.000');
    expect(priceChip.textContent).toContain('600.000');
    expect(priceChip.getAttribute('aria-label')).toContain('Bỏ bộ lọc khoảng giá');
  });

  it('preserves an invalid route price order for the API while canonicalizing only the slider display', () => {
    queryParams.next({
      checkInDate: '2026-08-10',
      checkOutDate: '2026-08-12',
      minPrice: '600000',
      maxPrice: '400000',
    });
    fixture.detectChanges();

    expect(api.searchHotels).toHaveBeenLastCalledWith(expect.objectContaining({
      minPrice: 600000,
      maxPrice: 400000,
    }));
    expect(component.currentFilterState.minPrice).toBe(400000);
    expect(component.currentFilterState.maxPrice).toBe(600000);
  });

  it('keeps invalid direct pagination values on the API request while canonicalizing paginator state', () => {
    queryParams.next({ pageNumber: '0', pageSize: '101' });

    expect(api.searchHotels).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNumber: 0,
      pageSize: 101,
    }));
    expect(component.pageNumber()).toBe(1);
    expect(component.pageSize()).toBe(100);
  });

  it('issues one search request for each settled route snapshot', () => {
    expect(api.searchHotels).toHaveBeenCalledTimes(1);

    queryParams.next({ pageNumber: '2', pageSize: '20', sortBy: 'PRICE_ASC' });

    expect(api.searchHotels).toHaveBeenCalledTimes(2);
    expect(api.searchHotels).toHaveBeenLastCalledWith(expect.objectContaining({
      pageNumber: 2,
      pageSize: 20,
      sortBy: 'PRICE_ASC',
    }));
  });

  it('cancels a stale search and ignores its late result', () => {
    const stale = new Subject<ReturnType<typeof searchPage>>();
    const latest = new Subject<ReturnType<typeof searchPage>>();
    api.searchHotels
      .mockReturnValueOnce(stale)
      .mockReturnValueOnce(latest);

    queryParams.next({ pageNumber: '1', pageSize: '20', sortBy: 'POPULAR' });
    expect(stale.observed).toBe(true);
    queryParams.next({ pageNumber: '1', pageSize: '20', sortBy: 'RATING' });
    expect(stale.observed).toBe(false);

    stale.next(searchPage([hotel(1)]));
    latest.next(searchPage([hotel(2)]));
    fixture.detectChanges();

    expect(component.properties().map(property => property.id)).toEqual([2]);
  });

  it('clamps the paginator to 100 and renders the returned maximum page in exact order', () => {
    const hotels = Array.from({ length: 100 }, (_, index) => hotel(1000 - index));
    api.searchHotels.mockReturnValue(of(searchPage(hotels, 250, 3, 100)));

    queryParams.next({ pageNumber: '1', pageSize: '100' });
    fixture.detectChanges();

    const cards = fixture.debugElement.queryAll(By.directive(PropertyResultCardStubComponent));
    expect(component.pageSize()).toBe(100);
    expect(component.totalItems()).toBe(250);
    expect(cards).toHaveLength(100);
    expect(cards.map(card => card.componentInstance.property.id)).toEqual(hotels.map(property => property.id));
  });

  it('resets sort to page one and preserves filters and sort across page changes', () => {
    component.selectedSort = 'PRICE_ASC';
    component.onSortChange();

    expect(router.navigate).toHaveBeenLastCalledWith([], expect.objectContaining({
      queryParams: { sortBy: 'PRICE_ASC', pageNumber: 1 },
      queryParamsHandling: 'merge',
    }));

    router.navigate.mockClear();
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
    component.onPageChange({ page: 2, rows: 50 });

    expect(router.navigate).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({
      queryParams: { pageNumber: 3, pageSize: 50 },
      queryParamsHandling: 'merge',
    }));
    const patch = router.navigate.mock.calls[0][1].queryParams;
    expect(patch).not.toHaveProperty('sortBy');
    expect(patch).not.toHaveProperty('propertyTypes');
    expect(patch).not.toHaveProperty('starRatings');
    expect(patch).not.toHaveProperty('minPrice');
    scrollTo.mockRestore();
  });

  it('redirects an empty out-of-range page once and keeps zero-based response numbers internal to the API', () => {
    router.navigate.mockClear();
    api.searchHotels.mockReturnValue(of({
      content: [], totalElements: 21, totalPages: 3, number: 998, size: 20,
    }));

    queryParams.next({ pageNumber: '999', pageSize: '20', sortBy: 'RATING', propertyTypes: 'RESORT' });
    fixture.detectChanges();

    expect(router.navigate).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({
      queryParams: { pageNumber: 3 },
      queryParamsHandling: 'merge',
    }));
    expect(component.isLoading()).toBe(true);
    expect(fixture.nativeElement.querySelector('[data-search-empty]')).toBeNull();

    queryParams.next({ pageNumber: '999', pageSize: '20', sortBy: 'RATING', propertyTypes: 'RESORT' });
    expect(router.navigate).toHaveBeenCalledOnce();

    api.searchHotels.mockReturnValue(of({
      content: [{
        id: 7,
        name: 'Stable page result',
        addressLine: 'Dong Thap',
        starRating: 5,
        latitude: 10.4,
        longitude: 106.4,
      } as Hotel],
      totalElements: 21,
      totalPages: 3,
      number: 2,
      size: 20,
    }));
    queryParams.next({ pageNumber: '3', pageSize: '20', sortBy: 'RATING', propertyTypes: 'RESORT' });
    fixture.detectChanges();

    expect(router.navigate).toHaveBeenCalledOnce();
    expect(component.pageNumber()).toBe(3);
    expect(component.properties()[0].id).toBe(7);
    expect(component.isLoading()).toBe(false);
    expect(fixture.nativeElement.querySelector('[data-search-sort]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-search-pagination]')?.getAttribute('data-page-number')).toBe('3');
  });

  it('removes one star and resets the page through merged query params', () => {
    const chip = fixture.nativeElement.querySelector('[data-filter-chip="starRating:5"]') as HTMLButtonElement;
    chip.click();

    expect(router.navigate).toHaveBeenLastCalledWith([], expect.objectContaining({
      queryParams: { starRatings: '4', pageNumber: 1 },
      queryParamsHandling: 'merge',
    }));
    const patch = router.navigate.mock.calls.at(-1)?.[1].queryParams;
    expect(patch).not.toHaveProperty('checkInDate');
    expect(patch).not.toHaveProperty('sortBy');
    expect(patch).not.toHaveProperty('pageSize');
  });

  it('removes the inclusive price range at page one without overwriting search state', () => {
    const chip = fixture.nativeElement.querySelector('[data-filter-chip="price"]') as HTMLButtonElement;
    chip.click();

    expect(router.navigate).toHaveBeenLastCalledWith([], expect.objectContaining({
      queryParams: { minPrice: null, maxPrice: null, pageNumber: 1 },
      queryParamsHandling: 'merge',
    }));
    const patch = router.navigate.mock.calls.at(-1)?.[1].queryParams;
    expect(patch).not.toHaveProperty('checkInDate');
    expect(patch).not.toHaveProperty('checkOutDate');
    expect(patch).not.toHaveProperty('sortBy');
    expect(patch).not.toHaveProperty('pageSize');
  });

  it('canonicalizes applied filters and clears all filters at page one', () => {
    component.onFiltersChanged({
      minPrice: 600000,
      maxPrice: 400000,
      propertyTypes: ['hotel', 'HOTEL', 'bad'],
      starRatings: [3, 5, 5, 9],
      minReviewScore: 8.5,
      amenityIds: [],
    });
    expect(router.navigate).toHaveBeenLastCalledWith([], expect.objectContaining({
      queryParams: expect.objectContaining({
        minPrice: 400000, maxPrice: 600000,
        propertyTypes: 'HOTEL', starRatings: '5,3', minReviewScore: 8.5, pageNumber: 1,
      }),
      queryParamsHandling: 'merge',
    }));

    component.clearAllFilters();
    expect(router.navigate).toHaveBeenLastCalledWith([], expect.objectContaining({
      queryParams: {
        minPrice: null,
        maxPrice: null,
        propertyTypes: null,
        starRatings: null,
        minReviewScore: null,
        amenityIds: null,
        pageNumber: 1,
      },
      queryParamsHandling: 'merge',
    }));
  });

  it('focuses, traps and restores focus for the mobile filter dialog', async () => {
    const trigger = fixture.nativeElement.querySelector('[data-mobile-filter-trigger]') as HTMLButtonElement;
    trigger.click();
    fixture.detectChanges();
    await new Promise(resolve => globalThis.setTimeout(resolve, 0));

    const dialog = fixture.nativeElement.querySelector('#mobile-property-filters') as HTMLElement;
    const close = dialog.querySelector('[data-mobile-filter-close]') as HTMLButtonElement;
    const apply = dialog.querySelector('.apply-button') as HTMLButtonElement;
    expect(document.activeElement).toBe(close);

    dialog.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true }));
    expect(document.activeElement).toBe(apply);
    dialog.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
    expect(document.activeElement).toBe(close);

    dialog.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(component.mobileFilterVisible).toBe(false);
    expect(document.activeElement).toBe(trigger);
  });
});

function hotel(id: number): Hotel {
  return {
    id,
    name: `Hotel ${id}`,
    addressLine: `Address ${id}`,
    starRating: 5,
    latitude: 10.4,
    longitude: 106.4,
  };
}

function searchPage(
  content: Hotel[],
  totalElements = content.length,
  totalPages = content.length ? 1 : 0,
  size = 20,
) {
  return { content, totalElements, totalPages, number: 0, size };
}
=======
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
>>>>>>> codex/ui-functional-audit-polish
