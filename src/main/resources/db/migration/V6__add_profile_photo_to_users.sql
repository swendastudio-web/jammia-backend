-- Optional profile photo. Only the file name is stored here;
-- the image itself is saved as a file on the server's disk.
ALTER TABLE users ADD COLUMN profile_photo_filename VARCHAR(100);
