<<<<<<< HEAD
import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
=======
>>>>>>> codex/ui-functional-audit-polish
import { CommonModule } from '@angular/common';
import { Component, Input, inject } from '@angular/core';
import { CarouselModule } from 'primeng/carousel';

import { PublicI18nService } from '../../../../../core/i18n/public-i18n.service';
import { ImageFallbackService } from '../../../../../core/services/image-fallback.service';
<<<<<<< HEAD
import { FeedbackStateComponent } from '../../../../../shared/components/feedback-state/feedback-state.component';
=======
import { LocationSuggestion } from '../../../../../core/services/client-api.service';
import { HomeSearchStateService } from '../../services/home-search-state.service';
>>>>>>> codex/ui-functional-audit-polish

@Component({
  selector: 'app-popular-destinations',
  standalone: true,
  imports: [CommonModule, CarouselModule, FeedbackStateComponent],
  template: `
<<<<<<< HEAD
    <section class="mb-12" aria-labelledby="popular-destinations-title">
      <div class="mb-6">
        <span class="text-xs font-extrabold uppercase text-amber-700">Khám phá theo khu vực</span>
        <h2 id="popular-destinations-title" class="text-2xl md:text-[28px] font-bold text-gray-900 mt-1 font-serif">Điểm đến phổ biến</h2>
      </div>
      
      <!-- Skeleton Loading -->
      <div *ngIf="loading" class="flex gap-4 overflow-hidden">
        <div *ngFor="let i of [1,2,3,4,5]" class="w-full md:w-1/3 lg:w-1/5 flex-shrink-0 flex flex-col gap-2">
          <div class="w-full aspect-[4/3] rounded-2xl bg-gray-200 animate-pulse"></div>
          <div class="h-4 bg-gray-200 rounded w-1/2 animate-pulse mt-2"></div>
          <div class="h-3 bg-gray-100 rounded w-1/3 animate-pulse"></div>
        </div>
      </div>

      <app-feedback-state
        *ngIf="!loading && error"
        state="error"
        title="Không thể tải điểm đến"
        message="Dữ liệu điểm đến tạm thời không khả dụng."
        actionLabel="Thử lại"
        (actionTriggered)="retry.emit()">
      </app-feedback-state>

      <app-feedback-state
        *ngIf="!loading && !error && destinations.length === 0"
        state="empty"
        title="Chưa có điểm đến phổ biến"
        message="Các khu vực sẽ hiển thị khi có cơ sở lưu trú đang hoạt động.">
      </app-feedback-state>

      <!-- Carousel -->
      <div *ngIf="!loading && !error && destinations.length > 0" class="destination-carousel">
        <p-carousel [value]="destinations" [numVisible]="5" [numScroll]="1" [circular]="false" [responsiveOptions]="responsiveOptions" [showIndicators]="false">
          <ng-template pTemplate="item" let-dest>
            <div class="px-2 cursor-pointer group" [attr.data-destination-id]="dest.id" (click)="selectDestination(dest)">
              <div class="rounded-lg overflow-hidden shadow-sm hover:shadow-lg transition-all duration-300 border border-gray-200 bg-white">
                <div class="aspect-[4/3] w-full overflow-hidden relative bg-gray-100">
                  <img [src]="displayImage(dest.imageUrl)" [alt]="dest.imageAltText || dest.name"
                    [attr.data-image-provenance]="displayProvenance(dest.imageUrl, dest.imageProvenance)"
                    loading="lazy" (error)="handleImageError($event)"
                    class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-700">
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
    </section>
=======
    <div class="destination-section">
      <header class="section-intro">
        <div>
          <span class="eyebrow">{{ i18n.text('PUBLIC.HOME_CARDS.DESTINATION_KICKER') }}</span>
          <h2 id="destinations-title">{{ i18n.text('PUBLIC.HOME_CARDS.DESTINATION_TITLE') }}</h2>
        </div>
        <p>{{ i18n.text('PUBLIC.HOME_CARDS.DESTINATION_DESCRIPTION') }}</p>
      </header>

      <div *ngIf="loading" class="destination-grid" [attr.aria-label]="i18n.text('PUBLIC.HOME_CARDS.DESTINATION_LOADING')" aria-busy="true">
        <div *ngFor="let item of [1,2,3,4,5]" class="destination-skeleton">
          <span></span><b></b><i></i>
        </div>
      </div>

      <div *ngIf="!loading && destinations.length" class="destination-carousel">
        <p-carousel
          [value]="destinations"
          [numVisible]="5"
          [numScroll]="1"
          [circular]="false"
          [responsiveOptions]="responsiveOptions"
          [showIndicators]="false">
          <ng-template pTemplate="item" let-dest>
            <button type="button" class="destination-card" (click)="selectDestination(dest)">
              <span class="destination-image">
                <img
                  [src]="displayImage(dest.imageUrl, dest.id)"
                  [alt]="dest.name"
                  loading="lazy"
                  (error)="handleImageError($event, dest.id)">
                <span class="destination-arrow" aria-hidden="true"><i class="pi pi-arrow-up-right"></i></span>
              </span>
              <span class="destination-copy">
                <strong>{{ dest.name }}</strong>
                <small>{{ i18n.text('PUBLIC.HOME_CARDS.PROPERTY_COUNT', { count: dest.propertyCount || 0 }) }}</small>
              </span>
            </button>
          </ng-template>
        </p-carousel>
      </div>

      <div *ngIf="!loading && !destinations.length" class="empty-state" [class.error-state]="error" [attr.role]="error ? 'alert' : 'status'">
        <span class="empty-icon" aria-hidden="true"><i class="pi" [ngClass]="error ? 'pi-wifi' : 'pi-map'"></i></span>
        <div>
          <strong>{{ i18n.text(error ? 'PUBLIC.HOME_CARDS.DESTINATION_ERROR_TITLE' : 'PUBLIC.HOME_CARDS.DESTINATION_EMPTY_TITLE') }}</strong>
          <p>{{ i18n.text(error ? 'PUBLIC.HOME_CARDS.DESTINATION_ERROR_BODY' : 'PUBLIC.HOME_CARDS.DESTINATION_EMPTY_BODY') }}</p>
        </div>
        <button type="button" (click)="viewAll()">{{ i18n.text('PUBLIC.HOME_CARDS.VIEW_ALL_STAYS') }}</button>
      </div>
    </div>
>>>>>>> codex/ui-functional-audit-polish
  `,
  styles: [`
    .destination-section{padding-top:2rem}.section-intro{display:grid;grid-template-columns:minmax(0,1fr) minmax(18rem,.72fr);align-items:end;gap:1.5rem;margin-bottom:1.25rem}.eyebrow{display:block;color:#9a5b05;font-size:.75rem;font-weight:800;letter-spacing:.08em;text-transform:uppercase}.section-intro h2{margin:.3rem 0 0;color:#122039;font-size:clamp(1.8rem,3vw,2.35rem);line-height:1}.section-intro p{margin:0;color:#64748b;font-size:.86rem;line-height:1.65}.destination-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:1rem}.destination-skeleton{overflow:hidden;min-height:15rem;background:#fff;border:1px solid #e2e8f0;border-radius:1rem}.destination-skeleton span{display:block;aspect-ratio:4/3}.destination-skeleton b,.destination-skeleton i{display:block;height:.75rem;margin:1rem;border-radius:999px}.destination-skeleton i{width:52%;margin-top:-.3rem}.destination-skeleton span,.destination-skeleton b,.destination-skeleton i{background:linear-gradient(90deg,#e9eef4 25%,#f8fafc 50%,#e9eef4 75%);background-size:200% 100%;animation:shimmer 1.2s infinite}.destination-card{width:calc(100% - .8rem);margin:.35rem .4rem .75rem;padding:0;overflow:hidden;color:#172033;background:#fff;border:1px solid #dbe4ef;border-radius:1rem;box-shadow:0 .4rem 1.25rem rgb(15 23 42 / .06);font:inherit;text-align:left;cursor:pointer;transition:transform 180ms ease,border-color 180ms ease,box-shadow 180ms ease}.destination-card:hover,.destination-card:focus-visible{border-color:#72a3a0;box-shadow:0 1rem 2rem rgb(15 23 42 / .12);transform:translateY(-3px)}.destination-image{position:relative;display:block;aspect-ratio:4/3;overflow:hidden;background:#eef2f6}.destination-image::after{position:absolute;inset:auto 0 0;height:45%;content:'';background:linear-gradient(transparent,rgb(15 23 42 / .34))}.destination-image img{width:100%;height:100%;object-fit:cover;transition:transform 420ms ease}.destination-card:hover img{transform:scale(1.045)}.destination-arrow{position:absolute;right:.75rem;bottom:.75rem;z-index:1;display:grid;width:2.25rem;height:2.25rem;place-items:center;color:#fff;background:rgb(15 23 42 / .78);border:1px solid rgb(255 255 255 / .28);border-radius:50%}.destination-copy{display:flex;flex-direction:column;gap:.2rem;padding:1rem}.destination-copy strong{font-family:var(--hotel-font-heading);font-size:1.25rem;line-height:1.1}.destination-copy small{color:#64748b;font-size:.76rem}.empty-state{display:flex;min-height:8.5rem;align-items:center;gap:1rem;padding:1.25rem 1.4rem;color:#475569;background:linear-gradient(135deg,#fff,#f4f8f9);border:1px dashed #9fb7b6;border-radius:1rem}.empty-state.error-state{background:linear-gradient(135deg,#fff,#fff7ed);border-color:#fdba74}.empty-state.error-state .empty-icon{color:#b45309;background:#fef3c7}.empty-icon{display:grid;width:3rem;height:3rem;flex:0 0 auto;place-items:center;color:#0f766e;background:#ccfbf1;border-radius:50%;font-size:1.25rem}.empty-state div{min-width:0;flex:1}.empty-state strong{color:#172033}.empty-state p{margin:.15rem 0 0;font-size:.82rem}.empty-state button{min-height:2.75rem;padding:0 1rem;color:#fff;background:#0f766e;border:0;border-radius:999px;font:inherit;font-size:.82rem;font-weight:750;cursor:pointer}@keyframes shimmer{to{background-position:-200% 0}}:host ::ng-deep .destination-carousel .p-carousel-prev,:host ::ng-deep .destination-carousel .p-carousel-next{width:2.75rem!important;height:2.75rem!important;color:#0f766e!important;background:#fff!important;border:1px solid #cbd5e1!important;border-radius:50%!important;box-shadow:0 .5rem 1rem rgb(15 23 42 / .1)!important}:host ::ng-deep .destination-carousel .p-carousel-prev:hover,:host ::ng-deep .destination-carousel .p-carousel-next:hover{background:#f0fdfa!important;border-color:#0f766e!important}:host ::ng-deep .destination-carousel .p-carousel-prev.p-disabled,:host ::ng-deep .destination-carousel .p-carousel-next.p-disabled{opacity:.25!important}@media(max-width:64rem){.destination-grid{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:48rem){.destination-section{padding-top:1.65rem}.section-intro{grid-template-columns:1fr;gap:.45rem}.section-intro p{max-width:31rem}.destination-grid{display:flex;overflow:hidden}.destination-skeleton{min-width:72vw}.empty-state{align-items:flex-start;flex-wrap:wrap}.empty-state button{width:100%}}@media(max-width:36rem){.section-intro h2{font-size:1.9rem}.destination-card{width:calc(100% - .5rem);margin:.25rem .25rem .65rem}}@media(prefers-reduced-motion:reduce){.destination-card,.destination-image img{transition:none}.destination-card:hover,.destination-card:focus-visible,.destination-card:hover img{transform:none}.destination-skeleton span,.destination-skeleton b,.destination-skeleton i{animation:none}}
  `]
})
export class PopularDestinationsComponent {
  @Input() destinations: LocationSuggestion[] = [];
  @Input() loading = false;
  @Input() error = false;
<<<<<<< HEAD
  @Output() readonly retry = new EventEmitter<void>();
  
  private stateService = inject(HomeSearchStateService);
  private imageFallback = inject(ImageFallbackService);
=======
>>>>>>> codex/ui-functional-audit-polish

  private readonly stateService = inject(HomeSearchStateService);
  private readonly imageFallback = inject(ImageFallbackService);
  readonly i18n = inject(PublicI18nService);

  readonly responsiveOptions = [
    { breakpoint: '1199px', numVisible: 4, numScroll: 1 },
    { breakpoint: '991px', numVisible: 3, numScroll: 1 },
    { breakpoint: '767px', numVisible: 2, numScroll: 1 },
    { breakpoint: '575px', numVisible: 1, numScroll: 1 }
  ];

  selectDestination(destination: LocationSuggestion): void {
    this.stateService.selectSuggestion({
      type: 'PROVINCE',
      id: destination.id,
      name: destination.name,
      displayName: destination.displayName || destination.name
    });
    this.stateService.submitSearch();
  }

  viewAll(): void {
    this.stateService.submitSearch();
  }

  displayImage(imageUrl?: string): string {
    return imageUrl || this.imageFallback.destination();
  }

<<<<<<< HEAD
  displayProvenance(imageUrl?: string, provenance?: string): string {
    return imageUrl ? provenance || 'API_UNSPECIFIED' : 'FRONTEND_FALLBACK';
  }

  handleImageError(event: Event): void {
    const image = event.target as HTMLImageElement;
    const fallback = this.imageFallback.destination();
    if (image.src.endsWith(fallback)) return;
    image.dataset['imageFallback'] = 'true';
    this.imageFallback.replace(event, fallback);
=======
  handleImageError(event: Event, _id: number): void {
    this.imageFallback.replace(event, this.imageFallback.destination());
>>>>>>> codex/ui-functional-audit-polish
  }
}
