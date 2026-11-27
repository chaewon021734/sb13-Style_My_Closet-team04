
package com.project.sb13ootbsteam04.feed.service;

import com.project.sb13ootbsteam04.clothes.entity.Clothes;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttribute;
import com.project.sb13ootbsteam04.clothes.entity.ClothesAttributeDef;
import com.project.sb13ootbsteam04.clothes.repository.ClothesRepository;
import com.project.sb13ootbsteam04.feed.dto.FeedResponse;
import com.project.sb13ootbsteam04.feed.entity.Feed;
import com.project.sb13ootbsteam04.feed.entity.FeedClothes;
import com.project.sb13ootbsteam04.feed.repository.CommentRepository;
import com.project.sb13ootbsteam04.feed.repository.FeedClothesRepository;
import com.project.sb13ootbsteam04.feed.repository.FeedLikeRepository;
import com.project.sb13ootbsteam04.user.entity.Profile;
import com.project.sb13ootbsteam04.user.entity.User;
import com.project.sb13ootbsteam04.user.repository.ProfileRepository;
import com.project.sb13ootbsteam04.user.repository.UserRepository;
import com.project.sb13ootbsteam04.weather.entity.Weather;
import com.project.sb13ootbsteam04.weather.repository.WeatherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedResponseService {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final FeedLikeRepository feedLikeRepository;
    private final CommentRepository commentRepository;
    private final WeatherRepository weatherRepository;
    private final FeedClothesRepository feedClothesRepository;
    private final ClothesRepository clothesRepository;

    // 피드 엔티티를 응답 DTO로 변환
    public FeedResponse toResponse(Feed feed) {

        if (feed == null) {
            throw new IllegalArgumentException(
                    "피드 정보는 필수입니다."
            );
        }

        // 1. 작성자 조회
        User author = userRepository.findById(feed.getUserId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "피드 작성자를 찾을 수 없습니다: "
                                        + feed.getUserId()
                        )
                );

        // 2. 작성자 프로필 이미지 조회
        String profileImageUrl = profileRepository
                .findById(author.getId())
                .map(Profile::getProfileImageUrl)
                .orElse(null);

        FeedResponse.AuthorDto authorDto =
                new FeedResponse.AuthorDto(
                        author.getId(),
                        author.getName(),
                        profileImageUrl
                );

        // 3. 날씨 정보 조회
        FeedResponse.WeatherDto weatherDto = null;

        if (feed.getWeatherId() != null) {
            weatherDto = weatherRepository
                    .findById(feed.getWeatherId())
                    .map(this::toWeatherDto)
                    .orElse(null);
        }

        // 4. 피드에 연결된 의상 ID 조회
        List<UUID> clothesIds =
                feedClothesRepository.findByFeed_Id(feed.getId())
                        .stream()
                        .map(FeedClothes::getClothesId)
                        .distinct()
                        .toList();

        // 5. 의상 정보 일괄 조회
        List<FeedResponse.OotdDto> ootds;

        if (clothesIds.isEmpty()) {
            ootds = List.of();
        } else {
            List<Clothes> clothesList =
                    clothesRepository.findAllById(clothesIds);

            // 의상 ID를 기준으로 조회 결과 정리
            Map<UUID, Clothes> clothesMap = clothesList.stream()
                    .collect(Collectors.toMap(
                            Clothes::getId,
                            Function.identity()
                    ));

            // 기존 의상 연결 순서를 유지하면서 DTO 변환
            ootds = clothesIds.stream()
                    .map(clothesMap::get)
                    .filter(clothes -> clothes != null)
                    .map(this::toOotdDto)
                    .toList();
        }

        // 6. 좋아요 수 조회
        long likeCount = feedLikeRepository.countByFeed_Id(
                feed.getId()
        );

        // 7. 댓글 수 조회
        long commentCount = commentRepository.countByFeed_Id(
                feed.getId()
        );

        // 8. 최종 응답 생성
        return new FeedResponse(
                feed.getId(),
                feed.getCreatedAt(),
                feed.getUpdatedAt(),
                authorDto,
                weatherDto,
                ootds,
                feed.getContent(),
                likeCount,
                Math.toIntExact(commentCount),
                false
        );
    }

    // 날씨 엔티티 -> 날씨 응답 DTO
    private FeedResponse.WeatherDto toWeatherDto(Weather weather) {

        FeedResponse.PrecipitationDto precipitation =
                new FeedResponse.PrecipitationDto(
                        weather.getPrecipitationType().name(),
                        weather.getPrecipitationAmount(),
                        weather.getPrecipitationProbability()
                );

        FeedResponse.TemperatureDto temperature =
                new FeedResponse.TemperatureDto(
                        weather.getTemperatureCurrent(),
                        weather.getTemperatureComparedToDayBefore(),
                        weather.getTemperatureMin(),
                        weather.getTemperatureMax()
                );

        return new FeedResponse.WeatherDto(
                weather.getId(),
                weather.getSkyStatus().name(),
                precipitation,
                temperature
        );
    }

    // 의상 엔티티 -> OOTD 응답 DTO
    private FeedResponse.OotdDto toOotdDto(Clothes clothes) {

        List<FeedResponse.AttributeDto> attributes =
                clothes.getAttributes()
                        .stream()
                        .map(this::toAttributeDto)
                        .toList();

        return new FeedResponse.OotdDto(
                clothes.getId(),
                clothes.getName(),
                clothes.getImageUrl(),
                clothes.getType().name(),
                attributes
        );
    }

    // 의상 속성 엔티티 -> 속성 응답 DTO
    private FeedResponse.AttributeDto toAttributeDto(
            ClothesAttribute attribute
    ) {

        ClothesAttributeDef definition =
                attribute.getDefinition();

        return new FeedResponse.AttributeDto(
                definition.getId(),
                definition.getName(),
                definition.getSelectableValues(),
                attribute.getValue()
        );
    }

    // 로그인 사용자 정보가 있는 경우 좋아요 여부 반영
    public FeedResponse toResponse(
            Feed feed,
            UUID currentUserId
    ) {

        FeedResponse response = toResponse(feed);

        if (currentUserId == null) {
            return response;
        }

        boolean likedByMe =
                feedLikeRepository.existsByFeed_IdAndUserId(
                        feed.getId(),
                        currentUserId
                );

        return new FeedResponse(
                response.id(),
                response.createdAt(),
                response.updatedAt(),
                response.author(),
                response.weather(),
                response.ootds(),
                response.content(),
                response.likeCount(),
                response.commentCount(),
                likedByMe
        );
    }
}
