import { CommonModule } from '@angular/common';
import { Component, Input, inject } from '@angular/core';
import { DateRangeSelectorComponent } from '../date-range-selector/date-range-selector.component';
import { GuestRoomSelectorComponent } from '../guest-room-selector/guest-room-selector.component';
import { LocationAutocompleteComponent } from '../location-autocomplete/location-autocomplete.component';
import { HomeSearchStateService } from '../../services/home-search-state.service';

@Component({
  selector: 'app-sticky-search-bar', standalone: true,
  imports: [CommonModule, LocationAutocompleteComponent, DateRangeSelectorComponent, GuestRoomSelectorComponent],
  template: `
    <section *ngIf="isVisible" class="search-shell" [class.embedded]="embedded" aria-label="Tìm kiếm chỗ nghỉ">
      <div class="search-inner">
        <button type="button" class="mobile-summary" (click)="mobileOpen = true">
          <i class="pi pi-search"></i><span><strong>{{ stateService.state().locationDisplayName || 'Bạn muốn đến đâu?' }}</strong>
          <small>{{ dateSummary }} · {{ stateService.guestSummary() }}</small></span><i class="pi pi-sliders-h"></i>
        </button>

        <div class="desktop-fields">
          <div class="field location"><app-location-autocomplete></app-location-autocomplete></div>
          <div class="field dates"><app-date-range-selector></app-date-range-selector></div>
          <div class="field guests"><app-guest-room-selector></app-guest-room-selector></div>
          <button type="button" class="search-button" (click)="search()"><i class="pi pi-search"></i><span>Tìm</span></button>
        </div>
      </div>

      <div *ngIf="mobileOpen" class="mobile-sheet" role="dialog" aria-modal="true" aria-label="Thay đổi tìm kiếm">
        <header><h2>Thay đổi tìm kiếm</h2><button type="button" (click)="mobileOpen = false" aria-label="Đóng"><i class="pi pi-times"></i></button></header>
        <div class="mobile-fields">
          <div class="field"><app-location-autocomplete></app-location-autocomplete></div>
          <div class="field"><app-date-range-selector></app-date-range-selector></div>
          <div class="field"><app-guest-room-selector></app-guest-room-selector></div>
        </div>
        <button type="button" class="search-button mobile-submit" (click)="search(); mobileOpen = false">Tìm chỗ nghỉ</button>
      </div>
    </section>
  `,
  styles: [`
    .search-shell{position:fixed;inset:0 0 auto;z-index:60;background:#fff;border-bottom:1px solid #e2e8f0;box-shadow:0 4px 16px rgba(15,23,42,.08)}.search-shell.embedded{position:sticky;top:0}.search-inner{max-width:1280px;margin:auto;padding:12px 20px}.desktop-fields{display:grid;grid-template-columns:minmax(260px,1.45fr) minmax(300px,1.2fr) minmax(220px,.9fr) 116px;gap:8px;align-items:stretch}.field{height:58px;border:1px solid #dce3eb;border-radius:7px;background:#fff;min-width:0}.field:focus-within{border-color:#1769e0;box-shadow:0 0 0 2px rgba(23,105,224,.12)}.search-button{border:0;border-radius:7px;background:#1769e0;color:#fff;font-weight:800;font-size:15px;display:flex;align-items:center;justify-content:center;gap:8px;cursor:pointer}.search-button:hover{background:#0f58c7}.mobile-summary{display:none}.mobile-sheet{display:none}
    @media(max-width:860px){.search-inner{padding:9px 14px}.desktop-fields{display:none}.mobile-summary{width:100%;min-height:58px;display:grid;grid-template-columns:26px 1fr 24px;gap:10px;align-items:center;text-align:left;border:1px solid #dce3eb;border-radius:7px;background:#fff;padding:9px 12px;color:#172033}.mobile-summary span{min-width:0;display:flex;flex-direction:column}.mobile-summary strong,.mobile-summary small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.mobile-summary small{font-size:12px;color:#64748b;margin-top:3px}.mobile-sheet{display:block;position:fixed;inset:0;z-index:100;background:#f7f9fc;padding:18px;overflow:auto}.mobile-sheet header{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px}.mobile-sheet h2{font-size:20px;margin:0}.mobile-sheet header button{width:40px;height:40px;border:0;border-radius:50%;background:#e8edf3}.mobile-fields{display:grid;gap:12px}.mobile-fields .field{height:64px}.mobile-submit{width:100%;height:52px;margin-top:20px}}
  `]
})
export class StickySearchBarComponent {
  @Input() isVisible = false;
  @Input() embedded = false;
  mobileOpen = false;
  readonly stateService = inject(HomeSearchStateService);
  get dateSummary(): string { const s=this.stateService.state(); return `${this.shortDate(s.checkInDate)} - ${this.shortDate(s.checkOutDate)}`; }
  search(): void { this.stateService.submitSearch(); }
  private shortDate(value: Date | null): string { return value ? new Intl.DateTimeFormat('vi-VN',{day:'2-digit',month:'2-digit'}).format(value) : '--/--'; }
}
