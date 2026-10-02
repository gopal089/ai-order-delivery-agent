-- Establish the first Flyway-managed database version.
-- Business tables are intentionally deferred until their data model and
-- tenant-ownership rules are defined and approved.
SELECT 1;
