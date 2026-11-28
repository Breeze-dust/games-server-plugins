package com.hub.voice;

import java.util.UUID;

/**
 * 语音后端抽象。实现类才会 import Simple Voice Chat 的 API 类，
 * 这样服务器没装 SVC 时 MiniGameLib 仍然能正常加载。
 */
interface VoiceChatBackend {

    /** SVC 要等服务端启动后的下一个 tick 才回调 initialize()，在那之前都是 false。 */
    boolean ready();

    UUID createIsolatedGroup(String name);

    /** 建一个常驻组：立刻登记并广播给客户端，空组也不会被 SVC 回收。 */
    UUID createPersistentGroup(UUID groupId, String name);

    boolean groupExists(UUID groupId);

    boolean inGroup(UUID player, UUID groupId);

    void removeGroup(UUID groupId);

    /** groupId 为 null 表示退出当前组。 */
    void apply(UUID player, UUID groupId);
}
