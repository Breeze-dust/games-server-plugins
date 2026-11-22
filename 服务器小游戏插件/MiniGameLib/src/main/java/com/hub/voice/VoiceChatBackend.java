package com.hub.voice;

import java.util.UUID;

/**
 * 语音后端抽象。实现类才会 import Simple Voice Chat 的 API 类，
 * 这样服务器没装 SVC 时 MiniGameLib 仍然能正常加载。
 */
interface VoiceChatBackend {

    UUID createIsolatedGroup(String name);

    void removeGroup(UUID groupId);

    /** groupId 为 null 表示退出当前组。 */
    void apply(UUID player, UUID groupId);
}
