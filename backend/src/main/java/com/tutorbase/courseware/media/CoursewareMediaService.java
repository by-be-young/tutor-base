package com.tutorbase.courseware.media;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.CoursewareProperties;
import org.springframework.stereotype.Service;

/**
 * 课件图片：上传到 Supabase Storage 公开桶，按可读 ASCII 文件名存储。
 *
 * <p>写入使用一个只具备该桶 INSERT 权限的 Supabase Auth 账号（密码登录换 access token），
 * 不使用 service_role 密钥；读取走公开 URL，不需要鉴权。
 */
@Service
public class CoursewareMediaService {

    private static final Pattern FILE_NAME = Pattern.compile(
            "[a-z0-9][a-z0-9._-]{0,120}\\.(png|jpg|jpeg|webp|gif)");
    private static final int SUGGEST_LIMIT = 50;
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EXPIRES_IN = Pattern.compile("\"expires_in\"\\s*:\\s*(\\d+)");

    private final CoursewareProperties properties;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile Token token;

    CoursewareMediaService(CoursewareProperties properties) {
        this.properties = properties;
    }

    /** 重名检查结果；`suggested` 在重名时给出第一个可用后缀。 */
    public record Check(boolean exists, String fileName, String suggested, String url) {
    }

    public record Stored(String fileName, String url, long bytes) {
    }

    public Check check(String rawFileName) {
        ensureConfigured();
        String fileName = normalize(rawFileName);
        boolean exists = exists(publicUrl(fileName));
        String suggested = exists ? suggest(fileName) : fileName;
        return new Check(exists, fileName, suggested, publicUrl(fileName));
    }

    public Stored upload(String rawFileName, byte[] content, String contentType) {
        ensureConfigured();
        String fileName = normalize(rawFileName);
        validateImage(fileName, content, contentType);

        if (exists(publicUrl(fileName))) {
            throw new CoursewareFailures.MediaExists("图片文件名已存在：" + fileName);
        }
        putObject(fileName, content, contentTypeFor(fileName), false);
        return new Stored(fileName, publicUrl(fileName), content.length);
    }

    private String normalize(String rawFileName) {
        String fileName = String.valueOf(rawFileName == null ? "" : rawFileName)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-");
        if (!FILE_NAME.matcher(fileName).matches()) {
            throw new CoursewareFailures.Validation(List.of(
                    "图片文件名只能使用小写字母、数字、连字符、下划线或点，并以 png/jpg/jpeg/webp/gif 结尾"));
        }
        return fileName;
    }

    private void validateImage(String fileName, byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw new CoursewareFailures.Validation(List.of("图片内容为空"));
        }
        if (content.length > properties.maxMediaBytes()) {
            throw new CoursewareFailures.Validation(List.of(
                    "图片不能超过 " + (properties.maxMediaBytes() / 1024 / 1024) + " MB"));
        }
        String expected = contentTypeFor(fileName);
        if (contentType != null && !contentType.isBlank() && !contentType.equalsIgnoreCase(expected)) {
            throw new CoursewareFailures.Validation(List.of("图片类型与扩展名不一致"));
        }
        if (!matchesSignature(fileName, content)) {
            throw new CoursewareFailures.Validation(List.of("图片内容与扩展名不匹配"));
        }
    }

    private static String contentTypeFor(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/gif";
    }

    private static boolean matchesSignature(String fileName, byte[] content) {
        if (fileName.endsWith(".png")) {
            return content.length > 8 && (content[0] & 0xff) == 0x89 && content[1] == 'P'
                    && content[2] == 'N' && content[3] == 'G';
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return content.length > 3 && (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8
                    && (content[2] & 0xff) == 0xff;
        }
        if (fileName.endsWith(".gif")) {
            return content.length > 6 && content[0] == 'G' && content[1] == 'I' && content[2] == 'F';
        }
        if (fileName.endsWith(".webp")) {
            return content.length > 12 && content[0] == 'R' && content[1] == 'I' && content[2] == 'F'
                    && content[3] == 'F' && content[8] == 'W' && content[9] == 'E'
                    && content[10] == 'B' && content[11] == 'P';
        }
        return false;
    }

    private String suggest(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        for (int index = 2; index <= SUGGEST_LIMIT; index += 1) {
            String candidate = stem + "-" + index + extension;
            if (!exists(publicUrl(candidate))) {
                return candidate;
            }
        }
        throw new CoursewareFailures.Validation(List.of("同名图片太多，请换一个更具体的文件名"));
    }

    private boolean exists(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private void ensureConfigured() {
        if (properties.mediaSupabaseUrl().isBlank() || properties.mediaSupabaseAnonKey().isBlank()
                || properties.mediaSupabaseEmail().isBlank() || properties.mediaSupabasePassword().isBlank()) {
            throw new CoursewareFailures.StorageUnavailable(
                    "未配置课件图片上传账号，请设置 TUTOR_COURSEWARE_MEDIA_SUPABASE_* 环境变量");
        }
    }

    private void putObject(String fileName, byte[] content, String contentType, boolean retried) {
        try {
            String path = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            baseUrl() + "/storage/v1/object/" + properties.mediaBucket() + "/" + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + accessToken(false))
                    .header("apikey", properties.mediaSupabaseAnonKey())
                    .header("Content-Type", contentType)
                    .header("x-upsert", "false")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(content))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200 || status == 201) {
                return;
            }
            if (status == 401 && !retried) {
                token = null;
                putObject(fileName, content, contentType, true);
                return;
            }
            if (status == 409 || (status == 400 && response.body() != null
                    && response.body().toLowerCase(Locale.ROOT).contains("duplicate"))) {
                throw new CoursewareFailures.MediaExists("图片文件名已存在：" + fileName);
            }
            throw new CoursewareFailures.StorageUnavailable(
                    "上传课件图片失败：HTTP " + status + " " + abbreviate(response.body()));
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new CoursewareFailures.StorageUnavailable("上传课件图片失败：" + fileName, exception);
        }
    }

    private String accessToken(boolean force) {
        Token current = token;
        if (!force && current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return current.value();
        }
        synchronized (this) {
            if (!force && token != null && token.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
                return token.value();
            }
            try {
                String body = "{\"email\":" + jsonString(properties.mediaSupabaseEmail())
                        + ",\"password\":" + jsonString(properties.mediaSupabasePassword()) + "}";
                HttpRequest request = HttpRequest.newBuilder(URI.create(
                                baseUrl() + "/auth/v1/token?grant_type=password"))
                        .timeout(Duration.ofSeconds(15))
                        .header("apikey", properties.mediaSupabaseAnonKey())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new CoursewareFailures.StorageUnavailable(
                            "图片上传账号登录失败：HTTP " + response.statusCode());
                }
                Matcher tokenMatcher = ACCESS_TOKEN.matcher(response.body());
                Matcher expiresMatcher = EXPIRES_IN.matcher(response.body());
                String value = tokenMatcher.find() ? tokenMatcher.group(1) : "";
                long expiresIn = expiresMatcher.find() ? Long.parseLong(expiresMatcher.group(1)) : 3600;
                if (value.isBlank()) {
                    throw new CoursewareFailures.StorageUnavailable("图片上传账号登录响应缺少 access_token");
                }
                token = new Token(value, Instant.now().plusSeconds(Math.max(60, expiresIn)));
                return value;
            } catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new CoursewareFailures.StorageUnavailable("图片上传账号登录失败", exception);
            }
        }
    }

    private String baseUrl() {
        return properties.mediaSupabaseUrl().replaceAll("/+$", "");
    }

    private String publicUrl(String fileName) {
        String path = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return baseUrl() + "/storage/v1/object/public/" + properties.mediaBucket() + "/" + path;
    }

    private static String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    private static String jsonString(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (char character : String.valueOf(value).toCharArray()) {
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                default -> builder.append(character);
            }
        }
        return builder.append('"').toString();
    }

    private record Token(String value, Instant expiresAt) {
    }
}
