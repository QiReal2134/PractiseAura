package dev.aura.practise.game;

/** 管理员执行 /pa setbed 后等待其左键点床的临时状态 */
public record PendingBed(String arenaName, Team team, int position, long expireAtMillis) {

    public boolean expired() {
        return System.currentTimeMillis() > expireAtMillis;
    }
}
