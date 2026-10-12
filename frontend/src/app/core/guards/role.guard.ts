import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { rolesOf } from '../models/user.model';

/** A portal: the user needs one of the roles, as their main role or an additional one. */
export function roleGuard(allowedRoles: number[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const user = auth.currentUser();
    if (!user || !rolesOf(user).some((r) => allowedRoles.includes(r))) {
      router.navigate(['/login']);
      return false;
    }
    // Accounts still on the password an admin gave them must choose their own first.
    if (user.mustChangePassword) {
      router.navigate(['/change-password']);
      return false;
    }
    return true;
  };
}
