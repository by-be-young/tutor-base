package com.tutorbase.courseware.media;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.CoursewareProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 用 JDK 自带 HttpServer 覆盖 Supabase 登录、公开读与上传三段 HTTP 交互。 */
class CoursewareMediaServiceHttpTest {

    @Test
    void checksExistingNameAndSuggestsNextSuffix() throws IOException {
        AtomicReference<String> lastHead = new AtomicReference<>();
        HttpServer server = start(name -> {
            lastHead.set(name);
            return name.equals("chemistry-2-3-fig1.png") ? 200 : 400;
        });
        try {
            CoursewareMediaService service = service(server);
            CoursewareMediaService.Check check = service.check("chemistry-2-3-fig1.png");
            assertTrue(check.exists());
            assertEquals("chemistry-2-3-fig1-2.png", check.suggested());
            assertTrue(check.url().startsWith("http://127.0.0.1:"));

            CoursewareMediaService.Check free = service.check("chemistry-2-3-fig2.png");
            assertFalse(free.exists());
            assertEquals("chemistry-2-3-fig2.png", free.suggested());
            assertEquals("chemistry-2-3-fig2.png", lastHead.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void uploadsThroughPasswordLoginAndStorageInsert() throws IOException {
        AtomicReference<String> uploaded = new AtomicReference<>();
        HttpServer server = start(name -> 400, uploaded);
        try {
            CoursewareMediaService.Stored stored = service(server)
                    .upload("chemistry-2-3-fig3.png", validPng(), "image/png");
            assertEquals("chemistry-2-3-fig3.png", stored.fileName());
            assertEquals("chemistry-2-3-fig3.png", uploaded.get());
            assertTrue(stored.url().endsWith("/courseware-media/chemistry-2-3-fig3.png"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void duplicateInsertBecomesMediaExists() throws IOException {
        HttpServer server = start(name -> 400, new AtomicReference<>(), 409);
        try {
            assertThrows(CoursewareFailures.MediaExists.class, () -> service(server)
                    .upload("chemistry-2-3-fig4.png", validPng(), "image/png"));
        } finally {
            server.stop(0);
        }
    }

    private static CoursewareMediaService service(HttpServer server) {
        int port = server.getAddress().getPort();
        return new CoursewareMediaService(new CoursewareProperties(
                Path.of("var", "courseware"), 1048576, true, 2147483648L, 10485760,
                "http://127.0.0.1:" + port, "test-anon-key", "bot@example.com", "test-password",
                "courseware-media"));
    }

    private static HttpServer start(Status publicStatus) throws IOException {
        return start(publicStatus, new AtomicReference<>());
    }

    private static HttpServer start(Status publicStatus, AtomicReference<String> uploaded) throws IOException {
        return start(publicStatus, uploaded, 200);
    }

    private static HttpServer start(Status publicStatus, AtomicReference<String> uploaded, int insertStatus)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/v1/token", exchange -> {
            respond(exchange, 200, "{\"access_token\":\"test-token\",\"expires_in\":3600}");
        });
        server.createContext("/storage/v1/object/public/courseware-media/", exchange -> {
            String name = exchange.getRequestURI().getPath()
                    .substring("/storage/v1/object/public/courseware-media/".length());
            respond(exchange, publicStatus.status(name), "");
        });
        server.createContext("/storage/v1/object/courseware-media/", exchange -> {
            String name = exchange.getRequestURI().getPath()
                    .substring("/storage/v1/object/courseware-media/".length());
            exchange.getRequestBody().readAllBytes();
            uploaded.set(name);
            assertEquals("Bearer test-token", exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("test-anon-key", exchange.getRequestHeaders().getFirst("apikey"));
            respond(exchange, insertStatus, insertStatus == 200 ? "{\"Key\":\"" + name + "\"}" : "duplicate");
        });
        server.start();
        return server;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static byte[] validPng() {
        byte[] png = new byte[16];
        png[0] = (byte) 0x89;
        png[1] = 'P';
        png[2] = 'N';
        png[3] = 'G';
        png[4] = 0x0d;
        png[5] = 0x0a;
        png[6] = 0x1a;
        png[7] = 0x0a;
        return png;
    }

    @FunctionalInterface
    private interface Status {
        int status(String name);
    }
}
