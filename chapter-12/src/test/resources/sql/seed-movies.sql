-- Seed data for ProjectionTest

INSERT INTO app_users (username, email) VALUES
    ('alice',   'alice@example.com'),
    ('bob',     'bob@example.com'),
    ('charlie', 'charlie@example.com');

INSERT INTO movies (title, genre, release_year, rating) VALUES
    ('Inception',         'SCIENCE_FICTION', 2010, 8.8),
    ('The Dark Knight',   'ACTION',          2008, 9.0),
    ('Interstellar',      'SCIENCE_FICTION', 2014, 8.6),
    ('Get Out',           'HORROR',          2017, 7.7),
    ('The Grand Illusion','DRAMA',           1937, 8.1);

-- Reviews for Inception: alice=9, bob=8
INSERT INTO reviews (movie_id, reviewer_id, content, rating, version)
SELECT m.id, u.id,
       'Excellent film',
       CASE u.username WHEN 'alice' THEN 9 ELSE 8 END,
       0
FROM   movies m, app_users u
WHERE  m.title = 'Inception'
AND    u.username IN ('alice', 'bob');

-- Review for The Dark Knight: charlie=10
INSERT INTO reviews (movie_id, reviewer_id, content, rating, version)
SELECT m.id, u.id, 'Best superhero film', 10, 0
FROM   movies m, app_users u
WHERE  m.title = 'The Dark Knight'
AND    u.username = 'charlie';
