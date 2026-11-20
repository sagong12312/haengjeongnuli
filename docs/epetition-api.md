# 행정누리 전자민원 (haengjeongnuli e-Petition) 연동 규격

이 문서는 국민용 **행정누리 전자민원** 서비스를 만드는 쪽에 넘기는 인수인계 문서입니다.
행정누리(공무원용 내부 시스템)는 이미 이 규격의 연동 API를 갖추고 있습니다.
전자민원은 이 API만 부르면 되고, 행정누리 DB에 직접 붙지 않습니다.

## 1. 구성

```
국민 브라우저 ──HTTPS──▶ 행정누리 전자민원 (인터넷 구간, DMZ)
                               │  서버 대 서버 호출만 (HMAC 서명)
                               ▼
                         행정누리 내부 시스템 (/api/epetition/v1/**)
                               │
                               ▼
                         행정누리 DB (Oracle/Tibero)
```

- 국민 PC는 행정누리 내부 시스템에 **직접 접속하지 않습니다**. 행정누리는 내부망 허용 목록 밖의 접속을 막습니다.
- 전자민원 서버만 연동 API를 부를 수 있습니다. 행정누리에 정한 서버 주소에서 서명된 요청만 받습니다.
- 전자민원은 민원 내용이나 개인정보를 **자기 DB에 오래 두지 않는** 것을 권합니다. 접수 결과(접수번호)만 화면에 보여 주고, 조회는 그때마다 API로 합니다.

## 2. 행정누리 쪽 설정 (운영 담당)

| 환경변수 | 뜻 | 예 |
|---|---|---|
| `APP_EPETITION_ENABLED` | 연동 API 켜기 | `true` |
| `APP_EPETITION_SECRET` | 서명 비밀값. **영문·숫자·기호로 32자 이상** 무작위 (한글 금지), 양쪽 서버 환경변수로만 보관하고 코드·저장소에 넣지 않음 | `openssl rand -hex 32` 결과 |
| `APP_EPETITION_NETWORKS` | 전자민원 서버 주소 (CIDR, 쉼표로 여러 개) | `10.20.30.40/32` |

| `APP_EPETITION_REQUIRE_HTTPS` | HTTPS로 온 요청만 받기 (기본 `true`, 내 PC 시험용 local 프로필만 `false`) | `true` |
| `APP_FORWARD_HEADERS` | 앞단 프록시가 TLS를 끝내면 `native` (프록시의 X-Forwarded-Proto를 믿음) | `native` |

꺼져 있거나 비밀값이 영문·숫자·기호 32자 이상이 아니면 연동 API 주소는 모두 404를 돌려줍니다.
**서명은 위조를 막을 뿐 내용을 감추지 않습니다.** 이름·연락처·조회 비밀번호가 오가므로 두 서버 사이는 반드시 HTTPS로 연결합니다(`NURI_BASE_URL=https://…`).

## 3. 인증 (모든 요청)

요청마다 아래 헤더 세 개를 붙입니다.

| 헤더 | 값 |
|---|---|
| `X-EP-Timestamp` | 지금 시각, 유닉스 초 (예: `1763280000`). 행정누리 시계와 **±300초** 안이어야 함 |
| `X-EP-Nonce` | 요청마다 새로 만든 값. 영문·숫자·`_`·`-`로 16~64자 (예: UUID에서 `-`를 뺀 값). 같은 값을 다시 쓰면 거절 (재전송 방지) |
| `X-EP-Signature` | 아래 방식으로 만든 서명 (소문자 16진수 64자) |
| `X-EP-Client-IP` | 전자민원에 접속한 국민의 IP (**꼭 보내세요**). 행정누리 접속기록에 남기고, 조회 비밀번호를 많이 틀리는 IP를 막는 데 씁니다 |

서명 만드는 법은 다음과 같습니다.

```
본문해시 = hex( SHA-256( 요청 본문 바이트 ) )        ← 본문이 없으면(GET) 빈 바이트의 해시
기준문자열 = 메서드 + "\n" + 경로와쿼리 + "\n" + Timestamp + "\n" + Nonce + "\n" + 본문해시
서명 = hex( HMAC-SHA256( 비밀값(UTF-8), 기준문자열(UTF-8) ) )
```

- **메서드**: 대문자로 씁니다 (`GET`, `POST`).
- **경로와쿼리**: 실제로 보내는 그대로 씁니다. 예: `/api/epetition/v1/consultations?page=2&q=%EA%B0%80%EB%A1%9C%EB%93%B1`
  - 쿼리는 **퍼센트 인코딩한 그 문자열**로 서명합니다. 서명한 뒤에 HTTP 라이브러리가 쿼리를 다시 인코딩하지 않게 하세요.
  - 쿼리가 없으면 `?`도 붙이지 않습니다.
- **본문**: 서명에 쓴 바이트와 실제로 보내는 바이트가 같아야 합니다. JSON을 서명한 다음 다시 직렬화하지 마세요.

### 서명 예시 (구현 확인용)

| 항목 | 값 |
|---|---|
| 비밀값 | `example-secret-0123456789-abcdefghij` |
| 메서드·경로 | `POST` `/api/epetition/v1/complaints/lookup` |
| Timestamp | `1763280000` |
| Nonce | `3f9c2a7e5b1d4c6f8a0e2b4d6f8a1c3e` |
| 본문 | `{"receiptNo":"2026-000123","password":"abc12345"}` |
| 본문해시 | `b656e7a3e15040730d5f10601d863bf9e5772037078b318a548eb2b38e37c42e` |
| **서명** | `023b7f4b0d86966d4097b5559e801b09d4d2672414b2d895187bf61945e86a2d` |

행정누리의 테스트(`EPetitionSignatureTest`)도 같은 값을 확인합니다.

Python으로 서명하는 예입니다.

```python
import hmac, hashlib, time, uuid
def sign_headers(secret: bytes, method: str, path_and_query: str, body: bytes):
    ts, nonce = str(int(time.time())), uuid.uuid4().hex
    canon = f"{method.upper()}\n{path_and_query}\n{ts}\n{nonce}\n{hashlib.sha256(body).hexdigest()}"
    sig = hmac.new(secret, canon.encode(), hashlib.sha256).hexdigest()
    return {"X-EP-Timestamp": ts, "X-EP-Nonce": nonce, "X-EP-Signature": sig}
```

Node.js로 서명하는 예입니다.

```js
const crypto = require('crypto');
function signHeaders(secret, method, pathAndQuery, body /* Buffer */) {
  const ts = String(Math.floor(Date.now() / 1000)), nonce = crypto.randomUUID().replace(/-/g, '');
  const bodyHash = crypto.createHash('sha256').update(body).digest('hex');
  const canon = `${method.toUpperCase()}\n${pathAndQuery}\n${ts}\n${nonce}\n${bodyHash}`;
  const sig = crypto.createHmac('sha256', secret).update(canon).digest('hex');
  return { 'X-EP-Timestamp': ts, 'X-EP-Nonce': nonce, 'X-EP-Signature': sig };
}
```

인증에 실패하면 아래처럼 응답합니다. 본문은 `{"ok":false,"error":"코드"}`입니다.

| 상태 | error | 뜻 |
|---|---|---|
| 404 | — | 연동이 꺼져 있음 |
| 403 | `forbidden_network` | 허용하지 않은 서버 주소 |
| 401 | `bad_timestamp`, `bad_signature` | 시각·논스·서명이 틀림 |
| 401 | `replayed` | 이미 쓴 논스 |
| 413 | `body_too_large` | 본문이 256KB를 넘음 |
| 403 | `https_required` | HTTPS가 아닌 요청 (`APP_EPETITION_REQUIRE_HTTPS=true`일 때) |

## 4. 응답 형식

- 성공하면 `200 {"ok": true, "data": {...}}`를 돌려줍니다.
- 실패하면 `4xx {"ok": false, "error": "코드", "message": "국민에게 보여 줄 수 있는 한국어 안내"}`를 돌려줍니다.
- 시각은 모두 `yyyy-MM-ddTHH:mm` 꼴의 한국 시각입니다.
- 모든 응답에 `Cache-Control: no-store`가 붙습니다. 전자민원도 국민 화면에 같은 헤더를 붙이세요.

## 5. API

### 5-1. 민원 접수 `POST /api/epetition/v1/complaints`

```json
{
  "kind": "QUERY | SUGGESTION | OTHER | GRIEVANCE",
  "category": "ROAD (민원 분야, 선택: 비우면 ETC)",
  "lawInterpretation": false,
  "name": "홍길동",
  "mobile": "010-1234-5678",
  "homePhone": null,
  "email": "hong@example.com",
  "address": "○○시 중앙로 1",
  "fax": null,
  "notifyMethod": "문자 | 이메일 | 팩스 | 우편 | 방문 수령",
  "title": "가로등 고장",
  "content": "중앙로 가로등이 꺼져 있습니다.",
  "password": "abc12345",
  "publicConsent": true,
  "privacyConsent": true,
  "files": [ { "name": "현장 사진.jpg", "contentBase64": "/9j/4AAQ..." } ]
}
```

| 항목 | 규칙 |
|---|---|
| `category` | 민원 분야. `ROAD`(도로·교통) `PARKING`(주차) `TRANSIT`(대중교통) `CLEAN`(환경·청소) `WATER`(상하수도) `PARK`(공원·녹지) `BUILDING`(주택·건축) `LAND`(도시계획·토지) `WELFARE`(복지) `HEALTH`(보건·위생) `TAX`(세무) `SAFETY`(안전·재난) `CULTURE`(문화·체육·관광) `ECONOMY`(경제·일자리) `ANIMAL`(동물) `ADMIN`(행정·민원 서비스) `ETC`(기타). 목록은 `GET /info`의 `categories`. 틀린 값은 `422 invalid_category`, 비우면 `ETC`. 민원 종류(`kind`)는 법이 정한 것이라 그대로이고, 분야는 담당 부서를 찾는 데 씁니다 |
| `kind` | 질의(QUERY)·건의(SUGGESTION)·기타(OTHER)·고충(GRIEVANCE)만 받습니다. **법정민원(LEGAL)은 받지 않습니다.** 구비서류와 수수료가 있어 창구나 정부24로 안내하세요 |
| `lawInterpretation` | 질의민원일 때 법령 해석 질의면 `true`(처리기간 14일), 제도·절차 질의면 `false`(7일) |
| `name` | 필수, 100자 이내 |
| `mobile` | 휴대전화(010·011·016~019). 하이픈·띄어쓰기는 있어도 없어도 되고 `010-1234-5678` 꼴로 저장합니다. 없으면 `homePhone`을 넣습니다. **휴대전화가 없으면 문자 통지를 고를 수 없습니다** |
| `homePhone` | 집·사무실 전화: 지역번호(02, 031~064), 070, 0502~0508, 대표번호(15xx·16xx·18xx), 휴대전화 |
| `email` | 이메일 형식, 200자 이내. 통지 방법이 이메일이면 필수 |
| `fax` | 통지 방법이 팩스면 필수. `homePhone`과 같은 꼴 (예: `02-123-4567`, `0505-123-4567`) |
| `notifyMethod` | 비우면 휴대전화가 있을 때 문자, 없으면 이메일로 정합니다 |
| `title` / `content` | 필수. 제목 300자, 내용 20,000자 이내 |
| `password` | 처리상황 조회 비밀번호. **영문과 숫자를 섞어 8~20자**. 행정누리에 BCrypt로만 저장하고, 전자민원은 저장하지 않습니다 |
| `publicConsent` | 처리 결과를 공개 상담으로 공개해도 되는지에 대한 민원인 동의. 동의해도 담당 공무원이 개인정보를 확인하고 게시해야 공개됩니다 |
| `files` | (선택) 첨부 **3개까지**, 하나에 **5MB까지**, 사진(jpg·jpeg·png)·PDF·한글(hwp·hwpx)만. `contentBase64`는 파일 내용을 Base64로. 확장자뿐 아니라 실제 내용(파일 머리)도 검사해 형식을 속이면 거절하고, **하나라도 문제면 민원 전체를 접수하지 않습니다**(`422 invalid_file`, message에 파일 이름). 접수되면 행정누리 민원의 구비서류로 들어갑니다. 이 요청만 본문을 21MB까지 받습니다 |
| `privacyConsent` | 개인정보 수집·이용 동의. **`true`가 아니면 접수하지 않습니다** |

응답 예는 다음과 같습니다.

```json
{"ok":true,"data":{"receiptNo":"2026-000123","kind":"OTHER","kindLabel":"기타민원",
 "receivedAt":"2026-11-16T10:05","deadlineAt":"2026-11-16T13:05"}}
```

- 처리기한은 행정누리가 계산합니다. 기준은 민원처리법 시행령 제14~17조와 근무시간·공휴일입니다. 전자민원이 따로 계산하지 마세요.
- 접수되면 행정누리의 창구 직원과 관리자에게 "담당을 배정하라"는 알림이 갑니다.
- 오류 코드는 `invalid_kind`, `invalid_password`, `invalid_content`, `invalid_input`(message에 이유), `bad_request`(JSON 오류)입니다.

### 5-2. 처리상황 조회 `POST /api/epetition/v1/complaints/lookup`

비밀번호가 주소나 로그에 남지 않도록 POST로 보냅니다.

```json
{"receiptNo": "2026-000123", "password": "abc12345"}
```

응답 예는 다음과 같습니다.

```json
{"ok":true,"data":{
  "receiptNo":"2026-000123","kindLabel":"기타민원","title":"가로등 고장","content":"…","name":"홍길동",
  "status":"DONE","statusLabel":"처리 완료","receivedAt":"…","deadlineAt":"…","dept":"도로과",
  "steps":[{"step":"접수","at":"…"},{"step":"담당 배정","at":"…"},{"step":"처리 완료","at":"…"}],
  "result":{"type":"답변","text":"가로등을 고쳤습니다.","remedy":null,"completedAt":"…","notifiedBy":"문자"}
}}
```

- `status`는 `RECEIVED`(접수), `IN_PROGRESS`(처리 중), `DONE`(처리 완료) 가운데 하나입니다. `result`는 처리가 끝났을 때만 들어 있습니다.
- `steps`의 `step` 값은 접수, 담당 배정, 처리기간 연장, 진행상황 통지, 처리 완료입니다. **담당 공무원 이름과 내부 메모는 보내지 않습니다.**
- 불수용·일부 수용이면 `result.remedy`에 거부 이유와 구제절차(행정심판·소송 안내)가 들어 있습니다(민원처리법 제27조 제3항). 국민 화면에 꼭 보여 주세요.
- 오류 응답은 다음과 같습니다.
  - `401 not_matched`: 접수번호가 없거나 비밀번호가 틀림. 둘을 구별하지 않습니다.
  - `423 locked`: 같은 접수번호로 비밀번호를 **5번 틀리면 30분** 동안 맞는 비밀번호도 거절합니다. message에 풀리는 시각이 있습니다.
- `429 too_many_attempts`: 같은 국민 IP(`X-EP-Client-IP`)가 **10분 동안 10번** 틀리면(접수번호가 달라도) 그 IP의 조회를 잠시 막습니다. 여러 접수번호에 흔한 비밀번호를 넣어 보는 공격을 막기 위한 것입니다.
- 전자민원 쪽에서도 국민 IP별 조회 횟수 제한과 자동입력 방지(CAPTCHA 등)를 꼭 두세요. 행정누리의 잠금은 접수번호 단위입니다.

### 5-3. 공개 상담 목록 `GET /api/epetition/v1/consultations?page=1&q=검색어&kind=QUERY&category=ROAD&sort=likes`

- 대상은 세 가지가 모두 맞는 민원입니다: 처리 완료, 민원인이 공개에 동의함, 담당 공무원이 게시함.
- 한 쪽에 20건씩이고, `q`는 제목·내용에서 찾습니다(100자 이내).
- `kind`(QUERY·SUGGESTION·GRIEVANCE·OTHER)로 종류를, `category`로 분야를 거르고, `sort`는 `recent`(기본, 최근 처리순)·`views`·`likes`입니다.
- 항목마다 `kind`, `category`·`categoryLabel`, `views`(조회수), `likes`(공감 수)가 더 붙습니다. 취하한 민원은 나오지 않습니다.

```json
{"ok":true,"data":{"total":42,"page":1,"totalPages":3,"items":[
  {"id":123,"receiptNo":"2026-000123","kindLabel":"기타민원","title":"가로등 고장","name":"홍*동",
   "receivedAt":"…","completedAt":"…","dept":"도로과"}]}}
```

이름은 가려서 보냅니다(`홍*동`, `김*`). 연락처·주소는 보내지 않습니다.

### 5-4. 공개 상담 한 건 `GET /api/epetition/v1/consultations/{id}`

5-2와 같은 꼴입니다(`category`·`categoryLabel` 포함, 단 `actionToken` 등 본인용 값은 없음). 이름은 가려져 있고, 게시되지 않은 건이면 `404 not_found`를 돌려줍니다.
더 붙는 값: `views`, `likes`, `liked`(이 `X-EP-Client-IP`가 이미 공감했는지), `related`(같은 종류의 다른 공개 상담 5건 `{id,title,completedAt}`).
조회수는 같은 `X-EP-Client-IP`가 같은 글을 하루에 여러 번 열어도 한 번만 셉니다.

### 5-4-1. 공감 `POST /api/epetition/v1/consultations/{id}/like` (본문 `{}`)

"도움이 됐어요". 같은 `X-EP-Client-IP`는 한 번만 셉니다. IP는 그대로 남기지 않고 서명 비밀값을 섞은 SHA-256 값으로만 저장합니다.
응답 `{"added":true,"likes":3}` (이미 눌렀으면 `added:false`). 게시되지 않은 글이면 `404 not_found`.

### 5-5. 행정처분 공개 `GET /api/epetition/v1/dispositions?page=1&q=상호또는주소`

식품위생법·공중위생관리법 위반 업소의 행정처분 가운데, 담당 공무원이 공개로 고른 **유효한**(취소되지 않은) 처분만 보냅니다. 범위는 **처분일부터 1년**이고, 한 쪽에 20건씩입니다.

```json
{"ok":true,"data":{"total":3,"page":1,"totalPages":1,"items":[
  {"decidedOn":"2026-11-10","bizName":"○○식당","bizType":"일반음식점영업","address":"○○시 중앙로 1",
   "law":"식품위생법","kind":"영업정지","startDate":"2026-11-20","endDate":"2026-12-04","amount":null,
   "violation":"유통기한 경과 제품 보관","basis":"식품위생법 제75조"}]}}
```

`kind`는 시정명령, 영업정지, 일부 시설 사용중지, 영업소 폐쇄, 허가·등록 취소, 과징금, 과태료 가운데 하나입니다. 대표자 이름은 보내지 않습니다.

### 5-6. 민원 안내·통계·공지·자주 묻는 질문 (읽기)

| 요청 | 응답 `data` |
|---|---|
| `GET /info` | `kinds`: `[{kind,label,description,period,online}]` (online=false 는 법정민원), `categories`: `[{category,label,examples}]`, `maxAddenda` |
| `GET /stats` | 최근 12개월: `received`, `done`, `withdrawn`, `online`, `avgDays`, `onTimeRate`(%), `satisfactionAvg`, `satisfactionCount`, `satisfactionDist`(1~5점 명수), `months[{month,received,done}]`, `kinds[{kind,label,received,done}]`, `categories[{category,label,received,done}]`(건수가 있는 분야만). 숫자만, 개인정보 없음. 취하는 처리 건수·평균에서 뺌 |
| `GET /notices?page=1&q=` | `{total,page,totalPages,items:[{id,title,pinned,views,createdAt}]}` (고정 먼저) |
| `GET /notices/{id}` | `{id,title,content,pinned,views,createdAt,updatedAt}` (없으면 404). 조회수는 5-4와 같은 방식 |
| `GET /faqs` | `{categories:[{category,items:[{id,question,answer}]}]}` (보이기로 한 것만) |

공지사항·자주 묻는 질문은 민원 창구 직원·관리자가 행정누리 **업무 메뉴 › 전자민원 운영**(`/epetition`)에서 씁니다. 내용은 글자로만 보여 주세요.

### 5-7. 민원인이 하는 일: 추가 의견·취하·만족도

5-2 조회(또는 5-8 내 민원 한 건)에 성공하면 본인용 값이 더 붙습니다.

```json
{"actionToken":"(32자)","canWithdraw":true,"canAddendum":true,"canRate":false,
 "satisfaction":{"score":5,"comment":"…","at":"…"}}
```

`actionToken`은 **20분** 동안만 쓰며 행정누리 메모리에만 있습니다(다시 켜면 없어짐). 비밀번호를 다시 보내지 않도록 이 값으로만 아래를 요청합니다.
지나면 `401 token_expired` → 다시 조회하게 안내하세요. `steps`에는 `민원인 추가 의견`·`민원 취하`가 나오고, 본인 조회 때만 `text`(민원인이 쓴 글)가 붙습니다.

| 요청 | 본문 | 조건·오류 |
|---|---|---|
| `POST /complaints/addendum` | `{"token","text"}` (1,000자) | 처리 완료 전, 5번까지. `409 already_done`·`409 too_many`·`422 invalid_input`. 처리 이력에 남고 담당자(없으면 창구 직원)에게 알림 |
| `POST /complaints/withdraw` | `{"token","reason"}` (500자, 선택) | 처리 완료 전만(`409 already_done`). 처리 완료(결과 구분 `취하`)가 되며 공개 상담에 게시할 수 없음. 담당자에게 알림 |
| `POST /complaints/satisfaction` | `{"token","score":1~5,"comment"}` (1,000자) | 처리 완료(취하 제외) 뒤 한 번만(`409 cannot_rate`). 직원 민원 상세와 전자민원 운영 화면에 보임 |

### 5-8. 회원

실명 확인(본인인증)은 하지 않는 아이디·비밀번호 회원입니다. `memberId`는 **전자민원이 자기 로그인 세션에서 꺼내 넣습니다**(국민이 보낸 값을 그대로 넣지 마세요). 이 API는 서명된 전자민원 서버만 부를 수 있으므로 행정누리는 `memberId`를 믿습니다.

| 요청 | 본문 / 응답 | 오류 |
|---|---|---|
| `POST /members` | `{"loginId","password","name","mobile","email","address","termsConsent":true,"privacyConsent":true}` → 회원 정보 | `invalid_login_id`(영문 소문자로 시작, 소문자·숫자·`_` 5~20자), `invalid_password`(2종류 10자 이상 또는 3종류 8자 이상, 64자까지, 아이디 포함·같은 글자 4번 연속 안 됨), `login_id_taken`(409), `consent_required`, `invalid_input` |
| `POST /members/login` | `{"loginId","password"}` → 회원 정보 + `previousLoginAt` | `not_matched`(401, 없는 아이디와 같음), 5번 틀리면 30분 `locked`(423) |
| `GET /members/{id}` | 회원 정보 `{memberId,loginId,name,mobile,email,address,createdAt,pwChangedAt}` | `member_not_found`(404) → 로그아웃시키세요 |
| `POST /members/{id}/profile` | `{"name","mobile","email","address"}` | `invalid_input` |
| `POST /members/{id}/password` | `{"currentPassword","newPassword"}` | `wrong_password`, `invalid_password` |
| `POST /members/{id}/delete` | `{"password"}` → `{deleted,complaints}` | 회원 정보는 바로 지우고, 낸 민원은 남기되 연결만 끊음 |
| `GET /members/{id}/complaints?page=1&status=open` | `{total,page,totalPages,items:[{receiptNo,kindLabel,title,status,statusLabel,receivedAt,deadlineAt,completedAt}]}`. `status`(선택): `open` 처리 중(접수 대기 포함), `done` 처리 완료(취하 포함), 없거나 다른 값이면 모두. `q`(선택): 제목에 들어간 말 (50자까지) | |
| `GET /members/check?loginId=` | `{available, reason}` — 가입 화면에서 아이디를 쓸 수 있는지 바로 알려 준다 (글자 규칙·예약어·중복). 쓸 수 있으면 `reason` 은 빈 문자열 | |
| `GET /members/{id}/complaints/{receiptNo}` | 5-2와 같은 꼴 (본인용 값 포함) | 남의 민원이면 404 |

5-1 접수에 `"memberId": 7`을 넣으면 회원 민원이 되며, 이때 `password`는 빼도 됩니다(넣으면 비회원 조회도 됨).
비밀번호는 BCrypt로만 저장합니다. 가입·로그인·정보 변경·탈퇴는 행정누리 접속기록에 남습니다.

### 5-9. 민원인 PC 보안 프로그램 `GET /api/epetition/v1/security-apps`

행정누리 관리자가 **시스템 관리 › 보안 프로그램 안내**에서 대상을 "민원인 PC" 또는 "직원·민원인 모두"로 정한 항목입니다.

```json
{"items":[{"id":12,"name":"키보드 보안","purpose":"입력 내용 보호","guide":"1. 내려받아 실행…","url":"https://…",
           "required":true,"version":2,"checkUrl":"http://127.0.0.1:14440/status"}]}
```

- `required`: `true` 강제(설치하지 않으면 전자민원을 쓰지 못하게), `false` 권고(안내만).
- `version`: 관리자가 "다시 확인 요청"을 누르면 올라갑니다. 민원인의 설치 확인은 `id.version` 으로 기억하고, 판이 바뀌면 다시 받습니다.
- `checkUrl`(없을 수 있음): 프로그램이 민원인 PC에 띄우는 확인 주소로, `127.0.0.1`·`localhost`만 옵니다. 있으면 브라우저가 이 주소에 응답이 오는지 확인한 뒤에만 설치 확인을 받습니다. 그 화면에서만 CSP `connect-src`에 이 주소를 더합니다.
- `url`·`checkUrl`은 형식을 다시 확인하고 쓰세요(`http(s)`만, 확인 주소는 이 PC 주소만).
- **휴대폰·태블릿은 PC 용 프로그램을 설치할 수 없으므로 강제 항목으로 막지 않습니다.**
- 정부 방침은 공공 누리집에서 플러그인을 걷어 내고 보안 프로그램 설치를 이용자가 고르게 하는 것이므로, 민원인에게는 되도록 권고로 둡니다.

## 6. 전자민원 쪽에서 지켜야 할 것

1. **HTTPS만** 씁니다. HSTS를 켜고, 쿠키에 Secure·HttpOnly·SameSite를 설정합니다. 같은 PC가 아닌 곳에서 `http://`로 오면 받지 않습니다(이 서버가 인증서로 켜졌거나 앞단 프록시가 HTTPS를 받을 때만 `https://`로 넘김).
2. **개인정보 수집·이용 동의**를 받습니다. 수집 항목, 목적, 보유기간, 동의 거부 권리와 불이익을 화면에 알리고 동의한 경우에만 `privacyConsent: true`로 보냅니다(개인정보 보호법 제15조). 보유기간은 기관 기준(행정누리의 민원 기록 보존기간)과 맞춥니다.
3. **조회 비밀번호를 저장하지 않습니다.** 전자민원 로그에 요청 본문을 남기지 마세요. 비밀번호와 연락처가 들어 있습니다.
4. 국민 IP별 요청 횟수 제한과 자동입력 방지를 둡니다. 특히 접수와 조회에 필요합니다.
5. 서명 비밀값은 환경변수나 비밀값 저장소에만 둡니다. 유출이 의심되면 양쪽 값을 같이 바꿉니다.
6. 화면에 보여 주는 민원 내용과 처리 결과는 **HTML로 해석하지 않고 글자로만** 출력합니다(XSS 방지).
7. 처리 결과의 `remedy`(거부 이유·구제절차)는 빠뜨리지 않고 보여 줍니다.
8. 접수 완료 화면에 접수번호, 처리기한, "비밀번호를 잊으면 조회할 수 없으니 기관에 문의하세요" 안내를 보여 줍니다.

## 7. 아직 없는 것 (필요하면 행정누리 쪽에 요청)

- **법정민원 신청**: 구비서류와 수수료가 있어 정부24 연계 대상입니다.
- **본인확인**(휴대폰·공동인증): 행정누리는 본인확인 결과를 받지 않습니다. 회원도 실명 확인 없이 가입합니다. 붙이게 되면 확인 결과(CI 등)를 넘길 필드를 협의해야 합니다.
- **회원 아이디·비밀번호 찾기**: 본인확인 수단이 없어 인터넷으로는 못 합니다. 기관에 문의하게 안내합니다.
- **조회 비밀번호 재설정**: 민원인이 기관에 연락하면, 직원이 행정누리 민원 상세에서 본인을 확인한 뒤 새 임시 비밀번호(영문 6 + 숫자 4)를 만들어 알려 줍니다(잠금도 풀림). 인터넷으로 스스로 재설정하는 기능은 없습니다(본인확인 수단이 없어서).
