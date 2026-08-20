package com.dimchig.bedwarsbro.gui;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.dimchig.bedwarsbro.ChatSender;
import com.dimchig.bedwarsbro.ColorCodesManager;
import com.dimchig.bedwarsbro.CustomScoreboard;
import com.dimchig.bedwarsbro.Main;
import com.dimchig.bedwarsbro.Main.CONFIG_MSG;
import com.dimchig.bedwarsbro.MyChatListener;
import com.dimchig.bedwarsbro.CustomScoreboard.TEAM_COLOR;
import com.dimchig.bedwarsbro.particles.ParticleController;
import com.dimchig.bedwarsbro.stuff.BWBed;
import com.dimchig.bedwarsbro.stuff.BWItem;
import com.dimchig.bedwarsbro.stuff.BWItemsHandler;
import com.dimchig.bedwarsbro.stuff.HintsValidator;
import com.dimchig.bedwarsbro.stuff.BWItemsHandler.BWItemArmourLevel;
import com.dimchig.bedwarsbro.stuff.BWItemsHandler.BWItemType;
import com.dimchig.bedwarsbro.stuff.HintsPlayerScanner.BWPlayer;
import com.mojang.realmsclient.dto.RealmsServer.McoServerComparator;

import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.Item;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class GuiMinimap extends Gui {
	public static boolean isActive = false;
	public static boolean isHidePlayersOnShift = false;
	
	private static int MINIMAP_SCALING = 60; //visible range
	private static int MINIMAP_QUALITY_MULTIPLIER = 5; //multiplies by x and scales down by x to get more quality
	private static final int TERRAIN_CHUNK_SIZE = 11;
	private static final int TERRAIN_GRID_SIZE = TERRAIN_CHUNK_SIZE * 16;
	
	private static int offset;
	private static int topX, topY, botX, botY;
	private static boolean isFrameActive = true;
	private static int frame_border_size = 1;
	public static int map_size;
	private static boolean show_heights;
	private static boolean show_additional_information;
	private ResourceLocation resourceLoc_enemy = new ResourceLocation("bedwarsbro:textures/gui/minimap_icons.png");
	private static Minecraft mc;
	private TextureManager textureManager;
	private int tick_cnt = 0;
	private final DecimalFormat timeFormatter = new DecimalFormat("0.0");
	private static int minimap_frame_color = new Color(0.4f, 0.4f, 0.4f, 1f).getRGB();
	
	public static ArrayList<MyBed> bedsFound = new ArrayList<MyBed>();
	private static long time_last_bed_scanned = 0;
	private static long TIME_BED_SCAN = 2 * 1000;
	private static int zoom = 0;
	public static boolean showNicknames = false;
	
	public static class MyBed {
		public BlockPos pos;
		public BlockPos pos_feet;
		public long t;
		public boolean isPlayersBed;
		public ArrayList<BlockPos> obsidianPoses;
		
		public MyBed(BlockPos pos, BlockPos pos_feet, long t, boolean isPlayersBed) {
			this.pos = pos;
			this.pos_feet = pos_feet;
			this.t = t;
			this.isPlayersBed = isPlayersBed;
			this.obsidianPoses = new ArrayList<BlockPos>();
		}
	}
	
	public GuiMinimap() {
		mc = Minecraft.getMinecraft();
		textureManager = mc.getTextureManager();
		updateBooleans();
		
		bedsFound = new ArrayList<MyBed>();
		
		updateSizes();
	}
	
	public static void updateSizes() {
		try {
			map_size = Math.max(50, Math.min(500, Main.getConfigInt(CONFIG_MSG.MINIMAP_SIZE)));
			
			frame_border_size = (int)(map_size / MINIMAP_SCALING / 2) + 1;
			
			isFrameActive = Main.getConfigBool(CONFIG_MSG.MINIMAP_FRAME);
			if (!isFrameActive) frame_border_size = 0;
			
			ScaledResolution sr = new ScaledResolution(mc);
	        int screen_width = sr.getScaledWidth();
	        int screen_height = sr.getScaledHeight(); 
			
			String ox = Main.getConfigString(CONFIG_MSG.MINIMAP_X);
			int offsetX = Integer.parseInt(ox.replace("-", ""));
			if (ox.startsWith("-")) {
				offsetX = screen_width - offsetX - map_size - frame_border_size;
			} else {
				offsetX += frame_border_size;
			}
			
			String oy = Main.getConfigString(CONFIG_MSG.MINIMAP_Y);
			int offsetY = Integer.parseInt(oy.replace("-", ""));
			if (oy.startsWith("-")) {
				offsetY = screen_height - offsetY - map_size - frame_border_size;
			} else {
				offsetY += frame_border_size;
			}
			
			show_heights = Main.getConfigBool(CONFIG_MSG.MINIMAP_SHOW_HEIGHT);
			show_additional_information = Main.getConfigBool(CONFIG_MSG.MINIMAP_ADDITIONAL_INFORMTAION);
			
			topX = offsetX;
			topY = offsetY;
			botX = topX + map_size;
			botY = topY + map_size;
			
		} catch (Exception ex) {
			ChatSender.addText("&aMinimap: &cОшибка в config!");
			map_size = 100;
			topX = 0;
			topY = 0;
			botX = topX + map_size;
			botY = topY + map_size;
			frame_border_size = (int)(map_size / MINIMAP_SCALING / 2) + 1;
		}
	}
	
	public void handleZoom() {
		zoom = 1 - zoom;
		updateSizes();
	}
	
	public static void updateBooleans() {
		isActive = HintsValidator.isMinimapActive();
		isHidePlayersOnShift = Main.getConfigBool(CONFIG_MSG.MINIMAP_HIDE_PLAYERS_ON_SHIFT);
		isFrameActive = Main.getConfigBool(CONFIG_MSG.MINIMAP_FRAME);
		zoom = 0;
		showNicknames = false;
		updateSizes();		
	}
	
	public static class Pos {
		public double x;
		public double y;
		public double z;
		public Pos(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}
	
	public static class PosItem {
		double x;
		double y;
		double z;
		int type;
		int cnt;
		public PosItem(double x, double y, double z, int type, int cnt) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.type = type;
			this.cnt = cnt;
		}	 
	}
	
	public void clearGameBeds() {		
		if (bedsFound == null) bedsFound = new ArrayList<MyBed>();
		bedsFound.clear();
	}
	
	private int minimap_scan_z_value = 0;
	private int minimap_scan_z_step = 2;
	private int minimap_scan_range = 75;
	private boolean minimap_scan_is_active = false;
	
	public void myScan() {
		
		if (!minimap_scan_is_active) return;
		if (bedsFound == null) bedsFound = new ArrayList<MyBed>();

		if (Main.chatListener.IS_IN_GAME == false) return;
		
		
		
		BWBed bed = Main.chatListener.GAME_BED;
		
		if (bed == null) return;
		
		int range = minimap_scan_range;
		//minimap_scan_range = 200;
		//minimap_scan_z_step = 100;
		
		if (minimap_scan_z_value >= range) {
			minimap_scan_is_active = false;
			minimap_scan_z_value = -range;
		}
		
		
		
		
		
		int min_z = minimap_scan_z_value;
		minimap_scan_z_value = Math.min(min_z + minimap_scan_z_step, range);
		int max_z = minimap_scan_z_value;
		if (bed.part1_posY == 71 || bed.part1_posY == 69) {
			scanArea(-range, range, min_z, max_z, 71);
			scanArea(-range, range, min_z, max_z, 69);
		} else {
			scanArea(-range, range, min_z, max_z, bed.part1_posY);
		}
	}
	
	public void scanArea(int min_x, int max_x, int min_z, int max_z, int y_level) {
		int px = (int)Math.floor(mc.thePlayer.posX);
		int pz = (int)Math.floor(mc.thePlayer.posZ);
		int py = y_level;
		
		
		
		long t = new Date().getTime();
		
		BlockPos pos = new BlockPos(px + min_x, py, pz + min_z);
		World world = mc.theWorld;
		BWBed player_bed = Main.chatListener.GAME_BED;
		for (int zi = min_z; zi < max_z; zi++) {
			for (int xi = min_x; xi <= max_x; xi++) {
				pos = pos.offset(EnumFacing.EAST);
				
				IBlockState state = world.getBlockState(pos);
				if (state == null) continue;
				Block b = state.getBlock();
				if (b == Blocks.bed && state.getValue(BlockBed.PART) == BlockBed.EnumPartType.HEAD) {
					

					double x = pos.getX();
					double y = pos.getY();
					double z = pos.getZ();
					double x2 = x;
					double y2 = y;
					double z2 = z;
					
					EnumFacing facing = world.getBlockState(new BlockPos(x, y, z)).getValue(BlockBed.FACING);
					switch (facing) {
						case EAST:x--;x2++;z2++;break;
						case NORTH:z2 += 2; x2++;break;
						case SOUTH:z--;z2++;x2++;break;
						case WEST:z2++;x2 += 2;break;
						case DOWN: case UP: default: break;
					}
					
					
					x2--;
					z2--;
					MyBed bed = new MyBed(new BlockPos(x, y, z), new BlockPos(x2, y, z2), t, false);
					
					
					if (player_bed != null) {
						if ((player_bed.part1_posX == pos.getX() && player_bed.part1_posZ == pos.getZ()) || (player_bed.part2_posX == pos.getX() && player_bed.part2_posZ == pos.getZ())) bed.isPlayersBed = true;
					}
					
					//obsidian
					BlockPos[] arr = new BlockPos[]{
							new BlockPos(x - 1, y, z),
							new BlockPos(x + 1, y, z),
							new BlockPos(x, y, z - 1),
							new BlockPos(x, y, z + 1),
							new BlockPos(x2 - 1, y2, z2),
							new BlockPos(x2 + 1, y2, z2),
							new BlockPos(x2, y2, z2 - 1),
							new BlockPos(x2, y2, z2 + 1),
							new BlockPos(x, y + 1, z),
							new BlockPos(x2, y2 + 1, z2),
					};
					
					for (BlockPos pp: arr) {
						GL11.glColor4f(0F, 1F, 1F, 1F);
						int block_x = pp.getX();
						int block_y = pp.getY();
						int block_z = pp.getZ();
						if ((block_x == x && block_y == y && block_z == z) || (block_x == x2 && block_y == y2 && block_z == z2)) continue;
						
						IBlockState block_state = world.getBlockState(pp);
						if (block_state == null) continue;
						Block block = block_state.getBlock();
						if (block == null) continue;
						if (block != Blocks.obsidian) continue;
						bed.obsidianPoses.add(pp);
					}
					
					bedsFound.add(bed);
				}
			}
			pos = pos.offset(EnumFacing.SOUTH);
			pos = pos.offset(EnumFacing.EAST, -max_x -max_x - 1);
		}
	}
	
	public void drawBlockLine(WorldRenderer worldrenderer, int i1, int j1, int i2, int j2, int chunk_size, Chunk zero_chunk, Pos playerPos, double player_angle, float scaling_coef, double cos, double sin) {		 
		 int chunkOffsetI1 = j1 / 16 - (chunk_size - 1)/2 - 1;
		 int chunkOffsetI2 = j2 / 16 - (chunk_size - 1)/2 - 1;
		 int chunkOffsetJ1 = i1 / 16 - (chunk_size - 1)/2 - 1;
		 int chunkOffsetJ2 = i2 / 16 - (chunk_size - 1)/2 - 1;
		 int block_x2 = (zero_chunk.xPosition + chunkOffsetI1) * 16 + (j1 % 16);
		 int block_x1 = (zero_chunk.xPosition + chunkOffsetI2) * 16 + (j2 % 16);
		 
		 int block_z2 = (zero_chunk.zPosition + chunkOffsetJ1) * 16 + (i1 % 16);
		 int block_z1 = (zero_chunk.zPosition + chunkOffsetJ2) * 16 + (i2 % 16);
		 
		 if (block_x2 < block_x1) {
			 int temp = block_x2;
			 block_x2 = block_x1;
			 block_x1 = temp;
		 }
		 
		 //ChatSender.addText("&b" + block_x1 + ", " + block_z1 + " -> " + block_x2 + " " + block_z2);
		 drawBlock(worldrenderer, block_x1, block_z1, block_x2 + 1, block_z2 + 1, playerPos, scaling_coef, cos, sin);
		 
	}

	public ArrayList<Long> avg_ms = new ArrayList<Long>();
	
	private int strenth_potion_labels_count = 0;
	private final byte[] terrainMask = new byte[TERRAIN_GRID_SIZE * TERRAIN_GRID_SIZE];
	private final ArrayList<PosItem> itemPositions = new ArrayList<PosItem>();
	private int itemPositionCount;
	private final Pos playerPosition = new Pos(0, 0, 0);
	private final Pos markerPosition = new Pos(0, 0, 0);
	
	public void draw(Minecraft mc) {
		//initGameBeds();
		if (!isActive) return;
		if (textureManager == null) textureManager = mc.getTextureManager();
		//mc.fontRendererObj.dra
		//drawCircle();
		int color_bg = getColor("00000011");
		int color_bg2 = getColor("000000aa");
		int color_dot = getColor("ff0000ff");
		int color_player = getColor("00ff00ff");
		int dot_size = 2;
		strenth_potion_labels_count = 0;
		//GL11.glColor4f(0F, 0F, 0F, 0F);
		//drawRect(topX, topY, botX, botY, color_bg);
		
		EntityPlayerSP player = mc.thePlayer;
		playerPosition.x = player.posX;
		playerPosition.y = player.posY;
		playerPosition.z = player.posZ;
		Pos playerPos = playerPosition;
		float player_angle = player.rotationYaw;
		double player_angle_radians = Math.toRadians(180 - player_angle);
		double player_angle_cos = Math.cos(player_angle_radians);
		double player_angle_sin = Math.sin(player_angle_radians);
		int scaling = MINIMAP_SCALING;
		if (zoom == 1) scaling = scaling / 2;
		
		long time_start = new Date().getTime();
		
		Tessellator tessellator = Tessellator.getInstance();
	    WorldRenderer worldrenderer = tessellator.getWorldRenderer();     
	    GlStateManager.pushMatrix();
	    GlStateManager.scale(1F / MINIMAP_QUALITY_MULTIPLIER, 1F / MINIMAP_QUALITY_MULTIPLIER, 1F / MINIMAP_QUALITY_MULTIPLIER);
	    //GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
	    
	    worldrenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
	    GlStateManager.disableTexture2D();
	    GlStateManager.enableAlpha();
	    GlStateManager.color(0.5f, 0.5f, 0.5f, 1f);
	    
	    
	    
	    try {
	    	
	    	int chunk_size = TERRAIN_CHUNK_SIZE; // НЕПАРНОЕ!!
		    int n = chunk_size * 16;
		    Arrays.fill(terrainMask, (byte)0);
		    int playerChunkX = ((int)Math.floor(player.posX)) >> 4;
		    int playerChunkZ = ((int)Math.floor(player.posZ)) >> 4;
		    Chunk zero_chunk = mc.theWorld.getChunkFromChunkCoords(playerChunkX, playerChunkZ);
		    
		   
		    for (int i = 0; i < chunk_size; i++) {
		    	for (int j = 0; j < chunk_size; j++) {
			    	 Chunk chunk = mc.theWorld.getChunkFromChunkCoords(zero_chunk.xPosition + i - (chunk_size + 1) / 2, zero_chunk.zPosition + j - (chunk_size + 1) / 2);
				     int[] heights = chunk.getHeightMap();
			    	 
			    	 for (int x = 0; x < 16; x++) {
					 	 for (int z = 0; z < 16; z++) {
					 		
							 int h = heights[x * 16 + z];
							 if (h > 0) terrainMask[(x + j * 16) * n + z + i * 16] = 1;
						 }
					 }
			     }
		    }
		    
		    int pi = -1;
		    int pj = -1;
		    
		    int multiplier = MINIMAP_QUALITY_MULTIPLIER;
		    float scaling_coef = map_size / (float)(scaling * 2) * multiplier;
		    
		    double angle = Math.toRadians(180 - player_angle);

		    for (int i = 0; i < n; i++) {
			     for (int j = 0; j < n; j++) {
			    	 
			    	 
			    	int matrixIndex = i * n + j;
			    	if (terrainMask[matrixIndex] == 1) {
			    		 
				   		 int chunkOffsetI = j / 16 - (chunk_size - 1)/2 - 1;
						 int chunkOffsetJ = i / 16 - (chunk_size - 1)/2 - 1;
						 int block_x = (zero_chunk.xPosition + chunkOffsetI) * 16 + (j % 16);
						 int block_z = (zero_chunk.zPosition + chunkOffsetJ) * 16 + (i % 16);
						 
						 
							
						 double tx = block_x - player.posX;
						 double tz = block_z - player.posZ;
						
						 double screenDeltaX = (tx * player_angle_cos - tz * player_angle_sin);
						 double screenDeltaZ = (tx * player_angle_sin + tz * player_angle_cos);
	
						 if (screenDeltaX  > scaling || screenDeltaX < -scaling || screenDeltaZ  > scaling || screenDeltaZ < -scaling) terrainMask[matrixIndex] = 0;						 
			 
			    	 }			    	 			    	 
			    	 
			    	if (terrainMask[matrixIndex] == 1) {
			    		 if (pi == -1 && pj == -1) {
			    			 pi = i;
			    			 pj = j;
			    			 continue;
			    		 }
			    		 
			    		 if (j == n - 1) {
			    			 drawBlockLine(worldrenderer, pi, pj, i, j, chunk_size, zero_chunk, playerPos, angle, scaling_coef, player_angle_cos, player_angle_sin);
			    			 pi = -1;
			    			 pj = -1;
			    		 }
			    		
			    	 } else {
			    		 if (pi != -1 && pj != -1) {
			    			 drawBlockLine(worldrenderer, pi, pj, i, j - 1, chunk_size, zero_chunk, playerPos, angle, scaling_coef, player_angle_cos, player_angle_sin);
			    			 pi = -1;
			    			 pj = -1;
			    		 }
			    	 }

			     }
		    }
		    
	    } catch (Exception e) {
			e.printStackTrace();
		}
	
	    tessellator.draw();
	    GlStateManager.enableTexture2D();
	    GlStateManager.popMatrix();
	    
	    
	    /*avg_ms.add(new Date().getTime() - time_start);
	    if (avg_ms.size() > 1000) avg_ms.remove(0);
	    
	    long temp_cnt = 0;
	    for (long x: avg_ms) temp_cnt += x * 1000;
	    float avg_ms_value = temp_cnt * 1f / avg_ms.size();	    
	    String temp_text = "&b" + (int)((avg_ms_value - avg_ms_value % 10)) + " mms";
	    mc.fontRendererObj.drawString(ColorCodesManager.replaceColorCodesInString(temp_text), topX + map_size/2 - mc.fontRendererObj.getStringWidth(temp_text)/2, botY - 10, new Color(1f, 1f, 1f, 1f).getRGB());*/
	    
	    int frame_size = frame_border_size;
	    if (zoom == 1) frame_size *= 2;
	    if (isFrameActive) {
		    
		    drawRect(topX, topY - frame_size, botX, topY + frame_size, minimap_frame_color);
		    drawRect(topX, botY - frame_size, botX, botY + frame_size, minimap_frame_color);
		    
		    drawRect(topX - frame_size, topY - frame_size, topX + frame_size, botY + frame_size, minimap_frame_color);
		    drawRect(botX - frame_size, topY - frame_size, botX + frame_size, botY + frame_size, minimap_frame_color);
		}
	    
	    GlStateManager.color(1f, 1f, 1f, 1f);

	    if (zoom == 1) mc.fontRendererObj.drawString("x2", botX + frame_border_size - mc.fontRendererObj.getStringWidth("x2") - 1, botY + frame_border_size - mc.fontRendererObj.FONT_HEIGHT, new Color(1f, 1f, 1f, 0.1f).getRGB());
	    
		List<EntityPlayer> entities = mc.theWorld.playerEntities;
		
		BWBed game_bed = Main.chatListener.GAME_BED;
		
		for (EntityPlayer en: entities) {
			if (en == null || en.getName() == null|| en.getDisplayName() == null) continue;
			if (en.getName().equals(player.getName())) continue; 
			if (isHidePlayersOnShift == true && en.isSneaking()) continue;
			Pos blockPos = setMarkerPosition(en.posX, en.posY, en.posZ);
			double motionY = en.prevPosY - en.posY;
			if (game_bed != null && en.posY < game_bed.part1_posY - 5 && motionY > 1) {
				continue;
			}
			
			int texture_offset_x = 2;
			int texture_offset_y = 2;

        	TEAM_COLOR team_color = MyChatListener.getEntityTeamColor(en);
        	
    		if (team_color == TEAM_COLOR.RED) {
    			texture_offset_x = 0;
    			texture_offset_y = 0;
    		} else if (team_color == TEAM_COLOR.YELLOW) {
    			texture_offset_x = 1;
    			texture_offset_y = 0;
    		} else if (team_color == TEAM_COLOR.GREEN) {
    			texture_offset_x = 2;
    			texture_offset_y = 0;
    		} else if (team_color == TEAM_COLOR.AQUA) {
    			texture_offset_x = 0;
    			texture_offset_y = 1;
    		} else if (team_color == TEAM_COLOR.BLUE) {
    			texture_offset_x = 1;
    			texture_offset_y = 1;
    		} else if (team_color == TEAM_COLOR.PINK) {
    			texture_offset_x = 2;
    			texture_offset_y = 1;
    		} else if (team_color == TEAM_COLOR.WHITE) {
    			texture_offset_x = 0;
    			texture_offset_y = 2;
    		} else if (team_color == TEAM_COLOR.GRAY) {
    			texture_offset_x = 1;
    			texture_offset_y = 2;
    		} else if (team_color == TEAM_COLOR.NONE) {
    			texture_offset_x = 2;
    			texture_offset_y = 2;
    		}

    		
    		//find in scanned players
    		BWPlayer bwplayer = null;
    		for (BWPlayer p: Main.myTickEvent.getCurrentScannedPlayers()) {
    			if (p.en == en) {
    				bwplayer = p;
    				break;
    			}
    		}
    		mc.renderEngine.bindTexture(resourceLoc_enemy);  
    		
			drawPoint(blockPos, playerPos, bwplayer, scaling, player_angle, en.rotationYaw, en.rotationPitch, team_color, texture_offset_x, texture_offset_y, false);
		}
								
		drawDynamicEntityMarkers(mc.theWorld, playerPos, scaling, player_angle);
		
		
		GlStateManager.color(1f, 1f, 1f, 1f);
		mc.renderEngine.bindTexture(resourceLoc_enemy);
		drawPoint(playerPos, playerPos, null, scaling, player_angle, player_angle, mc.thePlayer.rotationPitch, TEAM_COLOR.NONE, 0, 0, true); //player white dot
		//drawPoint(new Pos(91, 292, -11), playerPos, null, scaling, player_angle, 0, 0, TEAM_COLOR.RED, 0, 0, false); 
		
		
		
		//draw resources
		
		
		GlStateManager.pushMatrix();
	    GlStateManager.scale(0.5F, 0.5F, 0.5F);

	     
		
		List<Entity> items = mc.theWorld.loadedEntityList;
		int cnt_emerald = 0;
		int cnt_diamond = 0;
		
		itemPositionCount = 0;
		for (Entity en: items) {
			if (en instanceof EntityItem) {
				EntityItem itemEntity = (EntityItem) en;
				Item item = itemEntity.getEntityItem().getItem();
				if (item == null) continue;
				
				if (MyChatListener.GAME_BED != null && en.posY < MyChatListener.GAME_BED.part1_posY - 30) {
					continue;
				}
				
				int item_type = -1;
				if (item == Items.emerald) {	
					item_type = 0;
				} else if (item == Items.diamond) {
					item_type = 1;
				} else if (item == Items.gold_ingot) {
					item_type = 2;
				} else if (item == Items.iron_ingot) {
					item_type = 3;
				}
				if (item_type != -1) {
					int cnt = itemEntity.getEntityItem().stackSize;
					
					//find similar
					boolean isFound = false;
					for (int itemIndex = 0; itemIndex < itemPositionCount; itemIndex++) {
						PosItem p = itemPositions.get(itemIndex);
						if (p.type != item_type) continue;
						double deltaX = p.x - en.posX;
						double deltaZ = p.z - en.posZ;
						if (deltaX * deltaX + deltaZ * deltaZ < 9) {
							p.cnt += cnt;
							isFound = true;
							break;
						}
					}
					if (!isFound) {
						PosItem position;
						if (itemPositionCount < itemPositions.size()) {
							position = itemPositions.get(itemPositionCount);
							position.x = en.posX;
							position.y = en.posY;
							position.z = en.posZ;
							position.type = item_type;
							position.cnt = cnt;
						} else {
							position = new PosItem(en.posX, en.posY, en.posZ, item_type, cnt);
							itemPositions.add(position);
						}
						itemPositionCount++;
					}
				}
			}
		}
		
		for (int itemIndex = 0; itemIndex < itemPositionCount; itemIndex++) {
			PosItem p = itemPositions.get(itemIndex);
			//if (p.type == 1 && p.cnt < 4) continue;
			if (p.type == 2 && p.cnt < 6) continue;
			if (p.type == 3 && p.cnt < 32) continue;
			
			drawItemResouce(setMarkerPosition(p.x, p.y, p.z), playerPos, scaling, player_angle, p.type, p.cnt);
		}
		
		GlStateManager.popMatrix();
		
		
		
		long t = new Date().getTime();
		if (t - time_last_bed_scanned > TIME_BED_SCAN) {
			minimap_scan_is_active = true;						
			time_last_bed_scanned = t;			
		}
		
		myScan();
		
		//initGameBeds();
		if (bedsFound.size() > 0) {
			
			Iterator<MyBed> i = bedsFound.iterator();
			while (i.hasNext()) {
				MyBed b = i.next();		
				if (t - b.t > TIME_BED_SCAN * 2) i.remove();
				
				mc.renderEngine.bindTexture(resourceLoc_enemy);
				drawBed(b.pos, playerPos, scaling, player_angle, dot_size, b.isPlayersBed);
				
				String text = "OBBY";
				String additional = "" + (int)((b.obsidianPoses.size() / 8f) * 100f) + "%";
				
				if (b.obsidianPoses.size() == 0) continue;
				
				
				GlStateManager.pushMatrix();
				float text_size = 0.5f;
			    GlStateManager.scale(text_size, text_size, text_size);
				
				drawTextOnMap(new Pos(b.pos.getX(), b.pos.getY(), b.pos.getZ()), playerPos, scaling, text_size, player_angle, text, getColor("bb2affff"), 0, 3);

				if (b.obsidianPoses.size() < 8) {
					drawTextOnMap(new Pos(b.pos.getX(), b.pos.getY(), b.pos.getZ()), playerPos, scaling, text_size, player_angle, additional, getColor("d787fcff"), 13, 3);
				}
				GlStateManager.popMatrix();
				
			}
		}
		
		
		
	}
	
	private void drawDynamicEntityMarkers(World world, Pos playerPos, int scaling, float playerAngle) {
		for (Entity entity : world.loadedEntityList) {
			if (entity instanceof EntityDragon) {
				EntityDragon dragon = (EntityDragon)entity;
				Pos position = setMarkerPosition(dragon.posX, dragon.posY, dragon.posZ);
				mc.renderEngine.bindTexture(resourceLoc_enemy);
				drawPoint(position, playerPos, null, scaling, playerAngle, 180 + dragon.rotationYaw, dragon.rotationPitch, TEAM_COLOR.NONE, 2, 1, false);
				GlStateManager.pushMatrix();
				float textSize = 0.5f;
				GlStateManager.scale(textSize, textSize, textSize);
				drawTextOnMap(position, playerPos, scaling, textSize, playerAngle, "Dragon", getColor("ff00ffff"), 0, -9);
				drawTextOnMap(position, playerPos, scaling, textSize, playerAngle, "" + (int)(dragon.getHealth() / 2) + "%", getColor("ff0000ff"), 0, 5);
				GlStateManager.popMatrix();
			} else if (entity instanceof EntityFireball) {
				EntityFireball fireball = (EntityFireball)entity;
				if (fireball.posY > 300 || fireball.posY < -10) continue;
				mc.renderEngine.bindTexture(resourceLoc_enemy);
				drawTexture(setMarkerPosition(fireball.posX, fireball.posY, fireball.posZ), playerPos, scaling, playerAngle, 0, 205, 244, 12, 12, 0.4f, 0f, 0f);
			} else if (entity instanceof EntityEnderPearl) {
				EntityEnderPearl pearl = (EntityEnderPearl)entity;
				if (pearl.posY > 300 || pearl.posY < -10) continue;
				mc.renderEngine.bindTexture(resourceLoc_enemy);
				drawTexture(setMarkerPosition(pearl.posX, pearl.posY, pearl.posZ), playerPos, scaling, playerAngle, 0, 192, 243, 13, 13, 0.4f, 0f, 0f);
			} else if (entity instanceof EntityArrow) {
				EntityArrow arrow = (EntityArrow)entity;
				if (arrow.posY > 300 || arrow.posY < -10 || arrow.lastTickPosY == arrow.posY) continue;
				double deltaX = arrow.lastTickPosX - arrow.posX;
				double deltaZ = arrow.lastTickPosZ - arrow.posZ;
				float yaw = (float)Math.toDegrees(Math.atan2(deltaZ, deltaX)) + 90;
				mc.renderEngine.bindTexture(resourceLoc_enemy);
				drawTexture(setMarkerPosition(arrow.posX, arrow.posY, arrow.posZ), playerPos, scaling, playerAngle, yaw - playerAngle - 45, 179, 243, 13, 13, 0.3f, 0f, 0f);
			} else if (entity instanceof EntityTNTPrimed) {
				EntityTNTPrimed tnt = (EntityTNTPrimed)entity;
				if (tnt.posY > 300 || tnt.posY < -10) continue;
				Pos position = setMarkerPosition(tnt.posX, tnt.posY, tnt.posZ);
				mc.renderEngine.bindTexture(resourceLoc_enemy);
				drawTexture(position, playerPos, scaling, playerAngle, 0, 139, 240, 16, 16, 0.3f, 0f, 0f);
				float time = Math.max(tnt.fuse / 20f, 0f);
				GlStateManager.pushMatrix();
				float textSize = 0.4f;
				GlStateManager.scale(textSize, textSize, textSize);
				float green = Math.min(tnt.fuse / 50f, 1f);
				int color = 0xff000000 | ((int)((1f - green) * 255) << 16) | ((int)(green * 255) << 8);
				drawTextOnMap(position, playerPos, scaling, textSize, playerAngle, timeFormatter.format(time), color, 0, -8);
				GlStateManager.popMatrix();
			}
		}
	}

	public static int getTopX() {
		return topX;
	}

	public static int getTopY() {
		return topY;
	}

	public static int getFrameBorderSize() {
		return Math.max(0, frame_border_size);
	}

	/** Saves the layout selected in the visual editor. */
	public static void saveEditorLayout(int newTopX, int newTopY, int newMapSize) {
		map_size = Math.max(50, Math.min(500, newMapSize));
		frame_border_size = (int)(map_size / MINIMAP_SCALING / 2) + 1;
		if (!isFrameActive) frame_border_size = 0;
		int border = getFrameBorderSize();
		ScaledResolution resolution = new ScaledResolution(mc);
		int maxX = Math.max(border, resolution.getScaledWidth() - map_size - border);
		int maxY = Math.max(border, resolution.getScaledHeight() - map_size - border);
		topX = Math.max(border, Math.min(newTopX, maxX));
		topY = Math.max(border, Math.min(newTopY, maxY));
		botX = topX + map_size;
		botY = topY + map_size;

		// Store the position from the upper-left corner. This keeps it stable when the window is resized.
		Main.clientConfig.get(CONFIG_MSG.MINIMAP_X.text).set(Integer.toString(topX - border));
		Main.clientConfig.get(CONFIG_MSG.MINIMAP_Y.text).set(Integer.toString(topY - border));
		Main.clientConfig.get(CONFIG_MSG.MINIMAP_SIZE.text).set(map_size);
		Main.saveConfig();
	}

	private Pos setMarkerPosition(double x, double y, double z) {
		markerPosition.x = x;
		markerPosition.y = y;
		markerPosition.z = z;
		return markerPosition;
	}

	void drawItemResouce(Pos pos, Pos playerPos, int scaling, float player_angle, int item_idx, int item_count) {
		if (item_count <= 0) return;
		int color = getColor("ffffffff");
		double offsetX = 0;
		double offsetZ = 0;
		if (item_idx == 0) {
			//emerald
			color = getColor("00ff00ff");
		} else if (item_idx == 1) {
			//diamond
			color = getColor("00ffffff");
		} else if (item_idx == 2) {
			//gold
			color = getColor("ffea00ff");
			offsetZ = -4;
		} else if (item_idx == 3) {
			//iron
			color = getColor("e0e0e0ff");
			offsetZ = 2;
		}
		
		String text = "" + item_count;
		if (item_count > 64) {
			text = "" + timeFormatter.format(item_count / 64f) + "s";
		}
		
		drawTextOnMap(pos, playerPos, scaling, 0.5f, player_angle, text, color, offsetX, offsetZ);
	}
	
	void drawTextOnMap(Pos pos, Pos playerPos, int scaling, float matrix_scale, float player_angle, String text, int color, double offsetX, double offsetZ) {
		
		double deltaX = pos.x - playerPos.x;
		double deltaY = pos.y - playerPos.y;
		double deltaZ = pos.z - playerPos.z;
		
		float multiplier = 1f / matrix_scale;
		
		int cx = (int)((topX + botX) / 2 * multiplier);
		int cy = (int)((topY + botY) / 2 * multiplier);
		
		
		float scaling_coef = map_size / (float)(scaling * 2) * multiplier;
		float screenDeltaX = (float)(deltaX * scaling_coef);
		float screenDeltaZ = (float)(deltaZ * scaling_coef);

		double x1 = screenDeltaX;
		double z1 = screenDeltaZ;
		double angle = Math.toRadians(180 - player_angle);
		screenDeltaX = (float)(x1 * Math.cos(angle) - z1 * Math.sin(angle)) + cx;
		screenDeltaZ = (float)(x1 * Math.sin(angle) + z1 * Math.cos(angle)) + cy;
		
		screenDeltaX += offsetX * scaling_coef;
		screenDeltaZ += offsetZ * scaling_coef;
		
		if (Math.abs(screenDeltaX - cx) > map_size / 2 * multiplier) return;
		if (Math.abs(screenDeltaZ - cy) > map_size / 2 * multiplier) return;
		
		drawCenteredString(mc.fontRendererObj, text, (int)screenDeltaX, (int)screenDeltaZ, color);
	}

	private void drawBlock(WorldRenderer worldrenderer, int x1, int z1, int x2, int z2, Pos playerPos, float scaling_coef, double cos, double sin) {
		double deltaX = x1 + (x2 - x1)/2 + ((x2 - x1) % 2) * 0.5 - playerPos.x;
		double deltaZ = z1 + (z2 - z1)/2 - playerPos.z;
		
		int cx = (topX + botX) / 2 * MINIMAP_QUALITY_MULTIPLIER;
		int cy = (topY + botY) / 2 * MINIMAP_QUALITY_MULTIPLIER;
		
		float tx = (float)(deltaX * scaling_coef);
		float tz = (float)(deltaZ * scaling_coef);
		
		
		
		float dot_size = (scaling_coef);

		
		double screenDeltaX = (float)(tx * cos - tz * sin) + cx;
		double screenDeltaZ = (float)(tx * sin + tz * cos) + cy;

		
		double minimapX1 = topX * MINIMAP_QUALITY_MULTIPLIER;
		double minimapY1 = topY * MINIMAP_QUALITY_MULTIPLIER;
		double minimapX2 = botX * MINIMAP_QUALITY_MULTIPLIER;
		double minimapY2 = botY * MINIMAP_QUALITY_MULTIPLIER;
		//ChatSender.addText(screenDeltaX);
		
		double w = (x2 - x1) * dot_size;
		double h = (z2 - z1) * dot_size;
		double topX1 = screenDeltaX - (w/2);
		double topY1 = screenDeltaZ - (h/2);
		double topX2 = screenDeltaX - (w/2);
		double topY2 = screenDeltaZ + (h/2);

		double topX3 = screenDeltaX + (w/2);
		double topY3 = screenDeltaZ + (h/2);
		double topX4 = screenDeltaX + (w/2);
		double topY4 = screenDeltaZ - (h/2);
		//rotate		
		
	

		double halfWidth = w / 2;
		double halfHeight = h / 2;
		topX1 = screenDeltaX - halfWidth * cos + halfHeight * sin;
		topY1 = screenDeltaZ - halfWidth * sin - halfHeight * cos;
		topX2 = screenDeltaX - halfWidth * cos - halfHeight * sin;
		topY2 = screenDeltaZ - halfWidth * sin + halfHeight * cos;
		topX3 = screenDeltaX + halfWidth * cos - halfHeight * sin;
		topY3 = screenDeltaZ + halfWidth * sin + halfHeight * cos;
		topX4 = screenDeltaX + halfWidth * cos + halfHeight * sin;
		topY4 = screenDeltaZ + halfWidth * sin - halfHeight * cos;
		
		//ChatSender.addText(minimapX2);
//		topX1 = Math.max(minimapX1, Math.min(minimapX2, topX1));
//		topX2 = Math.max(minimapX1, Math.min(minimapX2, topX2));
//		topX3 = Math.max(minimapX1, Math.min(minimapX2, topX3));
//		topX4 = Math.max(minimapX1, Math.min(minimapX2, topX4));
//		topY1 = Math.max(minimapY1, Math.min(minimapY2, topY1));
//		topY2 = Math.max(minimapY1, Math.min(minimapY2, topY2));
//		topY3 = Math.max(minimapY1, Math.min(minimapY2, topY3));
//		topY4 = Math.max(minimapY1, Math.min(minimapY2, topY4));			

		//ChatSender.addText(ix1 + " " + iy1);
		
		
		
		worldrenderer.pos(topX1, topY1, 0.0).endVertex();
	    worldrenderer.pos(topX2, topY2, 0.0).endVertex();
	    worldrenderer.pos(topX3, topY3, 0.0).endVertex();
	    worldrenderer.pos(topX4, topY4, 0.0).endVertex();
	    
	    
	    
		//drawRect(0, 0, 10, 10, 0);
	}
	
	private void drawPoint(Pos pos, Pos playerPos, BWPlayer bwplayer, int scaling, float player_angle, float enemy_angle, float enemy_pitch, TEAM_COLOR team_color, int texture_offset_x, int texture_offset_y, boolean isMainPlayer) {

		if (isMainPlayer) {
			int cx = topX + map_size/2;
			int cy = topY + map_size/2;
			
			 GlStateManager.pushMatrix();
			 //GlStateManager.rotate(50F, 0F, 0F, 0F);
			 GlStateManager.translate(cx, cy, 0);
			 GlStateManager.scale(0.3F, 0.3F, 0.3F);
			 GlStateManager.color(1f, 1f, 1f, 1f);
			 drawTexturedModalRect(-7, -7, 243, 243, 13, 13);
			 GlStateManager.popMatrix();
			
			
			
			return;
		}
		
		double deltaX = pos.x - playerPos.x;
		double deltaY = pos.y - playerPos.y;
		double deltaZ = pos.z - playerPos.z;
		
		
		int cx = (topX + botX) / 2;
		int cy = (topY + botY) / 2;
		
		float scaling_coef = map_size / (float)(scaling * 2);
		float screenDeltaX = (float)(deltaX * scaling_coef);
		float screenDeltaZ = (float)(deltaZ * scaling_coef);
		
		
		//rotate
		double x1 = screenDeltaX;
		double z1 = screenDeltaZ;
		double angle = Math.toRadians(180 - player_angle);

		double x2 = x1 * Math.cos(angle) - z1 * Math.sin(angle);
		double y2 = x1 * Math.sin(angle) + z1 * Math.cos(angle);

		screenDeltaX = (float)x2;
		screenDeltaZ = (float)y2;
		
		
		float padding = 1.5f;
		if (Math.abs(screenDeltaX) > map_size/2 + padding || Math.abs(screenDeltaZ) > map_size/2 + padding) return;
		
		
		//draw look
		
		enemy_angle -= player_angle;
		
		
		try {
			 GlStateManager.pushMatrix();
			 GlStateManager.translate(cx + screenDeltaX, cy + screenDeltaZ, 0);
			 GlStateManager.rotate(enemy_angle, 0F, 0F, 1F);
			 GlStateManager.scale(0.07F, 0.07F, 0.07F);
			 GlStateManager.color(1f, 1f, 1f, 1f);
			 drawTexturedModalRect(-40, -40, 80 * texture_offset_x, 80 * texture_offset_y, 80, 80);
			 GlStateManager.popMatrix();
			
			 
			 
			 
			 //heightMap
			 if (team_color != TEAM_COLOR.NONE) {
				 GlStateManager.pushMatrix();
				 GlStateManager.scale(0.5F, 0.5F, 0.5F);
				 
				 Color color = Main.particleController.getParticleColorForTeam(team_color);
				 if (team_color == TEAM_COLOR.GRAY) color = new Color(1f, 1f, 1f, 1f);
				 if (team_color == TEAM_COLOR.BLUE) color = new Color(0f, 0.5f, 1f, 1f);
				 
				 if (showNicknames && bwplayer != null) {
					
					 drawTextOnMap(pos, playerPos, scaling, 0.5f, player_angle, "" + bwplayer.name, color.getRGB(), 0, 3);
					 
				 } else if (show_heights) {
				 
					 BWBed game_bed = Main.chatListener.GAME_BED;
					 int bed_pos_y = (int)pos.y;
					 if (game_bed != null) bed_pos_y = game_bed.part1_posY;
					 
					 int height_difference = (int)(pos.y - playerPos.y);
					 int player_height_above_bed = (int)(playerPos.y - bed_pos_y);
	
					 double speedY = mc.thePlayer.posY - mc.thePlayer.prevPosY;
					 if ((player_height_above_bed > 5 || height_difference >= 5) && speedY > -1) {
						
					 	drawTextOnMap(pos, playerPos, scaling, 0.5f, player_angle, "" + height_difference, color.getRGB(), 0, 3);
					 }
				 }
				 
				 GlStateManager.popMatrix();
			 }
			 
			 //bwplayer = new BWPlayer(mc.thePlayer, "DimChig", BWItemsHandler.findItem("bow", ""), 3, BWItemArmourLevel.LEATHER, 0, 0, 0, 0);
			 
			 
			 
			 if (show_additional_information && team_color != TEAM_COLOR.NONE && bwplayer != null && bwplayer.item_in_hand != null) {
				 //draw items in hands
				 BWItemType item_type = bwplayer.item_in_hand.type;

				 float offsetY = -5f;	 
				 mc.renderEngine.bindTexture(resourceLoc_enemy);	
				 
				 String text_item_count = "";
				 int text_color = 0;
				 float offsetY2 = 0;
				 
				 if (item_type == BWItemType.BLOCK_WOOL && enemy_pitch > 75) {
					 Tessellator tessellator = Tessellator.getInstance();
				     WorldRenderer worldrenderer = tessellator.getWorldRenderer();     
				     GlStateManager.pushMatrix();
				     GlStateManager.translate(cx + screenDeltaX, cy + screenDeltaZ, 0);
				     GlStateManager.scale(0.2F, 0.2F, 0.2F);
				     //GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
				     worldrenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
				     GlStateManager.disableTexture2D();
				     Color color = Main.playerFocus.getColorByTeam(team_color);
				     GlStateManager.color(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, 1f);
				     
				     float width = 30;
				     float height = 4;
				     
				     float px1 = -width/2f;
				     float py1 = -height/2f - 20;
				     float px2 = px1 + width;
				     float py2 = py1 + height;
				     
				     worldrenderer.pos(px1 , py1, 0.0).endVertex();					 					 
					 worldrenderer.pos(px1, py2, 0.0).endVertex();
					 worldrenderer.pos(px2, py2, 0.0).endVertex();
					 worldrenderer.pos(px2, py1, 0.0).endVertex();
					 
					 
				     
				     
				     tessellator.draw();
				     GlStateManager.enableTexture2D();
				     GlStateManager.popMatrix();

				 } else if (item_type == BWItemType.BOW) {
	 				 drawTexture(pos, playerPos, scaling, player_angle, 0, 164, 240, 15, 16, 0.3f, 0, offsetY);
				 } else if (item_type == BWItemType.PEARL) {
					 drawTexture(pos, playerPos, scaling, player_angle, 0, 192, 243, 13, 13, 0.3f, 0, offsetY);
					 
				     text_item_count =  "" + bwplayer.item_in_hand_amount;
				     text_color = getColor("2ccdb1ff");
				     offsetY2 = offsetY - 2;
				 } else if (item_type == BWItemType.FIREBALL) {
					 drawTexture(pos, playerPos, scaling, player_angle, 0, 205, 244, 12, 12, 0.35f, 0, offsetY);
					 
					 text_item_count =  "" + bwplayer.item_in_hand_amount;
				     text_color = getColor("eb8517ff");
				     offsetY2 = offsetY - 2;
				 } else if (item_type == BWItemType.POTION_STRENGTH) {
					 ScaledResolution sr = new ScaledResolution(mc);
			         int screen_width = sr.getScaledWidth();
			         int screen_height = sr.getScaledHeight(); 
					 
					 drawTexture(pos, playerPos, scaling, player_angle, 0, 155, 243, 9, 13, 0.5f, 0, offsetY - 2);
					 String potion_text = "&4&lСИЛА &8у &" + CustomScoreboard.getCodeByTeamColor(bwplayer.team_color) + bwplayer.name;					 
					 mc.fontRendererObj.drawStringWithShadow(ColorCodesManager.replaceColorCodesInString(potion_text), screen_width/2 - mc.fontRendererObj.getStringWidth(ColorCodesManager.removeColorCodes(potion_text))/2, screen_height / 2 + mc.fontRendererObj.FONT_HEIGHT + 10 * strenth_potion_labels_count, new Color(1f, 1f, 1f, 1f).getRGB());
					 strenth_potion_labels_count++;
				 } else if (item_type == BWItemType.DIAMOND) {
					 drawTexture(pos, playerPos, scaling, player_angle, 0, 116, 243, 12, 13, 0.4f, 0, offsetY - 1);
					 
					 text_item_count =  "" + bwplayer.item_in_hand_amount;
				     text_color = getColor("8cf4e2ff");
				     offsetY2 = offsetY - 2;
				 } else if (item_type == BWItemType.EMERALD) {
					 drawTexture(pos, playerPos, scaling, player_angle, 0, 128, 242, 11, 14, 0.46f, 0, offsetY - 1);
					 
					 text_item_count =  "" + bwplayer.item_in_hand_amount;
				     text_color = getColor("2bdf64ff");
				     offsetY2 = offsetY - 2;
				 }	
				 
				 if (text_item_count.length() > 0) {
					 GlStateManager.pushMatrix();
					 float text_size = 0.5f;
				     GlStateManager.scale(text_size, text_size, text_size);
					 drawTextOnMap(pos, playerPos, scaling, text_size, player_angle, "" + bwplayer.item_in_hand_amount, text_color, 6, offsetY2);
					 GlStateManager.popMatrix();
				 }
			 }
			 
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	private void drawTexture(Pos pos, Pos playerPos, int scaling, float player_angle, float rotation, int tex_x, int tex_y, int tex_size_x, int tex_size_y, float tex_scale, float offsetX, float offsetY) {
		double deltaX = pos.x - playerPos.x;
		double deltaY = pos.y - playerPos.y;
		double deltaZ = pos.z - playerPos.z;
		
		
		int cx = (topX + botX) / 2;
		int cy = (topY + botY) / 2;
		
		float scaling_coef = map_size / (float)(scaling * 2);
		float screenDeltaX = (float)(deltaX * scaling_coef);
		float screenDeltaZ = (float)(deltaZ * scaling_coef);
		//rotate
		double x1 = screenDeltaX;
		double z1 = screenDeltaZ;
		double angle = Math.toRadians(180 - player_angle);

		double x2 = x1 * Math.cos(angle) - z1 * Math.sin(angle);
		double y2 = x1 * Math.sin(angle) + z1 * Math.cos(angle);

		screenDeltaX = (float)x2;
		screenDeltaZ = (float)y2;
		
		 if (Math.abs(screenDeltaX) > map_size/2 || Math.abs(screenDeltaZ) > map_size/2) return;
		 GlStateManager.pushMatrix();
		 GlStateManager.translate(cx + screenDeltaX + offsetX, cy + screenDeltaZ + offsetY, 0);
		 GlStateManager.rotate(rotation, 0F, 0F, 1F);
		 GlStateManager.scale(tex_scale, tex_scale, tex_scale);
		 GlStateManager.color(1f, 1f, 1f, 1f);
		 drawTexturedModalRect(-tex_size_x/2f, -tex_size_y/2f, tex_x, tex_y, tex_size_x, tex_size_y);
		 GlStateManager.popMatrix();
		
	}
	
	
	private void drawBed(BlockPos pos, Pos playerPos, int scaling, float player_angle, int dot_size, boolean isPlayersBed) {
		dot_size = 6;

		double deltaX = pos.getX() - playerPos.x + 0.5;
		double deltaY = pos.getY() - playerPos.y;
		double deltaZ = pos.getZ() - playerPos.z + 0.5;
		
		
		int cx = (topX + botX) / 2;
		int cy = (topY + botY) / 2;
		
		float scaling_coef = map_size / (float)(scaling * 2);
		float screenDeltaX = (float)(deltaX * scaling_coef);
		float screenDeltaZ = (float)(deltaZ * scaling_coef);
		//rotate
		double x1 = screenDeltaX;
		double z1 = screenDeltaZ;
		double angle = Math.toRadians(180 - player_angle);

		double x2 = x1 * Math.cos(angle) - z1 * Math.sin(angle);
		double y2 = x1 * Math.sin(angle) + z1 * Math.cos(angle);

		screenDeltaX = (float)x2;
		screenDeltaZ = (float)y2;
		
		 if (Math.abs(screenDeltaX) > map_size/2 || Math.abs(screenDeltaZ) > map_size/2) return;
		 GlStateManager.pushMatrix();
		 GlStateManager.translate(cx + screenDeltaX, cy + screenDeltaZ, 0);
		 GlStateManager.rotate(0F, 0F, 0F, 1F);
		 GlStateManager.scale(0.25F, 0.25F, 0.25F);
		 GlStateManager.color(1f, 1f, 1f, 1f);
		 drawTexturedModalRect(-7, -7, 230 + (isPlayersBed ? -13 : 0), 243, 13, 13);
		 GlStateManager.popMatrix();
		
	}
	
	private int getColor(String hexColor) {
        Color colorNoAlpha = Color.decode("0x" + hexColor.substring(0, 6));
        int alpha = Integer.parseInt(hexColor.substring(6, 8), 16);
        return new Color(colorNoAlpha.getRed(), colorNoAlpha.getGreen(), colorNoAlpha.getBlue(), alpha).getRGB();
    }
}
