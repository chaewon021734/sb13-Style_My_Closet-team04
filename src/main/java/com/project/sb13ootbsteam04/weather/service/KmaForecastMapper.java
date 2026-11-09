package com.project.sb13ootbsteam04.weather.service;

import com.project.sb13ootbsteam04.weather.dto.KmaForecastItem;
import com.project.sb13ootbsteam04.weather.entity.PrecipitationType;
import com.project.sb13ootbsteam04.weather.entity.SkyStatus;
import com.project.sb13ootbsteam04.weather.entity.Weather;
import com.project.sb13ootbsteam04.weather.entity.WeatherLocation;
import com.project.sb13ootbsteam04.weather.entity.WindStrength;
import com.project.sb13ootbsteam04.weather.exception.WeatherForecastException;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static com.project.sb13ootbsteam04.global.exception.ErrorCode.WEATHER_FORECAST_RESPONSE_INVALID;

@Component
class KmaForecastMapper {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuuMMddHHmm").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern PRECIPITATION_AMOUNT = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)(?:mm)?");

    List<Weather> toForecasts(WeatherLocation location, List<KmaForecastItem> items) {
        if (items == null || items.isEmpty() || items.get(0) == null) {
            throw invalid("예보 항목이 없습니다.");
        }
        var issue = parseTime(items.get(0).baseDate(), items.get(0).baseTime());
        // 17시 이후 발표분은 정량 예보를 하루 더 제공한다. 경계일의 00시는 정량 예보에 포함된다.
        var quantitativeEnd = issue.toLocalDate().plusDays(issue.getHour() < 17 ? 3 : 4).atStartOfDay();
        var slots = groupByForecastTime(location, items, issue);
        return slots.entrySet().stream()
                .map(slot -> toForecast(location, issue, slot.getKey(), slot.getValue(),
                        slot.getKey().isAfter(quantitativeEnd)))
                .toList();
    }

    private Map<LocalDateTime, Map<String, String>> groupByForecastTime(
            WeatherLocation location, List<KmaForecastItem> items, LocalDateTime issue) {
        Map<LocalDateTime, Map<String, String>> slots = new TreeMap<>();
        for (var item : items) {
            if (item == null || !Objects.equals(item.nx(), location.getGridX())
                    || !Objects.equals(item.ny(), location.getGridY())
                    || !parseTime(item.baseDate(), item.baseTime()).equals(issue)) {
                throw invalid("격자 좌표 또는 예보 발표 시각이 일치하지 않습니다.");
            }
            var forecastTime = parseTime(item.fcstDate(), item.fcstTime());
            if (!forecastTime.isAfter(issue) || item.category() == null || item.category().isBlank()) {
                throw invalid("예보 대상 시각 또는 항목 코드가 올바르지 않습니다.");
            }
            var values = slots.computeIfAbsent(forecastTime, ignored -> new HashMap<>());
            if (values.containsKey(item.category()) && !Objects.equals(values.get(item.category()), item.fcstValue())) {
                throw invalid("같은 예보 시각에 " + item.category() + " 값이 서로 다릅니다.");
            }
            values.put(item.category(), item.fcstValue());
        }
        return slots;
    }

    private Weather toForecast(WeatherLocation location, LocalDateTime issue, LocalDateTime target,
                               Map<String, String> values, boolean extended) {
        var precipitationRaw = required(values, "PCP");
        var windRaw = required(values, "WSD");
        var wind = extended ? null : number(windRaw, "WSD", 0, Double.MAX_VALUE);
        var probability = optionalNumber(values, "POP", 0, 100);
        return Weather.builder()
                .locationId(location.getId())
                .forecastedAt(issue.atZone(KST).toInstant())
                .forecastAt(target.atZone(KST).toInstant())
                .forecastStepMinutes(extended ? 180 : 60)
                .skyStatus(skyStatus(required(values, "SKY")))
                .precipitationType(precipitationType(required(values, "PTY")))
                .precipitationAmountRaw(precipitationRaw)
                .precipitationAmount(precipitationAmount(precipitationRaw, extended))
                .precipitationProbability(probability == null ? null : probability / 100)
                .humidityCurrent(number(required(values, "REH"), "REH", 0, 100))
                .temperatureCurrent(number(required(values, "TMP"), "TMP", -Double.MAX_VALUE, Double.MAX_VALUE))
                .temperatureMin(optionalNumber(values, "TMN", -Double.MAX_VALUE, Double.MAX_VALUE))
                .temperatureMax(optionalNumber(values, "TMX", -Double.MAX_VALUE, Double.MAX_VALUE))
                .windSpeedRaw(windRaw)
                .windSpeed(wind)
                .windSpeedAsWord(windStrength(windRaw, wind))
                .build();
    }

    private static LocalDateTime parseTime(String date, String time) {
        if (date == null || time == null || date.length() != 8 || time.length() != 4) {
            throw invalid("예보 날짜 또는 시각이 누락되었거나 형식이 올바르지 않습니다.");
        }
        try {
            return LocalDateTime.parse(date + time, TIMESTAMP);
        } catch (DateTimeException exception) {
            throw invalid("예보 날짜 또는 시각이 올바르지 않습니다.");
        }
    }

    private static String required(Map<String, String> values, String category) {
        var value = values.get(category);
        if (value == null || value.isBlank() || value.length() > 100) {
            throw invalid("필수 예보 항목이 누락되었거나 올바르지 않습니다. 항목: " + category);
        }
        return value;
    }

    private static Double optionalNumber(Map<String, String> values, String category, double min, double max) {
        var value = values.get(category);
        return value == null || value.isBlank() ? null : number(value, category, min, max);
    }

    private static double number(String raw, String category, double min, double max) {
        try {
            double value = Double.parseDouble(raw);
            if (Double.isFinite(value) && value != -999 && value >= min && value <= max) {
                return value;
            }
        } catch (NumberFormatException exception) {
            throw invalid("예보 항목이 숫자가 아닙니다. 항목: " + category);
        }
        throw invalid("예보 항목의 값이 범위를 벗어났습니다. 항목: " + category);
    }

    private static Double precipitationAmount(String raw, boolean extended) {
        var value = raw.strip();
        if (value.equals("강수없음") || value.equals("0")) {
            return 0.0;
        }
        // 강수 구간과 연장 예보의 강수 강도 코드는 정확한 강수량으로 환산할 수 없다.
        var match = PRECIPITATION_AMOUNT.matcher(value);
        return !extended && match.matches() ? number(match.group(1), "PCP", 0, Double.MAX_VALUE) : null;
    }

    private static SkyStatus skyStatus(String code) {
        return switch (code) {
            case "1" -> SkyStatus.CLEAR;
            case "3" -> SkyStatus.MOSTLY_CLOUDY;
            case "4" -> SkyStatus.CLOUDY;
            default -> throw invalid("지원하지 않는 하늘 상태 코드입니다.");
        };
    }

    private static PrecipitationType precipitationType(String code) {
        return switch (code) {
            case "0" -> PrecipitationType.NONE;
            case "1" -> PrecipitationType.RAIN;
            case "2" -> PrecipitationType.RAIN_SNOW;
            case "3" -> PrecipitationType.SNOW;
            case "4" -> PrecipitationType.SHOWER;
            default -> throw invalid("지원하지 않는 강수 형태 코드입니다.");
        };
    }

    private static WindStrength windStrength(String raw, Double speed) {
        if (speed == null) {
            return switch (raw) {
                case "1" -> WindStrength.WEAK;
                case "2" -> WindStrength.MODERATE;
                case "3" -> WindStrength.STRONG;
                default -> throw invalid("지원하지 않는 연장 예보 풍속 코드입니다.");
            };
        }
        if (speed < 4) {
            return WindStrength.WEAK;
        }
        return speed < 9 ? WindStrength.MODERATE : WindStrength.STRONG;
    }

    private static WeatherForecastException invalid(String reason) {
        return new WeatherForecastException(WEATHER_FORECAST_RESPONSE_INVALID, reason);
    }
}
