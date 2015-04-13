package com.elmakers.mine.bukkit.magic;

public class AutoSaveTask implements Runnable {
    private final MagicController controller;

    public AutoSaveTask(MagicController controller) {
        this.controller = controller;
    }

    @Override
    public void run() {
        controller.getLogger().info("Auto-saving Magic data");
        controller.save(true);
    }
}