import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AfterViewChecked, ChangeDetectorRef, Component, DestroyRef, ElementRef, OnInit, ViewChild, inject } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../../core/services/auth';
import { ClientApiService, Hotel, RoomType } from '../../../core/services/client-api.service';
import { ImageFallbackService } from '../../../core/services/image-fallback.service';
import {
  PROPERTY_CLAIM_VERIFICATION_METHODS,
  PropertyClaimService,
  PropertyClaimVerificationMethod,
  propertyClaimRequestErrorMessage
} from '../../../core/services/property-claim.service';

@Component({selector:'app-hotel-detail',standalone:true,imports:[CommonModule,FormsModule],templateUrl:'./hotel-detail.component.html',styleUrls:['./hotel-detail.component.css']})
export class HotelDetailComponent implements OnInit, AfterViewChecked {
  @ViewChild('claimDialog') private claimDialog?:ElementRef<HTMLElement>;
  @ViewChild('claimCta') private claimCta?:ElementRef<HTMLElement>;
  private readonly route=inject(ActivatedRoute); private readonly router=inject(Router); private readonly authService=inject(AuthService); private readonly api=inject(ClientApiService); private readonly propertyClaims=inject(PropertyClaimService); private readonly changeDetector=inject(ChangeDetectorRef); private readonly destroyRef=inject(DestroyRef); readonly fallback=inject(ImageFallbackService);
  private claimFocusPending=false; private claimInvoker:HTMLElement|null=null;
  hotel:Hotel|null=null; roomTypes:RoomType[]=[]; isLoading=true; pageError=''; roomError=''; selectedRoomType:RoomType|null=null; selectedQuantity=0; hotelId=0;
  bookingQueryParams:{checkIn?:string;checkOut?:string;adultCount:number;childCount:number;roomCount:number}={adultCount:2,childCount:0,roomCount:1};
  showClaimModal=false;
  claimForm:{verificationMethod:PropertyClaimVerificationMethod;verificationData:string;note:string}={verificationMethod:'BUSINESS_LICENSE',verificationData:'',note:''};
  claimSubmitting=false; claimSubmitted=false; claimRequestError=''; claimRequestSuccess=''; claimSubmittedPropertyId:number|null=null;
  ngOnInit():void{this.route.queryParams.subscribe(p=>{this.bookingQueryParams={checkIn:p['checkInDate']||p['checkIn'],checkOut:p['checkOutDate']||p['checkOut'],adultCount:Number(p['adultCount'])||Number(p['guests'])||2,childCount:Number(p['childCount'])||0,roomCount:Number(p['roomCount'])||1};if(this.hotel?.id)this.loadRoomTypes(this.hotel.id);});this.route.paramMap.subscribe(p=>{const id=Number(p.get('id'));if(!Number.isInteger(id)||id<=0){this.showPageError('Đường dẫn chỗ nghỉ không hợp lệ. Vui lòng quay lại trang tìm kiếm để chọn một cơ sở khác.');return;}this.hotelId=id;this.loadHotelData(id);});}
  ngAfterViewChecked():void{
    if(!this.claimFocusPending||!this.claimDialog)return;
    this.claimFocusPending=false;
    this.claimDialog.nativeElement.querySelector<HTMLElement>('[data-claim-initial-focus]')?.focus();
  }
  get guests():number{return this.bookingQueryParams.adultCount+this.bookingQueryParams.childCount;} get nights():number{if(!this.bookingQueryParams.checkIn||!this.bookingQueryParams.checkOut)return 1;return Math.max(1,Math.round((new Date(this.bookingQueryParams.checkOut).getTime()-new Date(this.bookingQueryParams.checkIn).getTime())/86400000));}
  get roomSubtotal():number{return (this.selectedRoomType?.basePrice||0)*this.nights*this.selectedQuantity;} get roomTotal():number{return (this.selectedRoomType?.totalPrice||0)*this.selectedQuantity||this.roomSubtotal;} get roomTax():number{return Math.max(0,this.roomTotal-this.roomSubtotal);} get canContinue():boolean{return !!this.selectedRoomType&&this.selectedQuantity>0&&this.selectedQuantity<=this.maxQuantity(this.selectedRoomType)&&this.capacityValid;}
  get capacityValid():boolean{if(!this.selectedRoomType||!this.selectedQuantity)return false;const maxA=this.selectedRoomType.maxAdults??this.selectedRoomType.maxGuests??this.selectedRoomType.maxGuest;const maxC=this.selectedRoomType.maxChildren??this.selectedRoomType.maxGuests??this.selectedRoomType.maxGuest;const maxG=this.selectedRoomType.maxGuests??this.selectedRoomType.maxGuest;return this.bookingQueryParams.adultCount<=maxA*this.selectedQuantity&&this.bookingQueryParams.childCount<=maxC*this.selectedQuantity&&this.guests<=maxG*this.selectedQuantity;}
  loadHotelData(id:number):void{this.isLoading=true;this.pageError='';this.roomError='';this.hotel=null;this.api.getHotelById(id).subscribe({next:h=>{if(!h){this.showPageError('Không tìm thấy chỗ nghỉ này. Cơ sở có thể đã ngừng hiển thị hoặc đường dẫn không còn hiệu lực.');return;}this.hotel=h;this.changeDetector.detectChanges();this.loadRoomTypes(id);},error:error=>{this.showPageError(error?.status===404?'Không tìm thấy chỗ nghỉ này. Cơ sở có thể đã ngừng hiển thị hoặc đường dẫn không còn hiệu lực.':'Không thể tải thông tin chỗ nghỉ lúc này. Vui lòng thử lại sau.');}});}
  loadRoomTypes(id:number):void{this.roomError='';this.api.getRoomTypesByHotel(id,this.bookingQueryParams.checkIn,this.bookingQueryParams.checkOut,this.guests).subscribe({next:r=>{this.roomTypes=r;this.selectedRoomType=null;this.selectedQuantity=0;this.isLoading=false;this.changeDetector.detectChanges();setTimeout(()=>{if(this.route.snapshot.fragment==='rooms')this.scrollToRooms();});},error:error=>{if(error?.status===404){this.showPageError('Không tìm thấy chỗ nghỉ này. Cơ sở có thể đã ngừng hiển thị hoặc đường dẫn không còn hiệu lực.' );return;}this.roomTypes=[];this.roomError='Không thể tải tình trạng phòng. Vui lòng thử lại.';this.isLoading=false;this.changeDetector.detectChanges();}});}
  selectQuantity(room:RoomType,value:any):void{const quantity=Math.max(0,Math.min(this.maxQuantity(room),Number(value)||0));if(quantity===0){if(this.selectedRoomType?.id===room.id){this.selectedRoomType=null;this.selectedQuantity=0;}return;}this.selectedRoomType=room;this.selectedQuantity=quantity;}
  maxQuantity(room:RoomType):number{return Math.max(0,Math.min(room.availableRooms??0,this.bookingQueryParams.roomCount));} quantities(room:RoomType):number[]{return Array.from({length:this.maxQuantity(room)+1},(_,i)=>i);}
  continueBooking():void{if(!this.canContinue||!this.selectedRoomType||!this.hotel)return;this.router.navigate(['/booking',this.selectedRoomType.id],{queryParams:{checkIn:this.bookingQueryParams.checkIn,checkOut:this.bookingQueryParams.checkOut,adultCount:this.bookingQueryParams.adultCount,childCount:this.bookingQueryParams.childCount,roomCount:this.bookingQueryParams.roomCount,quantity:this.selectedQuantity,hotelId:this.hotel.id,roomTypeName:this.selectedRoomType.nameVi,nightlyPrice:this.selectedRoomType.basePrice,estimatedTotal:this.roomTotal}});}
  roomImage(room:RoomType):string{return room.imageUrls?.[0]||this.fallback.room(room.code);} handleRoomImageError(e:Event,room:RoomType):void{this.fallback.replace(e,this.fallback.room(room.code));} handleHotelImageError(e:Event):void{this.fallback.replace(e,this.fallback.property(this.hotel?.propertyType));}
  bedLabel(value?:string):string{return ({SINGLE:'1 giường đơn',DOUBLE:'1 giường đôi',TWIN:'2 giường đơn',MULTIPLE:'Nhiều giường'} as Record<string,string>)[value||'']||value||'Theo cấu hình phòng';}
  formatVnd(value:number):string{return `${new Intl.NumberFormat('vi-VN',{maximumFractionDigits:0}).format(value||0)} ₫`;}
  scrollToRooms():void{document.getElementById('rooms')?.scrollIntoView({behavior:'smooth'});}
  openClaimModal(event?:Event):void{
    if(!this.hotel||this.claimSubmittedPropertyId===this.hotel.id)return;
    if(!this.authService.isLoggedIn()){
      void this.router.navigate(['/login'],{queryParams:{returnUrl:this.claimReturnUrl(this.hotel.id)}});
      return;
    }
    this.claimInvoker=event?.currentTarget instanceof HTMLElement?event.currentTarget:this.claimCta?.nativeElement??null;
    this.claimForm={verificationMethod:'BUSINESS_LICENSE',verificationData:'',note:''};
    this.claimSubmitting=false;this.claimSubmitted=false;this.claimRequestError='';this.claimRequestSuccess='';this.claimFocusPending=true;this.showClaimModal=true;
  }
  closeClaimModal():void{
    if(this.claimSubmitting)return;
    this.showClaimModal=false;this.claimFocusPending=false;
    const invoker=this.claimInvoker;this.claimInvoker=null;invoker?.focus();
  }
  handleClaimDialogKeydown(event:KeyboardEvent):void{
    if(event.key==='Escape'){
      if(!this.claimSubmitting){event.preventDefault();this.closeClaimModal();}
      return;
    }
    if(event.key!=='Tab'||!this.claimDialog)return;
    const focusable=this.claimFocusableElements();
    if(focusable.length===0){event.preventDefault();return;}
    const first=focusable[0];const last=focusable[focusable.length-1];const active=document.activeElement;
    if(event.shiftKey&&(active===first||!this.claimDialog.nativeElement.contains(active))){event.preventDefault();last.focus();}
    else if(!event.shiftKey&&(active===last||!this.claimDialog.nativeElement.contains(active))){event.preventDefault();first.focus();}
  }
  submitClaim(form:NgForm):void{
    if(!this.hotel||this.claimSubmitting||this.claimSubmitted)return;
    form.control.markAllAsTouched();
    const verificationMethod=this.claimForm.verificationMethod;
    const verificationData=this.claimForm.verificationData.trim();
    const note=this.claimForm.note.trim();
    const validMethod=PROPERTY_CLAIM_VERIFICATION_METHODS.includes(verificationMethod);
    if(!validMethod||!verificationData||verificationData.length>1000||note.length>500){
      this.claimRequestError='Vui lòng chọn phương thức hợp lệ, nhập thông tin xác minh tối đa 1000 ký tự và ghi chú tối đa 500 ký tự.';
      this.claimRequestSuccess='';
      return;
    }

    const propertyId=this.hotel.id;
    this.claimSubmitting=true;this.claimRequestError='';this.claimRequestSuccess='';
    this.propertyClaims.submit(propertyId,{
      verificationMethod,
      verificationData,
      ...(note?{note}:{})
    }).pipe(
      finalize(()=>{this.claimSubmitting=false;this.changeDetector.detectChanges();}),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next:()=>{
        this.claimSubmitted=true;
        this.claimSubmittedPropertyId=propertyId;
        this.claimRequestSuccess='Yêu cầu đã được gửi. Đội ngũ LuxeStay sẽ xem xét và thông báo kết quả.';
      },
      error:(error:HttpErrorResponse)=>{
        if(error.status===401){this.redirectExpiredClaimSession(propertyId);return;}
        this.claimRequestError=propertyClaimRequestErrorMessage(error);
      }
    });
  }
  retry():void{if(this.hotelId>0)this.loadHotelData(this.hotelId);}
  browseProperties():void{this.router.navigate(['/search']);}
  private claimFocusableElements():HTMLElement[]{
    if(!this.claimDialog)return[];
    return Array.from(this.claimDialog.nativeElement.querySelectorAll<HTMLElement>('button:not([disabled]),a[href],input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])'));
  }
  private claimReturnUrl(propertyId:number):string{return `/hotel/${propertyId}`;}
  private redirectExpiredClaimSession(propertyId:number):void{
    this.claimRequestError='Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để gửi yêu cầu.';
    this.claimForm={verificationMethod:'BUSINESS_LICENSE',verificationData:'',note:''};
    this.showClaimModal=false;this.claimFocusPending=false;
    void this.router.navigate(['/login'],{queryParams:{returnUrl:this.claimReturnUrl(propertyId)}});
  }
  private showPageError(message:string):void{this.hotel=null;this.roomTypes=[];this.selectedRoomType=null;this.selectedQuantity=0;this.pageError=message;this.isLoading=false;this.changeDetector.detectChanges();}
}
