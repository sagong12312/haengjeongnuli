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

## 로그인

- 처음 실행해서 계정이 하나도 없으면 **첫 관리자 계정 `admin`**을 만들고, 비밀번호를 서버 창에 **한 번만** 보여줍니다. 로그인한 뒤 오른쪽 위 이름을 눌러 비밀번호를 바꾸세요.
  - 비밀번호를 직접 정하려면 처음 실행 전에 환경변수 `APP_INIT_ADMIN_PASSWORD`를 넣습니다.
- 관리자는 머리글의 **계정 관리**(`/admin/users`)에서 직원 계정을 만들고, 부서 이동·잠금 해제·사용 중지·비밀번호 재설정을 합니다. 부서 이동은 `USER_DEPT_HISTORY`에 이전·새 부서, 처리자, 시각이 남고 화면 아래 '최근 부서 이동'에서 볼 수 있습니다.
- 권한: 일반 직원 / 창구 직원(민원 접수 가능) / 시스템 관리자(계정 관리, H2 콘솔)
- 비밀번호는 BCrypt로 암호화해 `USER_ACCOUNT` 테이블에 저장합니다. 규칙: 9자 이상, 영문·숫자·특수문자 포함, 아이디 포함 불가 (`PasswordPolicy`에서 변경)
- 비밀번호를 5번 틀리면 계정이 잠기고, 관리자가 풀어 줍니다.
- 모든 등록·수정 요청에 CSRF(요청 위조) 확인을 합니다.

### 전자정부 프레임워크 라이브러리 함께 받기

전자정부 프레임워크 저장소(maven.egovframe.go.kr)에 접속할 수 있는 PC에서:

```bash
./gradlew bootRun -PuseEgov
```

버전을 바꾸려면 `-PegovVersion=5.0.0` 처럼 지정합니다. 아티팩트 이름과 버전은 저장소에서 확인 후 `build.gradle`에서 조정하세요.

### 글꼴

문체부 돋음체 파일을 `src/main/resources/static/fonts/MDotum.ttf`에 두면 적용됩니다. 이용 조건(재배포 허용 여부)을 확인하지 못해 저장소에는 올리지 않았습니다(`.gitignore` 처리). 파일이 없으면 맑은 고딕으로 표시됩니다.

## 접속 허용 범위

기본값으로 **이 PC와 집 공유기 안의 기기(사설 IP: 192.168.x.x, 10.x.x.x, 172.16~31.x.x)**에서만 접속할 수 있고, 인터넷(바깥)에서 오는 접속은 로그인 화면도 보여주지 않고 막습니다. 막힌 접속은 서버 창에 기록됩니다.

- 범위는 `application.yml`의 `app.access.allowed-networks` 또는 환경변수 `APP_ALLOWED_NETWORKS`로 바꿉니다 (CIDR, 쉼표 구분).
- **이 PC에서만** 쓰려면:

```powershell
$env:APP_ALLOWED_NETWORKS="127.0.0.1/32,::1/128"
.\gradlew.bat bootRun
```

- 접속한 기기의 실제 주소만 보며, 요청 헤더(X-Forwarded-For 등)로 주소를 속여도 통과하지 않습니다.
- 함께 지킬 것: 공유기에서 8080 포트를 **포트 포워딩하지 않기**, Windows 방화벽에서 Java를 **'개인 네트워크'만 허용**하기.

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
   - 이미 예전 `schema.sql`로 만든 DB라면 `db/oracle/migration/` 안의 파일을 번호 순서대로 한 번씩 실행합니다. (로컬 H2는 자동)
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
- **게시글 수정**: 글쓴이 본인과 시스템 관리자만 목록의 '수정' 버튼으로 제목·내용·공개 범위·분류·상단 고정을 고치고, 첨부파일을 더하거나 뺄 수 있습니다(자료실 글은 파일 1개 이상 필수). 게시판 종류와 글쓴이는 바뀌지 않으며, 수정한 사람·시각을 기록하고 목록에 '(수정됨)'을 표시합니다.
- **게시물 삭제**: 글쓴이 본인과 시스템 관리자만 목록의 '삭제' 버튼으로 지울 수 있습니다. 실제로 지우지 않고 `DELETED_YN='Y'`로 표시(누가·언제 지웠는지 기록)하며, 목록에서 빠지고 첨부파일도 내려받을 수 없게 됩니다. 이 기능 전에 쓴 글은 글쓴이 아이디가 없어 관리자만 지울 수 있습니다.
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
| `/login` | 로그인 |
| `/` | 메인메뉴 (출근 상태 인사말, 업무 메뉴, 나의 할 일) |
| `/approval?box=wait\|prog\|done\|back\|recv` | 전자결재 문서함 |
| `/approval/draft` | 기안하기 |
| `/civil?kind=LEGAL\|QUERY\|SUGGESTION\|OTHER\|GRIEVANCE` | 민원 목록 |
| `/civil/new` | 민원 접수 (창구 직원만) |
| `/finance?area=fin\|tax` | 재정·세무 |
| `/hr` | 인사 (출근·퇴근) |
| `/board?tab=notice\|suggest\|archive` | 게시판 |
| `/board/write`, `/board/upload` | 글쓰기, 자료 올리기 |
| `/mypage/password` | 내 정보·비밀번호 변경 |
| `/admin/users` | 직원 계정 관리 (관리자) |

## 아직 임시로 처리한 것

- **로그인**: 비밀번호 변경 주기, 비밀번호 찾기, 접속 기록 화면, GPKI(행정전자서명) 인증서 로그인은 아직 없습니다.
- **데이터 저장**: 민원·게시판만 DB에 저장합니다. 기안 상신 등 나머지는 안내 문구만 보여주고, 출퇴근 상태는 브라우저 세션에만 저장됩니다.
- **담당 배정**: 부서·직원 목록이 없어 처리 부서와 담당자를 직접 입력합니다.
- **처리기한 계산**: 토·일요일만 빼고 계산하며, 공휴일과 '5일 이하는 시간 단위' 규정은 아직 반영하지 않았습니다.
- **법령 문구**: 민원 처리기간, 보존기간, 정보공개법 비공개 사유 등은 검색 자료 기준이며 국가법령정보센터 원문으로 최종 확인이 필요합니다.
- 이름·번호·금액 등 실제 데이터는 `[대괄호]` 자리표시자로 비워 두었습니다.

## 화면 시안 (`design/`)

처음 화면을 설계할 때 쓴 디자인 캔버스 시안(`.dc.html`)입니다. 브라우저에서 바로 열리지 않으며, 실제 화면은 `src/main/resources/templates/`에 있습니다.
