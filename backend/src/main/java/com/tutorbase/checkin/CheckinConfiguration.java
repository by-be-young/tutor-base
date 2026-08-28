package com.tutorbase.checkin;

import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CheckinConfiguration {

    /** 签到按上海时区判定「今日」，与站点的目标用户群体一致。 */
    @Bean
    ZoneId checkinZone() {
        return ZoneId.of("Asia/Shanghai");
    }
}
