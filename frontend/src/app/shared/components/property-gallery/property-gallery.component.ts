import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, SimpleChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import {
  PropertyGalleryImage,
  PropertyGalleryService
} from '../../../core/services/property-gallery.service';

@Component({
  selector: 'app-property-gallery',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './property-gallery.component.html',
  styleUrl: './property-gallery.component.css'
})
export class PropertyGalleryComponent implements OnChanges {
  private readonly galleryService = inject(PropertyGalleryService);

  @Input({ required: true }) propertyId!: number;
  @Input() editable = true;

  images: PropertyGalleryImage[] = [];
  loading = false;
  saving = false;
  error = '';
  linkUrl = '';
  altTextVi = '';
  altTextEn = '';
  makePrimary = false;
  selectedFile?: File;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['propertyId'] && this.propertyId) this.load();
  }

  load(): void {
    if (!this.propertyId) return;
    this.loading = true;
    this.error = '';
    this.galleryService.list(this.propertyId).pipe(
      finalize(() => { this.loading = false; })
    ).subscribe({
      next: images => { this.images = images; },
      error: error => { this.error = this.errorMessage(error, 'Khong the tai thu vien anh.'); }
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0];
  }

  upload(): void {
    if (!this.editable || !this.selectedFile || this.saving) return;
    this.saving = true;
    this.error = '';
    this.galleryService.upload(
      this.propertyId,
      this.selectedFile,
      this.altTextVi,
      this.altTextEn,
      this.makePrimary
    ).pipe(finalize(() => { this.saving = false; })).subscribe({
      next: image => {
        this.images = this.normalizeAfterCreate(image);
        this.resetComposer();
      },
      error: error => { this.error = this.errorMessage(error, 'Khong the tai anh len.'); }
    });
  }

  addLink(): void {
    const imageUrl = this.linkUrl.trim();
    if (!this.editable || !imageUrl || this.saving) return;
    this.saving = true;
    this.error = '';
    this.galleryService.addLink(this.propertyId, {
      imageUrl,
      altTextVi: this.altTextVi.trim() || undefined,
      altTextEn: this.altTextEn.trim() || undefined,
      primary: this.makePrimary
    }).pipe(finalize(() => { this.saving = false; })).subscribe({
      next: image => {
        this.images = this.normalizeAfterCreate(image);
        this.resetComposer();
      },
      error: error => { this.error = this.errorMessage(error, 'Khong the them lien ket anh.'); }
    });
  }

  move(index: number, direction: -1 | 1): void {
    const target = index + direction;
    if (!this.editable || this.saving || target < 0 || target >= this.images.length) return;
    const previous = [...this.images];
    const reordered = [...this.images];
    [reordered[index], reordered[target]] = [reordered[target], reordered[index]];
    this.images = reordered.map((image, sortOrder) => ({ ...image, sortOrder }));
    this.saving = true;
    this.error = '';
    this.galleryService.reorder(this.propertyId, this.images.map(image => image.id)).pipe(
      finalize(() => { this.saving = false; })
    ).subscribe({
      next: images => { this.images = images; },
      error: error => {
        this.images = previous;
        this.error = this.errorMessage(error, 'Khong the sap xep thu vien anh.');
      }
    });
  }

  setPrimary(image: PropertyGalleryImage): void {
    if (!this.editable || image.primary || this.saving) return;
    this.saving = true;
    this.error = '';
    this.galleryService.setPrimary(this.propertyId, image.id).pipe(
      finalize(() => { this.saving = false; })
    ).subscribe({
      next: selected => {
        this.images = this.images.map(item => ({
          ...item,
          primary: item.id === selected.id
        }));
      },
      error: error => { this.error = this.errorMessage(error, 'Khong the dat anh dai dien.'); }
    });
  }

  remove(image: PropertyGalleryImage): void {
    if (!this.editable || this.saving) return;
    this.saving = true;
    this.error = '';
    this.galleryService.delete(this.propertyId, image.id).pipe(
      finalize(() => { this.saving = false; })
    ).subscribe({
      next: images => { this.images = images; },
      error: error => { this.error = this.errorMessage(error, 'Khong the xoa anh.'); }
    });
  }

  private normalizeAfterCreate(created: PropertyGalleryImage): PropertyGalleryImage[] {
    const existing = created.primary
      ? this.images.map(image => ({ ...image, primary: false }))
      : this.images;
    return [...existing, created].sort((left, right) => left.sortOrder - right.sortOrder);
  }

  private resetComposer(): void {
    this.linkUrl = '';
    this.altTextVi = '';
    this.altTextEn = '';
    this.makePrimary = false;
    this.selectedFile = undefined;
  }

  private errorMessage(error: any, fallback: string): string {
    return error?.error?.message || fallback;
  }
}
