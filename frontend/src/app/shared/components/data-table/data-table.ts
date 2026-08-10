import { Component, Input, Output, EventEmitter, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule, TableLazyLoadEvent } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { TooltipModule } from 'primeng/tooltip';
import { PageRequest, SortRequest, FilterRequest } from '../../models/pagination.model';
import { PermissionDirective } from '../../directives/permission';

export interface ColumnDefinition {
  field: string;
  header: string;
  type?: 'text' | 'number' | 'date' | 'currency' | 'boolean' | 'badge';
  sortable?: boolean;
  filterable?: boolean;
  align?: 'left' | 'center' | 'right';
  width?: string;
  format?: string;
}

@Component({
  selector: 'app-data-table',
  standalone: true,
  imports: [
    CommonModule,
    TableModule,
    ButtonModule,
    InputTextModule,
    IconFieldModule,
    InputIconModule,
    TooltipModule,
    PermissionDirective
  ],
  templateUrl: './data-table.html',
  styleUrl: './data-table.css'
})
export class DataTable implements OnInit {
  @Input() columns: ColumnDefinition[] = [];
  @Input() data: any[] = [];
  @Input() totalRecords: number = 0;
  @Input() pageSize: number = 20;
  @Input() loading: boolean = false;
  @Input() permissions = {
    view: '',
    edit: '',
    delete: ''
  };

  @Output() pageChange = new EventEmitter<PageRequest>();
  @Output() sortChange = new EventEmitter<SortRequest>();
  @Output() filterChange = new EventEmitter<FilterRequest>();
  @Output() rowClick = new EventEmitter<any>();
  @Output() edit = new EventEmitter<any>();
  @Output() delete = new EventEmitter<any>();
  @Output() view = new EventEmitter<any>();

  globalFilter = signal<string>('');

  ngOnInit(): void {}

  onLazyLoad(event: TableLazyLoadEvent) {
    const pageRequest: PageRequest = {
      pageNumber: event.first ? Math.floor(event.first / (event.rows || this.pageSize)) + 1 : 1,
      pageSize: event.rows || this.pageSize,
      keyword: this.globalFilter()
    };

    if (event.sortField) {
      pageRequest.sortField = event.sortField as string;
      pageRequest.sortDirection = event.sortOrder === 1 ? 'asc' : 'desc';
    }

    this.pageChange.emit(pageRequest);
  }

  onGlobalSearch(event: Event) {
    const value = (event.target as HTMLInputElement).value;
    this.globalFilter.set(value);
    
    // We emit filter change and it will typically trigger a reload from page 1
    this.filterChange.emit({ keyword: value });
  }

  exportExcel() {
    // Standard Excel Export Logic (can be intercepted by parent or handled globally)
    console.log('Exporting to Excel');
  }

  exportPdf() {
    console.log('Exporting to PDF');
  }
}
