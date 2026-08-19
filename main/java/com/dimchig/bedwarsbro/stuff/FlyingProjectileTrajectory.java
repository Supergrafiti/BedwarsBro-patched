package com.dimchig.bedwarsbro.stuff;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.dimchig.bedwarsbro.Main;
import com.dimchig.bedwarsbro.Main.CONFIG_MSG;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.item.EntityExpBottle;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class FlyingProjectileTrajectory {
    private static final int MAX_PROJECTILES = 96;
    private static final int MAX_STEPS = 180;
    private static final double MAX_DISTANCE_SQ = 192.0 * 192.0;
    private static final double MIN_MOTION_SQ = 0.000001;

    private static final Physics PEARL = new Physics(0.99, 0.80, 0.03, 0.13, true, false);
    private static final Physics ARROW = new Physics(0.99, 0.60, 0.05, 0.25, false, false);
    private static final Physics POTION = new Physics(0.99, 0.80, 0.05, 0.13, false, false);
    private static final Physics EXPERIENCE = new Physics(0.99, 0.80, 0.07, 0.13, false, false);
    private static final Physics THROWABLE = new Physics(0.99, 0.80, 0.03, 0.13, false, false);
    private static final Physics TNT = new Physics(0.98, 0.98, 0.04, 0.49, false, true);

    private final Minecraft mc = Minecraft.getMinecraft();
    private boolean enabled;

    public FlyingProjectileTrajectory() {
        updateBooleans();
    }

    public void updateBooleans() {
        enabled = Main.getConfigBool(CONFIG_MSG.FLYING_PROJECTILE_TRAJECTORIES);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!enabled || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        List<Trajectory> trajectories = collectTrajectories(event.partialTicks);
        if (trajectories.isEmpty()) {
            return;
        }

        Vec3 camera = interpolate(mc.thePlayer, event.partialTicks);
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glTranslated(-camera.xCoord, -camera.yCoord, -camera.zCoord);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2.5f);

            for (Trajectory trajectory : trajectories) {
                drawTrajectory(trajectory);
            }
        } finally {
            GL11.glDepthMask(true);
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private List<Trajectory> collectTrajectories(float partialTicks) {
        List<Trajectory> result = new ArrayList<Trajectory>();
        int rendered = 0;

        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof Entity)) {
                continue;
            }
            Entity entity = (Entity) object;
            Physics physics = getPhysics(entity);
            if (physics == null || entity.isDead || mc.thePlayer.getDistanceSqToEntity(entity) > MAX_DISTANCE_SQ) {
                continue;
            }

            double motionSq = entity.motionX * entity.motionX
                    + entity.motionY * entity.motionY
                    + entity.motionZ * entity.motionZ;
            if (motionSq < MIN_MOTION_SQ) {
                continue;
            }

            Trajectory trajectory = simulate(entity, physics, partialTicks);
            if (trajectory.points.size() > 1) {
                result.add(trajectory);
                if (++rendered >= MAX_PROJECTILES) {
                    break;
                }
            }
        }
        return result;
    }

    private Physics getPhysics(Entity entity) {
        if (entity instanceof EntityEnderPearl) {
            return PEARL;
        }
        if (entity instanceof EntityArrow) {
            return ARROW;
        }
        if (entity instanceof EntityPotion) {
            return POTION;
        }
        if (entity instanceof EntityExpBottle) {
            return EXPERIENCE;
        }
        if (entity instanceof EntityThrowable) {
            return THROWABLE;
        }
        if (entity instanceof EntityTNTPrimed) {
            return TNT;
        }
        return null;
    }

    private Trajectory simulate(Entity entity, Physics physics, float partialTicks) {
        ArrayList<Vec3> points = new ArrayList<Vec3>();
        Vec3 current = interpolate(entity, partialTicks);
        points.add(current);
        double motionX = entity.motionX;
        double motionY = entity.motionY;
        double motionZ = entity.motionZ;
        int maxSteps = MAX_STEPS;
        if (entity instanceof EntityTNTPrimed) {
            maxSteps = Math.min(maxSteps, Math.max(1, ((EntityTNTPrimed) entity).fuse));
        }

        for (int step = 0; step < maxSteps; step++) {
            if (physics.gravityBeforeMove) {
                motionY -= physics.gravity;
            }

            Vec3 next = current.addVector(motionX, motionY, motionZ);
            Impact impact = findImpact(entity, current, next, physics.radius);
            if (impact != null) {
                points.add(impact.position);
                Vec3 landing = physics.pearl ? findSafePearlLanding(impact.position) : impact.position;
                return new Trajectory(points, impact.position, landing, !physics.pearl || landing != null);
            }

            points.add(next);
            current = next;
            if (current.yCoord < -16.0 || current.yCoord > 512.0) {
                break;
            }

            double drag = isWater(current) ? physics.waterDrag : physics.airDrag;
            motionX *= drag;
            motionY *= drag;
            motionZ *= drag;
            if (!physics.gravityBeforeMove) {
                motionY -= physics.gravity;
            }
            if (motionX * motionX + motionY * motionY + motionZ * motionZ < MIN_MOTION_SQ) {
                break;
            }
        }
        return new Trajectory(points, current, null, false);
    }

    private Impact findImpact(Entity projectile, Vec3 start, Vec3 end, double radius) {
        MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(start, end, false, true, false);
        Impact closest = blockHit == null ? null : new Impact(blockHit.hitVec, start.distanceTo(blockHit.hitVec));

        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof Entity)) {
                continue;
            }
            Entity entity = (Entity) object;
            if (entity == projectile || entity.isDead || !entity.canBeCollidedWith()) {
                continue;
            }
            if (entity == mc.thePlayer && isLaunchedByLocalPlayer(projectile)) {
                continue;
            }
            MovingObjectPosition entityHit = entity.getEntityBoundingBox().expand(radius, radius, radius)
                    .calculateIntercept(start, end);
            if (entityHit == null) {
                continue;
            }
            double distance = start.distanceTo(entityHit.hitVec);
            if (closest == null || distance < closest.distance) {
                closest = new Impact(entityHit.hitVec, distance);
            }
        }
        return closest;
    }

    private boolean isLaunchedByLocalPlayer(Entity projectile) {
        if (projectile instanceof EntityArrow) {
            return ((EntityArrow) projectile).shootingEntity == mc.thePlayer;
        }
        if (projectile instanceof EntityThrowable) {
            return ((EntityThrowable) projectile).getThrower() == mc.thePlayer;
        }
        return false;
    }

    private boolean isWater(Vec3 point) {
        IBlockState state = mc.theWorld.getBlockState(new BlockPos(point));
        return state != null && state.getBlock().getMaterial() == Material.water;
    }

    private Vec3 findSafePearlLanding(Vec3 impact) {
        int x = MathHelper.floor_double(impact.xCoord);
        int z = MathHelper.floor_double(impact.zCoord);
        int startY = MathHelper.floor_double(impact.yCoord);
        int minY = Math.max(0, startY - 6);

        for (int y = startY; y >= minY; y--) {
            IBlockState state = mc.theWorld.getBlockState(new BlockPos(x, y, z));
            if (state == null || !state.getBlock().getMaterial().blocksMovement()) {
                continue;
            }
            double feetY = y + 1.0;
            if (impact.yCoord < feetY - 0.25 || impact.yCoord > feetY + 2.5) {
                continue;
            }
            AxisAlignedBB playerBox = new AxisAlignedBB(impact.xCoord - 0.30, feetY, impact.zCoord - 0.30,
                    impact.xCoord + 0.30, feetY + 1.80, impact.zCoord + 0.30);
            if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, playerBox).isEmpty()) {
                return new Vec3(impact.xCoord, feetY, impact.zCoord);
            }
        }
        return null;
    }

    private void drawTrajectory(Trajectory trajectory) {
        float red = trajectory.valid ? 0.10f : 1.00f;
        float green = trajectory.valid ? 1.00f : 0.12f;
        GL11.glColor4f(red, green, 0.10f, 0.90f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (Vec3 point : trajectory.points) {
            GL11.glVertex3d(point.xCoord, point.yCoord, point.zCoord);
        }
        GL11.glEnd();

        Vec3 marker = trajectory.landing == null ? trajectory.impact : trajectory.landing;
        if (marker != null) {
            drawImpactMarker(marker, trajectory.landing == null ? 0.18 : 0.38, red, green, 0.10f);
        }
    }

    private void drawImpactMarker(Vec3 point, double size, float red, float green, float blue) {
        GL11.glColor4f(red, green, blue, 1.0f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(point.xCoord - size, point.yCoord, point.zCoord);
        GL11.glVertex3d(point.xCoord + size, point.yCoord, point.zCoord);
        GL11.glVertex3d(point.xCoord, point.yCoord - size, point.zCoord);
        GL11.glVertex3d(point.xCoord, point.yCoord + size, point.zCoord);
        GL11.glVertex3d(point.xCoord, point.yCoord, point.zCoord - size);
        GL11.glVertex3d(point.xCoord, point.yCoord, point.zCoord + size);
        GL11.glEnd();
    }

    private Vec3 interpolate(Entity entity, float partialTicks) {
        return new Vec3(
                entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks,
                entity.prevPosY + (entity.posY - entity.prevPosY) * partialTicks,
                entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks);
    }

    private static final class Physics {
        final double airDrag;
        final double waterDrag;
        final double gravity;
        final double radius;
        final boolean pearl;
        final boolean gravityBeforeMove;

        Physics(double airDrag, double waterDrag, double gravity, double radius, boolean pearl,
                boolean gravityBeforeMove) {
            this.airDrag = airDrag;
            this.waterDrag = waterDrag;
            this.gravity = gravity;
            this.radius = radius;
            this.pearl = pearl;
            this.gravityBeforeMove = gravityBeforeMove;
        }
    }

    private static final class Impact {
        final Vec3 position;
        final double distance;

        Impact(Vec3 position, double distance) {
            this.position = position;
            this.distance = distance;
        }
    }

    private static final class Trajectory {
        final List<Vec3> points;
        final Vec3 impact;
        final Vec3 landing;
        final boolean valid;

        Trajectory(List<Vec3> points, Vec3 impact, Vec3 landing, boolean valid) {
            this.points = points;
            this.impact = impact;
            this.landing = landing;
            this.valid = valid;
        }
    }
}
