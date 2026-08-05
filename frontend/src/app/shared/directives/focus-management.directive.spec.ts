import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { FocusOnErrorDirective, RouteFocusTargetDirective } from './focus-management.directive';

@Component({
  standalone: true,
  imports: [RouteFocusTargetDirective],
  template: `<main appRouteFocusTarget>Route content</main><button type="button">Outside</button>`,
})
class RouteFocusHostComponent {}

@Component({
  standalone: true,
  imports: [FocusOnErrorDirective],
  template: `<section [appFocusOnError]="true" role="alert">Request failed</section>`,
})
class ErrorFocusHostComponent {}

describe('focus management directives', () => {
  const routerEvents = new Subject<unknown>();

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RouteFocusHostComponent, ErrorFocusHostComponent],
      providers: [{ provide: Router, useValue: { events: routerEvents.asObservable() } }],
    }).compileComponents();
  });

  it('focuses the main landmark after route navigation', async () => {
    const fixture = TestBed.createComponent(RouteFocusHostComponent);
    fixture.detectChanges();
    await Promise.resolve();
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    button.focus();

    routerEvents.next(new NavigationEnd(1, '/', '/'));
    routerEvents.next(new NavigationEnd(2, '/from', '/to'));
    await Promise.resolve();

    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('main'));
  });

  it('moves focus to a rendered error alert', async () => {
    const fixture = TestBed.createComponent(ErrorFocusHostComponent);
    fixture.detectChanges();
    await Promise.resolve();

    const alert = fixture.nativeElement.querySelector('[role="alert"]') as HTMLElement;
    expect(document.activeElement).toBe(alert);
    expect(alert.tabIndex).toBe(-1);
  });
});
