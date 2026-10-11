import { Component } from '@angular/core';
import { WebsiteRecordsComponent } from './website/website-records.component';

/**
 * Admin > Volunteer Form Options: the ways to help on the website's "Volunteer with us" form. Who may see and change
 * them is set in Roles & Permissions ("Volunteer form options").
 */
@Component({
  selector: 'app-admin-volunteer-options',
  standalone: true,
  imports: [WebsiteRecordsComponent],
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">Volunteer Form Options</h4></div></div></div>
    <app-website-records resource="volunteer-options" [embedded]="true"></app-website-records>
  `
})
export class AdminVolunteerOptionsComponent {}
