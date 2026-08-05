/** @vitest-environment jsdom */

import { ChangeDetectorRef, ElementRef, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { ClientApiService, SearchSuggestionGroups } from '../../../../../core/services/client-api.service';
import { ImageFallbackService } from '../../../../../core/services/image-fallback.service';
import { HomeSearchStateService } from '../../services/home-search-state.service';
import { LocationAutocompleteComponent } from './location-autocomplete.component';

describe('LocationAutocompleteComponent', () => {
  let component: LocationAutocompleteComponent | undefined;
  let stateService: {
    state: ReturnType<typeof signal>;
    recentSearches: ReturnType<typeof signal>;
    updateKeyword: ReturnType<typeof vi.fn>;
    selectSuggestion: ReturnType<typeof vi.fn>;
    clearLocation: ReturnType<typeof vi.fn>;
    applyRecentSearch: ReturnType<typeof vi.fn>;
    removeRecentSearch: ReturnType<typeof vi.fn>;
    clearRecentSearches: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    vi.stubGlobal('requestAnimationFrame', (callback: FrameRequestCallback) => {
      callback(0);
      return 1;
    });
    stateService = {
      state: signal({
        locationDisplayName: '', latitude: null, longitude: null, provinceId: null
      }),
      recentSearches: signal([]),
      updateKeyword: vi.fn(),
      selectSuggestion: vi.fn(),
      clearLocation: vi.fn(),
      applyRecentSearch: vi.fn(),
      removeRecentSearch: vi.fn(),
      clearRecentSearches: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: HomeSearchStateService, useValue: stateService },
        { provide: ElementRef, useValue: new ElementRef({ contains: () => false, querySelector: () => null }) },
        { provide: ChangeDetectorRef, useValue: { markForCheck: vi.fn() } },
        {
          provide: ClientApiService,
          useValue: {
            getSearchSuggestions: vi.fn(() => of(emptyGroups())),
            getPopularDestinations: vi.fn(() => of([]))
          }
        },
        {
          provide: ImageFallbackService,
          useValue: {
            property: vi.fn(() => '/property.webp'),
            destination: vi.fn(() => '/destination.webp'),
            replace: vi.fn()
          }
        }
      ]
    });

    component = TestBed.runInInjectionContext(() => new LocationAutocompleteComponent());
  });

  afterEach(() => {
    component?.ngOnDestroy();
    component = undefined;
    TestBed.resetTestingModule();
    vi.unstubAllGlobals();
  });

  it('moves across grouped results and selects a landmark with the keyboard', () => {
    const subject = component as LocationAutocompleteComponent;
    subject.popupOpen = true;
    subject.searchControl.setValue('my tho', { emitEvent: false });
    subject.resultGroups = {
      provinces: [{ type: 'PROVINCE', id: 1, name: 'Province', displayName: 'Province' }],
      wards: [],
      properties: [{ type: 'PROPERTY', id: 2, name: 'Property', displayName: 'Property' }],
      landmarks: [{
        type: 'LANDMARK', id: 3, name: 'Landmark', displayName: 'Landmark, Province',
        provinceId: 1, latitude: 10.36, longitude: 106.36, defaultRadiusKm: 8
      }]
    };
    subject.onKeydown(keyboardEvent('ArrowDown'));
    subject.onKeydown(keyboardEvent('ArrowDown'));
    subject.onKeydown(keyboardEvent('ArrowDown'));
    expect(subject.activeResult?.type).toBe('LANDMARK');

    subject.onKeydown(keyboardEvent('Enter'));

    expect(stateService.selectSuggestion).toHaveBeenCalledWith(expect.objectContaining({
      type: 'LANDMARK', id: 3, latitude: 10.36, longitude: 106.36, defaultRadiusKm: 8
    }));
    expect(subject.popupOpen).toBe(false);
  });
});

function emptyGroups(): SearchSuggestionGroups {
  return { provinces: [], wards: [], properties: [], landmarks: [] };
}

function keyboardEvent(key: string): KeyboardEvent {
  return { key, preventDefault: vi.fn() } as unknown as KeyboardEvent;
}
