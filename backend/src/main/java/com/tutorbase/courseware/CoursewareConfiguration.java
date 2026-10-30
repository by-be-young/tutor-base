package com.tutorbase.courseware;

import com.tutorbase.courseware.storage.CoursewareStorage;
import com.tutorbase.courseware.storage.LocalFileStorage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CoursewareProperties.class)
class CoursewareConfiguration {

    @Bean
    CoursewareStorage coursewareStorage(CoursewareProperties properties) {
        return new LocalFileStorage(properties.root());
    }
}
