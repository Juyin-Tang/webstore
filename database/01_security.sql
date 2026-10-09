CREATE USER IF NOT EXISTS 'webstore_app'@'localhost'
    IDENTIFIED BY 'Webstore_App_2026!';

GRANT SELECT, INSERT, UPDATE, DELETE ON webstore.* TO 'webstore_app'@'localhost';

SHOW GRANTS FOR 'webstore_app'@'localhost';