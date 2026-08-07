# UI Contract: Home Landing & Public Footer

## Home contract

- The Home route renders an above-the-fold heading, supporting copy and the existing search component.
- Search submission delegates to `HomeSearchStateService.submitSearch()` and preserves the current query behavior.
- Destination selection delegates to `selectSuggestion()` then `submitSearch()`.
- Property cards navigate to `/hotel/:id` with current booking query parameters.
- Loading, empty and image fallback states are visible and do not create unexplained blank regions.

## Footer contract

- Footer exposes grouped navigation with `nav` semantics and links/buttons that resolve to existing routes or explicit contact actions.
- Footer content remains readable and keyboard reachable at 375px, 768px, 1024px and 1440px.
- Footer reserves a safe visual offset for `app-chat-widget`; no footer CTA is obscured by the fixed support trigger/panel.
- No contract in this feature changes authentication, authorization, payment or chat message ownership.
