# Database schema docs — where they live

The RVNKLore schema documents are **not** in this submodule. They live in the parent repo:

```
projects/RVNKLore/Schema/
```

15 documents, including `db-schema-core.md`, `db-schema-data-relationships.md`,
`db-schema-data-taxonomy.md` and the `Tables/` breakdown.

> This file replaces `docs/schema`, which was a symlink to
> `C:/tools/_PROJECTS/Ravenkaft Dev/RVNKLore/Schema` and had been broken since the move off
> Windows. **This project does not use symlinks** — cross-platform checkouts break them silently
> and they are invisible in a diff. Use a short text pointer like this one instead.

Schema docs that *are* maintained inside this submodule:

- `docs/rvnklore-schema.md`
- `docs/database-schema.md`
