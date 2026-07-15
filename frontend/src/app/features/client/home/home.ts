import { ChangeDetectorRef, Component, OnInit, OnDestroy, inject, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ClientApiService } from '../../../core/services/client-api.service';
import { LayoutStateService } from '../../../core/services/layout-state.service';
import { HeroSearchComponent } from './components/hero-search/hero-search.component';
import { StickySearchBarComponent } from './components/sticky-search-bar/sticky-search-bar.component';
import { PopularDestinationsComponent } from './components/popular-destinations/popular-destinations.component';
import { FeaturedPropertiesComponent } from './components/featured-properties/featured-properties.component';
import { HomeSearchStateService } from './services/home-search-state.service';
import { AuthService } from '../../../core/services/auth';
import { Hotel, LocationSuggestion } from '../../../core/services/client-api.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    CommonModule,
    HeroSearchComponent,
    StickySearchBarComponent,
    PopularDestinationsComponent,
    FeaturedPropertiesComponent
  ],
  templateUrl: './home.html',
  styleUrls: ['./home.css']
})
export class HomeComponent implements OnInit, OnDestroy {
  private router = inject(Router);
  private clientApi = inject(ClientApiService);
  private layoutState = inject(LayoutStateService);
  private searchState = inject(HomeSearchStateService);
  private authService = inject(AuthService);
  private changeDetector = inject(ChangeDetectorRef);
  
  destinations: LocationSuggestion[] = [];
  featuredProperties: Hotel[] = [];
  isLoadingDestinations = true;
  isLoadingFeatured = true;

  @ViewChild('heroSearchRef', { static: true }) heroSearchRef!: ElementRef;

  showStickySearch = false;
  private observer!: IntersectionObserver;

  ngOnInit() {
    this.loadPopularDestinations();
    this.loadFeaturedProperties();

    // IntersectionObserver to show sticky search when hero search is out of view
    this.observer = new IntersectionObserver(
      ([entry]) => {
        // if entry.isIntersecting is false, it means the hero search is out of view (scrolled past)
        this.showStickySearch = !entry.isIntersecting && entry.boundingClientRect.top < 0;
        this.layoutState.hideMainHeader.set(this.showStickySearch);
      },
      { threshold: 0 }
    );
    if (this.heroSearchRef) {
      this.observer.observe(this.heroSearchRef.nativeElement);
    }
  }

  ngOnDestroy() {
    if (this.observer) {
      this.observer.disconnect();
    }
    this.layoutState.hideMainHeader.set(false);
  }

  private loadPopularDestinations() {
    this.isLoadingDestinations = true;
    this.clientApi.getPopularDestinations(8).subscribe({
      next: (provinces) => {
        this.destinations = provinces;
        this.isLoadingDestinations = false;
        this.changeDetector.detectChanges();
      },
      error: () => {
        this.destinations = [];
        this.isLoadingDestinations = false;
        this.changeDetector.detectChanges();
      }
    });
  }

  private loadFeaturedProperties() {
    this.isLoadingFeatured = true;
    this.clientApi.searchHotels({
      ...this.searchState.bookingQueryParams(),
      pageNumber: 0,
      pageSize: 8,
      sortBy: 'RATING'
    }).subscribe({
      next: response => {
        this.featuredProperties = response.content;
        this.isLoadingFeatured = false;
        this.changeDetector.detectChanges();
      },
      error: () => {
        this.featuredProperties = [];
        this.isLoadingFeatured = false;
        this.changeDetector.detectChanges();
      }
    });
  }

  openOwnerPortal(): void {
    const auth = this.authService.getAuthState();
    if (!auth.isAuthenticated) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: '/partner/register' } });
    } else {
      this.clientApi.getProfile().subscribe({
        next: context => {
          const roles = (context.roles || []).map(role => typeof role === 'string' ? role : role.code);
          if (roles.includes('PROPERTY_OWNER') || context.assignedProperties?.length) this.router.navigate(['/management/dashboard']);
          else if (context.partnerRegistrationStatus === 'PENDING') this.router.navigate(['/partner/registration-status']);
          else this.router.navigate(['/partner/register']);
        },
        error: () => this.router.navigate(['/partner/register'])
      });
    }
  }
}
