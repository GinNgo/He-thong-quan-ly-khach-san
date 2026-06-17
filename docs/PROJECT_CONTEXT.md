# PROJECT_CONTEXT.md

## Project Name

Hotel Management and Online Reservation System

## Description

A bilingual Vietnamese-English Hotel Management and Reservation System built for academic and real-world demonstration purposes.

The system allows customers to search rooms, make reservations, receive electronic invoices, and interact with an AI assistant.

The system includes:

* Customer Portal
* Receptionist Portal
* Admin Dashboard
* AI Assistant
* Electronic Invoice Module

---

## Tech Stack

Frontend:

* Angular 20+
* TypeScript
* PrimeNG
* Bootstrap 5
* ngx-translate

Backend:

* Java 21
* Spring Boot 3
* Spring Security
* JWT
* Spring Data JPA

Database:

* SQL Server

AI:

* OpenAI API or Gemini API

Document:

* OpenPDF / iText

---

## Roles

### Customer

* Register
* Login
* Search Rooms
* Reserve Rooms
* Payment
* View Reservation History
* Download Invoices
* AI Chat

### Receptionist

* Check-in
* Check-out
* Reservation Management
* Customer Management

### Admin

* User Management
* Room Management
* Room Type Management
* Service Management
* Invoice Management
* Revenue Dashboard
* AI Analytics

---

## Core Modules

Authentication

Room Management

Reservation Management

Customer Management

Invoice Management

Revenue Statistics

AI Assistant

Bilingual Support

Responsive UI

---

## Non Functional Requirements

Responsive

Mobile First

Dark Mode Ready

Multi-language

Clean Architecture

RESTful APIs

Role-based Authorization

Unit Test Support

Swagger Documentation

---

# Authorization & Subscription Requirements

## Authentication

* JWT Authentication
* Refresh Token
* Email Verification
* Forgot Password
* Change Password

---

## Authorization Model

Use Role-Based Access Control (RBAC).

A user can have one or multiple roles.

Tables:

users

roles

permissions

user_roles

role_permissions

---

## Roles

### Super Admin

Full system access.

Permissions:

* Manage users
* Manage roles
* Manage permissions
* Manage hotels
* View all statistics
* Configure AI settings
* Configure subscription plans

---

### Hotel Admin

Manage a specific hotel.

Permissions:

* Manage rooms
* Manage room types
* Manage services
* Manage employees
* Manage reservations
* Manage invoices
* View hotel statistics

---

### Receptionist

Permissions:

* Check-in
* Check-out
* Reservation management
* Customer support

---

### Customer

Permissions:

* Register
* Login
* Search rooms
* Make reservations
* View invoices
* AI assistant access

---

## Permission-Based UI

Frontend must render menus dynamically.

Example:

IF user has permission ROOM_VIEW

Show:

* Room Management Menu

ELSE

Hide menu.

Never rely only on frontend checks.

Backend must validate permissions for every API.

---

## Subscription Plans

### Free

* Room search
* Reservation
* Booking history

No AI features.

---

### Premium

* AI Hotel Assistant
* Personalized room recommendations
* Priority booking
* Extended booking history

---

### Business

* Electronic invoices
* Advanced reporting
* Revenue analytics
* Occupancy forecasting
* AI business insights

---

## Feature Gate System

Each feature can require:

* Permission
* Subscription Plan

Example:

AI_CHAT

Required:

Premium OR Business

---

AI_REVENUE_ANALYTICS

Required:

Business

---

ELECTRONIC_INVOICE

Required:

Business

---

## Dynamic Menu System

Menu items must be generated based on:

User Role
+
Permissions
+
Subscription Plan

Example:

Customer Free

Dashboard
Reservations

Customer Premium

Dashboard
Reservations
AI Assistant

Hotel Admin Business

Dashboard
Rooms
Reservations
Invoices
Reports
AI Analytics
Users
