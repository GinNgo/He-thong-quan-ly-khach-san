# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Multi-Property & Subscriptions**: Kh·ªüi t·∫°o c·∫•u tr√∫c d·ªØ li·ªáu v√† t√†i li·ªáu thi·∫øt k·∫ø (ERD, UML, API_SPEC) cho ph√¢n h·ªá ƒêa c∆° s·ªü (Locations, Properties, UserProperties) v√† H·ªá th·ªëng G√≥i d·ªãch v·ª• (SubscriptionPlans, AccountSubscriptions).
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

## [Unreleased]
### Added
- TÌnh nang T? d?ng Nh?p D? Li?u (Automated Property Import) t? ngu?n tr?c tuy?n (Nominatim/OSM).
- C·c th?c th? m?i: PropertyImportBatch, PropertyImportItem, PropertyClaimRequest, PropertyExternalPhoto.
- Co ch? Deduplication 5 c?p ki?m tra tr˘ng l?p co s? (M„ ngo‡i, TÍn, S–T, Website, T?a d?).
- Lu?ng duy?t quy?n s? h?u co s? (Claim Ownership) cho c·c co s? du?c import t? d?ng (tr?ng th·i IMPORTED_PENDING_REVIEW).
- UI/UX Qu?n l˝ Import v‡ Claim cho Admin.
- Component yÍu c?u Claim cho phÌa Kh·ch H‡ng (User/Client) trÍn trang chi ti?t kh·ch s?n.
