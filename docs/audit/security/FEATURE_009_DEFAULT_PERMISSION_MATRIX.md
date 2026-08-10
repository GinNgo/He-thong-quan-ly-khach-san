# Feature 009 Default Permission Matrix

Action bits remain backward compatible: `VIEW=1`, `CREATE=2`, `UPDATE=4`, `DELETE=8`, `EXPORT=16`, `APPROVE=32`, `TASK_EXECUTE=64`.

| Function family | Supported default actions | Default role intent |
|---|---|---|
| Reports and audit | `VIEW`, `EXPORT` | Manager/Accountant by property scope; platform Admin for platform reports |
| Check-in, checkout, housekeeping and assignment | `VIEW`, `UPDATE`, `APPROVE`, `TASK_EXECUTE` | Manager and Receptionist according to duty; Accountant view-only when granted |
| Refunds | `VIEW`, `EXPORT`, `APPROVE`, `TASK_EXECUTE` | Accountant/Manager by explicit grant and property scope |
| Resource CRUD | Full compatible mask | Manager/Admin according to function and scope |
| Platform billing and readiness | Full compatible mask | Platform Admin, except package purchase explicitly granted to property representatives |

## Invariants

- Any non-zero action mask includes `VIEW`.
- UI displays only actions declared by `supported_action_mask`.
- Backend rejects invalid, inactive or unsupported grants.
- Function permission never bypasses customer ownership, property assignment or subscription feature gates.

