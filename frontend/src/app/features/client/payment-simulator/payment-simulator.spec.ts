import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PaymentSimulatorComponent } from './payment-simulator';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';

describe('PaymentSimulator', () => {
  let component: PaymentSimulatorComponent;
  let fixture: ComponentFixture<PaymentSimulatorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaymentSimulatorComponent],
      providers: [provideRouter([]), provideHttpClient()],
    }).compileComponents();

    fixture = TestBed.createComponent(PaymentSimulatorComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
