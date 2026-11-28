package com.project.sb13ootbsteam04.weather.event;

public interface WeatherChangeEventPublisher {

    void publish(WeatherChangeDetectedEvent event);
}
