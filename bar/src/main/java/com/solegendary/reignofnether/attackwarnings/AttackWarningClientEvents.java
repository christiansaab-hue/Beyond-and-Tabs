package com.solegendary.reignofnether.attackwarnings;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.minimap.MinimapClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.registrars.SoundRegistrar;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

public class AttackWarningClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final int ATTACK_WARNING_CD_MAX = 300;
    private static int attackWarningCd = 0;
    private static BlockPos lastAttackPos = null;
    private static final int FLICKER_SPEED = 10;
    private static int flickerTimer = FLICKER_SPEED;
    private static boolean showFrame = true;
    private static final int WARN_DURATION_MAX = 600;
    private static int warnDuration = 0;

    public static Button getWarningButton() {
        return new Button(
            "Go to alert",
            20,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/warning.png"),
            null,
            null,
            () -> showFrame,
            () -> lastAttackPos == null || warnDuration <= 0,
            () -> true,
            () -> {
                OrthoviewClientEvents.centreCameraOnPos(lastAttackPos);
                attackWarningCd = ATTACK_WARNING_CD_MAX;
                lastAttackPos = null;
            },
            () -> {
                attackWarningCd = ATTACK_WARNING_CD_MAX * 2;
                lastAttackPos = null;
            },
            List.of(
                FormattedCharSequence.forward(I18n.get("attack_warning.reignofnether.go_to"), Style.EMPTY),
                FormattedCharSequence.forward(I18n.get("attack_warning.reignofnether.ignore"), Style.EMPTY)
            )
        );
    }

    public static void checkAndTriggerAttackWarning(String attackedPlayerName, BlockPos attackPos, boolean isCommander) {
        if (MC.player == null || !OrthoviewClientEvents.isEnabled())
            return;
        String me = MC.player.getName().getString();
        if (!me.equals(attackedPlayerName)) {
            // BAR-style: an ally's commander under fire shows up on your minimap too (orange, not the red own-unit ping)
            if (isCommander && AlliancesClient.isAllied(me, attackedPlayerName))
                MinimapClientEvents.addCombatPing(attackPos.getX(), attackPos.getZ(), MinimapClientEvents.PING_ALLY_COMMANDER);
            return;
        }
        // red pulsing ring on the minimap wherever our stuff is being hit (rate-limited inside, so a big fight
        // shows a few rings rather than a carpet of them)
        MinimapClientEvents.addCombatPing(attackPos.getX(), attackPos.getZ(), MinimapClientEvents.PING_UNDER_ATTACK);

        Vec3 centrePos = MiscUtil.getOrthoviewCentreWorldPos(MC);
        float dist2dSqr = new Vec2((float) attackPos.getX(), (float) attackPos.getZ())
                .distanceToSqr(new Vec2((float) centrePos.x, (float) centrePos.z));

        // update lastAttackPos even on cooldown so we can keep the teleport position up to date
        if (lastAttackPos != null)
            lastAttackPos = attackPos;

        if (dist2dSqr > Math.pow(OrthoviewClientEvents.getZoom() * 2, 2) && attackWarningCd <= 0) {
            HudClientEvents.showTemporaryMessage(I18n.get("attack_warning.reignofnether.danger"), 200);
            lastAttackPos = attackPos;
            if (MC.player != null)
                MC.player.playSound(SoundRegistrar.UNDER_ATTACK.get(), 0.5f, 1.0f);
            attackWarningCd = ATTACK_WARNING_CD_MAX;
            warnDuration = WARN_DURATION_MAX;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        if (attackWarningCd > 0)
            attackWarningCd -= 1;
        flickerTimer -= 1;
        if (flickerTimer <= 0) {
            flickerTimer = FLICKER_SPEED;
            showFrame = !showFrame;
        }
        if (warnDuration > 0)
            warnDuration -= 1;
    }
}
