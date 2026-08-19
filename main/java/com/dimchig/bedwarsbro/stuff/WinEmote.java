package com.dimchig.bedwarsbro.stuff;

import java.util.ArrayList;
import java.util.Random;

import com.dimchig.bedwarsbro.CustomScoreboard.TEAM_COLOR;
import com.dimchig.bedwarsbro.MyChatListener;
import com.dimchig.bedwarsbro.particles.ParticleController;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

public class WinEmote {

	private static final int MAX_Y = 120;
	private static final int POSITIONS_PER_COLUMN = MAX_Y + 1;
	private static final long MAX_DURATION_MS = 15_000L;
	private static final Random RANDOM = new Random();

	public static int emoteStage = -1;
	public static int maxEmoteBlocksPerTick = 10000;
	public static int targetRange = 120;
	public static int currentRange = -1;
	public static BlockPos startingPos = null;
	public static long startingTime = 0;
	public static TEAM_COLOR emoteStage_team_color;

	private static int ringPositionIndex;
	private static ArrayList<IBlockState> emoteStates;

	public static void handleEmote() {
		Minecraft minecraft = Minecraft.getMinecraft();
		if (!MyChatListener.IS_IN_GAME || minecraft.theWorld == null) {
			reset();
			return;
		}

		if (emoteStage <= 0 || startingPos == null || emoteStates == null || emoteStates.isEmpty()) return;
		if (System.currentTimeMillis() - startingTime > MAX_DURATION_MS) {
			reset();
			return;
		}

		World world = minecraft.theWorld;
		BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
		int processed = 0;
		while (processed < maxEmoteBlocksPerTick && currentRange <= targetRange) {
			int positionsInRing = getPositionsInRing(currentRange);
			if (ringPositionIndex >= positionsInRing) {
				currentRange++;
				ringPositionIndex = 0;
				break;
			}

			int columnIndex = ringPositionIndex / POSITIONS_PER_COLUMN;
			int y = ringPositionIndex % POSITIONS_PER_COLUMN;
			long offset = getRingColumnOffset(currentRange, columnIndex);
			position.set(startingPos.getX() + (int)(offset >> 32), y, startingPos.getZ() + (int)offset);

			if (world.getBlockState(position).getBlock() != Blocks.air) {
				world.setBlockState(position, emoteStates.get(RANDOM.nextInt(emoteStates.size())));
			}

			ringPositionIndex++;
			processed++;
		}

		if (currentRange > targetRange) reset();
	}

	private static int getPositionsInRing(int range) {
		return (range == 0 ? 1 : range * 8) * POSITIONS_PER_COLUMN;
	}

	private static long getRingColumnOffset(int range, int index) {
		if (range == 0) return packOffset(0, 0);

		int sideLength = range * 2 + 1;
		if (index < sideLength) return packOffset(-range, -range + index);
		index -= sideLength;
		if (index < sideLength) return packOffset(range, -range + index);
		index -= sideLength;

		int middleLength = sideLength - 2;
		if (index < middleLength) return packOffset(-range + 1 + index, -range);
		return packOffset(-range + 1 + index - middleLength, range);
	}

	private static long packOffset(int x, int z) {
		return ((long)x << 32) | (z & 0xffffffffL);
	}

	public static void changeWorldBlocks(TEAM_COLOR teamColor) {
		Entity player = Minecraft.getMinecraft().thePlayer;
		if (player == null) return;

		emoteStage = 1;
		emoteStage_team_color = teamColor;
		currentRange = 0;
		ringPositionIndex = 0;
		startingTime = System.currentTimeMillis();
		startingPos = new BlockPos(player);
		emoteStates = getStates(teamColor);

		for (int i = 0; i < 10; i++) {
			ParticleController.spawnFinalKillParticles(player.posX, player.posY + player.getEyeHeight() / 2, player.posZ, teamColor);
		}
	}

	public static void reset() {
		emoteStage = -1;
		currentRange = -1;
		ringPositionIndex = 0;
		startingPos = null;
		startingTime = 0;
		emoteStage_team_color = null;
		emoteStates = null;
	}

	public static ArrayList<IBlockState> getStates(TEAM_COLOR teamColor) {
		ArrayList<IBlockState> states = new ArrayList<IBlockState>();
		if (teamColor == TEAM_COLOR.RED) {
			states.add(Blocks.redstone_block.getDefaultState());
			states.add(Blocks.wool.getDefaultState().withProperty(Blocks.stained_glass.COLOR, EnumDyeColor.RED));
			states.add(Blocks.stained_hardened_clay.getDefaultState().withProperty(Blocks.stained_glass.COLOR, EnumDyeColor.RED));
		} else if (teamColor == TEAM_COLOR.YELLOW) {
			states.add(Blocks.gold_block.getDefaultState());
			states.add(Blocks.wool.getDefaultState().withProperty(Blocks.stained_glass.COLOR, EnumDyeColor.YELLOW));
		} else if (teamColor == TEAM_COLOR.GREEN) {
			states.add(Blocks.emerald_block.getDefaultState());
			states.add(Blocks.wool.getDefaultState().withProperty(Blocks.stained_glass.COLOR, EnumDyeColor.LIME));
		} else if (teamColor == TEAM_COLOR.AQUA) {
			states.add(Blocks.diamond_block.getDefaultState());
			states.add(Blocks.wool.getDefaultState().withProperty(Blocks.stained_glass.COLOR, EnumDyeColor.LIGHT_BLUE));
		} else if (teamColor == TEAM_COLOR.BLUE) {
			states.add(Blocks.lapis_block.getDefaultState());
			states.add(Blocks.wool.getDefaultState().withProperty(Blocks.stained_glass.COLOR, EnumDyeColor.BLUE));
		} else if (teamColor == TEAM_COLOR.PINK) {
			states.add(Blocks.wool.getDefaultState().withProperty(Blocks.stained_glass.COLOR, EnumDyeColor.PINK));
			states.add(Blocks.wool.getDefaultState().withProperty(Blocks.stained_glass.COLOR, EnumDyeColor.MAGENTA));
		} else if (teamColor == TEAM_COLOR.GRAY) {
			states.add(Blocks.stone.getDefaultState());
			states.add(Blocks.cobblestone.getDefaultState());
			states.add(Blocks.stonebrick.getDefaultState());
		} else if (teamColor == TEAM_COLOR.WHITE) {
			states.add(Blocks.iron_block.getDefaultState());
			states.add(Blocks.quartz_block.getDefaultState());
			states.add(Blocks.wool.getDefaultState().withProperty(Blocks.stained_glass.COLOR, EnumDyeColor.WHITE));
		}
		return states;
	}
}
