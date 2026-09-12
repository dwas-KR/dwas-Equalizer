# Building dwas_EQ

## Requirements

- JDK 17
- Android SDK API 37
- Gradle Wrapper included in the repository
- Internet access for the first Gradle/dependency download

Project values in v0.4.0:

- Application ID / namespace: `kr.dwas.dwas_EQ`
- minSdk: 33
- targetSdk: 37
- compileSdk: 37
- versionCode: 9
- versionName: 0.4.0
- Android Gradle Plugin: 9.2.1
- Gradle: 9.4.1

## Windows

```bat
gradlew.bat clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## Linux / macOS

```bash
chmod +x gradlew
./gradlew clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## Output

Debug builds are normally produced below `app/build/outputs/apk/debug/`.

Public release APK signing is intentionally not automated by this repository package. Keep release keystores and passwords outside the repository and never commit them.
