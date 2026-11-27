ALTER TABLE feeds
    ADD CONSTRAINT fk_feeds_weather
        FOREIGN KEY (weather_id)
            REFERENCES weathers (id);