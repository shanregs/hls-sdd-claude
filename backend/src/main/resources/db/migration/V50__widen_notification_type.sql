-- Spec 032: MANAGER_REGULARIZATION_REQUESTED is 32 characters and the column held 30.
ALTER TABLE notification ALTER COLUMN type TYPE VARCHAR(60);
