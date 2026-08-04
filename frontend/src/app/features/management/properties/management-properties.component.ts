import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ManagedProperty, ManagementApiService } from '../../../core/services/management-api.service';
import { PropertyLocation, PropertyService } from '../../../core/services/property.service';
import { FeedbackStateComponent } from '../../../shared/components/feedback-state/feedback-state.component';

@Component({
  selector: 'app-management-properties',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FeedbackStateComponent],
  templateUrl: './management-properties.component.html',
  styleUrl: './management-properties.component.css',
})
export class ManagementPropertiesComponent implements OnInit {
  private api = inject(ManagementApiService);
  private propertyService = inject(PropertyService);
  private fb = inject(FormBuilder);
  private cdr = inject(ChangeDetectorRef);
  properties: ManagedProperty[] = [];
  provinces: PropertyLocation[] = [];
  wards: PropertyLocation[] = [];
  selected?: ManagedProperty;
  loading = true;
  saving = false;
  error = '';
  success = '';

  readonly form = this.fb.nonNullable.group({
    nameVi: ['', [Validators.required, Validators.maxLength(255)]],
    nameEn: ['', Validators.maxLength(255)],
    propertyType: ['HOTEL', Validators.required],
    provinceId: [null as number | null, Validators.required],
    wardId: [null as number | null, Validators.required],
    address: ['', [Validators.required, Validators.maxLength(1000)]],
    phone: ['', Validators.maxLength(50)],
    email: ['', [Validators.email, Validators.maxLength(255)]],
    website: ['', Validators.maxLength(255)],
    starRating: [0, [Validators.min(0), Validators.max(5)]],
    descriptionVi: ['', Validators.maxLength(4000)],
  });

  ngOnInit(): void {
    this.load();
    this.propertyService.getProvinces().subscribe({ next: values => { this.provinces = values; this.cdr.markForCheck(); } });
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.api.properties().subscribe({
      next: properties => { this.properties = properties; if (properties.length) this.select(properties[0]); this.loading = false; this.cdr.markForCheck(); },
      error: error => { this.error = error?.error?.message || 'Không thể tải danh sách cơ sở.'; this.loading = false; this.cdr.markForCheck(); },
    });
  }

  select(property: ManagedProperty): void {
    this.selected = property;
    this.success = '';
    this.error = '';
    this.form.reset({
      nameVi: property.nameVi, nameEn: property.nameEn || '', propertyType: property.propertyType || 'HOTEL',
      provinceId: property.provinceId || null, wardId: property.wardId || null, address: property.address || '',
      phone: property.phone || '', email: property.email || '', website: property.website || '',
      starRating: property.starRating || 0, descriptionVi: property.descriptionVi || '',
    });
    if (property.provinceId) this.loadWards(property.provinceId, property.wardId);
  }

  provinceChanged(): void {
    const provinceId = this.form.controls.provinceId.value;
    this.form.controls.wardId.setValue(null);
    this.wards = [];
    if (provinceId) this.loadWards(provinceId);
  }

  save(): void {
    if (!this.selected || this.form.invalid || this.saving) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving = true;
    this.error = '';
    this.success = '';
    this.api.updateProperty(this.selected.id, this.form.getRawValue()).subscribe({
      next: updated => {
        this.properties = this.properties.map(property => property.id === updated.id ? updated : property);
        this.select(updated);
        this.success = 'Đã lưu hồ sơ cơ sở.';
        this.saving = false;
        this.cdr.markForCheck();
      },
      error: error => { this.error = error?.error?.message || 'Không thể lưu hồ sơ cơ sở.'; this.saving = false; this.cdr.markForCheck(); },
    });
  }

  private loadWards(provinceId: number, wardId?: number): void {
    this.propertyService.getWards(provinceId).subscribe({
      next: values => {
        this.wards = values;
        if (wardId && values.some(value => value.id === wardId)) this.form.controls.wardId.setValue(wardId);
        this.cdr.markForCheck();
      },
      error: () => { this.error = 'Không thể tải danh sách phường/xã.'; this.cdr.markForCheck(); },
    });
  }
}
