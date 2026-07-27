import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { PartnerOverviewComponent } from './partner-overview.component';

describe('PartnerOverviewComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PartnerOverviewComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { data: { title: 'Chủ cơ sở', endpoint: 'property-owners' } } },
        },
      ],
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('leaves loading and renders API rows without manual change detection', async () => {
    const fixture = TestBed.createComponent(PartnerOverviewComponent);
    fixture.detectChanges();

    http.expectOne(`${environment.apiUrl}/admin/property-owners`).flush([
      { id: 1, owner: 'Owner One' },
    ]);
    await fixture.whenStable();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).not.toContain('Đang tải dữ liệu...');
    expect(element.textContent).toContain('Owner One');
    expect(element.querySelector('table')).not.toBeNull();
  });

  it('renders a recoverable error after a failed request', async () => {
    const fixture = TestBed.createComponent(PartnerOverviewComponent);
    fixture.detectChanges();

    http
      .expectOne(`${environment.apiUrl}/admin/property-owners`)
      .flush({ message: 'Không thể đọc dữ liệu chủ cơ sở.' }, { status: 500, statusText: 'Error' });
    await fixture.whenStable();

    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).toContain('Không thể đọc dữ liệu chủ cơ sở.');
    expect(element.querySelector('[role="alert"]')).not.toBeNull();
  });
});
