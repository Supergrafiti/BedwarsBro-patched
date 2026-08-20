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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ProjectileTrajectoryPreview {
    private static final int MAX_STEPS = 180;
    private static final int MAX_ENEMY_AIM_PREVIEWS = 12;
    private static final double DANGER_NEARBY_DISTANCE = 3.0;
    private static final double MIN_MOTION_SQ = 0.000001;

    private final Minecraft mc = Minecraft.getMinecraft();
    private boolean enabled;
    private boolean showEnemyAimTrajectories;

    public ProjectileTrajectoryPreview() {
        updateBooleans();
    }

    public void updateBooleans() {
        enabled = Main.getConfigBool(CONFIG_MSG.PROJECTILE_TRAJECTORY_PREVIEW);
        showEnemyAimTrajectories = Main.getConfigBool(CONFIG_MSG.ENEMY_AIM_TRAJECTORIES);
    }

    @SubscribeEvent
    public void onRenderWorldLastEvent(RenderWorldLastEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null || !enabled) {
            return;
        }

        Launch launch = getLaunch(mc.thePlayer);
        Prediction prediction = launch == null ? null : trace(launch, mc.thePlayer);

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

            if (prediction != null && prediction.points.size() >= 2) {
                float red = prediction.valid ? 0.10f : 1.00f;
                float green = prediction.valid ? 1.00f : 0.12f;
                drawPath(prediction.points, red, green, 0.10f);
                drawMarker(prediction, red, green, 0.10f);
            }
            if (showEnemyAimTrajectories) {
                drawEnemyAimPreviews();
            }
        } finally {
            GL11.glDepthMask(true);
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private Launch getLaunch(EntityPlayer player) {
        ItemStack stack = player.getCurrentEquippedItem();
        if (stack == null) {
            return null;
        }

        Item item = stack.getItem();
        if (item instanceof ItemBow) {
            if (!player.isUsingItem()) {
                return null;
            }
            int useTicks = stack.getMaxItemUseDuration() - player.getItemInUseCount();
            float pull = useTicks / 20.0f;
            pull = (pull * pull + pull * 2.0f) / 3.0f;
            if (pull < 0.1f) {
                return null;
            }
            return createLaunch(player, pull > 1.0f ? 3.0 : pull * 3.0, 0.05, 0.99, 0.60, 0.0, 0.25, false, false);
        }

        if (item == Items.ender_pearl) {
            return createLaunch(player, 1.5, 0.03, 0.99, 0.80, 0.0, 0.13, true, true);
        }
        if (item == Items.snowball || item == Items.egg) {
            return createLaunch(player, 1.5, 0.03, 0.99, 0.80, 0.0, 0.13, false, true);
        }
        if (item == Items.experience_bottle) {
            return createLaunch(player, 0.7, 0.07, 0.99, 0.80, -20.0, 0.13, false, true);
        }
        if (item instanceof ItemPotion && ItemPotion.isSplash(stack.getItemDamage())) {
            return createLaunch(player, 0.5, 0.05, 0.99, 0.80, -20.0, 0.13, false, true);
        }
        return null;
    }

    private Launch createLaunch(EntityPlayer player, double speed, double gravity, double airDrag,
            double waterDrag, double verticalAngleOffset, double radius, boolean pearl, boolean inheritHorizontalMotion) {
        double yaw = Math.toRadians(player.rotationYaw);
        double pitch = Math.toRadians(player.rotationPitch);
        double verticalPitch = Math.toRadians(player.rotationPitch + verticalAngleOffset);

        double directionX = -Math.sin(yaw) * Math.cos(pitch);
        double directionY = -Math.sin(verticalPitch);
        double directionZ = Math.cos(yaw) * Math.cos(pitch);
        double length = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);
        if (length < 0.00001) {
            return null;
        }

        Vec3 playerPosition = new Vec3(player.posX, player.posY, player.posZ);
        Vec3 start = new Vec3(
                playerPosition.xCoord - Math.cos(yaw) * 0.16,
                playerPosition.yCoord + player.getEyeHeight() - 0.10000000149011612,
                playerPosition.zCoord - Math.sin(yaw) * 0.16);
        double inheritedMotionX = inheritHorizontalMotion ? player.motionX : 0.0;
        double inheritedMotionZ = inheritHorizontalMotion ? player.motionZ : 0.0;
        return new Launch(start, directionX / length * speed + inheritedMotionX, directionY / length * speed,
                directionZ / length * speed + inheritedMotionZ, gravity, airDrag, waterDrag, radius, pearl);
    }

    /** Uses the same collision and drag model as the visible trajectory preview. */
    public boolean isPlayerAimingAt(EntityPlayer player, Entity target) {
        if (player == null || target == null || player.isDead || player == target) {
            return false;
        }
        Launch launch = getEnemyLaunch(player);
        if (launch == null) {
            return false;
        }
        Prediction prediction = trace(launch, player);
        if (prediction.entity == target) {
            return true;
        }

        Vec3 targetPosition = new Vec3(target.posX, target.posY + target.getEyeHeight(), target.posZ);
        for (int i = 1; i < prediction.points.size(); i++) {
            if (distanceToSegment(targetPosition, prediction.points.get(i - 1), prediction.points.get(i))
                    <= DANGER_NEARBY_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    private double distanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
        double segmentX = end.xCoord - start.xCoord;
        double segmentY = end.yCoord - start.yCoord;
        double segmentZ = end.zCoord - start.zCoord;
        double segmentLengthSq = segmentX * segmentX + segmentY * segmentY + segmentZ * segmentZ;
        if (segmentLengthSq < 0.000001) {
            return point.distanceTo(start);
        }
        double projection = ((point.xCoord - start.xCoord) * segmentX + (point.yCoord - start.yCoord) * segmentY
                + (point.zCoord - start.zCoord) * segmentZ) / segmentLengthSq;
        projection = Math.max(0.0, Math.min(1.0, projection));
        Vec3 closest = new Vec3(start.xCoord + segmentX * projection, start.yCoord + segmentY * projection,
                start.zCoord + segmentZ * projection);
        return point.distanceTo(closest);
    }

    private Launch getEnemyLaunch(EntityPlayer player) {
        ItemStack stack = player.getCurrentEquippedItem();
        if (stack == null) {
            return null;
        }
        if (stack.getItem() instanceof ItemBow) {
            return getLaunch(player);
        }
        if (stack.getItem() == Items.fire_charge) {
            return createLaunch(player, 1.5, 0.0, 0.95, 0.80, 0.0, 0.25, false, false);
        }
        return null;
    }

    private Prediction trace(Launch launch, Entity ignoredEntity) {
        ArrayList<Vec3> points = new ArrayList<Vec3>();
        Vec3 current = launch.start;
        double motionX = launch.motionX;
        double motionY = launch.motionY;
        double motionZ = launch.motionZ;
        points.add(current);

        for (int step = 0; step < MAX_STEPS; step++) {
            Vec3 next = current.addVector(motionX, motionY, motionZ);
            Impact impact = findImpact(current, next, launch.radius, ignoredEntity);
            if (impact != null) {
                points.add(impact.position);
                Vec3 landing = launch.pearl ? findSafePearlLanding(impact.position) : impact.position;
                return new Prediction(points, impact.position, landing, !launch.pearl || landing != null, impact.entity);
            }

            points.add(next);
            current = next;
            if (current.yCoord < -16.0 || current.yCoord > 512.0) {
                break;
            }

            double drag = isWater(current) ? launch.waterDrag : launch.airDrag;
            motionX *= drag;
            motionY *= drag;
            motionZ *= drag;
            motionY -= launch.gravity;
            if (motionX * motionX + motionY * motionY + motionZ * motionZ < MIN_MOTION_SQ) {
                break;
            }
        }
        return new Prediction(points, current, null, false, null);
    }

    private Impact findImpact(Vec3 start, Vec3 end, double radius, Entity ignoredEntity) {
        MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(start, end, false, true, false);
        Impact closest = blockHit == null ? null : new Impact(blockHit.hitVec, null, start.distanceTo(blockHit.hitVec));

        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof Entity)) {
                continue;
            }
            Entity entity = (Entity) object;
            if (entity == ignoredEntity || entity.isDead || !entity.canBeCollidedWith()) {
                continue;
            }
            MovingObjectPosition entityHit = entity.getEntityBoundingBox().expand(radius, radius, radius)
                    .calculateIntercept(start, end);
            if (entityHit == null) {
                continue;
            }
            double distance = start.distanceTo(entityHit.hitVec);
            if (closest == null || distance < closest.distance) {
                closest = new Impact(entityHit.hitVec, entity, distance);
            }
        }
        return closest;
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
            BlockPos support = new BlockPos(x, y, z);
            IBlockState state = mc.theWorld.getBlockState(support);
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

    private void drawPath(List<Vec3> points, float red, float green, float blue) {
        GL11.glColor4f(red, green, blue, 0.90f);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (Vec3 point : points) {
            GL11.glVertex3d(point.xCoord, point.yCoord, point.zCoord);
        }
        GL11.glEnd();
    }

    private void drawEnemyAimPreviews() {
        int rendered = 0;
        for (Object object : mc.theWorld.playerEntities) {
            if (!(object instanceof EntityPlayer) || rendered >= MAX_ENEMY_AIM_PREVIEWS) {
                continue;
            }
            EntityPlayer enemy = (EntityPlayer) object;
            if (!isEnemy(enemy) || mc.thePlayer.getDistanceSqToEntity(enemy) > 128.0 * 128.0) {
                continue;
            }
            Launch launch = getEnemyLaunch(enemy);
            if (launch == null) {
                continue;
            }
            Prediction prediction = trace(launch, enemy);
            if (prediction.points.size() < 2) {
                continue;
            }

            boolean hitsLocalPlayer = prediction.entity == mc.thePlayer;
            float red = hitsLocalPlayer ? 1.0f : 0.95f;
            float green = hitsLocalPlayer ? 0.10f : 0.55f;
            drawPath(prediction.points, red, green, 0.10f);
            drawMarker(prediction, red, green, 0.10f);
            rendered++;
        }
    }

    private boolean isEnemy(EntityPlayer player) {
        if (player == mc.thePlayer || player.isDead) {
            return false;
        }
        return mc.thePlayer.getTeam() == null || player.getTeam() == null || mc.thePlayer.getTeam() != player.getTeam();
    }

    private void drawMarker(Prediction prediction, float red, float green, float blue) {
        Vec3 point = prediction.landing == null ? prediction.impact : prediction.landing;
        if (point == null) {
            return;
        }

        double size = prediction.landing == null ? 0.18 : 0.38;
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

    private static final class Launch {
        final Vec3 start;
        final double motionX;
        final double motionY;
        final double motionZ;
        final double gravity;
        final double airDrag;
        final double waterDrag;
        final double radius;
        final boolean pearl;

        Launch(Vec3 start, double motionX, double motionY, double motionZ, double gravity, double airDrag,
                double waterDrag, double radius, boolean pearl) {
            this.start = start;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
            this.gravity = gravity;
            this.airDrag = airDrag;
            this.waterDrag = waterDrag;
            this.radius = radius;
            this.pearl = pearl;
        }
    }

    private static final class Impact {
        final Vec3 position;
        final Entity entity;
        final double distance;

        Impact(Vec3 position, Entity entity, double distance) {
            this.position = position;
            this.entity = entity;
            this.distance = distance;
        }
    }

    private static final class Prediction {
        final List<Vec3> points;
        final Vec3 impact;
        final Vec3 landing;
        final boolean valid;
        final Entity entity;

        Prediction(List<Vec3> points, Vec3 impact, Vec3 landing, boolean valid, Entity entity) {
            this.points = points;
            this.impact = impact;
            this.landing = landing;
            this.valid = valid;
            this.entity = entity;
        }
    }
}
