-- V16: Rebuild category materialized paths as proper hierarchical paths.
--
-- V13 backfilled path = name for all categories, so subcategories ended up with
-- flat paths (e.g. "Phones" instead of "Electronics/Phones"). This breaks the
-- subtree browse in ProductServiceImpl which relies on path LIKE 'Electronics/%'.
--
-- Uses a recursive CTE to compute the correct path for every category at any depth,
-- then bulk-updates rows whose path has changed.

WITH RECURSIVE category_tree AS (

    -- Base: root categories (no parent) keep path = name
    SELECT
        id,
        name,
        parent_id,
        CAST(name AS VARCHAR(2000)) AS computed_path
    FROM categories
    WHERE parent_id IS NULL

    UNION ALL

    -- Recursive: children inherit parent path
    SELECT
        c.id,
        c.name,
        c.parent_id,
        CAST(ct.computed_path || '/' || c.name AS VARCHAR(2000))
    FROM categories c
    JOIN category_tree ct ON c.parent_id = ct.id

)
UPDATE categories c
SET path = ct.computed_path
FROM category_tree ct
WHERE c.id = ct.id
  AND c.path IS DISTINCT FROM ct.computed_path;
