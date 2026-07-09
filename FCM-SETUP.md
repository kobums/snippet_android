# FCM 푸시 알림 설정 가이드

## 개요

이 프로젝트는 `google-services.json` 파일 **없이도 빌드가 통과**되도록 구현되어 있습니다.
파일을 `app/` 폴더에 넣으면 FCM이 자동으로 활성화됩니다.

## 설정 방법

### 1. Firebase 콘솔에서 설정 파일 받기

1. [Firebase Console](https://console.firebase.google.com/) 접속
2. 프로젝트 `snippet-164cd` 선택 (또는 신규 생성)
3. 프로젝트 설정 → 일반 탭 → "내 앱" 섹션
4. Android 앱 추가 (패키지명: `com.gowoobro.snippet`)
5. `google-services.json` 다운로드

### 2. 파일 배치

```
snippet_android/
└── app/
    └── google-services.json   ← 여기에 넣기
```

### 3. 빌드

파일을 넣은 후 평소와 동일하게 빌드하면 FCM이 자동으로 활성화됩니다.

```bash
./gradlew assembleDebug
```

## 동작 원리

- `app/build.gradle.kts`에서 `google-services.json` 존재 여부를 확인해 플러그인을 조건부 적용합니다.
- 파일이 없으면 `com.google.gms.google-services` 플러그인이 적용되지 않아 Firebase 초기화가 건너뛰어집니다.
- 파일이 있으면 플러그인이 적용되어 Firebase가 앱 시작 시 자동으로 초기화됩니다.
- `FcmTokenManager`는 FirebaseApp 초기화 실패 예외를 try-catch로 잡아 조용히 무시합니다.

## 구현된 기능

| 기능 | 파일 |
|------|------|
| 토큰 갱신 시 서버 재등록 | `fcm/SnippetMessagingService.kt` - `onNewToken` |
| 포그라운드 알림 표시 | `fcm/SnippetMessagingService.kt` - `onMessageReceived` |
| 토큰 조회 및 서버 등록 | `fcm/FcmTokenManager.kt` |
| 로그인 성공 시 토큰 등록 | `MainActivity.kt` |
| Android 13+ 알림 권한 요청 | `MainActivity.kt` |

## 서버 API

로그인 후 FCM 토큰은 다음 엔드포인트로 등록됩니다.

```
POST /api/users/fcmtoken
Authorization: Bearer {accessToken}
Content-Type: application/json

{"fcmToken": "..."}
```
