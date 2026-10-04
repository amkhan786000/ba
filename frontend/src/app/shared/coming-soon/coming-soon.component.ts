import { Component, Input } from '@angular/core';

/** Placeholder for menu items whose screens haven't been ported from Flask yet. */
@Component({
  selector: 'app-coming-soon',
  standalone: true,
  template: `
    <div class="row"><div class="col-12"><div class="page-title-box"><h4 class="page-title">{{ pageTitle }}</h4></div></div></div>
    <div class="card"><div class="card-body text-muted">This screen hasn't been migrated from the Python app yet.</div></div>
  `
})
export class ComingSoonComponent {
  @Input() pageTitle = '';
}
