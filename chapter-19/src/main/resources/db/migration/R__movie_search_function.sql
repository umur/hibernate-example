CREATE OR REPLACE FUNCTION search_movies(query_text TEXT)
RETURNS TABLE(id BIGINT, title VARCHAR, rank REAL) AS $$
    SELECT id, title,
           ts_rank(to_tsvector('english', title), plainto_tsquery('english', query_text)) AS rank
    FROM movies
    WHERE to_tsvector('english', title) @@ plainto_tsquery('english', query_text)
    ORDER BY rank DESC;
$$ LANGUAGE SQL;
