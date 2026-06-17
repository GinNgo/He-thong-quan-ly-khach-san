# DEVELOPMENT_RULES.md

## General Rules

Always update documentation before implementation.

Documentation priority:

1. ERD.md
2. UML.md
3. API_SPEC.md
4. THESIS.md

Never create code without updating design documents.

---

## Backend Rules

Use:

* Java 21
* Spring Boot 3
* Spring Security
* JWT
* JPA

Architecture:

Controller
Service
Repository
Entity
DTO
Mapper

No business logic inside Controller.

---

## Frontend Rules

Use:

* Angular 20+
* PrimeNG
* Bootstrap

Architecture:

Core
Shared
Features

Use standalone components.

Use lazy loading.

---

## Database Rules

Use SQL Server.

Every table must contain:

created_at

updated_at

created_by

updated_by

---

## Documentation Rules

Whenever a feature is added:

Update:

* ERD
* UML
* API Spec
* Thesis

Generate Mermaid diagrams whenever possible.

---

## Code Quality

Use SOLID principles.

Use clean architecture.

Generate Swagger documentation.

Generate Unit Tests.
