import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-property-result-card',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden hover:shadow-md transition-shadow duration-300 flex flex-col sm:flex-row mb-4 cursor-pointer" (click)="onViewDetails()">
      
      <!-- Image Section -->
      <div class="relative w-full sm:w-72 h-48 sm:h-auto flex-shrink-0">
        <img [src]="property.thumbnailUrl || 'assets/images/placeholder.jpg'" 
             [alt]="property.name" 
             class="w-full h-full object-cover"
             (error)="$event.target.src='assets/images/placeholder.jpg'" />
        
        <!-- Badges on image -->
        <div class="absolute top-3 left-3 flex flex-col gap-1">
          <span *ngIf="property.propertyType" class="bg-black/60 text-white text-xs font-semibold px-2 py-1 rounded backdrop-blur-sm">
            {{ formatPropertyType(property.propertyType) }}
          </span>
        </div>
      </div>

      <!-- Info Section -->
      <div class="flex-1 p-4 sm:p-5 flex flex-col sm:flex-row gap-4">
        
        <!-- Main details -->
        <div class="flex-[2] flex flex-col">
          <div class="flex items-start justify-between">
            <h3 class="text-xl font-bold text-gray-900 leading-tight mb-1 group-hover:text-primary transition-colors">
              {{ property.name }}
            </h3>
            
            <!-- Rating -->
            <div *ngIf="property.starRating" class="flex gap-0.5 mt-1 ml-2 flex-shrink-0">
               <i *ngFor="let i of [].constructor(property.starRating)" class="pi pi-star-fill text-accent-gold text-[10px]"></i>
            </div>
          </div>

          <div class="text-sm text-primary font-medium flex items-center gap-1 mb-2">
            <i class="pi pi-map-marker"></i>
            <span class="hover:underline">{{ property.addressLine }}</span>
            <span *ngIf="property.distanceKm" class="text-gray-500 font-normal">
              &bull; {{ property.distanceKm | number:'1.1-1' }} km từ trung tâm
            </span>
          </div>
          
          <p *ngIf="property.lowestRoomType" class="text-sm text-gray-700 mt-2 font-medium bg-gray-50 p-2 rounded border border-gray-100 inline-block">
            Phòng tiêu chuẩn: {{ property.lowestRoomType.name }} (Tối đa {{ property.lowestRoomType.maxGuests }} khách)
          </p>

          <div class="flex gap-2 mt-auto pt-4 flex-wrap">
             <span *ngIf="property.freeCancellation" class="text-xs text-green-700 bg-green-50 px-2 py-1 rounded border border-green-100 font-medium">Miễn phí hủy</span>
             <span *ngIf="property.payAtProperty" class="text-xs text-blue-700 bg-blue-50 px-2 py-1 rounded border border-blue-100 font-medium">Thanh toán tại chỗ</span>
             <span *ngIf="property.breakfastIncluded" class="text-xs text-orange-700 bg-orange-50 px-2 py-1 rounded border border-orange-100 font-medium">Bao gồm bữa sáng</span>
          </div>
        </div>

        <!-- Price & CTA -->
        <div class="flex-[1] flex flex-col justify-end items-end sm:border-l border-gray-100 sm:pl-4 mt-4 sm:mt-0">
          
          <div *ngIf="property.reviewScore" class="flex items-center gap-2 mb-auto pb-4">
             <div class="flex flex-col items-end">
               <span class="font-bold text-sm text-gray-800">Tuyệt vời</span>
               <span class="text-xs text-gray-500">{{ property.reviewCount || 0 }} đánh giá</span>
             </div>
             <div class="bg-primary text-white font-bold h-8 w-8 rounded flex items-center justify-center text-sm">
               {{ property.reviewScore | number:'1.1-1' }}
             </div>
          </div>

          <div *ngIf="property.pricing; else noPrice" class="text-right mt-auto w-full">
             <div class="text-xs text-gray-500 mb-1">Giá cho {{ property.pricing.numberOfNights }} đêm</div>
             <div *ngIf="property.pricing.discountedPrice < property.pricing.nightlyPrice" class="text-sm text-red-500 line-through">
                {{ property.pricing.nightlyPrice | currency:'VND':'symbol':'1.0-0' }}
             </div>
             <div class="text-2xl font-bold text-gray-900 leading-none mb-1">
                {{ property.pricing.totalAmount | currency:'VND':'symbol':'1.0-0' }}
             </div>
             <div class="text-xs text-gray-500 mb-3">Bao gồm thuế và phí</div>
             <button class="w-full bg-primary hover:bg-primary-hover text-white font-bold py-2 px-4 rounded-lg transition-colors flex items-center justify-center gap-2">
                Xem phòng <i class="pi pi-angle-right"></i>
             </button>
             <div *ngIf="property.availableRoomCount > 0 && property.availableRoomCount <= 5" class="text-xs text-red-600 font-medium mt-2 text-center">
               Chỉ còn {{ property.availableRoomCount }} phòng với giá này!
             </div>
          </div>
          <ng-template #noPrice>
             <div class="text-right mt-auto w-full">
               <button class="w-full bg-gray-100 hover:bg-gray-200 text-gray-800 font-bold py-2 px-4 rounded-lg transition-colors border border-gray-200">
                  Kiểm tra giá
               </button>
             </div>
          </ng-template>

        </div>
      </div>
    </div>
  `
})
export class PropertyResultCardComponent {
  @Input() property: any;
  @Output() viewDetails = new EventEmitter<number>();

  onViewDetails() {
    this.viewDetails.emit(this.property.id);
  }

  formatPropertyType(type: string): string {
    const map: any = {
      'HOTEL': 'Khách sạn',
      'RESORT': 'Khu nghỉ dưỡng',
      'VILLA': 'Biệt thự',
      'APARTMENT': 'Căn hộ',
      'HOMESTAY': 'Nhà khách'
    };
    return map[type] || type;
  }
}
