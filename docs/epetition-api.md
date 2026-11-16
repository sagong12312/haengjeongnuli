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
  "privacyConsent": true
}
```

| 항목 | 규칙 |
|---|---|
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

### 5-3. 공개 상담 목록 `GET /api/epetition/v1/consultations?page=1&q=검색어`

- 대상은 세 가지가 모두 맞는 민원입니다: 처리 완료, 민원인이 공개에 동의함, 담당 공무원이 게시함.
- 한 쪽에 20건씩이고, `q`는 제목·내용에서 찾습니다(100자 이내).

```json
{"ok":true,"data":{"total":42,"page":1,"totalPages":3,"items":[
  {"id":123,"receiptNo":"2026-000123","kindLabel":"기타민원","title":"가로등 고장","name":"홍*동",
   "receivedAt":"…","completedAt":"…","dept":"도로과"}]}}
```

이름은 가려서 보냅니다(`홍*동`, `김*`). 연락처·주소는 보내지 않습니다.

### 5-4. 공개 상담 한 건 `GET /api/epetition/v1/consultations/{id}`

5-2와 같은 꼴입니다. 이름은 가려져 있고, 게시되지 않은 건이면 `404 not_found`를 돌려줍니다.

### 5-5. 행정처분 공개 `GET /api/epetition/v1/dispositions?page=1&q=상호또는주소`

식품위생법·공중위생관리법 위반 업소의 행정처분 가운데, 담당 공무원이 공개로 고른 **유효한**(취소되지 않은) 처분만 보냅니다. 범위는 **처분일부터 1년**이고, 한 쪽에 20건씩입니다.

```json
{"ok":true,"data":{"total":3,"page":1,"totalPages":1,"items":[
  {"decidedOn":"2026-11-10","bizName":"○○식당","bizType":"일반음식점영업","address":"○○시 중앙로 1",
   "law":"식품위생법","kind":"영업정지","startDate":"2026-11-20","endDate":"2026-12-04","amount":null,
   "violation":"유통기한 경과 제품 보관","basis":"식품위생법 제75조"}]}}
```

`kind`는 시정명령, 영업정지, 일부 시설 사용중지, 영업소 폐쇄, 허가·등록 취소, 과징금, 과태료 가운데 하나입니다. 대표자 이름은 보내지 않습니다.

## 6. 전자민원 쪽에서 지켜야 할 것

1. **HTTPS만** 씁니다. HSTS를 켜고, 쿠키에 Secure·HttpOnly·SameSite를 설정합니다.
2. **개인정보 수집·이용 동의**를 받습니다. 수집 항목, 목적, 보유기간, 동의 거부 권리와 불이익을 화면에 알리고 동의한 경우에만 `privacyConsent: true`로 보냅니다(개인정보 보호법 제15조). 보유기간은 기관 기준(행정누리의 민원 기록 보존기간)과 맞춥니다.
3. **조회 비밀번호를 저장하지 않습니다.** 전자민원 로그에 요청 본문을 남기지 마세요. 비밀번호와 연락처가 들어 있습니다.
4. 국민 IP별 요청 횟수 제한과 자동입력 방지를 둡니다. 특히 접수와 조회에 필요합니다.
5. 서명 비밀값은 환경변수나 비밀값 저장소에만 둡니다. 유출이 의심되면 양쪽 값을 같이 바꿉니다.
6. 화면에 보여 주는 민원 내용과 처리 결과는 **HTML로 해석하지 않고 글자로만** 출력합니다(XSS 방지).
7. 처리 결과의 `remedy`(거부 이유·구제절차)는 빠뜨리지 않고 보여 줍니다.
8. 접수 완료 화면에 접수번호, 처리기한, "비밀번호를 잊으면 조회할 수 없으니 기관에 문의하세요" 안내를 보여 줍니다.

## 7. 아직 없는 것 (필요하면 행정누리 쪽에 요청)

- **첨부파일 접수**: 지금은 글로만 접수합니다. 붙임이 필요하면 업로드 API를 따로 만들어야 합니다(확장자·실제 형식 검사 포함).
- **법정민원 신청**: 구비서류와 수수료가 있어 정부24 연계 대상입니다.
- **본인확인**(휴대폰·공동인증): 행정누리는 본인확인 결과를 받지 않습니다. 붙이게 되면 확인 결과(CI 등)를 넘길 필드를 협의해야 합니다.
- **조회 비밀번호 재설정**: 지금은 기관에 문의하는 방식입니다.
