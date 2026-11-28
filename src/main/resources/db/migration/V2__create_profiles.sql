CREATE TABLE profiles (
                          user_id UUID PRIMARY KEY,
                          gender VARCHAR(20),
                          birth_date DATE,
                          location JSONB,
                          temperature_sensitivity INTEGER,
                          profile_image_url VARCHAR(500),

                          CONSTRAINT fk_profiles_user
                              FOREIGN KEY (user_id)
                                  REFERENCES users(id),

                          CONSTRAINT chk_temperature_sensitivity
                              CHECK (
                                  temperature_sensitivity IS NULL
                                      OR temperature_sensitivity BETWEEN 1 AND 5
                                  )
);
