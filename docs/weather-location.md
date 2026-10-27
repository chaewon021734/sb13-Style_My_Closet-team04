# 날씨 위치 조회 API

## 계약

- `GET /api/weathers/location?longitude=126.978&latitude=37.5665`
- 두 파라미터 모두 필수이다. 위도 -90~90, 경도 -180~180의 유한한 수만 허용한다.
- 정상 응답: `latitude`, `longitude`, 기상청 5km 격자 `x`, `y`, 행정구역 단계별 `locationNames`.
- 행정구역은 카카오 좌표→행정구역 API의 행정동(`region_type=H`) 결과를 사용한다.
- 잘못된 입력, 조회 결과 없음, 외부 조회 실패는 Swagger에 명시된 400 응답으로 반환한다. 오류 응답 필드는 `exceptionName`, `message`, `details`이다.

## 실행 설정

`KAKAO_REST_API_KEY` 환경변수 또는 프로젝트 루트 `.env`에 카카오 REST API 키를 지정한다. Spring Boot는 실행 작업 디렉터리의 `.env`를 optional properties 설정으로 읽는다. IDE 실행 시 작업 디렉터리를 프로젝트 루트로 지정한다. 키가 없으면 애플리케이션은 시작할 수 있으나 위치 조회는 명시적으로 실패한다. 실제 키를 저장소에 추가하지 않는다.

카카오 호출의 연결 제한시간은 3초, 읽기 제한시간은 5초이다. 외부 오류 응답 본문은 사용자 응답에 포함하지 않는다.

## 구조

기존 도메인 구조, 생성자 주입, record 응답 DTO 패턴을 따른다. Controller는 조회 조건을 `WeatherLocationCondition`으로 변환하고 Service로 전달한다. 위치 조회에는 DB 접근이나 트랜잭션이 필요하지 않다.

공통 예외 체계가 아직 비어 있어 기존 공통 예외 클래스를 상속한 `WeatherLocationException`과 날씨 Controller에만 적용되는 advice를 사용했다. 공통 핸들러 확정 시 이관한다.

## 검증 및 남은 의존성

2026-10-27 기준 전체 프로젝트는 기존 feed/follow가 참조하는 user/clothes 코드 누락으로 컴파일되지 않는다. 이 문제를 숨기는 소스 제외 설정은 프로젝트에 추가하지 않았다.

날씨 서비스, 격자 변환, 카카오 클라이언트, standalone MockMvc 테스트를 별도 범위로 검증한다. 외부 API는 모의 응답을 사용하므로 실제 API 키가 필요하지 않다. standalone MockMvc 테스트는 추후 인증 모듈의 접근 제어를 검증하지 않는다.

ERD 웹 링크는 접근 불가 안내를 반환했으나, 사용자가 제공한 `Style-My-Closet.png` 이미지로 스키마를 확인했다.

## 엔티티 및 Repository

- `WeatherLocation` → `weather_locations`: 위도·경도, `grid_x`, `grid_y`.
- `WeatherLocationName` → `weather_location_names`: `location_id`, `display_order`, `name`(100자). Repository는 표시 순서대로 행정구역명을 조회한다.
- `Weather` → `weathers`: 위치 ID, 발표·대상 시각, 예보 간격, 하늘 상태, 강수 원문·타입·양·확률, 습도 및 전일 차이, 온도 및 전일 차이·최저·최고, 풍속 원문·값·등급. Repository는 위치와 발표 시각에 해당하는 예보를 대상 시각순으로 조회한다.
- BaseEntity의 UUID 및 auditing을 재사용한다. ERD에 맞춰 해당 세 엔티티의 `updated_at` 컬럼 nullable을 재정의했다.
- 기존 feed/follow와 동일하게 도메인 간 참조는 UUID 필드로 표현한다. 실제 DB의 FK 제약 관리 방식은 팀의 스키마 관리 정책에 따른다.
- SkyStatus, PrecipitationType, WindStrength는 Swagger에 명시된 값을 사용하며 문자열로 저장한다.
- 강수량·확률, 전일 대비 값, 일 최저·최고 기온, 변환된 풍속은 ERD대로 null을 허용한다. 발표·대상 시각, 원문 값 등 필수 필드와 구분한다.
- 위치 조회 API는 좌표 해석 결과만 반환하며 조회할 때 위치 데이터를 자동 저장하지 않는다. 위치·날씨 저장은 추후 프로필 등록이나 날씨 수집 작업에서 Repository를 사용한다.

날씨 테스트 범위에는 H2(PostgreSQL 모드)를 사용한 실제 엔티티 저장·복원, 예보 필터·정렬, 행정구역 정렬 검증을 포함한다. 실제 PostgreSQL 스키마에 대한 검증은 별도이다.

## 참고

- [프로젝트 Swagger](https://project.sb.sprint.learn.codeit.kr/sb/otboo/api/swagger-ui/index.html#/)
- [기능 요구사항](https://codeit.notion.site/2646fd228e8d810f9823c641cea821cd)
- [카카오 좌표→행정구역](https://developers.kakao.com/docs/latest/ko/local/dev-guide#coord-to-district)
- [기상청 단기예보](https://www.data.go.kr/data/15084084/openapi.do)
