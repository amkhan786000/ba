import { Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/services/auth.service';
import { FILE_PREFIX } from './shared/format';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: `<router-outlet></router-outlet>`
})
export class AppComponent implements OnInit, OnDestroy {
  private modalWatcher?: MutationObserver;

  constructor(private auth: AuthService) {}

  /**
   * The pop-ups are plain Bootstrap markup shown with *ngIf (no Bootstrap JavaScript), so nothing adds Bootstrap's
   * "modal-open" class to <body>. Without it the page behind a pop-up scrolls instead of the pop-up, and a tall
   * pop-up can't be scrolled at all. This keeps the class in step with whether a pop-up is on screen.
   */
  ngOnInit(): void {
    const sync = () => document.body.classList.toggle('modal-open', !!document.querySelector('.modal.show'));
    this.modalWatcher = new MutationObserver(sync);
    this.modalWatcher.observe(document.body, { childList: true, subtree: true });
    sync();
  }

  ngOnDestroy(): void { this.modalWatcher?.disconnect(); }

  /**
   * Uploaded files need sign-in, but a plain <a href> sends no token. Any click on a link to /api/files/...
   * fetches the file with the user's token and opens it in a new tab instead.
   */
  @HostListener('document:click', ['$event'])
  openFile(event: MouseEvent): void {
    const link = (event.target as HTMLElement | null)?.closest?.('a[href]') as HTMLAnchorElement | null;
    if (!link || event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey) return;
    const url = new URL(link.href, window.location.origin);
    if (url.origin !== window.location.origin || !url.pathname.startsWith(FILE_PREFIX)) return;
    event.preventDefault();

    const tab = window.open('', '_blank'); // opened now, while the click still counts as the user's
    fetch(url.pathname, { headers: this.auth.token ? { Authorization: 'Bearer ' + this.auth.token } : {} })
      .then((res) => {
        if (!res.ok) throw new Error(res.status === 404 ? 'This file was not found, or you may not open it.' : 'Could not open the file.');
        return res.blob();
      })
      .then((blob) => {
        const objectUrl = URL.createObjectURL(blob);
        if (tab) tab.location.href = objectUrl; else window.location.href = objectUrl;
        setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
      })
      .catch((err: Error) => {
        tab?.close();
        alert(err.message);
      });
  }
}
