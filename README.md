# 행정누리

통합 행정업무 시스템 **행정누리**입니다.

- Java 17 이상 (개발 환경 JDK 21)
- Spring Boot 3.5.6 (전자정부 표준프레임워크 5.0과 같은 기반) + Thymeleaf
- 패키지 구조: 전자정부 프레임워크 방식 `egovframework.haengjeongnuli.{업무}.web / service`

## 실행

```bash
./gradlew bootRun
```

브라우저에서 http://localhost:8080 을 엽니다. Windows에서는 `gradlew.bat bootRun`.

### 전자정부 프레임워크 라이브러리 함께 받기

전자정부 프레임워크 저장소(maven.egovframe.go.kr)에 접속할 수 있는 PC에서:

```bash
./gradlew bootRun -PuseEgov
```

버전을 바꾸려면 `-PegovVersion=5.0.0` 처럼 지정합니다. 아티팩트 이름과 버전은 저장소에서 확인 후 `build.gradle`에서 조정하세요.

### 글꼴

문체부 돋음체 파일을 `src/main/resources/static/fonts/MDotum.ttf`에 두면 적용됩니다. 이용 조건(재배포 허용 여부)을 확인하지 못해 저장소에는 올리지 않았습니다(`.gitignore` 처리). 파일이 없으면 맑은 고딕으로 표시됩니다.

## 테스트

```bash
./gradlew test
```

## 화면과 주소

| 주소 | 화면 |
|---|---|
| `/` | 메인메뉴 (출근 상태 인사말, 업무 메뉴, 나의 할 일) |
| `/approval?box=wait\|prog\|done\|back\|recv` | 전자결재 문서함 |
| `/approval/draft` | 기안하기 |
| `/civil?kind=LEGAL\|QUERY\|SUGGESTION\|OTHER\|GRIEVANCE` | 민원 목록 |
| `/civil/new` | 민원 접수 (창구 직원만) |
| `/finance?area=fin\|tax` | 재정·세무 |
| `/hr` | 인사 (출근·퇴근) |
| `/board?tab=notice\|suggest\|archive` | 게시판 |
| `/board/write`, `/board/upload` | 글쓰기, 자료 올리기 |

## 아직 임시로 처리한 것

- **로그인·권한**: `application.yml`의 `app.user`에 적은 임시 사용자로 동작합니다. `role: GENERAL`로 바꾸면 일반 직원 화면(민원 접수 불가)을 볼 수 있습니다.
- **데이터 저장**: 데이터베이스가 없어 등록·접수·상신 버튼은 안내 문구만 보여줍니다. 출퇴근 상태는 브라우저 세션에만 저장됩니다.
- **처리기한 계산**: 토·일요일만 빼고 계산하며, 공휴일과 '5일 이하는 시간 단위' 규정은 아직 반영하지 않았습니다.
- **법령 문구**: 민원 처리기간, 보존기간, 정보공개법 비공개 사유 등은 검색 자료 기준이며 국가법령정보센터 원문으로 최종 확인이 필요합니다.
- 이름·번호·금액 등 실제 데이터는 `[대괄호]` 자리표시자로 비워 두었습니다.

## 화면 시안 (`design/`)

처음 화면을 설계할 때 쓴 디자인 캔버스 시안(`.dc.html`)입니다. 브라우저에서 바로 열리지 않으며, 실제 화면은 `src/main/resources/templates/`에 있습니다.
