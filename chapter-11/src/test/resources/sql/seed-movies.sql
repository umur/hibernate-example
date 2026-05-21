-- Seed data for MovieSpecificationTest

INSERT INTO app_users (username, email) VALUES
    ('alice', 'alice@example.com');

INSERT INTO movies (title, genre, release_year, rating) VALUES
    ('Inception',           'SCIENCE_FICTION', 2010, 8.8),
    ('The Dark Knight',     'ACTION',          2008, 9.0),
    ('Interstellar',        'SCIENCE_FICTION', 2014, 8.6),
    ('Get Out',             'HORROR',          2017, 7.7),
    ('Mad Max Fury Road',   'ACTION',          2015, 8.1),
    ('The Grand Illusion',  'DRAMA',           1937, 8.1),
    ('Parasite',            'THRILLER',        2019, 8.6),
    ('Hereditary',          'HORROR',          2018, 7.3);
