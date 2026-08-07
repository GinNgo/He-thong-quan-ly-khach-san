import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '@app/core/services/auth';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let router: Router;
  let authServiceMock: {
    isLoggedIn: ReturnType<typeof vi.fn>;
    getRoles: ReturnType<typeof vi.fn>;
    login: ReturnType<typeof vi.fn>;
    googleLogin: ReturnType<typeof vi.fn>;
    facebookLogin: ReturnType<typeof vi.fn>;
    setSession: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    authServiceMock = {
      isLoggedIn: vi.fn(() => false),
      getRoles: vi.fn(() => []),
      login: vi.fn(() => of(null)),
      googleLogin: vi.fn(),
      facebookLogin: vi.fn(),
      setSession: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        {
          provide: AuthService,
          useValue: authServiceMock,
        },
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParams: {} } } },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('centers field controls through the shared icon classes and toggles the password type', () => {
    const passwordInput = fixture.nativeElement.querySelector('#password') as HTMLInputElement;
    const toggle = fixture.nativeElement.querySelector('button[aria-label="Hiện mật khẩu"]') as HTMLButtonElement;

    expect(fixture.nativeElement.querySelectorAll('.field-icon').length).toBe(3);
    expect(passwordInput.type).toBe('password');

    toggle.click();
    fixture.detectChanges();

    expect(passwordInput.type).toBe('text');
    expect(toggle.getAttribute('aria-pressed')).toBe('true');
  });

  it('enables social sign-in when public OAuth IDs are configured', () => {
    const facebookButton = fixture.nativeElement.querySelector('.social-button--facebook') as HTMLButtonElement;
    const googleMobileButton = fixture.nativeElement.querySelector('.social-button--google-mobile') as HTMLButtonElement;

    expect(component.isGoogleConfigured).toBe(true);
    expect(component.isFacebookConfigured).toBe(true);
    expect(fixture.nativeElement.querySelector('asl-google-signin-button')).not.toBeNull();
    expect(facebookButton.disabled).toBe(false);
    expect(googleMobileButton.getAttribute('aria-label')).toBe('Tiếp tục với Google');
    expect(facebookButton.getAttribute('aria-label')).toBe('Tiếp tục với Facebook');
  });

  it('keeps the Google provider width within the shared login card content width', () => {
    expect(component.googleButtonWidth).toBeGreaterThanOrEqual(200);
    expect(component.googleButtonWidth).toBeLessThanOrEqual(400);
  });

  it('links the footer to real privacy, terms, and support destinations', () => {
    const anchors = [...fixture.nativeElement.querySelectorAll('footer a')] as HTMLAnchorElement[];
    const destinations = anchors.map(anchor => anchor.getAttribute('href'));

    expect(destinations).toEqual(expect.arrayContaining(['/privacy', '/terms', '/support']));
    expect(destinations).not.toContain('#');
  });

  it('stores the server-owned user id so authenticated chat can resolve the principal', () => {
    authServiceMock.login.mockReturnValue(of({
      accessToken: 'access-token',
      userId: 42,
      username: 'customer@example.com',
      roles: ['CUSTOMER'],
      permissions: [],
    }));
    component.loginObj.username = 'customer@example.com';
    component.loginObj.password = 'password';

    component.onSubmit();

    expect(authServiceMock.setSession).toHaveBeenCalledWith('access-token', expect.objectContaining({
      id: 42,
      username: 'customer@example.com',
    }));
  });

  it('keeps receptionist permissions and sends the account to the admin portal', () => {
    const permissions = [
      { function: 'REPORT', actionMask: 1 },
      { function: 'CUSTOMER', actionMask: 7 },
    ];
    authServiceMock.login.mockReturnValue(of({
      accessToken: 'access-token',
      userId: 7,
      username: 'receptionist1',
      roles: ['RECEPTIONIST'],
      permissions,
    }));
    component.loginObj.username = 'receptionist1';
    component.loginObj.password = 'receptionist1';

    component.onSubmit();

    expect(authServiceMock.setSession).toHaveBeenCalledWith('access-token', expect.objectContaining({
      id: 7,
      roles: ['RECEPTIONIST'],
      permissions,
    }));
    expect(router.navigate).toHaveBeenCalledWith(['/admin/dashboard']);
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

});
