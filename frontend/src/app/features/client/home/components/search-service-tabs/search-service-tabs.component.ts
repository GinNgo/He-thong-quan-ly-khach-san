import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HomeSearchStateService } from '../../services/home-search-state.service';

@Component({
  selector: 'app-search-service-tabs',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="flex flex-wrap md:flex-nowrap gap-2 bg-white rounded-t-2xl px-4 py-2 border-b border-gray-100 shadow-sm w-fit mx-auto md:mx-0">
      <button *ngFor="let tab of tabs" 
              (click)="selectTab(tab)"
              [class]="'flex items-center gap-2 px-4 py-2.5 font-semibold text-[14px] transition-colors rounded-lg relative group whitespace-nowrap border ' + 
                       (isActive(tab) ? 'border-primary bg-blue-50 text-primary' : 'border-gray-200 bg-white text-gray-500 hover:bg-gray-50 hover:border-gray-300') + 
                       (tab.disabled ? ' opacity-50 cursor-not-allowed' : ' cursor-pointer')"
              [disabled]="tab.disabled">
        
        <i [class]="tab.icon"></i>
        <span>{{ tab.label }}</span>

        <div *ngIf="tab.disabled" class="absolute -top-1 right-0 bg-orange-100 text-orange-600 text-[9px] px-1.5 py-0.5 rounded font-bold uppercase">
          Sắp ra mắt
        </div>
      </button>
    </div>
  `
})
export class SearchServiceTabsComponent {
  private stateService = inject(HomeSearchStateService);

  tabs = [
    { id: 'all', label: 'Tất cả chỗ nghỉ', icon: 'pi pi-home', types: [], disabled: false },
    { id: 'hotel', label: 'Khách sạn', icon: 'pi pi-building', types: ['HOTEL'], disabled: false },
    { id: 'motel', label: 'Nhà nghỉ', icon: 'pi pi-home', types: ['MOTEL'], disabled: false },
    { id: 'homestay', label: 'Homestay', icon: 'pi pi-star', types: ['HOMESTAY'], disabled: false },
    { id: 'apartment', label: 'Căn hộ & Villa', icon: 'pi pi-key', types: ['APARTMENT', 'VILLA'], disabled: false },
    { id: 'flight', label: 'Vé máy bay', icon: 'pi pi-send', types: [], disabled: true },
    { id: 'transfer', label: 'Đưa đón sân bay', icon: 'pi pi-car', types: [], disabled: true }
  ];

  isActive(tab: any): boolean {
    const currentTypes = this.stateService.state().propertyTypes;
    if (tab.types.length === 0 && currentTypes.length === 0 && tab.id === 'all') return true;
    if (tab.types.length > 0 && currentTypes.length === tab.types.length && tab.types.every((t: string) => currentTypes.includes(t))) return true;
    return false;
  }

  selectTab(tab: any) {
    if (tab.disabled) return;
    this.stateService.updatePropertyTypes(tab.types);
  }
}
