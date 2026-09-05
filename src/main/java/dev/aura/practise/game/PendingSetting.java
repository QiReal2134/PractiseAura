package dev.aura.practise.game;

/** /pa setting 输入模式：等待玩家在聊天栏输入数值的临时状态 */
public record PendingSetting(String key, long expireAtMillis) {

    public boolean expired() {
        return System.currentTimeMillis() > expireAtMillis;
    }
}
