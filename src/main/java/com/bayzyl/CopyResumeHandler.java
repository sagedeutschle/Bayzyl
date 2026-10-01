package com.bayzyl;

import org.bukkit.entity.Player;

/** Re-runs a /copy that a restart interrupted, using the selection and mask it was started with. */
final class CopyResumeHandler implements CrashRecoveryService.ResumeHandler {
    @FunctionalInterface
    interface CopyRunner {
        void run(Player player, Selection selection, BlockMask mask);
    }

    private final CopyRunner runner;

    CopyResumeHandler(CopyRunner runner) {
        this.runner = runner;
    }

    @Override
    public void resume(Player player, CrashRecoveryService.ActiveCommandSession session) {
        if (!(session.data().get("selection") instanceof Selection selection) || !selection.isComplete()) {
            player.sendMessage("§6[Bayzyl] §7The interrupted copy's selection could not be restored. "
                    + "Select the region and run §f/copy§7 again.");
            return;
        }
        BlockMask mask = session.data().get("mask") instanceof BlockMask restored ? restored : BlockMask.parse(null);
        runner.run(player, selection, mask);
    }
}
