-- Seed data for MovieQueryTest
-- Inserted fresh before each test class via @Sql

INSERT INTO app_users (username, email) VALUES
    ('alice',   'alice@example.com'),
    ('bob',     'bob@example.com'),
    ('charlie', 'charlie@example.com');

INSERT INTO movies (title, genre, release_year, rating) VALUES
    ('Inception',        'SCIENCE_FICTION', 2010, 8.8),
    ('The Dark Knight',  'ACTION',          2008, 9.0),
    ('Interstellar',     'SCIENCE_FICTION', 2014, 8.6),
    ('Get Out',          'HORROR',          2017, 7.7),
    ('The Grand Illusion','DRAMA',          1937, 8.1);

-- Reviews: alice and bob review Inception
INSERT INTO reviews (movie_id, reviewer_id, rating, content, version)
SELECT m.id, u.id,
       CASE u.username WHEN 'alice' THEN 9 ELSE 8 END,
       'Great film',
       0
FROM   movies m, app_users u
WHERE  m.title = 'Inception'
AND    u.username IN ('alice', 'bob');

-- Bob reviews The Dark Knight
INSERT INTO reviews (movie_id, reviewer_id, rating, content, version)
SELECT m.id, u.id, 10, 'Masterpiece', 0
FROM   movies m, app_users u
WHERE  m.title = 'The Dark Knight'
AND    u.username = 'bob';

-- Charlie reviews Get Out
INSERT INTO reviews (movie_id, reviewer_id, rating, content, version)
SELECT m.id, u.id, 8, 'Very tense', 0
FROM   movies m, app_users u
WHERE  m.title = 'Get Out'
AND    u.username = 'charlie';
