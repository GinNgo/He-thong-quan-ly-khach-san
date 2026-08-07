# Data Model: Home Landing & Footer Polish

This feature does not add persisted entities or backend schema changes. It consumes the following existing view data:

## Home discovery content

- `LocationSuggestion`: `id`, `name`, `displayName`, `propertyCount`, optional `imageUrl`.
- `Hotel`: `id`, `name`, location fields, property type, image URLs, review score/count, optional availability and pricing.
- `HomeSearchState`: selected location, date range, stay type, adult/child/room counts and derived query parameters.

## Footer navigation item

- Label: visible action text.
- Destination: existing Angular route plus optional Home fragment, or explicit `mailto:`/`tel:` action.
- Group: explore, partner, support or legal/contact.
- Accessibility: keyboard reachable, visible focus, and descriptive accessible name.
