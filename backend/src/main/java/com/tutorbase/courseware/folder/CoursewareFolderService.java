package com.tutorbase.courseware.folder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.tutorbase.courseware.CoursewareFailures;
import com.tutorbase.courseware.publish.CoursewareValidator;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 目录登记：让「空目录」在课件树里存在。
 *
 * <p>树的层级本来只从课件路径推导，新建了目录却没有课件时就看不到。这里按老师命名空间
 * 登记目录路径（`学科/子目录/孙目录`），前端把登记的目录与课件路径合并成一棵树。
 */
@Service
public class CoursewareFolderService {

    private final JdbcClient jdbc;

    CoursewareFolderService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 我登记过的目录路径（升序）。 */
    @Transactional(readOnly = true)
    public List<String> list(long teacherId) {
        return jdbc.sql("""
                SELECT path FROM public.course_folder
                WHERE teacher_account_id = :teacherId
                ORDER BY path
                """)
                .param("teacherId", teacherId)
                .query(String.class)
                .list();
    }

    /**
     * 新建目录；父目录会一并登记，保证树能逐级展开。
     *
     * @return 本次新登记的路径（父目录 + 目标目录），已存在的路径不重复返回
     */
    @Transactional
    public List<String> create(long teacherId, String rawPath) {
        List<String> created = new ArrayList<>();
        for (String path : normalize(rawPath)) {
            int inserted = jdbc.sql("""
                    INSERT INTO public.course_folder (teacher_account_id, path)
                    VALUES (:teacherId, :path)
                    ON CONFLICT (teacher_account_id, path) DO NOTHING
                    """)
                    .param("teacherId", teacherId)
                    .param("path", path)
                    .update();
            if (inserted > 0) {
                created.add(path);
            }
        }
        return created;
    }

    /** 校验并展开出从学科到目标目录的全部路径。 */
    static List<String> normalize(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new CoursewareFailures.Validation(List.of("目录路径不能为空"));
        }
        String path = CoursewareValidator.normalizePath(rawPath).replaceAll("/+$", "");
        if (path.endsWith(".md")) {
            throw new CoursewareFailures.Validation(List.of("目录路径不能以 .md 结尾：" + rawPath));
        }
        if (path.contains("..")) {
            throw new CoursewareFailures.Validation(List.of("目录路径不允许 ..：" + rawPath));
        }
        String[] segments = path.split("/", -1);
        if (segments.length < 2) {
            throw new CoursewareFailures.Validation(List.of("目录路径需要包含学科前缀：" + rawPath));
        }
        for (String segment : segments) {
            if (segment.isBlank()) {
                throw new CoursewareFailures.Validation(List.of("目录路径不能有空层级：" + rawPath));
            }
        }
        Set<String> ancestors = new LinkedHashSet<>();
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(segment);
            ancestors.add(builder.toString());
        }
        return new ArrayList<>(ancestors);
    }
}
