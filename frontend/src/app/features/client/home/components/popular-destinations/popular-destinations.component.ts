import { Component, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CarouselModule } from 'primeng/carousel';
import { HomeSearchStateService } from '../../services/home-search-state.service';
import { LocationSuggestion } from '../../../../../core/services/client-api.service';
import { ImageFallbackService } from '../../../../../core/services/image-fallback.service';

@Component({
  selector: 'app-popular-destinations',
  standalone: true,
  imports: [CommonModule, CarouselModule],
  template: `
    <div class="mb-12">
      <div class="mb-6">
        <span class="text-xs font-extrabold uppercase text-amber-700">Khám phá theo khu vực</span>
        <h2 class="text-2xl md:text-[28px] font-bold text-gray-900 mt-1 font-serif">Điểm đến phổ biến</h2>
      </div>
      
      <!-- Skeleton Loading -->
      <div *ngIf="loading" class="flex gap-4 overflow-hidden">
        <div *ngFor="let i of [1,2,3,4,5]" class="w-full md:w-1/3 lg:w-1/5 flex-shrink-0 flex flex-col gap-2">
          <div class="w-full aspect-[4/3] rounded-2xl bg-gray-200 animate-pulse"></div>
          <div class="h-4 bg-gray-200 rounded w-1/2 animate-pulse mt-2"></div>
          <div class="h-3 bg-gray-100 rounded w-1/3 animate-pulse"></div>
        </div>
      </div>

      <!-- Carousel -->
      <div *ngIf="!loading && destinations.length > 0" class="destination-carousel">
        <p-carousel [value]="destinations" [numVisible]="5" [numScroll]="1" [circular]="false" [responsiveOptions]="responsiveOptions" [showIndicators]="false">
          <ng-template pTemplate="item" let-dest>
            <div class="px-2 cursor-pointer group" (click)="selectDestination(dest)">
              <div class="rounded-lg overflow-hidden shadow-sm hover:shadow-lg transition-all duration-300 border border-gray-200 bg-white">
                <div class="aspect-[4/3] w-full overflow-hidden relative bg-gray-100">
                  <img [src]="displayImage(dest.imageUrl, dest.id)" [alt]="dest.name" loading="lazy"
                    (error)="handleImageError($event, dest.id)" class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-700">
                  <div class="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent opacity-0 group-hover:opacity-100 transition-opacity"></div>
                </div>
                <div class="p-4 text-center">
                  <h3 class="font-bold text-gray-900 text-lg group-hover:text-primary transition-colors">{{ dest.name }}</h3>
                  <p class="text-gray-500 text-sm mt-1">{{ dest.propertyCount || 0 | number }} chỗ nghỉ</p>
                </div>
              </div>
            </div>
          </ng-template>
        </p-carousel>
      </div>
    </div>
  `,
  styles: [`
    :host ::ng-deep .destination-carousel .p-carousel-prev,
    :host ::ng-deep .destination-carousel .p-carousel-next {
      background: white !important;
      color: #2563EB !important;
      border: 1px solid #E2E8F0 !important;
      box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1) !important;
      width: 40px !important;
      height: 40px !important;
      border-radius: 50% !important;
      transition: all 0.3s !important;
    }
    :host ::ng-deep .destination-carousel .p-carousel-prev:hover,
    :host ::ng-deep .destination-carousel .p-carousel-next:hover {
      background: #F8FAFC !important;
      border-color: #2563EB !important;
      transform: scale(1.05);
    }
    :host ::ng-deep .destination-carousel .p-carousel-prev.p-disabled,
    :host ::ng-deep .destination-carousel .p-carousel-next.p-disabled {
      opacity: 0 !important;
      pointer-events: none;
    }
  `]
})
export class PopularDestinationsComponent {
  @Input() destinations: LocationSuggestion[] = [];
  @Input() loading = false;
  
  private stateService = inject(HomeSearchStateService);
  private imageFallback = inject(ImageFallbackService);

  responsiveOptions = [
    { breakpoint: '1199px', numVisible: 4, numScroll: 1 },
    { breakpoint: '991px', numVisible: 3, numScroll: 1 },
    { breakpoint: '767px', numVisible: 2, numScroll: 1 },
    { breakpoint: '575px', numVisible: 1, numScroll: 1 }
  ];

  selectDestination(dest: LocationSuggestion) {
    this.stateService.selectSuggestion({ type: 'PROVINCE', id: dest.id, name: dest.name, displayName: dest.displayName || dest.name });
    this.stateService.submitSearch();
  }

  displayImage(imageUrl: string | undefined, _id: number): string {
    return imageUrl || this.imageFallback.destination();
  }

  handleImageError(event: Event, id: number): void {
    this.imageFallback.replace(event, this.imageFallback.destination());
  }
}
