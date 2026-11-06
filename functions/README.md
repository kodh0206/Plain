# Plain Cloud Functions

Plain 차량 캘린더의 서버 측 일정 CRUD를 담당합니다. Android 클라이언트가 전달한
Firebase Authentication UID를 차량 식별자로 사용하며, 모든 문서는
`users/{uid}/schedules/{scheduleId}` 아래에 저장합니다.

## 제공 함수

- `createSchedule`: 요청 ID를 문서 ID로 사용하는 중복 방지 일정 생성
- `getSchedules`: 최대 366일 범위 일정 목록 조회
- `getSchedule`: 단일 일정 조회
- `updateSchedule`: 예상 리비전이 일치할 때만 일정 부분 수정
- `deleteSchedule`: 예상 리비전이 일치할 때만 일정 삭제

일정은 제목·설명·시작/종료 시각·IANA 시간대 ID·종일 여부·주소/위도/경도·태그와
`revision`을 포함합니다. 수정 충돌은 callable `aborted` 오류로 반환합니다.

모든 함수는 인증된 callable 요청만 허용합니다. `firestore.rules`는 Android 앱의
Firestore 직접 접근을 차단하고, Functions의 Admin SDK만 데이터에 접근합니다.

## 로컬 요구사항

- Node.js 22
- Java 17 이상
- npm

Firebase CLI 15로 올릴 때는 Java 21 이상을 사용해야 합니다. 현재 프로젝트는 Java 17에서
검증된 Firebase CLI 14.x를 로컬 개발 의존성으로 고정했습니다.

## 설치와 검증

프로젝트 루트에서 다음 명령을 실행합니다.

```powershell
npm --prefix functions install
npm --prefix functions run test:unit
npm --prefix functions run build
.\functions\node_modules\.bin\firebase.cmd emulators:exec --project plain-local --only auth,firestore,functions "npm --prefix functions run test:emulator:run"
```

`.firebaserc`의 기본 프로젝트는 실제 Firebase 프로젝트가 아닌 `plain-local`입니다.
따라서 위 명령은 Auth, Firestore, Functions Emulator만 사용하고 운영 데이터에는 접근하지
않습니다. 실제 배포는 Blaze 요금제와 별도의 배포 승인 후 진행합니다.

Android 디버그 빌드는 `10.0.2.2`의 Auth(9099)와 Functions(5001) Emulator에만
연결합니다. Android와 수동 연동 검증을 할 때는 `google-services.json`의 프로젝트 ID로
Emulator를 시작해야 callable URL의 프로젝트 경로가 일치합니다. 릴리스 빌드에는 이 로컬
호스트 및 평문 HTTP 허용 설정이 포함되지 않습니다.
