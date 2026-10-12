-- Administration > Application Settings (e.g. how dates are shown: DD/MM/YYYY by default). The Application
-- Administrator (2) manages it; the Super Admin always may.
UPDATE roles SET permissions = CONCAT_WS(',', NULLIF(permissions, ''), 'APP_SETTINGS:VIEW', 'APP_SETTINGS:EDIT')
 WHERE role_id = 2 AND FIND_IN_SET('APP_SETTINGS:EDIT', COALESCE(permissions, '')) = 0;
