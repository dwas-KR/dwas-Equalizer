package org.gradle.wrapper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;







public final class GradleWrapperMain {
    private GradleWrapperMain() {}

    public static void main(String[] args) throws Exception {
        Path jar = Path.of(GradleWrapperMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path projectRoot = jar.getParent().getParent().getParent();
        Path propsPath = projectRoot.resolve("gradle/wrapper/gradle-wrapper.properties");
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(propsPath.toFile())) {
            props.load(in);
        }

        String distributionUrl = required(props, "distributionUrl");
        String expectedSha = required(props, "distributionSha256Sum").toLowerCase();
        int timeout = Integer.parseInt(props.getProperty("networkTimeout", "10000"));
        Path gradleUserHome = resolveGradleUserHome();
        String distName = distributionFileName(distributionUrl).replace("-bin.zip", "").replace("-all.zip", "");
        Path cacheRoot = gradleUserHome.resolve("wrapper/dists/dwas_EQ").resolve(distName + "-" + expectedSha.substring(0, 12));
        Path marker = cacheRoot.resolve(".verified");

        if (!Files.isRegularFile(marker)) {
            Files.createDirectories(cacheRoot);
            Path zip = cacheRoot.resolve("distribution.zip");
            download(URI.create(distributionUrl).toURL(), zip, timeout);
            String actualSha = sha256(zip);
            if (!actualSha.equalsIgnoreCase(expectedSha)) {
                Files.deleteIfExists(zip);
                throw new SecurityException("Gradle distribution SHA-256 mismatch. expected=" + expectedSha + " actual=" + actualSha);
            }
            unzip(zip, cacheRoot);
            Files.deleteIfExists(zip);
            Files.writeString(marker, actualSha + System.lineSeparator());
        }

        Path gradleHome = Files.list(cacheRoot)
                .filter(Files::isDirectory)
                .filter(p -> p.getFileName().toString().startsWith("gradle-"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Extracted Gradle home not found under " + cacheRoot));
        int exit = launchGradle(projectRoot, gradleHome, args);
        if (exit != 0) System.exit(exit);
    }

    private static int launchGradle(Path projectRoot, Path gradleHome, String[] args) throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        java.util.List<String> command = new java.util.ArrayList<>();
        if (windows) {
            command.add("cmd.exe");
            command.add("/d");
            command.add("/c");
            command.add(gradleHome.resolve("bin/gradle.bat").toString());
        } else {
            Path executable = gradleHome.resolve("bin/gradle");
            executable.toFile().setExecutable(true, false);
            command.add(executable.toString());
        }
        java.util.Collections.addAll(command, args);
        Process process = new ProcessBuilder(command)
                .directory(projectRoot.toFile())
                .inheritIO()
                .start();
        return process.waitFor();
    }

    private static Path resolveGradleUserHome() {
        String env = System.getenv("GRADLE_USER_HOME");
        if (env != null && !env.isBlank()) return Path.of(env);
        return Path.of(System.getProperty("user.home"), ".gradle");
    }

    private static String distributionFileName(String url) {
        int slash = url.lastIndexOf('/');
        return slash >= 0 ? url.substring(slash + 1) : url;
    }

    private static String required(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing wrapper property: " + key);
        return value;
    }

    private static void download(URL url, Path target, int timeout) throws Exception {
        if ("file".equalsIgnoreCase(url.getProtocol())) {
            Files.copy(Path.of(url.toURI()), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        URL current = url;
        for (int redirect = 0; redirect < 8; redirect++) {
            HttpURLConnection c = (HttpURLConnection) current.openConnection();
            c.setConnectTimeout(timeout);
            c.setReadTimeout(Math.max(timeout, 30000));
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "dwas_EQ-gradle-bootstrap/1");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = c.getHeaderField("Location");
                c.disconnect();
                if (location == null) throw new IllegalStateException("Gradle download redirect had no Location header");
                current = current.toURI().resolve(location).toURL();
                continue;
            }
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("Gradle distribution download failed with HTTP " + code + " from " + current);
            }
            try (InputStream in = new BufferedInputStream(c.getInputStream());
                 BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target.toFile()))) {
                in.transferTo(out);
            } finally {
                c.disconnect();
            }
            return;
        }
        throw new IllegalStateException("Too many redirects downloading Gradle distribution");
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(new FileInputStream(file.toFile()))) {
            byte[] buffer = new byte[1024 * 128];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void unzip(Path zip, Path destination) throws Exception {
        Path root = destination.toAbsolutePath().normalize();
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip.toFile())))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                Path out = root.resolve(entry.getName()).normalize();
                if (!out.startsWith(root)) throw new SecurityException("Blocked zip path traversal: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(out.toFile()))) {
                        zin.transferTo(stream);
                    }
                    if (entry.getName().endsWith("/bin/gradle") || entry.getName().endsWith("/bin/gradle.bat")) {
                        out.toFile().setExecutable(true, false);
                    }
                }
                zin.closeEntry();
            }
        }
    }
}
