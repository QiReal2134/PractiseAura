package dev.aura.practise.game;

import dev.aura.practise.mode.ModeHandler;

import java.util.UUID;

/** /pa duel 对战邀请：被邀请人 30 秒内可接受/拒绝 */
public record PendingDuel(UUID senderId, ModeHandler mode, int rounds, long expireAtMillis) {

    public boolean expired() {
        return System.currentTimeMillis() > expireAtMillis;
    }
}
