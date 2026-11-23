package dev.uwu.dimension.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Uses the visible world's depth, before the hand and HUD. Each pixel is classified in chunk space. */
@EventBusSubscriber(modid="uwudimension",value=Dist.CLIENT)
public final class TimePocketNegative {
    private static ShaderInstance negative;
    private static TextureTarget copy;

    @EventBusSubscriber(modid="uwudimension",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Shaders {
        @SubscribeEvent public static void load(RegisterShadersEvent event) throws IOException {
            var shader=new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("uwudimension","clock_negative"),DefaultVertexFormat.POSITION);
            event.registerShader(shader, instance->negative=instance);
        }
    }

    static void release() { if(copy!=null){copy.destroyBuffers();copy=null;} }

    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL || negative==null)return;
        var mc=Minecraft.getInstance();
        var field=TimePocketClient.field(mc.level);
        if(field==null || !field.negative())return;
        var eye=e.getCamera().getPosition();
        // Skip distant views without binding a framebuffer.
        if(Math.abs(eye.x-(field.x()*16.0+8))>mc.options.getEffectiveRenderDistance()*16+40
                || Math.abs(eye.z-(field.z()*16.0+8))>mc.options.getEffectiveRenderDistance()*16+40)return;
        var main=mc.getMainRenderTarget();
        if(!main.useDepth || main.width<1 || main.height<1)return;
        if(copy==null || copy.width!=main.width || copy.height!=main.height || copy.isStencilEnabled()!=main.isStencilEnabled()){
            release();
            copy=new TextureTarget(main.width,main.height,true,Minecraft.ON_OSX);
            if(main.isStencilEnabled())copy.enableStencil();
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,copy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0,0,main.width,main.height,0,0,copy.width,copy.height,
                GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,GL11.GL_NEAREST);
        main.bindWrite(false);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST), blend=GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull=GL11.glIsEnabled(GL11.GL_CULL_FACE), writeDepth=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableBlend();RenderSystem.disableCull();
            negative.setSampler("Scene",copy.getColorTextureId());
            negative.setSampler("SceneDepth",copy.getDepthTextureId());
            negative.safeGetUniform("InvClip").set(new Matrix4f(e.getProjectionMatrix()).mul(e.getModelViewMatrix()).invert());
            // Camera coordinates relative to the field avoid precision loss on distant Minecraft builds.
            negative.safeGetUniform("LocalEye").set((float)(eye.x-field.x()*16.0),(float)(eye.z-field.z()*16.0));
            negative.apply();
            var quad=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION);
            quad.addVertex(-1,-1,0);quad.addVertex(1,-1,0);quad.addVertex(1,1,0);quad.addVertex(-1,1,0);
            BufferUploader.draw(quad.buildOrThrow());
        } finally {
            negative.clear(); RenderSystem.depthMask(writeDepth);
            if(depth) RenderSystem.enableDepthTest();
            if(blend) RenderSystem.enableBlend();
            if(cull) RenderSystem.enableCull();
        }
    }
    private TimePocketNegative() {}
}
