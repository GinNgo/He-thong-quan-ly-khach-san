# Research: Home Landing & Footer Polish

## Decision 1: Keep Home discovery state inside existing components

- **Decision**: Continue using `HomeSearchStateService` for search submission and selection, `ClientApiService` for destinations/properties, and `ImageFallbackService` for image recovery.
- **Rationale**: This preserves real API behavior and existing query parameters while limiting the feature to layout and interaction quality.
- **Alternatives considered**: Introducing a new landing-page facade or static fixture content was rejected because it would duplicate state and violate the no-mock integration principle.

## Decision 2: Make empty states explicit instead of reserving blank space

- **Decision**: Destination and featured-property sections always retain a heading and show loading skeletons or a compact empty state when no data is available.
- **Rationale**: The current API can legitimately return no rows; an explicit state prevents the large unexplained gaps visible in the mobile reference.
- **Alternatives considered**: Hiding the entire section was rejected because it removes orientation and gives no explanation to users.

## Decision 3: Use existing routes and contact actions in the footer

- **Decision**: Footer navigation points to `/search`, Home fragments, partner/account routes and explicit `mailto:`/`tel:` actions for support and policy requests.
- **Rationale**: The route inventory does not contain standalone legal/help pages. Explicit contact actions are truthful and avoid dead links or 404 destinations.
- **Alternatives considered**: Adding new legal pages is outside this UI polish scope and would require product/content decisions.

## Decision 4: Reserve safe space for the floating support widget

- **Decision**: Footer receives sufficient bottom padding for the fixed support trigger/panel, with larger mobile padding and 44px footer targets.
- **Rationale**: The support control must remain available without covering footer navigation at the end of the page.
- **Alternatives considered**: Hiding support on the footer or changing chat authorization behavior was rejected because the widget is a shared product surface.
