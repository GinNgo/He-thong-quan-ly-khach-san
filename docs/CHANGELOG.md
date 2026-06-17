# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **DataSeeder**: Added `DataSeeder.java` to automatically assign `SUPER_ADMIN` role to the `admin` account and create a default `support` account on application startup to prevent login lockouts.

### Changed
- **Dashboard UI**: Completely overhauled the Admin Dashboard UI in the frontend:
  - Migrated a Tailwind CSS mockup to standard Bootstrap 5.
  - Implemented a sticky header with user profile dropdown, notifications, and search bar.
  - Implemented a collapsible, sticky dark-themed sidebar with interactive navigation.
  - Added statistics cards and placeholder charts for Engineering Work Orders.
  - Added a detailed work order table with priority and status badges.
- **Login UI**: Improved the login page interface by adjusting flex ratios for perfect balance and updating the application logo and favicon.

### Removed
- **AiAssistant**: Removed the floating AI Assistant button from the root layout (`app.html`) and moved it exclusively into the protected `admin-layout.html`.
