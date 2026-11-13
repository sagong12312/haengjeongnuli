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

## 데이터베이스

데이터 접근은 MyBatis(SQL은 `src/main/resources/egovframework/mapper/`)로 하며, Oracle·Tibero·H2에서 모두 도는 Oracle 문법만 씁니다.

| 실행 방법 | DB | 비고 |
|---|---|---|
| `./gradlew bootRun` | H2 파일 DB (Oracle 호환 모드) | 기본값. 프로젝트의 `data/` 폴더에 저장. 테이블 자동 생성 |
| `--spring.profiles.active=oracle` | Oracle | `ojdbc11` 드라이버 포함 |
| `--spring.profiles.active=tibero` | Tibero | 드라이버 jar를 `libs/`에 직접 넣어야 함 |

로컬 H2 데이터는 http://localhost:8080/h2-console 에서 볼 수 있습니다 (JDBC URL: `jdbc:h2:file:./data/haengjeongnuli;MODE=Oracle;AUTO_SERVER=TRUE`, 사용자 `sa`, 비밀번호 없음).

### Oracle / Tibero 연결 순서

1. DBA가 `src/main/resources/db/oracle/schema.sql`로 테이블과 시퀀스를 만듭니다. (Oracle·Tibero 공통)
2. Tibero라면 Tibero 설치 폴더의 `client/lib/jar/tibero7-jdbc.jar`(Tibero 6은 `tibero6-jdbc.jar`)를 프로젝트의 `libs/` 폴더에 복사합니다.
3. 접속 정보를 환경변수로 넣고 실행합니다. 비밀번호는 파일에 적지 않습니다.

Windows PowerShell 예 (Oracle):

```powershell
$env:DB_URL="jdbc:oracle:thin:@//DB주소:1521/서비스명"
$env:DB_USERNAME="계정"
$env:DB_PASSWORD="비밀번호"
.\gradlew.bat bootRun --args="--spring.profiles.active=oracle"
```

Tibero는 `DB_URL="jdbc:tibero:thin:@DB주소:8629:DB명"`, 프로필은 `tibero`.

### 지금 DB에 저장되는 것

- **민원**: 접수(접수번호 `연도-일련번호` 자동 부여, 처리기한 계산), 목록·검색, 처리 현황 숫자
- **게시판**: 글쓰기, 자료 올리기, 목록·검색, 메인메뉴 공지사항
- **첨부파일**: 게시판·자료실 첨부파일. 파일 자체는 `app.file.upload-dir`(기본 `./uploads`)에, 원래 이름·크기·받은 횟수는 `ATTACH_FILE` 테이블에 저장. 목록의 파일 이름을 누르면 내려받습니다.
  - 파일당 20MB, 한 번에 100MB까지 (`spring.servlet.multipart`)
  - 올릴 수 있는 형식은 `app.file.allowed-extensions`에서 정합니다 (실행 파일 등은 거부)
  - 서버에는 무작위 이름으로 저장하고, 내려받을 때 원래 이름으로 돌려줍니다

전자결재·인사·재정·세무 화면과 출퇴근, 민원 구비서류 첨부는 아직 DB에 연결하지 않았습니다.

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
- **데이터 저장**: 민원·게시판만 DB에 저장합니다. 기안 상신 등 나머지는 안내 문구만 보여주고, 출퇴근 상태는 브라우저 세션에만 저장됩니다.
- **담당 배정**: 부서·직원 목록이 없어 처리 부서와 담당자를 직접 입력합니다.
- **처리기한 계산**: 토·일요일만 빼고 계산하며, 공휴일과 '5일 이하는 시간 단위' 규정은 아직 반영하지 않았습니다.
- **법령 문구**: 민원 처리기간, 보존기간, 정보공개법 비공개 사유 등은 검색 자료 기준이며 국가법령정보센터 원문으로 최종 확인이 필요합니다.
- 이름·번호·금액 등 실제 데이터는 `[대괄호]` 자리표시자로 비워 두었습니다.

## 화면 시안 (`design/`)

처음 화면을 설계할 때 쓴 디자인 캔버스 시안(`.dc.html`)입니다. 브라우저에서 바로 열리지 않으며, 실제 화면은 `src/main/resources/templates/`에 있습니다.
