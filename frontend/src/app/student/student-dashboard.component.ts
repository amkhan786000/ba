import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../core/services/api.service';

@Component({
  selector: 'app-student-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div *ngIf="data">
      <p>Welcome, {{ data.student?.name }}</p>
      <p *ngIf="data.sponsor?.name">Sponsor: {{ data.sponsor.name }}</p>
    </div>
  `
})
export class StudentDashboardComponent implements OnInit {
  data: any;
  constructor(private api: ApiService) {}
  ngOnInit(): void { this.api.get('/student/dashboard').subscribe((d) => (this.data = d)); }
}
