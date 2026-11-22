package dev.uwu.dimension;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import org.joml.Vector3f;

/** The flower Guardian's bark sculpture. Its six bone pivots match the original male rig. */
final class MaleGuardianModel {
    private static final String[] BONES = {"basket","head","left","right","left_root","right_root"};
    private static final float[][] MESH = readMesh();
    private final ModelPart root;
    private final ModelPart[] parts = new ModelPart[BONES.length];

    MaleGuardianModel(ModelPart root) {
        this.root = root;
        for (int i=0;i<BONES.length;i++) parts[i] = root.getChild(BONES[i]);
    }

    static LayerDefinition layer() {
        var mesh = new MeshDefinition();
        var body = mesh.getRoot();
        var pivots = new float[][]{{0,0,0},{0,-4,0},{10,-2,0},{-10,-2,0},{4.5F,13,0},{-4.5F,13,0}};
        for (int i=0;i<BONES.length;i++) {
            float[] p = pivots[i];
            body.addOrReplaceChild(BONES[i], CubeListBuilder.create(), PartPose.offset(p[0], p[1], p[2]));
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    private static float[][] readMesh() {
        final String path = "/assets/uwudimension/geometry/petal_guardian_male.mesh";
        var stream = Objects.requireNonNull(MaleGuardianModel.class.getResourceAsStream(path), path);
        try (var input = new DataInputStream(new java.io.BufferedInputStream(stream))) {
            if (input.readInt()!=0x47444d31) throw new IOException("Unexpected Guardian mesh header");
            float[][] result = new float[BONES.length][];
            for (int bone=0;bone<BONES.length;bone++) {
                int vertices = input.readInt();
                if (vertices<=0 || vertices>65536 || vertices%4!=0) throw new IOException("Invalid Guardian vertex count");
                float[] values = new float[vertices*8];
                for (int i=0;i<values.length;i++) values[i] = input.readFloat();
                result[bone] = values;
            }
            if (input.read()!=-1) throw new IOException("Unexpected trailing Guardian geometry");
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read the male Guardian sculpture", ex);
        }
    }

    void render(PoseStack poses, VertexConsumer output, int light, int overlay, int tint) {
        if (!root.visible) return;
        var position = new Vector3f();
        var normal = new Vector3f();
        poses.pushPose();
        try {
            root.translateAndRotate(poses);
            for (int bone=0;bone<parts.length;bone++) {
                if (!parts[bone].visible) continue;
                poses.pushPose();
                parts[bone].translateAndRotate(poses);
                var transform = poses.last();
                float[] v = MESH[bone];
                for (int j=0;j<v.length;j+=8) {
                    transform.pose().transformPosition(v[j],v[j+1],v[j+2],position);
                    transform.transformNormal(v[j+3],v[j+4],v[j+5],normal);
                    output.addVertex(position.x,position.y,position.z,tint,v[j+6],v[j+7],overlay,light,normal.x,normal.y,normal.z);
                }
                poses.popPose();
            }
        } finally { poses.popPose(); }
    }
}
