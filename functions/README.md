# Plain Cloud Functions

Plain 차량 캘린더의 서버 측 일정 CRUD를 담당합니다. Android 클라이언트가 전달한
Firebase Authentication UID를 차량 식별자로 사용하며, 모든 문서는
`vehicles/{uid}/events/{eventId}` 아래에 저장합니다.

## 제공 함수

- `createCalendarEvent`: 일정 생성
- `getCalendarEvents`: 최대 366일 범위 일정 조회
- `updateCalendarEvent`: 일정 부분 수정
- `deleteCalendarEvent`: 일정 삭제

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
