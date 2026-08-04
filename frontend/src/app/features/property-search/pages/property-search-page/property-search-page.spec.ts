import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';

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

    await TestBed.configureTestingModule({
      imports: [PropertySearchPageComponent],
      providers: [
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

    fixture = TestBed.createComponent(PropertySearchPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('canonicalizes route filters before issuing the API request and rendering chips', () => {
    expect(api.searchHotels).toHaveBeenCalledWith(expect.objectContaining({
      propertyTypes: ['RESORT'],
      starRatings: [5, 4],
      minReviewScore: 0,
      pageNumber: 4,
      pageSize: 50,
      sortBy: 'RATING',
    }));
    expect(component.activeFilterCount).toBe(4);
    expect(fixture.nativeElement.querySelectorAll('[data-filter-chip]').length).toBe(4);
    expect(fixture.nativeElement.querySelector('[data-filter-chip="reviewScore:0"]')).not.toBeNull();
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

  it('canonicalizes applied filters and clears all filters at page one', () => {
    component.onFiltersChanged({
      minPrice: 0,
      maxPrice: 10000000,
      propertyTypes: ['hotel', 'HOTEL', 'bad'],
      starRatings: [3, 5, 5, 9],
      minReviewScore: 8.5,
      amenityIds: [],
    });
    expect(router.navigate).toHaveBeenLastCalledWith([], expect.objectContaining({
      queryParams: expect.objectContaining({
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
