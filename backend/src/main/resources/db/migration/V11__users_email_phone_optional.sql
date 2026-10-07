-- Email and phone are optional on users (many sponsors have neither). Both stay unique when given.
ALTER TABLE users MODIFY email VARCHAR(255) NULL;
ALTER TABLE users MODIFY phone VARCHAR(255) NULL;
