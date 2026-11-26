
-- 날씨 조회 위치 정보
CREATE TABLE weather_locations (
                                   id UUID NOT NULL,
                                   latitude DOUBLE PRECISION NOT NULL,
                                   longitude DOUBLE PRECISION NOT NULL,
                                   grid_x INTEGER NOT NULL,
                                   grid_y INTEGER NOT NULL,
                                   created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                                   updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,

                                   CONSTRAINT pk_weather_locations PRIMARY KEY (id)
);

-- 위치별 행정구역 이름
CREATE TABLE weather_location_names (
                                        id UUID NOT NULL,
                                        location_id UUID NOT NULL,
                                        display_order INTEGER NOT NULL,
                                        name VARCHAR(100) NOT NULL,
                                        created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                                        updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,

                                        CONSTRAINT pk_weather_location_names PRIMARY KEY (id),
                                        CONSTRAINT fk_weather_location_names_location
                                            FOREIGN KEY (location_id)
                                                REFERENCES weather_locations (id)
);

-- 날씨 예보 정보
CREATE TABLE weathers (
                          id UUID NOT NULL,
                          location_id UUID NOT NULL,
                          forecasted_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                          forecast_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                          forecast_step_minutes INTEGER NOT NULL,
                          sky_status VARCHAR(20) NOT NULL,
                          precipitation_type VARCHAR(20) NOT NULL,
                          precipitation_amount_raw VARCHAR(100) NOT NULL,
                          precipitation_amount DOUBLE PRECISION,
                          precipitation_probability DOUBLE PRECISION,
                          humidity_current DOUBLE PRECISION NOT NULL,
                          humidity_compared_to_day_before DOUBLE PRECISION,
                          temperature_current DOUBLE PRECISION NOT NULL,
                          temperature_compared_to_day_before DOUBLE PRECISION,
                          temperature_min DOUBLE PRECISION,
                          temperature_max DOUBLE PRECISION,
                          wind_speed_raw VARCHAR(100) NOT NULL,
                          wind_speed DOUBLE PRECISION,
                          wind_speed_as_word VARCHAR(10) NOT NULL,
                          created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                          updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,

                          CONSTRAINT pk_weathers PRIMARY KEY (id),
                          CONSTRAINT fk_weathers_location
                              FOREIGN KEY (location_id)
                                  REFERENCES weather_locations (id)
);

CREATE INDEX idx_weather_location_names_location_id
    ON weather_location_names (location_id);

CREATE INDEX idx_weathers_location_id
    ON weathers (location_id);
