# Android 배포 (Fastlane)

Jetpack Compose 네이티브 앱을 Google Play에 올리는 fastlane 설정입니다.
(Flutter가 아니므로 `flutter build`는 쓰지 않고 `./gradlew bundleRelease`로 AAB를 만듭니다.)

## 사전 준비 (최초 1회)

### 1. fastlane 설치
```bash
cd snippet_android
bundle install        # Gemfile 기반, 권장
# 또는: brew install fastlane
```

### 2. 업로드 키스토어 (snippet_app 것을 반드시 재사용)
패키지명이 `com.gowoobro.snippet`로 snippet_app과 **동일한 Play 앱**입니다. 따라서 **새 키스토어를 만들면 안 되고**, snippet_app이 쓰던 업로드 키스토어(`.jks`)를 그대로 써야 Play가 AAB를 받습니다.

- snippet_app은 `snippet_app/android/key.properties`(gitignore)에서 `storeFile`/`storePassword`/`keyAlias`/`keyPassword`를 읽어 서명했습니다. **키 항목 이름이 이 프로젝트의 `keystore.properties`와 동일**합니다.
- snippet_app의 `key.properties`와 참조하던 `.jks`를 가져와, 이 프로젝트 루트(`snippet_android/`)에 `keystore.properties`로 두면 됩니다:

```properties
storeFile=/절대경로/또는/snippet_android기준/상대경로/upload-keystore.jks
storePassword=****
keyAlias=upload
keyPassword=****
```

> 이 머신에는 원본 `.jks`/`key.properties`가 없습니다(커밋 금지 정책). snippet_app을 배포하던 곳에서 가져오세요.
> `keystore.properties`가 없으면 release 빌드는 debug 키로 서명됩니다(로컬 실행은 되지만 Play 업로드는 불가).

### 3. Play Console 서비스 계정 키 (snippet_app 것 그대로 재사용)
같은 Play 앱이므로 snippet_app이 쓰던 서비스 계정 JSON을 그대로 `fastlane/google-play-key.json`으로 두면 됩니다. (gitignore 처리됨)

## 버전 관리

버전은 `app/build.gradle.kts`의 `defaultConfig`에 저장됩니다.
- `versionCode` → 업로드마다 반드시 +1 되는 정수
- `versionName` → 표시 버전 (예: `1.0.0`)

각 lane이 자동으로 bump하므로 수동 수정은 필요 없습니다.

> **버전 연속성**: 이 앱은 snippet_app이 배포하던 것과 같은 Play 앱입니다. 현재 값은 snippet_app의 마지막 배포(versionName `1.0.19`, versionCode `25`)에 맞춰져 있어, `fastlane deploy` 시 `1.0.20` / versionCode `26`으로 이어집니다. Play는 이전보다 높은 versionCode만 받으므로 이 정렬을 유지하세요.

## 배포 명령

| 명령 | 동작 |
|------|------|
| `make build` / `fastlane build` | 서명된 AAB만 빌드 (업로드 없음) |
| `make internal` / `fastlane internal` | versionCode +1 → 내부 테스트 트랙 (draft) |
| `make alpha` / `fastlane alpha` | versionCode +1 → 알파(클로즈드) 트랙 |
| `make deploy` / `fastlane deploy` | patch 버전 +1, versionCode +1 → 프로덕션 |

## 릴리즈 노트

lane이 아래 우선순위로 노트를 결정해 `metadata/ko-KR/changelogs/{versionCode}.txt`에 쓰고 업로드합니다.

1. **명령줄 직접 지정**: `make deploy NOTES="- 새 기능"` 또는 `fastlane deploy notes:"- 새 기능"`
2. **changelog 파일**: `fastlane/changelogs/{versionName}.txt`가 있으면 그 내용 사용
3. **git 커밋 자동 생성**: 마지막 릴리즈 태그(`v*`) 이후의 커밋 제목에서 자동 생성 (chore/ci/docs/test 등 비기능 커밋 제외, conventional commit 접두사 제거, 500자 제한)
4. **기본 문구**: 위가 모두 없으면 "버그 수정 및 성능 개선"

업로드 성공 시 `v{versionName}-{versionCode}` 로컬 태그가 자동 생성되어, 다음 배포의 자동 노트는 그 태그 이후 커밋만 포함합니다. (태그는 push되지 않으므로 필요하면 `git push --tags`)
