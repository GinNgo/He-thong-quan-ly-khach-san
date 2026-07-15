import { CommonModule } from '@angular/common';
import { Component, Input, inject } from '@angular/core';
import { Router } from '@angular/router';
import { Hotel } from '../../../../../core/services/client-api.service';
import { HomeSearchStateService } from '../../services/home-search-state.service';
import { ImageFallbackService } from '../../../../../core/services/image-fallback.service';

@Component({
  selector: 'app-featured-properties',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="featured-section" aria-labelledby="featured-title">
      <div class="section-intro">
        <div>
          <span class="eyebrow">Cơ sở đang hoạt động</span>
          <h2 id="featured-title">Cơ sở nổi bật</h2>
        </div>
        <button type="button" (click)="viewAll()">Xem tất cả <i class="pi pi-arrow-right"></i></button>
      </div>

      <div *ngIf="loading" class="property-grid" aria-label="Đang tải cơ sở">
        <div *ngFor="let item of [1,2,3,4]" class="property-skeleton"><span></span><b></b><i></i></div>
      </div>

      <div *ngIf="!loading && properties.length" class="property-grid">
        <article *ngFor="let property of properties; trackBy: trackByProperty" class="property-card" tabindex="0"
          (click)="openProperty(property.id)" (keydown.enter)="openProperty(property.id)">
          <div class="property-image">
            <img [src]="displayImage(property)"
              [alt]="property.name" loading="lazy" (error)="handleImageError($event, property.propertyType)">
            <span>{{ propertyTypeLabel(property.propertyType) }}</span>
          </div>
          <div class="property-body">
            <div class="property-title-row">
              <h3>{{ property.name }}</h3>
              <strong *ngIf="property.reviewScore && property.reviewCount">{{ property.reviewScore | number:'1.1-1' }}</strong>
            </div>
            <p><i class="pi pi-map-marker"></i>{{ property.wardName || property.provinceName || property.addressLine }}</p>
            <div class="property-meta">
              <span *ngIf="property.reviewCount; else noReview">{{ property.reviewCount }} đánh giá</span>
              <ng-template #noReview><span>Chưa có đánh giá</span></ng-template>
              <span *ngIf="property.availableRoomCount !== undefined">{{ property.availableRoomCount }} phòng phù hợp</span>
            </div>
            <div class="property-price" *ngIf="property.pricing; else unavailablePrice">
              <span>Từ</span><strong>{{ property.pricing.nightlyPrice | currency:'VND':'symbol':'1.0-0' }}</strong><small>/đêm</small>
            </div>
            <ng-template #unavailablePrice><p class="unavailable-price">Hết phòng trong ngày đã chọn</p></ng-template>
          </div>
        </article>
      </div>
    </section>
  `,
  styles: [`
    .featured-section{margin:52px 0}.section-intro{display:flex;align-items:end;justify-content:space-between;gap:20px;margin-bottom:20px}.eyebrow{display:block;color:#b36b00;font-size:12px;font-weight:800;text-transform:uppercase}.section-intro h2{margin:4px 0 0;color:#172033;font-size:28px;line-height:1.2;letter-spacing:0}.section-intro button{border:0;background:transparent;color:#1d4ed8;font-weight:750;cursor:pointer}.property-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:18px}.property-card{overflow:hidden;border:1px solid #e2e6ec;border-radius:8px;background:#fff;cursor:pointer;transition:box-shadow .2s,border-color .2s,transform .2s}.property-card:hover,.property-card:focus-visible{border-color:#9bb7ef;box-shadow:0 12px 28px rgba(15,23,42,.11);transform:translateY(-2px);outline:0}.property-image{position:relative;aspect-ratio:4/3;background:#eef2f6}.property-image img{width:100%;height:100%;object-fit:cover}.property-image>span{position:absolute;left:10px;top:10px;padding:5px 8px;border-radius:4px;background:rgba(15,23,42,.82);color:#fff;font-size:11px;font-weight:700}.property-body{padding:14px}.property-title-row{display:flex;align-items:start;gap:10px}.property-title-row h3{min-width:0;flex:1;margin:0;color:#172033;font-size:16px;line-height:1.35;letter-spacing:0}.property-title-row>strong{padding:5px 6px;border-radius:5px 5px 5px 0;background:#1849a9;color:#fff;font-size:12px}.property-body>p{display:flex;gap:6px;align-items:center;margin:8px 0;color:#667085;font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.property-body>p i{color:#d28b00}.property-meta{display:flex;flex-wrap:wrap;gap:6px 12px;min-height:18px;color:#667085;font-size:11px}.property-price{display:flex;align-items:baseline;justify-content:flex-end;gap:4px;margin-top:14px;padding-top:12px;border-top:1px solid #eef0f3}.property-price span,.property-price small{color:#667085;font-size:11px}.property-price strong{color:#b42318;font-size:18px}.unavailable-price{margin:14px 0 0!important;padding-top:12px;border-top:1px solid #eef0f3;color:#b42318!important;font-weight:700;white-space:normal!important}.property-skeleton{height:330px;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden}.property-skeleton span{display:block;height:62%}.property-skeleton b,.property-skeleton i{display:block;height:12px;margin:16px;border-radius:4px}.property-skeleton i{width:55%}.property-skeleton span,.property-skeleton b,.property-skeleton i{background:linear-gradient(90deg,#edf0f4 25%,#f8fafc 50%,#edf0f4 75%);background-size:200% 100%;animation:shimmer 1.2s infinite}@keyframes shimmer{to{background-position:-200% 0}}@media(max-width:1000px){.property-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:580px){.featured-section{margin:38px 0}.section-intro h2{font-size:23px}.property-grid{display:flex;overflow-x:auto;scroll-snap-type:x mandatory;padding-bottom:8px}.property-card,.property-skeleton{min-width:78vw;scroll-snap-align:start}}
  `]
})
export class FeaturedPropertiesComponent {
  @Input() properties: Hotel[] = [];
  @Input() loading = false;
  private readonly router = inject(Router);
  private readonly stateService = inject(HomeSearchStateService);
  private readonly imageFallback = inject(ImageFallbackService);

  openProperty(id: number): void {
    this.router.navigate(['/hotel', id], { queryParams: this.stateService.bookingQueryParams() });
  }

  viewAll(): void { this.stateService.submitSearch(); }

  propertyTypeLabel(type?: string): string {
    const labels: Record<string, string> = { HOTEL:'Khách sạn',MOTEL:'Nhà nghỉ',HOMESTAY:'Homestay',HOSTEL:'Hostel',APARTMENT:'Căn hộ',VILLA:'Villa',RESORT:'Khu nghỉ dưỡng',GUEST_HOUSE:'Nhà khách' };
    return type ? labels[type] || 'Cơ sở lưu trú' : 'Cơ sở lưu trú';
  }

  fallbackImage(type?: string): string {
    return this.imageFallback.property(type);
  }

  displayImage(property: Hotel): string {
    return property.thumbnailUrl || property.mainImageUrl || property.mainImage || this.fallbackImage(property.propertyType);
  }

  handleImageError(event: Event, type?: string): void {
    this.imageFallback.replace(event, this.fallbackImage(type));
  }

  trackByProperty(_index: number, property: Hotel): number { return property.id; }
}
