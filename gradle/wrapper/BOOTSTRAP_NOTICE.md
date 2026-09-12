# Gradle wrapper bootstrap note

`gradle-wrapper.jar` in this generated project is a small clean-room bootstrap compiled from `bootstrap-src/GradleWrapperMain.java`.
The generation sandbox had no outbound DNS access, so it could not fetch Gradle's official wrapper JAR. The bootstrap reads the standard `gradle-wrapper.properties`, downloads Gradle 9.4.1 on the developer machine, verifies the official binary distribution SHA-256, extracts it under `GRADLE_USER_HOME`, and launches Gradle.

For a completely standard Gradle wrapper, after Gradle 9.4.1 is available run:

```text
gradle wrapper --gradle-version 9.4.1 --distribution-type bin
```

Then commit the official regenerated `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, and `gradlew.bat` over these bootstrap files.
