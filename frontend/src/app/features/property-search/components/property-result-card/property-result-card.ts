import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { Hotel } from '../../../../core/services/client-api.service';
import { ImageFallbackService } from '../../../../core/services/image-fallback.service';

@Component({
  selector: 'app-property-result-card', standalone: true, imports: [CommonModule],
  template: `
    <article class="result-card">
      <button type="button" class="media" (click)="view()" [attr.aria-label]="'Xem ' + property.name">
        <img [src]="imageUrl" [alt]="property.imageAltText || property.name" loading="lazy"
          (error)="handleImageError($event)">
        <span class="type-badge">{{ propertyTypeLabel(property.propertyType) }}</span>
        <span *ngIf="property.imageCount && property.imageCount > 1" class="image-count">
          <i class="pi pi-images"></i> {{ property.imageCount }}
        </span>
      </button>

      <div class="property-info">
        <div class="title-row">
          <div><p class="property-type">{{ propertyTypeLabel(property.propertyType) }}</p>
            <h2><button type="button" (click)="view()">{{ property.name }}</button></h2></div>
          <div *ngIf="property.starRating" class="star-rating" [attr.aria-label]="property.starRating + ' sao'">
            <span *ngFor="let _ of stars(property.starRating)">★</span>
          </div>
        </div>
        <p class="address"><i class="pi pi-map-marker"></i>
          <span>{{ locationLine }}</span>
          <small *ngIf="property.distanceText">{{ property.distanceText }}</small>
        </p>
        <div *ngIf="property.lowestRoomType" class="room-fact">
          <i class="pi pi-bed"></i><span><strong>{{ property.lowestRoomType.name }}</strong>
          · tối đa {{ property.lowestRoomType.maxGuests }} khách/phòng</span>
        </div>
        <div class="amenities" *ngIf="property.amenities?.length">
          <span *ngFor="let amenity of property.amenities?.slice(0, 4)">{{ amenity }}</span>
        </div>
        <p *ngIf="property.availableRoomCount" class="availability">
          <i class="pi pi-check-circle"></i> {{ property.availableRoomCount }} phòng phù hợp còn trống
        </p>
      </div>

      <div class="commercial">
        <div class="review" *ngIf="property.reviewScore; else unrated">
          <span><strong>{{ reviewLabel(property.reviewScore) }}</strong><small>{{ property.reviewCount || 0 }} đánh giá</small></span>
          <b>{{ property.reviewScore | number:'1.1-1' }}</b>
        </div>
        <ng-template #unrated><p class="unrated">Chưa có đánh giá</p></ng-template>

        <div *ngIf="property.pricing; else unavailable" class="price-block">
          <p>Từ <strong>{{ formatVnd(effectiveNightlyPrice) }}</strong><span>/đêm</span></p>
          <small>{{ property.pricing.roomQuantity || 1 }} phòng · {{ property.pricing.numberOfNights }} đêm</small>
          <div class="total">Tổng <b>{{ formatVnd(property.pricing.totalAmount) }}</b></div>
          <small>Thuế và phí: {{ formatVnd(taxAndFees) }}</small>
          <button type="button" class="view-button" (click)="view()">Xem phòng <i class="pi pi-arrow-right"></i></button>
        </div>
        <ng-template #unavailable><div class="price-block"><p class="unavailable">Không còn phòng phù hợp</p></div></ng-template>
      </div>
    </article>
  `,
  styles: [`
    .result-card{display:grid;grid-template-columns:245px minmax(0,1fr) 220px;background:#fff;border:1px solid #dfe5ec;border-radius:8px;overflow:hidden;margin-bottom:16px;box-shadow:0 3px 14px rgba(15,23,42,.05);transition:.2s}.result-card:hover{border-color:#b9c8db;box-shadow:0 8px 24px rgba(15,23,42,.09)}
    .media{position:relative;border:0;padding:0;background:#eef2f6;cursor:pointer;min-height:224px}.media img{width:100%;height:100%;object-fit:cover;display:block}.type-badge,.image-count{position:absolute;background:rgba(15,23,42,.82);color:#fff;border-radius:4px;font-size:11px;padding:5px 8px}.type-badge{left:10px;top:10px}.image-count{right:10px;bottom:10px}
    .property-info{padding:20px;min-width:0}.title-row{display:flex;justify-content:space-between;gap:12px}.property-type{margin:0 0 4px;color:#64748b;font-size:12px;font-weight:700;text-transform:uppercase}.title-row h2{font-size:20px;line-height:1.25;margin:0}.title-row h2 button{border:0;padding:0;background:none;text-align:left;color:#12213a;font:inherit;font-weight:800;cursor:pointer}.title-row h2 button:hover{color:#1769e0}.star-rating{color:#d79a00;font-size:12px;white-space:nowrap}
    .address{display:flex;align-items:flex-start;gap:7px;color:#2864a7;font-size:13px;margin:12px 0}.address small{color:#64748b;border-left:1px solid #cbd5e1;padding-left:8px}.room-fact{display:flex;align-items:center;gap:8px;padding:10px 12px;background:#f7f9fc;border-radius:6px;color:#334155;font-size:13px}.amenities{display:flex;gap:6px;flex-wrap:wrap;margin-top:12px}.amenities span{background:#edf7f2;color:#14734b;padding:4px 7px;border-radius:4px;font-size:11px}.availability{font-size:12px;color:#14734b;font-weight:700;margin:14px 0 0}
    .commercial{border-left:1px solid #edf1f5;padding:18px;display:flex;flex-direction:column;text-align:right}.review{display:flex;justify-content:flex-end;gap:9px;align-items:center}.review span{display:flex;flex-direction:column;font-size:12px}.review small,.unrated{font-size:11px;color:#64748b}.review b{background:#174f9b;color:#fff;padding:8px;border-radius:5px;font-size:14px}.unrated{margin:0}.price-block{margin-top:auto}.price-block p{margin:12px 0 2px;font-size:13px;color:#475569}.price-block p strong{display:block;color:#12213a;font-size:23px}.price-block p span{font-size:12px}.price-block small{color:#64748b;font-size:11px}.total{margin-top:10px;font-size:13px}.total b{font-size:16px}.view-button{width:100%;height:42px;margin-top:13px;border:0;border-radius:6px;background:#1769e0;color:#fff;font-weight:800;cursor:pointer}.view-button:hover{background:#0f58c7}.unavailable{color:#b42318!important;font-weight:700}
    @media(max-width:760px){.result-card{grid-template-columns:1fr}.media{height:210px;min-height:0}.commercial{border-left:0;border-top:1px solid #edf1f5;text-align:left}.review{justify-content:flex-start}.price-block p strong{display:inline;margin-left:5px}.address{flex-wrap:wrap}}
  `]
})
export class PropertyResultCardComponent {
  @Input({ required: true }) property!: Hotel & any;
  @Output() viewDetails = new EventEmitter<number>();
  private readonly fallback = inject(ImageFallbackService);
  get imageUrl(): string { return this.property.thumbnailUrl || this.property.mainImageUrl || this.fallback.property(this.property.propertyType); }
  get locationLine(): string {
    const parts = [this.property.addressLine].filter(Boolean) as string[];
    const normalized = (this.property.addressLine || '').toLocaleLowerCase('vi-VN');
    if (this.property.wardName && !normalized.includes(this.property.wardName.toLocaleLowerCase('vi-VN'))) parts.push(this.property.wardName);
    if (this.property.provinceName && !normalized.includes(this.property.provinceName.toLocaleLowerCase('vi-VN'))) parts.push(this.property.provinceName);
    return parts.join(', ');
  }
  get effectiveNightlyPrice(): number { return this.property.pricing?.discountedNightlyPrice ?? this.property.pricing?.discountedPrice ?? this.property.pricing?.nightlyPrice ?? 0; }
  get taxAndFees(): number { return (this.property.pricing?.taxAmount || 0) + (this.property.pricing?.feeAmount || 0); }
  view(): void { this.viewDetails.emit(this.property.id); }
  handleImageError(event: Event): void { this.fallback.replace(event, this.fallback.property(this.property.propertyType)); }
  stars(value: number): number[] { return Array.from({ length: Math.max(0, Math.min(5, value || 0)) }); }
  reviewLabel(score: number): string { return score >= 9 ? 'Tuyệt hảo' : score >= 8 ? 'Rất tốt' : score >= 7 ? 'Tốt' : 'Dễ chịu'; }
  formatVnd(value: number): string { return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(value || 0)} ₫`; }
  propertyTypeLabel(type?: string): string { return ({ HOTEL:'Khách sạn',RESORT:'Khu nghỉ dưỡng',VILLA:'Biệt thự',APARTMENT:'Căn hộ',HOMESTAY:'Homestay',MOTEL:'Nhà nghỉ',GUEST_HOUSE:'Nhà khách',HOSTEL:'Hostel' } as Record<string,string>)[type || ''] || 'Cơ sở lưu trú'; }
}
