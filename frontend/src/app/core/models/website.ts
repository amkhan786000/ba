/**
 * The scholarship application form and "Track your application" (with the document upload) live on the website
 * (Rahbar Diploma & Paramedical Sponsorship); the form is open while an Application Period is active here. Staff look
 * applications up in Applications > Track Application.
 */
export const WEBSITE_APPLY_URL = 'https://www.biharanjuman.com/projects/rahbar-diploma-paramedical-sponsorship/apply';

/** Old links to /apply go to the website's form. */
export function toWebsiteApply(): boolean {
  if (typeof window !== 'undefined') window.location.replace(WEBSITE_APPLY_URL);
  return false;
}

export const WEBSITE_TRACK_URL = 'https://www.biharanjuman.com/projects/rahbar-diploma-paramedical-sponsorship/track';

/** Old links to /track-application (e.g. in emails) go to the website's tracker, keeping ?id=. */
export function toWebsiteTrack(route: { queryParamMap: { get(k: string): string | null } }): boolean {
  const id = route.queryParamMap.get('id');
  if (typeof window !== 'undefined') window.location.replace(WEBSITE_TRACK_URL + (id ? '?id=' + encodeURIComponent(id) : ''));
  return false;
}
