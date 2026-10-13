/**
 * The scholarship application form lives on the website (Rahbar Diploma & Paramedical Sponsorship > Apply); it is
 * open while an Application Period is active here. Tracking an application and uploading documents stay in Rahbar.
 */
export const WEBSITE_APPLY_URL = 'https://www.biharanjuman.com/projects/rahbar-diploma-paramedical-sponsorship/apply';

/** Old links to /apply go to the website's form. */
export function toWebsiteApply(): boolean {
  if (typeof window !== 'undefined') window.location.replace(WEBSITE_APPLY_URL);
  return false;
}
