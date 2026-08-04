import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DataTable } from './data-table';

describe('DataTable', () => {
  let component: DataTable;
  let fixture: ComponentFixture<DataTable>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DataTable],
    }).compileComponents();

    fixture = TestBed.createComponent(DataTable);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('names the table region and exposes keyboard-operable rows', async () => {
    fixture.componentRef.setInput('tableLabel', 'Danh sach phong');
    fixture.componentRef.setInput('columns', [{ field: 'number', header: 'So phong' }]);
    fixture.componentRef.setInput('data', [{ number: '101' }]);
    fixture.componentRef.setInput('totalRecords', 1);
    const selected: unknown[] = [];
    component.rowClick.subscribe(row => selected.push(row));
    fixture.detectChanges();
    await fixture.whenStable();

    const region = fixture.nativeElement.querySelector('[role="region"]') as HTMLElement;
    const caption = fixture.nativeElement.querySelector(`#${component.tableCaptionId}`) as HTMLElement;
    const row = fixture.nativeElement.querySelector('tbody tr[tabindex="0"]') as HTMLTableRowElement;

    expect(region.getAttribute('aria-labelledby')).toBe(component.tableCaptionId);
    expect(caption.textContent).toContain('Danh sach phong');
    expect(fixture.nativeElement.querySelector('input[type="text"]').getAttribute('aria-label'))
      .toContain('Danh sach phong');

    row.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    expect(selected).toEqual([{ number: '101' }]);
  });
});
