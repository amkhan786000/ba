import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { Section, hasAdminArea, landingPath } from '../models/user.model';

/** The admin area: users with a staff role (main or additional); only-portal users (coordinator, convenor, sponsor, student) go to their portal. */
export const adminAreaGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const user = auth.currentUser();
  if (!user) return router.createUrlTree(['/login']);
  if (user.mustChangePassword) return router.createUrlTree(['/change-password']);
  if (!hasAdminArea(user)) return router.createUrlTree([landingPath(user)]);
  return true;
};

/** One admin screen: needs VIEW on its section (route data: { permission: 'USERS' }). */
export const permissionGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const section = route.data?.['permission'] as Section | undefined;
  if (!section || auth.can(section)) return true;
  return router.createUrlTree([landingPath(auth.currentUser())]);
};

/** "/admin" itself: sends the user to the first admin screen they may open. */
export const adminHomeGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return inject(Router).createUrlTree([landingPath(auth.currentUser())]);
};
