package com.tutorbase.courseware.media;

import java.nio.file.Path;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.CoursewareProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoursewareMediaServiceTest {

    private static CoursewareMediaService service(long maxMediaBytes) {
        return new CoursewareMediaService(new CoursewareProperties(
                Path.of("var", "courseware"), 1048576, true, 2147483648L, maxMediaBytes,
                "", "", "", "", "courseware-media"));
    }

    private static CoursewareMediaService configured(long maxMediaBytes) {
        return new CoursewareMediaService(new CoursewareProperties(
                Path.of("var", "courseware"), 1048576, true, 2147483648L, maxMediaBytes,
                "https://example.invalid", "test-anon-key", "bot@example.com", "test-password",
                "courseware-media"));
    }

    @Test
    void rejectsNonImageExtension() {
        CoursewareFailures.Validation failure = assertThrows(CoursewareFailures.Validation.class,
                () -> configured(10485760).upload("chemistry-2-3-fig1.exe", new byte[] {1}, "image/png"));
        assertTrue(failure.errors().get(0).contains("图片文件名"));
    }

    @Test
    void rejectsPathTraversal() {
        assertThrows(CoursewareFailures.Validation.class,
                () -> configured(10485760).upload("../chemistry.png", new byte[] {1}, "image/png"));
    }

    @Test
    void rejectsContentThatDoesNotMatchExtension() {
        CoursewareFailures.Validation failure = assertThrows(CoursewareFailures.Validation.class,
                () -> configured(10485760).upload("chemistry-2-3-fig1.png", "hello".getBytes(), "image/png"));
        assertTrue(failure.errors().contains("图片内容与扩展名不匹配"));
    }

    @Test
    void rejectsOversizedImage() {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0};
        CoursewareFailures.Validation failure = assertThrows(CoursewareFailures.Validation.class,
                () -> configured(8).upload("chemistry-2-3-fig1.png", png, "image/png"));
        assertTrue(failure.errors().get(0).contains("图片不能超过"));
    }

    @Test
    void reportsMissingConfigurationInsteadOfCallingNetwork() {
        CoursewareFailures.StorageUnavailable failure = assertThrows(CoursewareFailures.StorageUnavailable.class,
                () -> service(10485760).upload("chemistry-2-3-fig1.png", validPng(), "image/png"));
        assertTrue(failure.getMessage().contains("未配置课件图片上传账号"));
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
}
