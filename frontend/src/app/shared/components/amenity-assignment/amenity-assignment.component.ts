import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, SimpleChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { Amenity, AmenityDraft, AmenityScope, AmenityService } from '../../../core/services/amenity.service';

@Component({
  selector: 'app-amenity-assignment',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="amenity-editor" [attr.aria-busy]="loading || saving">
      <header>
        <div><span class="eyebrow">Tien nghi</span><h3>{{ title }}</h3></div>
        <button type="button" class="secondary" (click)="load()" [disabled]="loading || saving">Tai lai</button>
      </header>

      @if (error) { <p class="message error">{{ error }}</p> }
      @if (success) { <p class="message success">{{ success }}</p> }
      @if (loading) { <p class="empty">Dang tai danh muc tien nghi...</p> }
      @else if (!activeCatalog.length) { <p class="empty">Chua co tien nghi dang hoat dong.</p> }
      @else {
        <div class="amenity-grid">
          @for (amenity of activeCatalog; track amenity.id) {
            <label [class.selected]="selectedIds.includes(amenity.id)">
              <input type="checkbox" [checked]="selectedIds.includes(amenity.id)"
                (change)="toggle(amenity.id, $any($event.target).checked)" [disabled]="!editable || saving">
              <i [class]="amenity.icon || 'pi pi-check-circle'"></i>
              <span><strong>{{ amenity.nameVi }}</strong><small>{{ amenity.nameEn || amenity.code }}</small></span>
            </label>
          }
        </div>
        @if (editable) {
          <button type="button" class="primary" (click)="save()" [disabled]="saving">
            {{ saving ? 'Dang luu...' : 'Luu tien nghi' }}
          </button>
        }
      }

      @if (catalogEditable) {
        <details class="catalog-admin">
          <summary>Quan ly danh muc he thong</summary>
          <div class="catalog-form">
            <input [(ngModel)]="draft.code" placeholder="Ma (VD: SPA)" maxlength="50">
            <input [(ngModel)]="draft.nameVi" placeholder="Ten tieng Viet" maxlength="255">
            <input [(ngModel)]="draft.nameEn" placeholder="Ten tieng Anh" maxlength="255">
            <select [(ngModel)]="draft.category">
              @for (category of categories; track category) { <option [value]="category">{{ category }}</option> }
            </select>
            <input type="number" min="0" [(ngModel)]="draft.sortOrder" aria-label="Thu tu">
            <button type="button" class="secondary" (click)="createCatalogEntry()" [disabled]="catalogSaving">Them</button>
          </div>
          <ul>
            @for (amenity of catalog; track amenity.id) {
              <li><span>{{ amenity.code }} - {{ amenity.nameVi }}</span><b>{{ amenity.status }}</b>
                @if (amenity.status === 'ACTIVE') { <button type="button" (click)="deactivate(amenity.id)">Ngung</button> }
              </li>
            }
          </ul>
        </details>
      }
    </section>
  `,
  styles: [`
    .amenity-editor{border:1px solid #dbe3ed;border-radius:10px;padding:16px;background:#f8fafc;margin-top:14px}.amenity-editor header{display:flex;align-items:center;justify-content:space-between;gap:12px}.amenity-editor h3{margin:2px 0 0;font-size:17px;color:#172033}.eyebrow{font-size:10px;letter-spacing:.08em;text-transform:uppercase;color:#64748b;font-weight:800}.amenity-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:9px;margin:14px 0}.amenity-grid label{display:flex;align-items:center;gap:9px;padding:10px;border:1px solid #d7e0ea;border-radius:8px;background:#fff;cursor:pointer}.amenity-grid label.selected{border-color:#1769e0;background:#eef5ff}.amenity-grid i{color:#1769e0}.amenity-grid span{display:flex;flex-direction:column}.amenity-grid small{color:#64748b;font-size:11px}.primary,.secondary,.catalog-admin button{border:0;border-radius:6px;padding:8px 12px;font-weight:700;cursor:pointer}.primary{background:#1769e0;color:#fff}.secondary{background:#e8eef6;color:#24415f}.message{padding:9px;border-radius:6px}.error{background:#fff0ef;color:#a12b24}.success{background:#eaf8f0;color:#12643e}.empty{color:#64748b}.catalog-admin{margin-top:16px;border-top:1px solid #dbe3ed;padding-top:12px}.catalog-admin summary{cursor:pointer;font-weight:800}.catalog-form{display:grid;grid-template-columns:1fr 1.4fr 1.4fr 1fr 80px auto;gap:7px;margin:12px 0}.catalog-form input,.catalog-form select{min-width:0;padding:8px;border:1px solid #cbd5e1;border-radius:6px}.catalog-admin ul{list-style:none;padding:0;margin:0}.catalog-admin li{display:flex;align-items:center;gap:10px;padding:7px 0;border-top:1px solid #e2e8f0}.catalog-admin li span{flex:1}.catalog-admin li b{font-size:10px;color:#64748b}@media(max-width:760px){.catalog-form{grid-template-columns:1fr 1fr}.amenity-grid{grid-template-columns:1fr}}
  `]
})
export class AmenityAssignmentComponent implements OnChanges {
  private readonly api = inject(AmenityService);

  @Input({ required: true }) scope: AmenityScope = 'property';
  @Input() entityId?: number;
  @Input() editable = false;
  @Input() catalogEditable = false;
  @Input() title = 'Tien nghi duoc cung cap';

  readonly categories = ['GENERAL', 'INTERNET', 'PARKING', 'FOOD', 'WELLNESS', 'ROOM', 'ACCESSIBILITY'];
  catalog: Amenity[] = [];
  selectedIds: number[] = [];
  loading = false;
  saving = false;
  catalogSaving = false;
  error = '';
  success = '';
  draft: AmenityDraft = this.emptyDraft();

  get activeCatalog(): Amenity[] { return this.catalog.filter(item => item.status === 'ACTIVE'); }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['entityId'] || changes['scope'] || changes['catalogEditable']) this.load();
  }

  load(): void {
    if (!this.entityId) { this.catalog = []; this.selectedIds = []; return; }
    this.loading = true; this.error = ''; this.success = '';
    forkJoin({
      catalog: this.catalogEditable ? this.api.managementCatalog() : this.api.publicCatalog(),
      assigned: this.api.assignments(this.scope, this.entityId)
    }).subscribe({
      next: ({ catalog, assigned }) => {
        this.catalog = catalog;
        this.selectedIds = assigned.map(item => item.id);
        this.loading = false;
      },
      error: error => { this.loading = false; this.error = error?.error?.message || 'Khong the tai tien nghi.'; }
    });
  }

  toggle(id: number, selected: boolean): void {
    this.selectedIds = selected
      ? Array.from(new Set([...this.selectedIds, id]))
      : this.selectedIds.filter(value => value !== id);
  }

  save(): void {
    if (!this.entityId || !this.editable || this.saving) return;
    this.saving = true; this.error = ''; this.success = '';
    this.api.replaceAssignments(this.scope, this.entityId, this.selectedIds).subscribe({
      next: assigned => { this.selectedIds = assigned.map(item => item.id); this.saving = false; this.success = 'Da luu tien nghi.'; },
      error: error => { this.saving = false; this.error = error?.error?.message || 'Khong the luu tien nghi.'; }
    });
  }

  createCatalogEntry(): void {
    if (!this.catalogEditable || this.catalogSaving || !this.draft.code.trim() || !this.draft.nameVi.trim()) return;
    this.catalogSaving = true; this.error = '';
    this.api.createCatalogEntry(this.draft).subscribe({
      next: () => { this.catalogSaving = false; this.draft = this.emptyDraft(); this.load(); },
      error: error => { this.catalogSaving = false; this.error = error?.error?.message || 'Khong the them tien nghi.'; }
    });
  }

  deactivate(id: number): void {
    if (!this.catalogEditable || this.catalogSaving) return;
    this.catalogSaving = true; this.error = '';
    this.api.deactivateCatalogEntry(id).subscribe({
      next: () => { this.catalogSaving = false; this.load(); },
      error: error => { this.catalogSaving = false; this.error = error?.error?.message || 'Khong the ngung tien nghi.'; }
    });
  }

  private emptyDraft(): AmenityDraft {
    return { code: '', nameVi: '', nameEn: '', category: 'GENERAL', icon: 'pi pi-check-circle', sortOrder: 0 };
  }
}
