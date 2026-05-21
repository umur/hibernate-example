-- Truncate all tables in dependency order after each test class
TRUNCATE TABLE watchlist_entries, watchlists, watch_logs, reviews, movies, app_users
    RESTART IDENTITY CASCADE;
