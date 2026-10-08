package dev.beyondtabs.mod.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Render types for the match models: solid (back-face culled, so the ink-outline hulls work) and translucent. */
final class BTRender extends RenderType {
    private BTRender(String n, VertexFormat f, VertexFormat.Mode m, int b, boolean c, boolean s, Runnable a, Runnable r) { super(n, f, m, b, c, s, a, r); 
    /** Flashes, fire, sparks, beams, shockwaves: added on top of what's behind (reads as light), no depth write. */
    static final RenderType ADDITIVE = create("beyondtabs_additive", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1 << 18, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE).setTransparencyState(LIGHTNING_TRANSPARENCY).createCompositeState(false));
}

    /** Opaque models with baked lighting and outlines. */
    static final RenderType SOLID = create("beyondtabs_solid", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1 << 20, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(CULL).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE).setTransparencyState(NO_TRANSPARENCY).createCompositeState(false));

    /** Shadows, smoke, construction blueprints, placement preview: blended, no depth write. */
    static final RenderType TRANSLUCENT = create("beyondtabs_translucent", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1 << 18, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE).setTransparencyState(TRANSLUCENT_TRANSPARENCY).createCompositeState(false));

    /** Flashes, fire, sparks, beams, shockwaves: added on top of what's behind (reads as light), no depth write. */
    static final RenderType ADDITIVE = create("beyondtabs_additive", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1 << 18, false, false,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE).setTransparencyState(LIGHTNING_TRANSPARENCY).createCompositeState(false));
}
