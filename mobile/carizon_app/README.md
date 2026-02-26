# Carizon App (Flutter)

`https://carizon.shop/`를 Android/iOS WebView로 제공하는 Carizon 모바일 앱입니다.

## 구현 기능

- 앱 시작 시 `https://carizon.shop/` 로드
- Android 뒤로가기: WebView 히스토리 뒤로가기, 없으면 앱 종료
- 내부 링크(`carizon.shop`): 앱 내 WebView 이동
- 외부 링크: 시스템 브라우저로 열기
- 상단 로딩 진행바(`LinearProgressIndicator`)
- 네트워크 끊김 배너 + 새로고침 버튼
- 설정 화면
- 앱 버전 표시
- 웹 캐시/쿠키 삭제
- 문의 링크 열기
- 현재 페이지 공유

## 프로젝트 정보

- 앱 이름: `Carizon`
- Android 패키지명: `com.carizon.app`
- Android minSdk: `24`
- iOS 최소 버전: `13.0`

## 디렉터리 구조

```text
mobile/carizon_app/
  lib/
    main.dart
    screens/
      settings_screen.dart
  android/
  ios/
  pubspec.yaml
```

## 로컬 실행

```bash
cd mobile/carizon_app
flutter pub get
flutter run
```

## Android 빌드

```bash
cd mobile/carizon_app
flutter build apk --release
flutter build appbundle --release
```

## iOS 빌드

```bash
cd mobile/carizon_app
flutter build ios --release
```

iOS 스토어 배포(Archive)는 Xcode에서 `ios/Runner.xcworkspace`를 열어 진행하세요.
