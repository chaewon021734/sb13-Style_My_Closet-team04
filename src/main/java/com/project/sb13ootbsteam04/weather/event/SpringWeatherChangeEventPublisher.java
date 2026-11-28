package com.project.sb13ootbsteam04.weather.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class SpringWeatherChangeEventPublisher implements WeatherChangeEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publish(WeatherChangeDetectedEvent event) {
        eventPublisher.publishEvent(event);
    }
}
