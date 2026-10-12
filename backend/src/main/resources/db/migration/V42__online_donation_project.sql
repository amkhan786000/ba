-- Website donations: the project the donor chose (null: a general donation). Booked with it in Income & Expenses.
ALTER TABLE online_payments ADD COLUMN donation_project_id BIGINT NULL;
