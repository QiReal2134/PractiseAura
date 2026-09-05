package dev.aura.practise.game;

public enum GameState {
    WAITING,   // 等待玩家加入
    STARTING,  // 人已满，倒计时中
    RUNNING,   // 游戏进行中
    ENDING     // 已结束，等待清理
}
