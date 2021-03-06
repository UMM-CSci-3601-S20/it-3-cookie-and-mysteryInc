import { Note, NoteStatus, SaveNote } from '../notes/note';
import { OnInit, Component, OnDestroy, Input, NgModule } from '@angular/core';
import { DoorBoardService } from './doorBoard.service';
import { DoorBoard } from './doorBoard';
import { Subscription, Observable } from 'rxjs';
import { NoteService } from '../notes/note.service';
import { ActivatedRoute } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { AuthService } from '../auth/auth.service';
import { map } from 'rxjs/operators';
import { Router } from '@angular/router';

import { MatRadioChange } from '@angular/material/radio';
import {TextFieldModule} from '@angular/cdk/text-field';
import { stringToKeyValue } from '@angular/flex-layout/extended/typings/style/style-transforms';
import { async } from '@angular/core/testing';
// import { threadId } from 'worker_threads';



@Component({
  selector: 'app-doorBoard-page-component',
  templateUrl: 'doorBoard-page.component.html',
  styleUrls: ['doorBoard-page.component.scss'],
  providers: []
})

export class DoorBoardPageComponent implements OnInit, OnDestroy {
  thisUrl = location.href;

  // tslint:disable-next-line: no-input-rename
  @Input('cdkTextareaAutosize')
  @Input() note: SaveNote;
  validUser = true;
  finishInit = false;
  enabled: boolean;
  confirmDropDown = true;
  confirmQRDropDown = true;
  constructor(private doorBoardService: DoorBoardService, private noteService: NoteService,
              private route: ActivatedRoute, private sanitizer: DomSanitizer, private auth: AuthService) { }

  public notes: Note[];
  public serverFilteredNotes: Note[];
  public filteredNotes: Note[];
  public isExpiredNotes: Note[];
  public pinnedNotes: Note[];
  public GcalURL: SafeResourceUrl;
  doorBoard: DoorBoard;
  id: string;
  public href: string;
  getNotesSub: Subscription;
  getDoorBoardSub: Subscription;

  public noteStatus: NoteStatus = 'active';
  public noteAddDate: Date;
  public noteExpireDate: Date;
  public favorite: boolean;
  public isPinned: boolean;
  public noteBody: string;
  public getCurrentSub: Subscription;

  qrcodename: string;
  title = 'generate-qrcode';
  elementType: 'url' | 'canvas' | 'img' = 'url';
  url: string;
  value: string;
  display: boolean;
  //href: string;

  generateQRCode() {
    this.value = this.thisUrl + '/viewer';
    this.display = true;
  }

  downloadImage() {
    this.href = document.getElementsByTagName('img')[0].src;
  }

  public getNotesFromServer(): void {
    this.unsub();
    this.getNotesSub = this.noteService.getNotesByDoorBoard(
      this.id, {
        status: this.noteStatus,
        body: this.noteBody
      }).subscribe(returnedNotes => {
        this.serverFilteredNotes = returnedNotes;
        this.updateFilter();
      }, err => {
        console.log(err);
      });
  }

  public updateFilter(): void {
    this.filteredNotes = this.noteService.filterNotes(
      this.serverFilteredNotes,
      {
        isPinned: false,
        isExpired: true
      });
    this.isExpiredNotes = this.noteService.filterNotes(
      this.serverFilteredNotes,
      {
        isExpired: false,
        isPinned: false
      });
    this.pinnedNotes = this.noteService.filterNotes(
      this.serverFilteredNotes,
      {
        isPinned: true
      });
  }

  public createGmailConnection(doorBoardEmail: string): void {
    let gmailUrl = doorBoardEmail.replace(/@/g, '%40'); // Convert doorBoard e-mail to acceptable format for connection to gCalendar
    console.log('BEING CALLED');
    gmailUrl = 'https://calendar.google.com/calendar/embed?mode=WEEK&showPrint=0&src=' + gmailUrl; // Connection string
    // this.GcalURL = gmailUrl; // Set the global connection string
    this.GcalURL = this.sanitizer.bypassSecurityTrustResourceUrl(gmailUrl);
  }
  // public returnSafeLink(): SafeResourceUrl{
  //   return this.sanitizer.bypassSecurityTrustResourceUrl(this.GcalURL);  // Return a "safe" link to gCalendar
  // }

  public getName(): string {
    return this.doorBoard.name;
  }
  public getBuilding(): string {
    return this.doorBoard.building;
  }
  public getOfficeNumber(): string {
    return this.doorBoard.officeNumber;
  }
  public getEmail(): string {
    return this.doorBoard.email;
  }

  public getGcal(): string {
    let gmailUrl = this.getEmail().replace(/@/g, '%40');
    gmailUrl = 'https://calendar.google.com/calendar/embed?mode=WEEK&showPrint=0&src=' + gmailUrl;
    return gmailUrl;
  }

  public getSub(): string {
    if (this.doorBoard) {
    return this.doorBoard.sub;
    } else {
      return null;
    }
  }



  public getLoginSub(): Observable<string> {
    console.log('called get LOGINSUB');
    const currentSub = this.auth.userProfile$.pipe(
      map(profile => {
        if (profile) {
          console.log('Login = ' + JSON.stringify(profile.sub).replace(/['"]+/g, ''));
          return JSON.stringify(profile.sub).replace(/['"]+/g, '');
        } else {
          console.log('This was null');
          return null;
        }
      })
    );
    return currentSub;
  }


  public compareSubs(doorboardSubString: string): Observable<boolean> {
    console.log('doorboardSubString = ' + doorboardSubString);
    return  this.getLoginSub().pipe(map((val =>  (val !== null && doorboardSubString !== null  && val === doorboardSubString))));
  }



  radioChange($event: MatRadioChange) {
    console.log($event.source.name, $event.value);

    if ($event.source.name === 'radioGroup') {
      // console.log('Pressed Radio Button!');
      this.getNotesFromServer();
    }
  }

  public toggleDropDown(): void {
    if (this.confirmDropDown === true) {
      this.confirmDropDown = false;
    } else {
      this.confirmDropDown = true;
    }
  }

  ngOnInit(): void {
    // Subscribe doorBoard's notes
    let doorboardSub: string;
    this.unsub();
    this.route.paramMap.subscribe((pmap) => {
      this.id = pmap.get('id');

      if (this.getNotesSub) {
        this.getNotesSub.unsubscribe();
      }
      this.getNotesSub = this.noteService.getNotesByDoorBoard(this.id).subscribe(  notes => this.notes = notes);
      this.getNotesFromServer();
      if (this.getDoorBoardSub) {
        this.getDoorBoardSub.unsubscribe();
      }
      this.getDoorBoardSub = this.doorBoardService.getDoorBoardById(this.id).subscribe(  async (doorBoard: DoorBoard) => {
      this.doorBoard = doorBoard;
      doorboardSub = this.doorBoard.sub;
      console.log(this.doorBoard.email);
      this.createGmailConnection(this.doorBoard.email);
//
//       let attempt = 0;
//       this.compareSubs(this.doorBoard.sub).subscribe( (authVal: boolean) => {
//         attempt++;
//         console.log('got to 206 + attempt:' + attempt);
//         let result: boolean;
//         console.log(authVal);
//         result = authVal;
//         if (result) {
//      console.log('I think you are on to something');
//      this.validUser = true;
//  } else  {
//    console.log('You are not logged in to this page');
//    this.validUser = false;
//   }
//       });
  });


    });
  }

  ngOnDestroy(): void {
    if (this.getNotesSub) {
      this.getNotesSub.unsubscribe();
    }
    if (this.getDoorBoardSub) {
      this.getDoorBoardSub.unsubscribe();
    }

  }

  unsub(): void {
    if (this.getNotesSub) {
      this.getNotesSub.unsubscribe();
    }

    if (this.getDoorBoardSub) {
      this.getDoorBoardSub.unsubscribe();
    }
  }

}
