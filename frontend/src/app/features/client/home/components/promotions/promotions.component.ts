import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-promotions',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="mb-12">
      <h2 class="text-2xl font-bold text-gray-900 mb-6 font-serif">Chương trình khuyến mãi chỗ ở</h2>
      
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <div *ngFor="let promo of promotions" class="group bg-white rounded-2xl overflow-hidden shadow-sm border border-gray-100 hover:shadow-xl transition-all duration-300 flex flex-col h-full">
          <div class="h-48 overflow-hidden relative">
            <img [src]="promo.image" [alt]="promo.title" class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-700">
            <div class="absolute top-4 left-4 bg-red-600 text-white text-xs font-bold px-3 py-1.5 rounded-lg uppercase tracking-wider shadow-lg">
              Giảm đến 20%
            </div>
          </div>
          <div class="p-6 flex flex-col flex-1">
            <h3 class="font-bold text-gray-900 text-xl mb-2 group-hover:text-primary transition-colors">{{ promo.title }}</h3>
            <p class="text-gray-600 text-sm mb-6 flex-1 line-clamp-3">{{ promo.desc }}</p>
            
            <div class="flex items-center justify-between mt-auto">
              <div class="bg-gray-50 border border-gray-200 rounded-lg px-4 py-2 flex items-center gap-3">
                <span class="font-mono font-bold text-gray-800 tracking-wider">{{ promo.code }}</span>
                <button (click)="copyCode(promo.code)" class="text-primary hover:text-blue-700 transition-colors bg-transparent border-none outline-none cursor-pointer p-1 flex items-center justify-center rounded-md hover:bg-blue-50" [title]="copiedCode === promo.code ? 'Đã sao chép' : 'Sao chép mã'">
                  <i class="pi" [ngClass]="copiedCode === promo.code ? 'pi-check text-green-600' : 'pi-copy'"></i>
                </button>
              </div>
              <button class="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 text-sm font-semibold rounded-lg transition-colors border-none outline-none cursor-pointer" (click)="onDetails.emit(promo)">
                Xem chi tiết
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class PromotionsComponent {
  @Input() promotions: any[] = [];
  @Output() onDetails = new EventEmitter<any>();

  copiedCode = '';

  copyCode(code: string) {
    navigator.clipboard.writeText(code);
    this.copiedCode = code;
    setTimeout(() => this.copiedCode = '', 2000);
  }
}
