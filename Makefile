.PHONY: build internal alpha deploy

# 서명된 AAB만 빌드
build:
	fastlane build

# versionCode +1 → 내부 테스트 트랙 업로드 (draft)
# 릴리즈 노트 직접 지정: make internal NOTES="- 새 기능 추가"
internal:
	fastlane internal notes:"$(NOTES)"

# versionCode +1 → 알파(클로즈드) 트랙 업로드
alpha:
	fastlane alpha notes:"$(NOTES)"

# patch 버전 +1, versionCode +1 → 프로덕션 업로드
deploy:
	fastlane deploy notes:"$(NOTES)"
