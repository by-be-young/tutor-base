package com.tutorbase.checkin;

/** 当前会话未关联学习者身份（learnerId 为 null）时抛出，映射为 403 learner_context_required。 */
final class LearnerContextRequired extends RuntimeException {
}
