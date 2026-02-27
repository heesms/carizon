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

## 앱 아이콘 변경하기

`mobile/carizon_app/lib/main.dart`의 로고 변경은 앱 내부 UI만 바뀌고, 런처(홈 화면) 아이콘은 따로 교체해야 합니다.

- 안드로이드: `android/app/src/main/res/mipmap-* /ic_launcher.png` 교체
- iOS: `ios/Runner/Assets.xcassets/AppIcon.appiconset/Contents.json`에 맞는 각 사이즈 아이콘 교체
- 교체 후에는 `flutter clean` + 앱 삭제 후 재설치 필요

빠르게 쓰려면 프로젝트에 `flutter_launcher_icons`를 추가해 자동 생성할 수 있습니다.

현재 `carizon-frontend/public/app_icon.png`를 `assets/images/carizon_app_icon.png`로 교체해
웹뷰 오버레이 로고 및 런처 아이콘 반영 대상으로 사용하도록 설정했습니다.

런처 아이콘도 동일한 파일을 쓰려면 `.ico` → `.png`로 변환 후 각 사이즈로 배치해야 합니다.
Windows PowerShell 기준 예시:

```powershell
Add-Type -AssemblyName System.Drawing
$icon = [System.Drawing.Icon]::ExtractAssociatedIcon("C:\git\carizon\carizon\carizon-frontend\public\favicon.ico")
for ($size in 48,72,96,144,192,512) {
  $bmp = $icon.ToBitmap()
  $output = "C:\git\carizon\carizon\mobile\carizon_app\android\app\src\main\res\mipmap-\$(
    switch($size){
      192 {'xxhdpi'}
      144 {'xxxhdpi'}
      96  {'xhdpi'}
      72  {'hdpi'}
      48  {'mdpi'}
      default {'mdpi'}
    }
  )\ic_launcher.png"
  $bmp.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
}
```

실운용은 `iconutil`/온라인 변환기로 1024/180/120/60/40/20 px 등 각 해상도로 정확히 뽑는 게 더 안정적입니다.

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
